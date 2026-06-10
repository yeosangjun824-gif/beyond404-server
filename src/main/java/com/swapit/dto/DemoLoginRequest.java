package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;

public record DemoLoginRequest(
        @NotBlank String userName,
        @NotBlank String phoneNumber
) {
}
