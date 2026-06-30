package com.flightprovider.domain;

/**
 * Shared status constants for the flight-provider domain (PROV-X3).
 *
 * <p>Replaces the magic strings that used to be scattered across
 * {@code FlightService} / {@code SeatService}. Centralising them prevents
 * silent typos from breaking the booking / seat state machine.
 *
 * <ul>
 *   <li>{@link #SEAT_FREE} / {@link #SEAT_HELD} / {@link #SEAT_BOOKED} —
 *       per-seat status stored on {@code flight_seats}.</li>
 *   <li>{@link #BOOKING_CONFIRMED} / {@link #BOOKING_CANCELLED} —
 *       whole-booking status stored on {@code flight_bookings}.</li>
 * </ul>
 */
public final class FlightStatuses {

    private FlightStatuses() {
    }

    // ----- flight_seats.status -----
    /** Seat has no active row in {@code flight_seats} (derived, never persisted). */
    public static final String SEAT_FREE = "FREE";
    /** Seat is being held temporarily (has an {@code expires_at}). */
    public static final String SEAT_HELD = "HELD";
    /** Seat has been paid for / confirmed. */
    public static final String SEAT_BOOKED = "BOOKED";

    // ----- flight_bookings.status -----
    /** Booking is confirmed (counts against availability). */
    public static final String BOOKING_CONFIRMED = "CONFIRMED";
    /** Booking has been cancelled (releases its seats / counter). */
    public static final String BOOKING_CANCELLED = "CANCELLED";
}
