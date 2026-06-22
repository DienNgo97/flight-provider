package com.flightprovider.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Ket qua giu cho / xac nhan cho. */
public record SeatHoldResponse(
        String holdRef,
        Instant expiresAt,
        List<SeatDto> seats,
        BigDecimal totalPrice,
        String currency,
        long remainingSeconds) {
}
