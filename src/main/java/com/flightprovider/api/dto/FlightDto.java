package com.flightprovider.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FlightDto(
        Long id,
        String flightNumber,
        String airlineCode,
        String from,
        String to,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        BigDecimal price,
        String currency,
        int availableSeats,
        String aircraftType) {
}
