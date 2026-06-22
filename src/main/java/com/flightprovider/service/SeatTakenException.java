package com.flightprovider.service;

/** Nem khi ghe da bi nguoi khac giu/dat -> controller tra HTTP 409. */
public class SeatTakenException extends RuntimeException {
    public SeatTakenException(String message) {
        super(message);
    }
}
