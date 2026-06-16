package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSwapRequestRequest(
        Long userId,
        @NotBlank String userName,
        String phoneNumber,
        String applianceType
) {
}
