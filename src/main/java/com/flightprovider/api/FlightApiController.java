package com.flightprovider.api;

import com.flightprovider.api.dto.BookingRequest;
import com.flightprovider.api.dto.BookingResponse;
import com.flightprovider.api.dto.FlightDto;
import com.flightprovider.service.FlightService;
import jakarta.validation.Valid;
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

    public FlightApiController(FlightService flightService) {
        this.flightService = flightService;
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
}
