package com.swapit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SelectReplacementProductRequest(
        @NotBlank String productId,
        @NotBlank String productName,
        @NotBlank String productGrade,
        @NotNull Integer productPrice,
        Boolean sameDayEligible
) {
}
