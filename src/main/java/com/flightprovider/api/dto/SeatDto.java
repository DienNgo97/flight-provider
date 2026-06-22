package com.flightprovider.api.dto;

import java.math.BigDecimal;

/** 1 ghe trong so do: ma, vi tri, hang, gia + trang thai (FREE/HELD/BOOKED). */
public record SeatDto(
        String code,
        int row,
        String col,
        String seatClass,   // BUSINESS / ECONOMY
        String position,    // WINDOW / AISLE / MIDDLE
        BigDecimal price,
        String status) {    // FREE / HELD / BOOKED
}
