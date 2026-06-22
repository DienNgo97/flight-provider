package com.flightprovider.service;

import com.flightprovider.api.dto.SeatDto;
import com.flightprovider.api.dto.SeatHoldResponse;
import com.flightprovider.api.dto.SeatMapDto;
import com.flightprovider.domain.entity.Flight;
import com.flightprovider.domain.entity.FlightSeat;
import com.flightprovider.repository.FlightRepository;
import com.flightprovider.repository.FlightSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quan ly ghe theo so do (SeatLayout) + giu cho (HELD) / dat (BOOKED).
 * Ghe trong tinh DONG: = so do day du tru di cac ghe BOOKED + HELD con han.
 * available_seats cua Flight duoc dong bo lai sau moi thao tac de search() hien dung.
 */
@Service
public class SeatService {

    public static final int DEFAULT_HOLD_MINUTES = 20;

    private final FlightRepository flightRepository;
    private final FlightSeatRepository seatRepository;

    public SeatService(FlightRepository flightRepository, FlightSeatRepository seatRepository) {
        this.flightRepository = flightRepository;
        this.seatRepository = seatRepository;
    }

    /** So do day du cua 1 chuyen (kem gia + trang thai tung ghe). */
    @Transactional(readOnly = true)
    public SeatMapDto map(Long flightId) {
        Flight f = flightRepository.findById(flightId)
                .orElseThrow(() -> new IllegalArgumentException("Flight not found: " + flightId));
        Map<String, String> occupied = activeOccupancy(flightId);
        List<SeatDto> seats = new ArrayList<>(SeatLayout.TOTAL_SEATS);
        for (SeatLayout.Seat s : SeatLayout.all()) {
            String status = occupied.getOrDefault(s.code(), "FREE");
            BigDecimal price = SeatLayout.price(f.getBasePrice(), s.seatClass(), s.position());
            seats.add(new SeatDto(s.code(), s.row(), s.col(), s.seatClass(), s.position(), price, status));
        }
        String[] cols = new String[SeatLayout.COLS.length];
        for (int i = 0; i < cols.length; i++) cols[i] = String.valueOf(SeatLayout.COLS[i]);
        return new SeatMapDto(f.getId(), f.getFlightNumber(), f.getCurrency(),
                SeatLayout.ROWS, cols, SeatLayout.BUSINESS_ROWS, seats);
    }

    /** Giu cho cac ghe trong {minutes} phut (mac dinh 20). Nem SeatTakenException neu co ghe da bi chiem. */
    @Transactional
    public SeatHoldResponse hold(Long flightId, List<String> seatCodes, String holdRef, Integer minutes) {
        Flight f = flightRepository.findById(flightId)
                .orElseThrow(() -> new IllegalArgumentException("Flight not found: " + flightId));
        if (seatCodes == null || seatCodes.isEmpty()) {
            throw new IllegalArgumentException("No seats selected");
        }
        if (holdRef == null || holdRef.isBlank()) {
            throw new IllegalArgumentException("holdRef is required");
        }
        int mins = (minutes == null || minutes <= 0) ? DEFAULT_HOLD_MINUTES : minutes;

        // Don dep cac ghe HELD het han cua chuyen nay truoc (giai phong unique key).
        Instant now = Instant.now();
        for (FlightSeat fs : seatRepository.findByFlightId(flightId)) {
            if ("HELD".equals(fs.getStatus()) && fs.getExpiresAt() != null && fs.getExpiresAt().isBefore(now)) {
                seatRepository.delete(fs);
            }
        }

        Map<String, String> occupied = activeOccupancy(flightId);
        Instant expires = Instant.now().plusSeconds(mins * 60L);
        List<SeatDto> held = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();

        for (String raw : seatCodes) {
            SeatLayout.Seat layout = SeatLayout.find(raw);
            if (layout == null) {
                throw new IllegalArgumentException("Invalid seat: " + raw);
            }
            String code = layout.code();
            if (!seen.add(code)) {
                continue;   // bo trung trong cung yeu cau
            }
            if (occupied.containsKey(code)) {
                throw new SeatTakenException("Ghe " + code + " da duoc giu/dat, vui long chon ghe khac");
            }
            FlightSeat fs = new FlightSeat();
            fs.setFlightId(flightId);
            fs.setSeatCode(code);
            fs.setStatus("HELD");
            fs.setHoldRef(holdRef);
            fs.setExpiresAt(expires);
            seatRepository.save(fs);

            BigDecimal price = SeatLayout.price(f.getBasePrice(), layout.seatClass(), layout.position());
            total = total.add(price);
            held.add(new SeatDto(code, layout.row(), layout.col(), layout.seatClass(), layout.position(), price, "HELD"));
        }

        recomputeAvailable(f);
        long remaining = Math.max(0, expires.getEpochSecond() - Instant.now().getEpochSecond());
        return new SeatHoldResponse(holdRef, expires, held, total, f.getCurrency(), remaining);
    }

