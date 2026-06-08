package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;

public record PhotoUploadRequest(
        @NotBlank String fileName
) {
}

