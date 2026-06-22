package com.flightprovider.api.dto;

import java.util.List;

/**
 * Yeu cau giu cho.
 *  - seatCodes : danh sach ma ghe (vd ["12A","12B"])
 *  - holdRef   : ma tham chieu (booking-platform gui ma don de release/confirm sau)
 *  - holdMinutes: so phut giu (mac dinh 20 neu null/<=0)
 */
public record SeatHoldRequest(
        List<String> seatCodes,
        String holdRef,
        Integer holdMinutes) {
}
