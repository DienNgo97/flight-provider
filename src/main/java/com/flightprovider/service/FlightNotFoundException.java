package com.flightprovider.service;

/**
 * Thrown when a flight (or a booking referenced by code) genuinely does not
 * exist. Controllers map this to HTTP 404 (PROV-X4) — kept distinct from
 * {@link InvalidSeatRequestException} so that bad input is not mis-reported as
 * "flight disappeared".
 */
public class FlightNotFoundException extends RuntimeException {
    public FlightNotFoundException(String message) {
        super(message);
    }
}
