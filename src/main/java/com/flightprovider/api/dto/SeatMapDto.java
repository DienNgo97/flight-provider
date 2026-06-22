package com.flightprovider.api.dto;

import java.util.List;

/** So do ghe day du cua 1 chuyen. */
public record SeatMapDto(
        Long flightId,
        String flightNumber,
        String currency,
        int rows,
        String[] cols,
        int businessRows,
        List<SeatDto> seats) {
}
