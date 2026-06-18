package com.swapit.dto;

import jakarta.validation.constraints.NotNull;

public record CrewLocationRequest(
        @NotNull Double lat,
        @NotNull Double lng,
        Double heading,
        Double speed,
        Double accuracyMeters,
        String collectedAt,
        String source
) {
}
