package com.flightprovider.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BookingRequest(
        @NotBlank String passengerName,
        String contactEmail,
        @Min(1) int seats) {
}
