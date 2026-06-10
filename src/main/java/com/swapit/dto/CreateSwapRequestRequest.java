package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSwapRequestRequest(
        Long userId,
        @NotBlank String userName,
        @NotBlank String phoneNumber,
        String applianceType
) {
}
