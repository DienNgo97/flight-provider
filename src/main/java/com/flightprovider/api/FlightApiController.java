package com.flightprovider.api;

import com.flightprovider.api.dto.BookingRequest;
import com.flightprovider.api.dto.BookingResponse;
import com.flightprovider.api.dto.FlightDto;
import com.flightprovider.api.dto.SeatActionRequest;
import com.flightprovider.api.dto.SeatHoldRequest;
import com.flightprovider.service.FlightService;
import com.flightprovider.service.SeatService;
import com.flightprovider.service.SeatTakenException;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flights")
public class FlightApiController {

    private final FlightService flightService;
    private final SeatService seatService;

    public FlightApiController(FlightService flightService, SeatService seatService) {
        this.flightService = flightService;
        this.seatService = seatService;
    }

    @GetMapping("/search")
    public List<FlightDto> search(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "1") int passengers) {
        return flightService.search(from, to, date, passengers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightDto> getById(@PathVariable Long id) {
        return flightService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/book")
    public ResponseEntity<?> book(@PathVariable Long id, @Valid @RequestBody BookingRequest request) {
        try {
            return ResponseEntity.ok(flightService.book(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id,
                                    @RequestParam String confirmationCode) {
        try {
            BookingResponse resp = flightService.cancel(confirmationCode);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    // ===== So do ghe + giu cho (seat selection) =====

    /** So do ghe + gia + trang thai (FREE/HELD/BOOKED). */
    @GetMapping("/{id}/seats")
    public ResponseEntity<?> seats(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(seatService.map(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    /** Giu cho cac ghe (mac dinh 20 phut). 409 neu co ghe da bi chiem. */
    @PostMapping("/{id}/seats/hold")
    public ResponseEntity<?> holdSeats(@PathVariable Long id, @RequestBody SeatHoldRequest req) {
        try {
            return ResponseEntity.ok(seatService.hold(id, req.seatCodes(), req.holdRef(), req.holdMinutes()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (SeatTakenException | DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        }
    }

    /** Nha cac ghe dang giu theo holdRef (khi huy/het han phia booking). */
    @PostMapping("/{id}/seats/release")
    public ResponseEntity<?> releaseSeats(@PathVariable Long id, @RequestBody SeatActionRequest req) {
        seatService.release(id, req.holdRef());
        return ResponseEntity.ok(Map.of("released", true));
    }

    /** Xac nhan ghe (HELD -> BOOKED) khi thanh toan thanh cong. */
    @PostMapping("/{id}/seats/confirm")
    public ResponseEntity<?> confirmSeats(@PathVariable Long id, @RequestBody SeatActionRequest req) {
        try {
            return ResponseEntity.ok(seatService.confirm(id, req.holdRef()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (SeatTakenException ex) {
            return ResponseEntity.status(409).body(Map.of("error", ex.getMessage()));
        }
    }
}
