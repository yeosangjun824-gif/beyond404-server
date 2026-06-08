package com.swapit.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SwapRequestResponse(
        long id,
        String status,
        Appliance appliance,
        PreValuation preValuation,
        Booking booking,
        Tracking tracking,
        Credit credit,
        RecyclingReport recyclingReport
) {
    public record Appliance(
            String applianceType,
            String brand,
            String conditionGrade,
            String uploadedFileName
    ) {
    }

    public record PreValuation(
            int minEstimatedValue,
            int maxEstimatedValue,
            String currency,
            List<String> basis
    ) {
    }

    public record Booking(
            LocalDate bookingDate,
            String bookingTime,
            String address
    ) {
    }

    public record Tracking(
            String message,
            LocalDateTime estimatedArrivalAt
    ) {
    }

    public record Credit(
            int amount,
            String currency,
            String status
    ) {
    }

    public record RecyclingReport(
            String summary,
            List<String> steps
    ) {
    }
}

