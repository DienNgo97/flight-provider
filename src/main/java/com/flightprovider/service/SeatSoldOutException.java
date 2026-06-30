package com.flightprovider.service;

/**
 * Thrown when a whole-flight {@code book()} cannot be satisfied because the
 * atomic seat decrement matched no rows (i.e. another transaction took the last
 * seats first). Controllers map this to HTTP 409 (FP-05).
 */
public class SeatSoldOutException extends RuntimeException {
    public SeatSoldOutException(String message) {
        super(message);
    }
}