    /** Nha cac ghe dang HELD theo holdRef (huy/het han phia booking). BOOKED khong bi dong toi. */
    @Transactional
    public void release(Long flightId, String holdRef) {
        if (holdRef == null) return;
        for (FlightSeat fs : seatRepository.findByFlightIdAndHoldRef(flightId, holdRef)) {
            if ("HELD".equals(fs.getStatus())) {
                seatRepository.delete(fs);
            }
        }
        flightRepository.findById(flightId).ifPresent(this::recomputeAvailable);
    }

    /** Xac nhan (thanh toan thanh cong): HELD -> BOOKED. Nem neu hold da bi nha/het han. */
    @Transactional
    public SeatHoldResponse confirm(Long flightId, String holdRef) {
        Flight f = flightRepository.findById(flightId)
                .orElseThrow(() -> new IllegalArgumentException("Flight not found: " + flightId));
        List<FlightSeat> rows = new ArrayList<>();
        for (FlightSeat fs : seatRepository.findByFlightIdAndHoldRef(flightId, holdRef)) {
            if ("HELD".equals(fs.getStatus())) rows.add(fs);
        }
        if (rows.isEmpty()) {
            throw new SeatTakenException("Khong tim thay cho giu (da bi nha hoac het han): " + holdRef);
        }
        List<SeatDto> seats = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FlightSeat fs : rows) {
            fs.setStatus("BOOKED");
            fs.setExpiresAt(null);
            seatRepository.save(fs);
            SeatLayout.Seat l = SeatLayout.find(fs.getSeatCode());
            BigDecimal price = (l == null) ? BigDecimal.ZERO
                    : SeatLayout.price(f.getBasePrice(), l.seatClass(), l.position());
            total = total.add(price);
            seats.add(new SeatDto(fs.getSeatCode(),
                    l != null ? l.row() : 0, l != null ? l.col() : "",
                    l != null ? l.seatClass() : "", l != null ? l.position() : "",
                    price, "BOOKED"));
        }
        recomputeAvailable(f);
        return new SeatHoldResponse(holdRef, null, seats, total, f.getCurrency(), 0);
    }

    /** Nha moi ghe HELD het han (goi tu scheduler). Tra ve so ghe da nha. */
    @Transactional
    public int releaseExpired() {
        List<FlightSeat> expired = seatRepository.findByStatusAndExpiresAtBefore("HELD", Instant.now());
        if (expired.isEmpty()) return 0;
        Set<Long> flightIds = new HashSet<>();
        for (FlightSeat fs : expired) {
            flightIds.add(fs.getFlightId());
            seatRepository.delete(fs);
        }
        for (Long fid : flightIds) {
            flightRepository.findById(fid).ifPresent(this::recomputeAvailable);
        }
        return expired.size();
    }

    /** code -> status (BOOKED hoac HELD con han). Ghe khong nam trong map = trong. */
    private Map<String, String> activeOccupancy(Long flightId) {
        Map<String, String> m = new HashMap<>();
        Instant now = Instant.now();
        for (FlightSeat fs : seatRepository.findByFlightId(flightId)) {
            if ("BOOKED".equals(fs.getStatus())) {
                m.put(fs.getSeatCode(), "BOOKED");
            } else if ("HELD".equals(fs.getStatus())
                    && (fs.getExpiresAt() == null || fs.getExpiresAt().isAfter(now))) {
                m.put(fs.getSeatCode(), "HELD");
            }
        }
        return m;
    }

    private void recomputeAvailable(Flight f) {
        int occupied = activeOccupancy(f.getId()).size();
        f.setTotalSeats(SeatLayout.TOTAL_SEATS);
        f.setAvailableSeats(Math.max(0, SeatLayout.TOTAL_SEATS - occupied));
        flightRepository.save(f);
    }
}
