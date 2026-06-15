package com.swapit.dto;

import java.time.LocalDate;
import java.util.List;

public record BookingAvailabilityResponse(
        LocalDate date,
        List<Slot> slots
) {
    public record Slot(
            String time,
            boolean available,
            int reservedCount,
            int capacity
    ) {
    }
}
