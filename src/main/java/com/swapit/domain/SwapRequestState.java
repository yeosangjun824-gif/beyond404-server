package com.swapit.domain;

import com.swapit.domain.enums.SwapRequestStatus;
import com.swapit.dto.SwapRequestResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SwapRequestState {
    private final long id;
    private SwapRequestStatus status;
    private String uploadedFileName;
    private String applianceType;
    private String brand;
    private String conditionGrade;
    private int minEstimatedValue;
    private int maxEstimatedValue;
    private LocalDate bookingDate;
    private String bookingTime;
    private String address;
    private String trackingMessage;
    private Integer finalCreditValue;

    public SwapRequestState(long id) {
        this.id = id;
        this.status = SwapRequestStatus.CREATED;
        this.applianceType = "unknown";
        this.brand = "unknown";
        this.conditionGrade = "unknown";
        this.trackingMessage = "교환 신청이 생성되었습니다.";
    }

    public long getId() {
        return id;
    }

    public void applyMockInspection(String fileName) {
        this.uploadedFileName = fileName;
        this.applianceType = "washing_machine";
        this.brand = "LG";
        this.conditionGrade = "good";
        this.minEstimatedValue = 1500;
        this.maxEstimatedValue = 2400;
        this.status = SwapRequestStatus.PRE_VALUATION_READY;
        this.trackingMessage = "사진 분석과 예상 보상가 산정이 완료되었습니다.";
    }

    public void confirmBooking(LocalDate bookingDate, String bookingTime, String address) {
        this.bookingDate = bookingDate;
        this.bookingTime = bookingTime;
        this.address = address;
        this.status = SwapRequestStatus.BOOKING_CONFIRMED;
        this.trackingMessage = "수거 예약이 확정되었습니다.";
    }

    public void completeMockFinalValuation() {
        this.finalCreditValue = 1900;
        this.status = SwapRequestStatus.CREDIT_ISSUED;
        this.trackingMessage = "최종 검수 후 크레딧이 발급되었습니다.";
    }

    public SwapRequestResponse toResponse() {
        return new SwapRequestResponse(
                id,
                status.name(),
                new SwapRequestResponse.Appliance(applianceType, brand, conditionGrade, uploadedFileName),
                new SwapRequestResponse.PreValuation(
                        minEstimatedValue,
                        maxEstimatedValue,
                        "INR",
                        List.of(
                                "제품군: LG 세탁기",
                                "외관 상태: 양호",
                                "사진 기반 예비 산정",
                                "최종 금액은 수거 후 검수와 원자재 가치 평가로 확정"
                        )
                ),
                bookingDate == null ? null : new SwapRequestResponse.Booking(bookingDate, bookingTime, address),
                new SwapRequestResponse.Tracking(trackingMessage, LocalDateTime.now().plusMinutes(35)),
                finalCreditValue == null ? null : new SwapRequestResponse.Credit(finalCreditValue, "INR", "ISSUED"),
                new SwapRequestResponse.RecyclingReport(
                        "수거 후 분해 검수 예정",
                        List.of("재사용 가능 부품 선별", "금속/플라스틱 원자재 회수", "LG 크레딧 전환")
                )
        );
    }
}

