package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BookingRequest(
        @NotNull LocalDate bookingDate,
        @NotBlank String bookingTime,
        @NotBlank String address,
        String detailAddress,
        Double pickupLat,
        Double pickupLng,
        Double pickupAccuracyMeters,
        String pickupSource
) {
}
