package com.flightprovider.service;

/**
 * Thrown for invalid client input on seat operations (unknown seat code,
 * missing holdRef, empty seat list, ...). Controllers map this to HTTP 400
 * (PROV-X4).
 *
 * <p>Extends {@link IllegalArgumentException} so existing {@code catch
 * (IllegalArgumentException)} blocks still behave, but the controller catches
 * this subtype first to return 400 instead of 404.
 */
public class InvalidSeatRequestException extends IllegalArgumentException {
    public InvalidSeatRequestException(String message) {
        super(message);
    }
}
