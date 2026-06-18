package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateInstantCallRequest(
        Long userId,
        @NotBlank String userName,
        String phoneNumber,
        String applianceType,
        @NotBlank String address,
        String detailAddress,
        Double pickupLat,
        Double pickupLng,
        Double pickupAccuracyMeters,
        String pickupSource
) {
}
