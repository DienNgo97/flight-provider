package com.flightprovider.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * So do ghe co dinh cho moi chuyen (180 ghe = 30 hang x 6 cot A-F).
 *  - Hang 1..4  : Thuong gia (BUSINESS).
 *  - Hang 5..30 : Pho thong (ECONOMY).
 *  - Cot A,F = canh cua so (WINDOW); C,D = canh loi di (AISLE); B,E = giua (MIDDLE).
 *
 * Gia moi ghe suy ra tu base_price cua chuyen (lam tron 1.000d):
 *  - BUSINESS = base x 1.8
 *  - ECONOMY  = base + phu phi vi tri (cua so +80.000, loi di +40.000, giua +0)
 */
public final class SeatLayout {

    public static final int ROWS = 30;
    public static final char[] COLS = {'A', 'B', 'C', 'D', 'E', 'F'};
    public static final int BUSINESS_ROWS = 4;
    public static final int TOTAL_SEATS = ROWS * 6;

    /** 1 ghe trong so do (chua gan gia). */
    public record Seat(String code, int row, String col, String seatClass, String position) {}

    private SeatLayout() {}

    public static List<Seat> all() {
        List<Seat> seats = new ArrayList<>(TOTAL_SEATS);
        for (int r = 1; r <= ROWS; r++) {
            String klass = (r <= BUSINESS_ROWS) ? "BUSINESS" : "ECONOMY";
            for (char c : COLS) {
                String col = String.valueOf(c);
                seats.add(new Seat(r + col, r, col, klass, position(col)));
            }
        }
        return seats;
    }

    public static Seat find(String code) {
        if (code == null) return null;
        String norm = code.trim().toUpperCase();
        for (Seat s : all()) {
            if (s.code().equals(norm)) return s;
        }
        return null;
    }

    public static String position(String col) {
        switch (col) {
            case "A":
            case "F":
                return "WINDOW";
            case "C":
            case "D":
                return "AISLE";
            default:
                return "MIDDLE";
        }
    }

    /** Gia 1 ghe theo base_price chuyen + hang + vi tri, lam tron 1.000d. */
    public static BigDecimal price(BigDecimal base, String seatClass, String position) {
        BigDecimal b = (base == null) ? BigDecimal.ZERO : base;
        BigDecimal p;
        if ("BUSINESS".equals(seatClass)) {
            p = b.multiply(new BigDecimal("1.8"));
        } else {
            long surcharge = "WINDOW".equals(position) ? 80_000L
                    : "AISLE".equals(position) ? 40_000L : 0L;
            p = b.add(BigDecimal.valueOf(surcharge));
        }
        return p.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(1000));
    }
}
