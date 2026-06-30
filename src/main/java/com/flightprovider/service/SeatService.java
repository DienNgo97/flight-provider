package com.flightprovider.service;

import com.flightprovider.api.dto.SeatDto;
import com.flightprovider.api.dto.SeatHoldResponse;
import com.flightprovider.api.dto.SeatMapDto;
import com.flightprovider.domain.FlightStatuses;
import com.flightprovider.domain.entity.Flight;
import com.flightprovider.domain.entity.FlightBooking;
import com.flightprovider.domain.entity.FlightSeat;
import com.flightprovider.repository.FlightBookingRepository;
import com.flightprovider.repository.FlightRepository;
import com.flightprovider.repository.FlightSeatRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.UUID;

/**
 * Quan ly ghe theo so do (SeatLayout) + giu cho (HELD) / dat (BOOKED).
 *
 * <p><b>Single source of truth (FP-01/FP-04).</b> {@code Flight.availableSeats}
 * is always re-derived as:
 * <pre>
 *   availableSeats = TOTAL_SEATS
 *                  - active seat occupancy  (flight_seats HELD-still-valid / BOOKED)
 *                  - confirmed whole-flight seats (SUM flight_bookings.seats WHERE status='CONFIRMED')
 * </pre>
 * so the seat-selection path and the whole-flight {@code book()} path feed the
 * same number and can no longer clobber each other.
 *
 * <p>During any mutation that recomputes availability the Flight row is locked
 * with {@code PESSIMISTIC_WRITE} (FP-06) so concurrent seat operations
 * serialise instead of losing updates.
 */
@Service
public class SeatService {

    public static final int DEFAULT_HOLD_MINUTES = 20;

    private final FlightRepository flightRepository;
    private final FlightSeatRepository seatRepository;
    private final FlightBookingRepository bookingRepository;

    public SeatService(FlightRepository flightRepository,
                       FlightSeatRepository seatRepository,
                       FlightBookingRepository bookingRepository) {
        this.flightRepository = flightRepository;
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
    }

