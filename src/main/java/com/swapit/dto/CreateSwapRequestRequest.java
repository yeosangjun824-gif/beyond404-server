package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSwapRequestRequest(
        @NotBlank String userName,
        @NotBlank String phoneNumber
) {
}

