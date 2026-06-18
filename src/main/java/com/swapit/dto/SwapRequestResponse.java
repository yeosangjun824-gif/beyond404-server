package com.swapit.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SwapRequestResponse(
        long id,
        long customerId,
        String status,
        Appliance appliance,
        UserConsent userConsent,
        CaptureEvidence captureEvidence,
        PreValuation preValuation,
        RewardEstimate rewardEstimate,
        SelectedProduct selectedProduct,
        Booking booking,
        PickupRequest pickupRequest,
        CrewProfile crewProfile,
        CrewReview crewReview,
        DispatchInfo dispatchInfo,
        Tracking tracking,
        FinalValuation finalValuation,
        Credit credit,
        RewardOverview rewardOverview,
        DeliveryTracking deliveryTracking,
        PickupResultReport pickupResultReport,
        RecyclingReport recyclingReport,
        Settlement settlement,
        List<Notification> notifications
) {
    public record Appliance(
            String applianceType,
            String brand,
            String modelName,
            String estimatedAge,
            String exteriorCondition,
            String conditionGrade,
            String aiAnalysisStatus,
            double aiConfidence,
            String uploadedFileName,
            String sizeGrade,
            String sizeMetric
    ) {
    }

    public record UserConsent(
            boolean agreedToCreditPolicy,
            String notice,
            LocalDateTime agreedAt
    ) {
    }

    public record CaptureEvidence(
            String exteriorPhotoFileName,
            String labelPhotoFileName,
            String pickupPhotoFileName,
            String hubPhotoFileName,
            String pickupInspectionMemo,
            String hubMemo
    ) {
    }

    public record PreValuation(
            int minEstimatedValue,
            int maxEstimatedValue,
            String currency,
            List<String> basis
    ) {
    }

    public record RewardEstimate(
            int scrapValue,
            double creditRate,
            double creditCapRate,
            int estimatedFinalCredit,
            int exchangeCount,
            String userTier,
            List<String> basis
    ) {
    }

    public record SelectedProduct(
            String productId,
            String productName,
            String productGrade,
            int productPrice,
            boolean sameDayEligible
    ) {
    }

    public record Booking(
            LocalDate bookingDate,
            String bookingTime,
            String address,
            String detailAddress,
            Double pickupLat,
            Double pickupLng,
            Double pickupAccuracyMeters,
            String pickupSource
    ) {
    }

    public record PickupRequest(
            long pickupRequestId,
            String pickupType,
            String status,
            Long crewId,
            String crewName,
            String address,
            String scheduledAt,
            LocalDateTime requestedAt,
            List<NearbyCrew> nearbyCrews
    ) {
    }

    public record CrewProfile(
            String name,
            String photoUrl,
            double rating,
            List<String> reviewSummary
    ) {
    }

    public record CrewReview(
            int rating,
            String comment,
            LocalDateTime createdAt
    ) {
    }

    public record DispatchInfo(
            String alertMessage,
            int matchScore,
            int priorityRank,
            int rejectCount,
            int cancelCount,
            int penaltyCount,
            String recommendedReason
    ) {
    }

    public record Tracking(
            String message,
            LocalDateTime estimatedArrivalAt,
            DriverLocation driverLocation,
            LocationPoint processingCenter,
            String phase,
            TrackingMetrics metrics,
            List<NearbyCrew> nearbyCrews,
            List<TrackingEvent> events,
            RouteSummary route,
            List<LocationHistoryPoint> locationHistory
        ) {
    }

    public record LocationPoint(
            String label,
            double lat,
            double lng
    ) {
    }

    public record DriverLocation(
            double lat,
            double lng,
            double heading,
            double speed,
            LocalDateTime updatedAt,
            Double accuracyMeters,
            String source,
            LocalDateTime collectedAt
    ) {
    }

    public record NearbyCrew(
            Long crewId,
            String crewName,
            String status,
            double lat,
            double lng,
            double distanceMeters,
            boolean assigned
    ) {
    }

    public record TrackingEvent(
            String eventType,
            String message,
            LocalDateTime createdAt
    ) {
    }

    public record TrackingMetrics(
            Double crewToPickupMeters,
            Double crewToProcessingCenterMeters,
            boolean locationLive,
            Double driverAccuracyMeters,
            Double pickupAccuracyMeters,
            String proximityStatus,
            Double effectiveDistanceMeters,
            Long effectiveDurationSeconds,
            String distanceConfidence
    ) {
    }

    public record RouteSummary(
            String mode,
            Double distanceMeters,
            Long durationSeconds,
            String distanceLabel,
            String durationLabel,
            String encodedPolyline,
            List<RoutePoint> points,
            LocalDateTime calculatedAt,
            String routeSource,
            boolean approximate,
            boolean suppressedByProximity
    ) {
    }

    public record RoutePoint(
            double lat,
            double lng
    ) {
    }

    public record LocationHistoryPoint(
            double lat,
            double lng,
            double heading,
            double speed,
            LocalDateTime recordedAt,
            Double accuracyMeters,
            String source
    ) {
    }

    public record FinalValuation(
            Integer amount,
            String currency,
            String status,
            List<String> reasons
    ) {
    }

    public record Credit(
            int amount,
            String currency,
            String status
    ) {
    }

    public record RewardOverview(
            int currentCredit,
            String userTier,
            int exchangeCount,
            String nextTier,
            List<String> benefits
    ) {
    }

    public record DeliveryTracking(
            String status,
            String etaMessage,
            LocalDateTime updatedAt,
            List<DeliveryStage> stages
    ) {
    }

    public record DeliveryStage(
            String stageKey,
            String label,
            boolean completed,
            LocalDateTime completedAt
    ) {
    }

    public record Settlement(
            Integer baseFee,
            Integer distanceFee,
            Integer incentive,
            Integer penalty,
            Integer totalAmount,
            String status
    ) {
    }

    public record RecyclingReport(
            String summary,
            List<String> steps
    ) {
    }

    public record PickupResultReport(
            String resultType,
            String summary,
            List<String> details
    ) {
    }

    public record Notification(
            long notificationId,
            String title,
            String message,
            boolean read,
            LocalDateTime createdAt
    ) {
    }
}