    /** So do day du cua 1 chuyen (kem gia + trang thai tung ghe). */
    @Transactional(readOnly = true)
    public SeatMapDto map(Long flightId) {
        Flight f = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));
        Map<String, String> occupied = activeOccupancy(flightId);
        List<SeatDto> seats = new ArrayList<>(SeatLayout.TOTAL_SEATS);
        for (SeatLayout.Seat s : SeatLayout.all()) {
            String status = occupied.getOrDefault(s.code(), FlightStatuses.SEAT_FREE);
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
        // FP-06: lock the flight row for the whole mutation.
        Flight f = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));
        if (seatCodes == null || seatCodes.isEmpty()) {
            throw new InvalidSeatRequestException("No seats selected");
        }
        if (holdRef == null || holdRef.isBlank()) {
            throw new InvalidSeatRequestException("holdRef is required");
        }
        int mins = (minutes == null || minutes <= 0) ? DEFAULT_HOLD_MINUTES : minutes;

        // Don dep cac ghe HELD het han cua chuyen nay truoc (giai phong unique key).
        Instant now = Instant.now();
        for (FlightSeat fs : seatRepository.findByFlightId(flightId)) {
            if (FlightStatuses.SEAT_HELD.equals(fs.getStatus())
                    && fs.getExpiresAt() != null && fs.getExpiresAt().isBefore(now)) {
                seatRepository.delete(fs);
            }
        }
        seatRepository.flush();

        Map<String, String> occupied = activeOccupancy(flightId);
        Instant expires = Instant.now().plusSeconds(mins * 60L);
        List<SeatDto> held = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();

        for (String raw : seatCodes) {
            SeatLayout.Seat layout = SeatLayout.find(raw);
            if (layout == null) {
                throw new InvalidSeatRequestException("Invalid seat: " + raw);
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
            fs.setStatus(FlightStatuses.SEAT_HELD);
            fs.setHoldRef(holdRef);
            fs.setExpiresAt(expires);
            try {
                // FP-07: force the unique (flight_id, seat_code) constraint to fire now so a
                // racing hold surfaces as a clean SeatTakenException, not a raw JPA/SQL leak.
                seatRepository.saveAndFlush(fs);
            } catch (DataIntegrityViolationException ex) {
                throw new SeatTakenException("Ghe " + code + " da duoc giu/dat, vui long chon ghe khac");
            }

            BigDecimal price = SeatLayout.price(f.getBasePrice(), layout.seatClass(), layout.position());
            total = total.add(price);
            held.add(new SeatDto(code, layout.row(), layout.col(), layout.seatClass(), layout.position(), price, FlightStatuses.SEAT_HELD));
        }

        recomputeAvailable(f);
        long remaining = Math.max(0, expires.getEpochSecond() - Instant.now().getEpochSecond());
        return new SeatHoldResponse(holdRef, expires, held, total, f.getCurrency(), remaining);
    }

    /** Nha cac ghe dang HELD theo holdRef (huy/het han phia booking). BOOKED khong bi dong toi. */
    @Transactional
    public void release(Long flightId, String holdRef) {
        if (holdRef == null) return;
        // FP-06: lock the flight before mutating + recomputing.
        Flight f = flightRepository.findByIdForUpdate(flightId).orElse(null);
        if (f == null) return;
        for (FlightSeat fs : seatRepository.findByFlightIdAndHoldRef(flightId, holdRef)) {
            if (FlightStatuses.SEAT_HELD.equals(fs.getStatus())) {
                seatRepository.delete(fs);
            }
        }
        seatRepository.flush();
        recomputeAvailable(f);
    }

    /**
     * Xac nhan (thanh toan thanh cong): HELD -> BOOKED, va tao luon FlightBooking
     * de don co confirmationCode + co the huy duoc (FP-03).
     */
    @Transactional
    public SeatHoldResponse confirm(Long flightId, String holdRef) {
        // FP-06: lock the flight row for the whole mutation.
        Flight f = flightRepository.findByIdForUpdate(flightId)
                .orElseThrow(() -> new FlightNotFoundException("Flight not found: " + flightId));
        if (holdRef == null || holdRef.isBlank()) {
            throw new InvalidSeatRequestException("holdRef is required");
        }
        List<FlightSeat> rows = new ArrayList<>();
        for (FlightSeat fs : seatRepository.findByFlightIdAndHoldRef(flightId, holdRef)) {
            if (FlightStatuses.SEAT_HELD.equals(fs.getStatus())) rows.add(fs);
        }
        if (rows.isEmpty()) {
            throw new SeatTakenException("Khong tim thay cho giu (da bi nha hoac het han): " + holdRef);
        }
        List<SeatDto> seats = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FlightSeat fs : rows) {
            fs.setStatus(FlightStatuses.SEAT_BOOKED);
            fs.setExpiresAt(null);
            seatRepository.save(fs);
            SeatLayout.Seat l = SeatLayout.find(fs.getSeatCode());
            BigDecimal price = (l == null) ? BigDecimal.ZERO
                    : SeatLayout.price(f.getBasePrice(), l.seatClass(), l.position());
            total = total.add(price);
            seats.add(new SeatDto(fs.getSeatCode(),
                    l != null ? l.row() : 0, l != null ? l.col() : "",
                    l != null ? l.seatClass() : "", l != null ? l.position() : "",
                    price, FlightStatuses.SEAT_BOOKED));
        }

        // FP-03: create a FlightBooking so the seat-selected order has a
        // confirmation code and is cancellable via /cancel. Idempotent on the
        // holdRef so a double-confirm reuses the existing booking.
        FlightBooking booking = bookingRepository.findByHoldRef(holdRef).stream()
                .filter(b -> FlightStatuses.BOOKING_CONFIRMED.equals(b.getStatus()))
                .findFirst()
                .orElse(null);
        if (booking == null) {
            booking = new FlightBooking();
            booking.setConfirmationCode(generateCode());
            booking.setFlightId(flightId);
            booking.setHoldRef(holdRef);
            booking.setPassengerName(null);
            booking.setContactEmail(null);
            booking.setSeats(rows.size());
            booking.setTotalPrice(total);
            booking.setStatus(FlightStatuses.BOOKING_CONFIRMED);
            bookingRepository.save(booking);
        }

        recomputeAvailable(f);
        return new SeatHoldResponse(booking.getConfirmationCode(), null, seats, total, f.getCurrency(), 0);
    }

    /**
     * Huy don chon-ghe theo confirmationCode: nha cac ghe BOOKED ve trong va
     * danh dau booking CANCELLED (FP-03 cancel path). Tra ve so ghe da nha.
     */
    @Transactional
    public int cancelSeatBooking(String confirmationCode) {
        FlightBooking booking = bookingRepository.findByConfirmationCode(confirmationCode)
                .orElseThrow(() -> new FlightNotFoundException("Booking not found: " + confirmationCode));
        if (FlightStatuses.BOOKING_CANCELLED.equals(booking.getStatus())) {
            return 0;
        }
        Long flightId = booking.getFlightId();
        Flight f = flightRepository.findByIdForUpdate(flightId).orElse(null);

        int released = 0;
        String holdRef = booking.getHoldRef();
        if (holdRef != null) {
            for (FlightSeat fs : seatRepository.findByFlightIdAndHoldRef(flightId, holdRef)) {
                if (FlightStatuses.SEAT_BOOKED.equals(fs.getStatus())
                        || FlightStatuses.SEAT_HELD.equals(fs.getStatus())) {
                    seatRepository.delete(fs);
                    released++;
                }
            }
            seatRepository.flush();
        }

        booking.setStatus(FlightStatuses.BOOKING_CANCELLED);
        bookingRepository.save(booking);

        if (f != null) {
            recomputeAvailable(f);
        }
        return released;
    }

    /** Nha moi ghe HELD het han (goi tu scheduler). Tra ve so ghe da nha. */
    @Transactional
    public int releaseExpired() {
        List<FlightSeat> expired = seatRepository.findByStatusAndExpiresAtBefore(FlightStatuses.SEAT_HELD, Instant.now());
        if (expired.isEmpty()) return 0;
        Set<Long> flightIds = new HashSet<>();
        for (FlightSeat fs : expired) {
            flightIds.add(fs.getFlightId());
            seatRepository.delete(fs);
        }
        seatRepository.flush();
        for (Long fid : flightIds) {
            // FP-06: lock each flight before recomputing its counter.
            flightRepository.findByIdForUpdate(fid).ifPresent(this::recomputeAvailable);
        }
        return expired.size();
    }

    /** code -> status (BOOKED hoac HELD con han). Ghe khong nam trong map = trong. */
    private Map<String, String> activeOccupancy(Long flightId) {
        Map<String, String> m = new HashMap<>();
        Instant now = Instant.now();
        for (FlightSeat fs : seatRepository.findByFlightId(flightId)) {
            if (FlightStatuses.SEAT_BOOKED.equals(fs.getStatus())) {
                m.put(fs.getSeatCode(), FlightStatuses.SEAT_BOOKED);
            } else if (FlightStatuses.SEAT_HELD.equals(fs.getStatus())
                    && (fs.getExpiresAt() == null || fs.getExpiresAt().isAfter(now))) {
                m.put(fs.getSeatCode(), FlightStatuses.SEAT_HELD);
            }
        }
        return m;
    }

    /**
     * FP-01/FP-04: re-derive availableSeats from BOTH inventory tables so the
     * two booking paths agree and can never clobber each other.
     *
     * <pre>
     *   availableSeats = TOTAL_SEATS - seatOccupancy - wholeFlightBookingSeats
     * </pre>
     *
     * <ul>
     *   <li>{@code seatOccupancy} = active rows in {@code flight_seats}
     *       (valid HELD + BOOKED). This already covers every seat-selection
     *       booking, since {@code confirm()} keeps its BOOKED seat rows.</li>
     *   <li>{@code wholeFlightBookingSeats} = SUM of CONFIRMED
     *       {@code flight_bookings} seats, MINUS the seat-selection bookings
     *       (those carry a holdRef and are already counted via their seat
     *       rows) so they are not double-subtracted.</li>
     * </ul>
     */
    void recomputeAvailable(Flight f) {
        int seatOccupancy = activeOccupancy(f.getId()).size();

        int confirmedBookingSeats = bookingRepository.sumConfirmedSeats(f.getId());
        int seatBackedBookingSeats = 0;
        for (FlightBooking b : bookingRepository.findByFlightIdAndStatus(
                f.getId(), FlightStatuses.BOOKING_CONFIRMED)) {
            if (b.getHoldRef() != null) {
                seatBackedBookingSeats += b.getSeats();
            }
        }
        int wholeFlightBookingSeats = Math.max(0, confirmedBookingSeats - seatBackedBookingSeats);

        f.setTotalSeats(SeatLayout.TOTAL_SEATS);
        int available = SeatLayout.TOTAL_SEATS - seatOccupancy - wholeFlightBookingSeats;
        f.setAvailableSeats(Math.max(0, available));
        flightRepository.save(f);
    }

    private String generateCode() {
        return "FL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
