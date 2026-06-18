package com.swapit.domain;

import com.swapit.domain.enums.SwapRequestStatus;
import com.swapit.dto.SwapRequestResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SwapRequestState {
    private final long id;
    private final long customerId;
    private SwapRequestStatus status;

    private String uploadedFileName;
    private String applianceType;
    private String brand;
    private String modelName;
    private String estimatedAge;
    private String exteriorCondition;
    private String conditionGrade;
    private String aiAnalysisStatus;
    private double aiConfidence;
    private String applianceSizeGrade;
    private String applianceSizeMetric;

    private boolean creditPolicyAgreed;
    private LocalDateTime consentedAt;
    private String exteriorPhotoFileName;
    private String labelPhotoFileName;

    private int minEstimatedValue;
    private int maxEstimatedValue;
    private boolean preValuationAccepted;
    private int scrapValue;
    private double creditRate;
    private final double creditCapRate = 0.15;
    private int estimatedFinalCredit;
    private int exchangeCount;
    private String userTier;

    private String selectedProductId;
    private String selectedProductName;
    private String selectedProductGrade;
    private Integer selectedProductPrice;
    private boolean selectedProductSameDayEligible;

    private Long pickupRequestId;
    private String pickupType;
    private String pickupStatus;
    private Long crewId;
    private String crewName;
    private LocalDate bookingDate;
    private String bookingTime;
    private LocalDateTime pickupRequestedAt;
    private String address;
    private String detailAddress;
    private Double pickupLat;
    private Double pickupLng;
    private final List<SwapRequestResponse.NearbyCrew> nearbyCrews = new ArrayList<>();

    private String crewPhotoUrl;
    private double crewRating;
    private final List<String> crewReviewSummary = new ArrayList<>();

    private String trackingMessage;
    private String trackingPhase;
    private LocalDateTime estimatedArrivalAt;
    private SwapRequestResponse.DriverLocation driverLocation;
    private SwapRequestResponse.LocationPoint processingCenter;
    private final List<SwapRequestResponse.TrackingEvent> trackingEvents = new ArrayList<>();

    private Integer finalCreditValue;
    private String finalValuationStatus;
    private final List<String> finalValuationReasons = new ArrayList<>();
    private boolean reReviewRequested;

    private String creditStatus;
    private String pickupResultType;
    private String pickupResultSummary;
    private final List<String> pickupResultDetails = new ArrayList<>();
    private String pickupPhotoFileName;
    private String hubPhotoFileName;
    private String pickupInspectionMemo;
    private String hubMemo;

    private int dispatchMatchScore;
    private int dispatchPriorityRank;
    private int crewRejectCount;
    private int crewCancelCount;
    private int crewPenaltyCount;
    private String dispatchAlertMessage;
    private String dispatchRecommendedReason;

    private Integer settlementBaseFee;
    private Integer settlementDistanceFee;
    private Integer settlementIncentive;
    private Integer settlementPenalty;
    private Integer settlementTotalAmount;
    private String settlementStatus;

    private String deliveryStatus;
    private LocalDateTime deliveryUpdatedAt;

    private final List<SwapRequestResponse.Notification> notifications = new ArrayList<>();
    private long notificationSequence = 1;

    public SwapRequestState(long id, long customerId, String applianceType) {
        this.id = id;
        this.customerId = customerId;
        this.status = SwapRequestStatus.CREATED;
        this.applianceType = valueOrDefault(applianceType, "washing_machine");
        this.brand = "LG";
        this.modelName = mockModelName(this.applianceType);
        this.estimatedAge = "3-5년";
        this.exteriorCondition = "외관 확인 필요";
        this.conditionGrade = "unknown";
        this.aiAnalysisStatus = "PENDING";
        this.aiConfidence = 0.0;
        this.applianceSizeGrade = "중형";
        this.applianceSizeMetric = defaultSizeMetric(this.applianceType);
        this.minEstimatedValue = 0;
        this.maxEstimatedValue = 0;
        this.scrapValue = 0;
        this.creditRate = 0.0;
        this.estimatedFinalCredit = 0;
        this.exchangeCount = 1;
        this.userTier = "Starter";
        this.trackingPhase = "REQUEST_CREATED";
        this.trackingMessage = "SwapIt 교환 요청이 시작되었습니다.";
        this.estimatedArrivalAt = LocalDateTime.now().plusMinutes(35);
        addTrackingEvent("CREATED", "SwapIt 교환 요청 생성");
        addNotification("SwapIt 시작", "교환할 가전을 선택하고 촬영을 진행해 주세요.");
    }

    public long getId() {
        return id;
    }

    public Long getPickupRequestId() {
        return pickupRequestId;
    }

    public String getPickupStatus() {
        return pickupStatus;
    }

    public Long getCrewId() {
        return crewId;
    }

    public Double getPickupLat() {
        return pickupLat;
    }

    public Double getPickupLng() {
        return pickupLng;
    }

    public void applyMockInspection(
            String fileName,
            String requestedApplianceType,
            String imageUrl,
            String exteriorPhotoFileName,
            String labelPhotoFileName,
            Boolean agreedToCreditPolicy
    ) {
        this.status = SwapRequestStatus.INSPECTION_COMPLETED;
        this.creditPolicyAgreed = Boolean.TRUE.equals(agreedToCreditPolicy);
        this.consentedAt = this.creditPolicyAgreed ? LocalDateTime.now() : this.consentedAt;
        this.exteriorPhotoFileName = valueOrDefault(exteriorPhotoFileName, valueOrDefault(fileName, imageUrl));
        this.labelPhotoFileName = valueOrDefault(labelPhotoFileName, this.labelPhotoFileName);
        this.uploadedFileName = this.exteriorPhotoFileName;

        if (requestedApplianceType != null && !requestedApplianceType.isBlank()) {
            this.applianceType = requestedApplianceType;
        }

        this.brand = "LG";
        this.modelName = mockModelName(this.applianceType);
        this.estimatedAge = mockEstimatedAge(this.applianceType);
        this.exteriorCondition = mockCondition(this.applianceType);
        this.conditionGrade = "good";
        this.aiAnalysisStatus = "COMPLETED";
        this.aiConfidence = 0.89;
        this.applianceSizeGrade = "중형";
        this.applianceSizeMetric = defaultSizeMetric(this.applianceType);
        this.scrapValue = scrapValueFor(this.applianceType, this.applianceSizeGrade);
        this.estimatedFinalCredit = this.scrapValue;
        this.minEstimatedValue = this.estimatedFinalCredit;
        this.maxEstimatedValue = this.estimatedFinalCredit;
        this.status = SwapRequestStatus.PRE_VALUATION_READY;
        this.trackingMessage = "AI 분석 결과와 예상 보상 크레딧이 준비되었습니다.";
        addTrackingEvent("PHOTO_ANALYZED", "가전 외관 및 라벨 사진 분석 완료");
        addNotification("AI 분석 완료", "예상 보상 크레딧을 확인하고 교체 제품을 선택해 주세요.");
    }

    public void updateAppliance(
            String applianceType,
            String brand,
            String modelName,
            String estimatedAge,
            String exteriorCondition,
            String dbSizeGrade,
            String dbSizeMetric
    ) {
        this.applianceType = valueOrDefault(applianceType, this.applianceType);
        this.brand = valueOrDefault(brand, this.brand);
        this.modelName = valueOrDefault(modelName, this.modelName);
        this.estimatedAge = valueOrDefault(estimatedAge, this.estimatedAge);
        this.exteriorCondition = valueOrDefault(exteriorCondition, this.exteriorCondition);
        if (dbSizeGrade != null && !dbSizeGrade.isBlank()) {
            this.applianceSizeGrade = dbSizeGrade;
        }
        this.applianceSizeMetric = (dbSizeMetric != null && !dbSizeMetric.isBlank())
                ? dbSizeMetric
                : defaultSizeMetric(this.applianceType);
        this.scrapValue = scrapValueFor(this.applianceType, this.applianceSizeGrade);
        this.estimatedFinalCredit = selectedProductPrice == null
                ? scrapValue
                : calculateEstimatedFinalCredit(selectedProductPrice, selectedProductGrade);
        this.minEstimatedValue = this.estimatedFinalCredit;
        this.maxEstimatedValue = this.estimatedFinalCredit;
        addTrackingEvent("APPLIANCE_CONFIRMED", "AI 인식 결과 확인 및 수정 완료");
    }

    public void acceptPreValuation() {
        this.preValuationAccepted = true;
        this.status = SwapRequestStatus.PRE_VALUATION_ACCEPTED;
        this.trackingMessage = "예상 보상 크레딧이 확인되었습니다. LG 교체 제품을 선택해 주세요.";
        addTrackingEvent("PRE_VALUATION_ACCEPTED", "예상 보상 크레딧 확인 완료");
    }

    public void selectReplacementProduct(String productId, String productName, String productGrade, int productPrice, boolean sameDayEligible) {
        this.selectedProductId = productId;
        this.selectedProductName = productName;
        this.selectedProductGrade = productGrade;
        this.selectedProductPrice = productPrice;
        this.selectedProductSameDayEligible = sameDayEligible;
        this.creditRate = creditRateFor(productGrade, exchangeCount);
        this.estimatedFinalCredit = calculateEstimatedFinalCredit(productPrice, productGrade);
        this.minEstimatedValue = this.estimatedFinalCredit;
        this.maxEstimatedValue = this.estimatedFinalCredit;
        this.finalCreditValue = this.estimatedFinalCredit;
        this.status = SwapRequestStatus.PRODUCT_SELECTED;
        this.trackingMessage = "교체할 LG 제품이 선택되었습니다. 수거 예약을 진행해 주세요.";
        addTrackingEvent("REPLACEMENT_PRODUCT_SELECTED", "교체 제품 선택 완료");
        addNotification("교체 제품 선택", this.selectedProductName + " 선택이 완료되었습니다. 수거 예약을 진행해 주세요.");
    }

    public void confirmBooking(LocalDate bookingDate, String bookingTime, String address, String detailAddress, Double pickupLat, Double pickupLng) {
        this.pickupRequestId = ensurePickupRequestId();
        this.pickupType = "BOOKING";
        this.pickupStatus = "CONFIRMED";
        this.pickupRequestedAt = LocalDateTime.now();
        this.bookingDate = bookingDate;
        this.bookingTime = bookingTime;
        this.address = address;
        this.detailAddress = detailAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.status = SwapRequestStatus.BOOKING_CONFIRMED;
        this.trackingPhase = "SEARCHING_NEARBY_CREW";
        this.trackingMessage = "예약이 접수되었습니다. 근처 수거 크루를 배정하고 있습니다.";
        addTrackingEvent("BOOKING_CONFIRMED", "시간 예약 접수 완료");
        addNotification("수거 예약 완료", scheduledLabel() + " 기준으로 수거 예약이 접수되었습니다.");
    }

    public void requestInstantCall(String address, String detailAddress, Double pickupLat, Double pickupLng) {
        this.pickupRequestId = ensurePickupRequestId();
        this.pickupType = "INSTANT_CALL";
        this.pickupStatus = "REQUESTED";
        this.pickupRequestedAt = LocalDateTime.now();
        this.address = address;
        this.detailAddress = detailAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.status = SwapRequestStatus.INSTANT_CALL_REQUESTED;
        this.trackingPhase = "SEARCHING_NEARBY_CREW";
        this.trackingMessage = "바로콜 요청이 접수되었습니다. 근처 수거 크루를 찾는 중입니다.";
        addTrackingEvent("INSTANT_CALL_REQUESTED", "바로콜 요청 접수");
        addNotification("바로콜 요청", "근처 수거 크루를 찾고 있습니다.");
    }

    public void restorePickup(
            Long pickupRequestId,
            String pickupType,
            String pickupStatus,
            Long crewId,
            String crewName,
            LocalDate bookingDate,
            String bookingTime,
            LocalDateTime pickupRequestedAt,
            String address,
            String detailAddress,
            Double pickupLat,
            Double pickupLng
    ) {
        this.pickupRequestId = pickupRequestId;
        this.pickupType = pickupType;
        this.pickupStatus = pickupStatus;
        this.crewId = crewId;
        this.crewName = crewName;
        this.bookingDate = bookingDate;
        this.bookingTime = bookingTime;
        this.pickupRequestedAt = pickupRequestedAt;
        this.address = address;
        this.detailAddress = detailAddress;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;

        if ("BOOKING".equals(pickupType) && "CONFIRMED".equals(pickupStatus)) {
            this.status = SwapRequestStatus.BOOKING_CONFIRMED;
            this.trackingPhase = "SEARCHING_NEARBY_CREW";
            this.trackingMessage = "예약이 접수되었습니다. 근처 수거 크루를 배정하고 있습니다.";
            return;
        }

        if ("REQUESTED".equals(pickupStatus)) {
            this.status = SwapRequestStatus.INSTANT_CALL_REQUESTED;
            this.trackingPhase = "SEARCHING_NEARBY_CREW";
            this.trackingMessage = "바로콜 요청이 접수되었습니다. 근처 수거 크루를 찾는 중입니다.";
            return;
        }

        if ("ASSIGNED".equals(pickupStatus)) {
            this.status = SwapRequestStatus.CREW_ASSIGNED;
            this.trackingPhase = "CREW_ASSIGNED";
            this.trackingMessage = valueOrDefault(crewName, "LG 수거 크루") + " 크루가 배정되었습니다.";
            return;
        }

        if ("IN_PROGRESS".equals(pickupStatus)) {
            this.status = SwapRequestStatus.PICKUP_IN_PROGRESS;
            this.trackingPhase = "EN_ROUTE_TO_PICKUP";
            this.trackingMessage = "크루가 수거지로 이동 중입니다.";
            return;
        }

        if ("COMPLETED".equals(pickupStatus)) {
            this.status = SwapRequestStatus.REWARD_READY;
            this.trackingPhase = "DELIVERED_TO_EWASTE_HUB";
            this.trackingMessage = "수거가 완료되어 최종 보상 확인을 준비 중입니다.";
        }
    }

    public void acceptByCrew(long crewId, String crewName, String photoUrl, double rating, List<String> reviewSummary) {
        this.pickupRequestId = ensurePickupRequestId();
        this.crewId = crewId;
        this.crewName = valueOrDefault(crewName, "LG 수거 크루");
        this.crewPhotoUrl = photoUrl;
        this.crewRating = rating;
        this.crewReviewSummary.clear();
        if (reviewSummary != null) {
            this.crewReviewSummary.addAll(reviewSummary);
        }
        this.pickupStatus = "ASSIGNED";
        this.status = SwapRequestStatus.CREW_ASSIGNED;
        this.trackingPhase = "CREW_ASSIGNED";
        this.trackingMessage = this.crewName + " 크루가 배정되었습니다.";
        addTrackingEvent("CREW_ASSIGNED", "수거 크루 배정 완료");
        addNotification("크루 배정 완료", this.crewName + " 크루가 수거를 담당합니다.");
    }

    public void departCrew() {
        this.pickupStatus = "IN_PROGRESS";
        this.status = SwapRequestStatus.PICKUP_IN_PROGRESS;
        this.trackingPhase = "EN_ROUTE_TO_PICKUP";
        this.trackingMessage = "크루가 수거지로 이동 중입니다.";
        this.estimatedArrivalAt = LocalDateTime.now().plusMinutes(18);
        addTrackingEvent("CREW_DEPARTED", "크루 수거지 출발");
        addNotification("크루 출발", "크루가 현재 위치에서 출발했습니다.");
    }

    public void updateCrewLocation(double lat, double lng, double heading, double speed) {
        updateCrewLocation(lat, lng, heading, speed, null);
    }

    public void updateCrewLocation(double lat, double lng, double heading, double speed, Double accuracy) {
        this.driverLocation = new SwapRequestResponse.DriverLocation(lat, lng, heading, speed, accuracy, LocalDateTime.now());

        if ("ARRIVED".equals(pickupStatus)) {
            this.trackingPhase = "EN_ROUTE_TO_PROCESSING_CENTER";
            this.trackingMessage = "수거 후 e-waste 공장으로 이동 중입니다.";
            this.estimatedArrivalAt = LocalDateTime.now().plusMinutes(20);
        } else if ("ASSIGNED".equals(pickupStatus) || "IN_PROGRESS".equals(pickupStatus)) {
            this.trackingPhase = "EN_ROUTE_TO_PICKUP";
            this.trackingMessage = "크루가 수거지로 이동 중입니다.";
        }

        addTrackingEvent("CREW_LOCATION_UPDATED", "크루 위치 갱신");
    }

    public void arriveCrew() {
        this.pickupStatus = "ARRIVED";
        this.status = SwapRequestStatus.CREW_ARRIVED;
        this.trackingPhase = "PICKUP_CONFIRMED";
        this.trackingMessage = "크루가 문앞에 도착해 수거를 진행 중입니다.";
        this.estimatedArrivalAt = LocalDateTime.now().plusMinutes(20);
        addTrackingEvent("CREW_ARRIVED", "크루 문앞 도착");
        addNotification("크루 도착", "크루가 문앞에 도착했습니다.");
    }

    public void completePickup(String pickupPhotoFileName, String hubPhotoFileName, String inspectionMemo, String hubMemo) {
        this.pickupPhotoFileName = valueOrDefault(pickupPhotoFileName, this.pickupPhotoFileName);
        this.hubPhotoFileName = valueOrDefault(hubPhotoFileName, this.hubPhotoFileName);
        this.pickupInspectionMemo = valueOrDefault(inspectionMemo, this.pickupInspectionMemo);
        this.hubMemo = valueOrDefault(hubMemo, this.hubMemo);
        this.uploadedFileName = valueOrDefault(this.pickupPhotoFileName, this.uploadedFileName);
        this.pickupStatus = "COMPLETED";
        this.status = SwapRequestStatus.DELIVERED_TO_EWASTE_HUB;
        this.trackingPhase = "DELIVERED_TO_EWASTE_HUB";
        this.trackingMessage = "e-waste 공장에 전달 완료되었습니다.";
        this.estimatedArrivalAt = LocalDateTime.now();
        this.finalCreditValue = this.estimatedFinalCredit;
        this.finalValuationStatus = "READY";
        this.finalValuationReasons.clear();
        this.finalValuationReasons.addAll(List.of(
                "스크랩 가치와 교체 제품 등급을 기반으로 최종 보상 크레딧을 산정했습니다.",
                "현장 실물 확인과 허브 인수 확인을 완료했습니다.",
                "안심 처리 절차가 정상적으로 완료되었습니다."
        ));
        this.creditStatus = "READY";
        this.deliveryStatus = "PREPARING";
        this.deliveryUpdatedAt = LocalDateTime.now();
        this.status = SwapRequestStatus.REWARD_READY;
        this.settlementBaseFee = 18000;
        this.settlementDistanceFee = 3500;
        this.settlementIncentive = 2000;
        this.settlementPenalty = crewPenaltyCount > 0 ? 3000 : 0;
        this.settlementTotalAmount = settlementBaseFee + settlementDistanceFee + settlementIncentive - settlementPenalty;
        this.settlementStatus = "READY";
        createPickupResultReport();
        addTrackingEvent("EWASTE_HUB_DELIVERED", "e-waste 공장 전달 완료");
        addNotification("안심처리 완료", "e-waste 공장 전달이 완료되었습니다.");
    }

    public void completeMockFinalValuation() {
        this.finalCreditValue = estimatedFinalCredit == 0 ? scrapValue : estimatedFinalCredit;
        this.finalValuationStatus = "READY";
        this.creditStatus = "READY";
        this.status = SwapRequestStatus.REWARD_READY;
        this.deliveryStatus = valueOrDefault(this.deliveryStatus, "PREPARING");
        this.deliveryUpdatedAt = LocalDateTime.now();
        addTrackingEvent("FINAL_VALUATION_READY", "최종 보상 준비 완료");
    }

    public void completeFinalValuation(Integer amount, List<String> reasons) {
        this.finalCreditValue = amount == null ? estimatedFinalCredit : amount;
        this.finalValuationStatus = "READY";
        this.finalValuationReasons.clear();
        this.finalValuationReasons.addAll(reasons == null || reasons.isEmpty()
                ? List.of("최종 보상 금액이 준비되었습니다.")
                : reasons);
        this.creditStatus = "READY";
        this.status = SwapRequestStatus.REWARD_READY;
        this.deliveryStatus = valueOrDefault(this.deliveryStatus, "PREPARING");
        this.deliveryUpdatedAt = LocalDateTime.now();
        addTrackingEvent("FINAL_VALUATION_READY", "최종 보상 금액 확정");
        addNotification("보상 준비 완료", "최종 보상 크레딧이 준비되었습니다.");
    }

    public void requestReReview(String reason) {
        if (reReviewRequested) {
            return;
        }
        this.reReviewRequested = true;
        this.status = SwapRequestStatus.RE_REVIEW_REQUESTED;
        this.finalValuationStatus = "RE_REVIEWING";
        this.trackingMessage = "보상 재검토 요청이 접수되었습니다.";
        addTrackingEvent("RE_REVIEW_REQUESTED", "보상 재검토 요청: " + reason);
        addNotification("재검토 요청", "보상 재검토 요청이 접수되었습니다.");
    }

    public void completeReReview() {
        this.reReviewRequested = false;
        this.status = SwapRequestStatus.RE_REVIEW_COMPLETED;
        completeFinalValuation(finalCreditValue == null ? estimatedFinalCredit : finalCreditValue, List.of(
                "재검토 요청을 반영해 보상 금액을 다시 확인했습니다.",
                "현장 확인 자료와 허브 확인 자료를 함께 검토했습니다."
        ));
        addNotification("재검토 완료", "재검토 결과가 준비되었습니다.");
    }

    public void issueCredit() {
        if (finalCreditValue == null) {
            finalCreditValue = estimatedFinalCredit;
        }
        this.creditStatus = "ISSUED";
        this.deliveryUpdatedAt = LocalDateTime.now();
        if (deliveryStatus == null || "PREPARING".equals(deliveryStatus)) {
            this.deliveryStatus = "SHIPPED";
            this.status = SwapRequestStatus.DELIVERY_SHIPPED;
            addNotification("제품 출고", "새 가전이 출고되었습니다.");
            addTrackingEvent("DELIVERY_SHIPPED", "새 가전 출고");
            return;
        }
        this.deliveryStatus = "DELIVERED";
        this.status = SwapRequestStatus.DELIVERY_COMPLETED;
        addNotification("배송 완료", "새 가전 배송이 완료되었습니다.");
        addTrackingEvent("DELIVERY_COMPLETED", "새 가전 배송 완료");
    }

    public void advanceDeliveryTracking() {
        if (deliveryStatus == null || "PREPARING".equals(deliveryStatus)) {
            this.deliveryStatus = "SHIPPED";
            this.status = SwapRequestStatus.DELIVERY_SHIPPED;
            this.deliveryUpdatedAt = LocalDateTime.now();
            addTrackingEvent("DELIVERY_SHIPPED", "새 가전 출고");
            addNotification("새 가전 출고", "선택한 새 가전이 출고되었습니다.");
            return;
        }

        if ("SHIPPED".equals(deliveryStatus)) {
            this.deliveryStatus = "DELIVERED";
            this.status = SwapRequestStatus.DELIVERY_COMPLETED;
            this.deliveryUpdatedAt = LocalDateTime.now();
            addTrackingEvent("DELIVERY_COMPLETED", "새 가전 배송 완료");
            addNotification("새 가전 배송 완료", "선택한 새 가전이 도착했습니다.");
        }
    }

    public void setProcessingCenter(String label, double lat, double lng) {
        this.processingCenter = new SwapRequestResponse.LocationPoint(label, lat, lng);
    }

    public void setNearbyCrews(List<SwapRequestResponse.NearbyCrew> crews) {
        this.nearbyCrews.clear();
        if (crews != null) {
            this.nearbyCrews.addAll(crews);
        }
    }

    public void setDispatchContext(
            int dispatchMatchScore,
            int dispatchPriorityRank,
            int crewRejectCount,
            int crewCancelCount,
            int crewPenaltyCount,
            String dispatchAlertMessage,
            String dispatchRecommendedReason
    ) {
        this.dispatchMatchScore = dispatchMatchScore;
        this.dispatchPriorityRank = dispatchPriorityRank;
        this.crewRejectCount = crewRejectCount;
        this.crewCancelCount = crewCancelCount;
        this.crewPenaltyCount = crewPenaltyCount;
        this.dispatchAlertMessage = dispatchAlertMessage;
        this.dispatchRecommendedReason = dispatchRecommendedReason;
    }

    public SwapRequestResponse toResponse() {
        SwapRequestResponse.Booking booking = bookingDate == null && address == null && pickupLat == null && pickupLng == null
                ? null
                : new SwapRequestResponse.Booking(bookingDate, bookingTime, address, detailAddress, pickupLat, pickupLng);
        SwapRequestResponse.PickupRequest pickupRequest = pickupRequestId == null
                ? null
                : new SwapRequestResponse.PickupRequest(
                pickupRequestId,
                pickupType,
                pickupStatus,
                crewId,
                crewName,
                address,
                scheduledLabel(),
                pickupRequestedAt,
                List.copyOf(nearbyCrews)
        );
        SwapRequestResponse.CrewProfile crewProfile = crewName == null
                ? null
                : new SwapRequestResponse.CrewProfile(
                crewName,
                crewPhotoUrl == null ? "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=400&q=80" : crewPhotoUrl,
                crewRating == 0.0 ? 4.8 : crewRating,
                crewReviewSummary.isEmpty()
                        ? List.of("친절하게 수거를 진행해요.", "시간 약속을 잘 지켜요.")
                        : List.copyOf(crewReviewSummary)
        );
        SwapRequestResponse.TrackingMetrics trackingMetrics = new SwapRequestResponse.TrackingMetrics(
                calculateCrewToPickupDistanceMeters(),
                calculateCrewToProcessingCenterDistanceMeters(),
                isDriverLocationLive()
        );
        SwapRequestResponse.DispatchInfo dispatchInfo = pickupRequestId == null
                ? null
                : new SwapRequestResponse.DispatchInfo(
                dispatchAlertMessage == null ? "수거 요청 이후 배차 정보가 표시됩니다." : dispatchAlertMessage,
                dispatchMatchScore,
                dispatchPriorityRank,
                crewRejectCount,
                crewCancelCount,
                crewPenaltyCount,
                dispatchRecommendedReason == null ? "근처 크루 거리와 현재 동선을 기준으로 배차합니다." : dispatchRecommendedReason
        );
        SwapRequestResponse.FinalValuation finalValuation = finalValuationStatus == null
                ? null
                : new SwapRequestResponse.FinalValuation(
                finalCreditValue,
                "KRW",
                finalValuationStatus,
                List.copyOf(finalValuationReasons)
        );
        SwapRequestResponse.Credit credit = creditStatus == null
                ? null
                : new SwapRequestResponse.Credit(finalCreditValue == null ? estimatedFinalCredit : finalCreditValue, "KRW", creditStatus);
        SwapRequestResponse.PickupResultReport pickupResultReport = pickupResultSummary == null
                ? null
                : new SwapRequestResponse.PickupResultReport(
                pickupResultType,
                pickupResultSummary,
                List.copyOf(pickupResultDetails)
        );
        SwapRequestResponse.Settlement settlement = settlementStatus == null
                ? null
                : new SwapRequestResponse.Settlement(
                settlementBaseFee,
                settlementDistanceFee,
                settlementIncentive,
                settlementPenalty,
                settlementTotalAmount,
                settlementStatus
        );

        return new SwapRequestResponse(
                id,
                customerId,
                status.name(),
                new SwapRequestResponse.Appliance(
                        applianceType,
                        brand,
                        modelName,
                        estimatedAge,
                        exteriorCondition,
                        conditionGrade,
                        aiAnalysisStatus,
                        aiConfidence,
                        uploadedFileName,
                        applianceSizeGrade,
                        applianceSizeMetric
                ),
                new SwapRequestResponse.UserConsent(
                        creditPolicyAgreed,
                        "고의적 손상이나 허위 촬영이 확인되면 보상 조정 또는 법적 불이익이 있을 수 있습니다.",
                        consentedAt
                ),
                new SwapRequestResponse.CaptureEvidence(
                        exteriorPhotoFileName,
                        labelPhotoFileName,
                        pickupPhotoFileName,
                        hubPhotoFileName,
                        pickupInspectionMemo,
                        hubMemo
                ),
                new SwapRequestResponse.PreValuation(
                        minEstimatedValue,
                        maxEstimatedValue,
                        "KRW",
                        List.of(
                                "반납 제품 크기 등급: " + applianceSizeGrade,
                                "스크랩 가치 기준: " + scrapValue + "원",
                                "최종 크레딧 = 스크랩 가치 + min(신제품가 × 크레딧 비율, 신제품가 × 15%)",
                                "최종 현장 확인 및 허브 인수 확인 후 확정될 수 있습니다."
                        )
                ),
                new SwapRequestResponse.RewardEstimate(
                        scrapValue,
                        creditRate,
                        creditCapRate,
                        estimatedFinalCredit,
                        exchangeCount,
                        userTier,
                        List.of(
                                "반납 가전: " + applianceLabel(applianceType),
                                "크기 기준: " + applianceSizeMetric,
                                "스크랩 가치: " + scrapValue + "원",
                                "적용 비율: " + Math.round(creditRate * 100) + "%"
                        )
                ),
                selectedProductId == null ? null : new SwapRequestResponse.SelectedProduct(
                        selectedProductId,
                        selectedProductName,
                        selectedProductGrade,
                        selectedProductPrice == null ? 0 : selectedProductPrice,
                        selectedProductSameDayEligible
                ),
                booking,
                pickupRequest,
                crewProfile,
                null,
                dispatchInfo,
                new SwapRequestResponse.Tracking(
                        trackingMessage,
                        estimatedArrivalAt,
                        driverLocation,
                        processingCenter,
                        trackingPhase,
                        trackingMetrics,
                        List.copyOf(nearbyCrews),
                        List.copyOf(trackingEvents),
                        null,
                        List.of()
                ),
                finalValuation,
                credit,
                new SwapRequestResponse.RewardOverview(
                        finalCreditValue == null ? estimatedFinalCredit : finalCreditValue,
                        userTier,
                        exchangeCount,
                        nextTier(userTier),
                        List.of(
                                "이번 교환 예상 보상 크레딧 " + (finalCreditValue == null ? estimatedFinalCredit : finalCreditValue) + "원",
                                "누적 교환 횟수 " + exchangeCount + "회",
                                nextTier(userTier) + " 등급까지 1회 추가 교환 필요"
                        )
                ),
                new SwapRequestResponse.DeliveryTracking(
                        valueOrDefault(deliveryStatus, "PREPARING"),
                        deliveryEtaMessage(),
                        deliveryUpdatedAt,
                        buildDeliveryStages()
                ),
                pickupResultReport,
                new SwapRequestResponse.RecyclingReport(
                        pickupResultSummary == null ? "e-waste 처리 결과가 준비되면 표시됩니다." : pickupResultSummary,
                        pickupResultDetails.isEmpty()
                                ? List.of("수거 물품 인수 확인", "재사용 부품 분리", "재활용 소재 분류")
                                : List.copyOf(pickupResultDetails)
                ),
                settlement,
                List.copyOf(notifications)
        );
    }

    private long ensurePickupRequestId() {
        if (pickupRequestId == null) {
            pickupRequestId = id * 1000 + 1;
        }
        return pickupRequestId;
    }

    private int calculateEstimatedFinalCredit(int productPrice, String productGrade) {
        double rate = creditRateFor(productGrade, exchangeCount);
        this.creditRate = rate;
        int productBasedCredit = (int) Math.round(productPrice * rate);
        return scrapValue + productBasedCredit;
    }

    private int scrapValueFor(String applianceType, String sizeGrade) {
        return switch (valueOrDefault(applianceType, "washing_machine")) {
            case "refrigerator" -> switch (sizeGrade) {
                case "소형" -> 20000;
                case "대형" -> 58000;
                default -> 39000;
            };
            case "air_conditioner" -> switch (sizeGrade) {
                case "소형" -> 27000;
                case "대형" -> 94000;
                default -> 57000;
            };
            case "microwave" -> switch (sizeGrade) {
                case "소형" -> 9000;
                case "대형" -> 18000;
                default -> 13000;
            };
            case "tv" -> switch (sizeGrade) {
                case "소형" -> 7000;
                case "대형" -> 27000;
                default -> 14000;
            };
            case "air_purifier" -> switch (sizeGrade) {
                case "소형" -> 7000;
                case "대형" -> 16000;
                default -> 11000;
            };
            default -> switch (sizeGrade) {
                case "소형" -> 32000;
                case "대형" -> 58000;
                default -> 45000;
            };
        };
    }

    private double creditRateFor(String productGrade, int exchangeCount) {
        String grade = valueOrDefault(productGrade, "일반/중형");
        int bucket = Math.min(exchangeCount, 3);
        return switch (grade) {
            case "프리미엄" -> bucket >= 3 ? 0.15 : bucket == 2 ? 0.12 : 0.10;
            case "보급형" -> bucket >= 3 ? 0.09 : bucket == 2 ? 0.07 : 0.04;
            default -> bucket >= 3 ? 0.12 : bucket == 2 ? 0.10 : 0.07;
        };
    }

    private String scheduledLabel() {
        if (bookingDate == null || bookingTime == null) {
            return "바로콜 요청";
        }
        return bookingDate + " " + bookingTime;
    }

    private void createPickupResultReport() {
        this.pickupResultType = "DELIVERED_TO_EWASTE_HUB";
        this.pickupResultSummary = "e-waste 공장 전달 완료 및 안심 처리 절차가 시작되었습니다.";
        this.pickupResultDetails.clear();
        this.pickupResultDetails.add("문앞 수거 물품 인수 확인 완료");
        this.pickupResultDetails.add("허브 도착 후 최종 증빙 촬영 완료");
        this.pickupResultDetails.add("재사용 부품 분리 및 소재 분류 절차 시작");
    }

    private void addTrackingEvent(String eventType, String message) {
        trackingEvents.add(new SwapRequestResponse.TrackingEvent(eventType, message, LocalDateTime.now()));
    }

    private void addNotification(String title, String message) {
        notifications.add(new SwapRequestResponse.Notification(notificationSequence++, title, message, false, LocalDateTime.now()));
    }

    private Double calculateCrewToPickupDistanceMeters() {
        if (driverLocation == null || pickupLat == null || pickupLng == null) {
            return null;
        }
        return distanceMeters(driverLocation.lat(), driverLocation.lng(), pickupLat, pickupLng);
    }

    private Double calculateCrewToProcessingCenterDistanceMeters() {
        if (driverLocation == null || processingCenter == null) {
            return null;
        }
        return distanceMeters(driverLocation.lat(), driverLocation.lng(), processingCenter.lat(), processingCenter.lng());
    }

    private boolean isDriverLocationLive() {
        return driverLocation != null
                && driverLocation.updatedAt() != null
                && driverLocation.updatedAt().isAfter(LocalDateTime.now().minusSeconds(30));
    }

    private List<SwapRequestResponse.DeliveryStage> buildDeliveryStages() {
        boolean shipped = "SHIPPED".equals(deliveryStatus) || "DELIVERED".equals(deliveryStatus);
        boolean delivered = "DELIVERED".equals(deliveryStatus);
        LocalDateTime preparingAt = deliveryUpdatedAt;
        return List.of(
                new SwapRequestResponse.DeliveryStage("ORDER_CONFIRMED", "주문 확인", true, pickupRequestedAt),
                new SwapRequestResponse.DeliveryStage("PREPARING", "제품 출고 준비", deliveryStatus != null, preparingAt),
                new SwapRequestResponse.DeliveryStage("SHIPPED", "제품 출고", shipped, shipped ? deliveryUpdatedAt : null),
                new SwapRequestResponse.DeliveryStage("DELIVERED", "도착 완료", delivered, delivered ? deliveryUpdatedAt : null)
        );
    }

    private String deliveryEtaMessage() {
        if ("DELIVERED".equals(deliveryStatus)) {
            return "새 가전 배송이 완료되었습니다.";
        }
        if ("SHIPPED".equals(deliveryStatus)) {
            return "오늘 오후 도착 예정";
        }
        return "출고 준비 중";
    }

    private String nextTier(String tier) {
        return switch (valueOrDefault(tier, "Starter")) {
            case "Starter" -> "Plus";
            case "Plus" -> "Premium";
            case "Premium" -> "VIP";
            default -> "VIP";
        };
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadius * c * 10.0) / 10.0;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String mockModelName(String applianceType) {
        return switch (valueOrDefault(applianceType, "washing_machine")) {
            case "refrigerator" -> "DIOS R-T872";
            case "air_conditioner" -> "Whisen SQ07";
            case "tv" -> "OLED55C4";
            case "microwave" -> "MW23L";
            case "air_purifier" -> "PuriCare AS181";
            default -> "Tromm F13";
        };
    }

    private static String mockEstimatedAge(String applianceType) {
        return switch (valueOrDefault(applianceType, "washing_machine")) {
            case "refrigerator" -> "4-6년";
            case "air_conditioner" -> "2-4년";
            case "tv" -> "2-3년";
            case "microwave" -> "5년 이상";
            case "air_purifier" -> "2-4년";
            default -> "3-5년";
        };
    }

    private static String mockCondition(String applianceType) {
        return switch (valueOrDefault(applianceType, "washing_machine")) {
            case "refrigerator" -> "외관 양호, 문쪽 생활 스크래치";
            case "air_conditioner" -> "실내기 외관 양호";
            case "tv" -> "패널 외관 양호";
            case "microwave" -> "외부 사용감 있음";
            case "air_purifier" -> "필터 커버 양호, 외관 양호";
            default -> "외관 양호, 생활 사용감";
        };
    }

    private static String defaultSizeMetric(String applianceType) {
        return switch (valueOrDefault(applianceType, "washing_machine")) {
            case "refrigerator" -> "420L";
            case "air_conditioner" -> "1.5톤";
            case "tv" -> "55인치";
            case "microwave" -> "23L";
            case "air_purifier" -> "30평형";
            default -> "13kg";
        };
    }

    private static String applianceLabel(String applianceType) {
        return switch (valueOrDefault(applianceType, "washing_machine")) {
            case "refrigerator" -> "냉장고";
            case "air_conditioner" -> "에어컨";
            case "tv" -> "TV";
            case "microwave" -> "전자레인지";
            case "air_purifier" -> "공기청정기";
            default -> "세탁기";
        };
    }
}
