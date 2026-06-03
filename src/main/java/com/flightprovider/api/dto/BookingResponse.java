package com.flightprovider.api.dto;

import java.math.BigDecimal;

public record BookingResponse(
        String confirmationCode,
        Long flightId,
        String flightNumber,
        int seats,
        BigDecimal totalPrice,
        String currency,
        String status) {
}
