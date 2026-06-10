package com.swapit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "valuations")
public class ValuationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "valuation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "swap_request_id", nullable = false)
    private SwapRequestEntity swapRequest;

    @Column(name = "valuation_type", nullable = false, length = 30)
    private String valuationType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "min_amount")
    private Integer minAmount;

    @Column(name = "max_amount")
    private Integer maxAmount;

    @Column(name = "final_amount")
    private Integer finalAmount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "summary_reason")
    private String summaryReason;

    @Column(name = "exterior_reason")
    private String exteriorReason;

    @Column(name = "parts_reason")
    private String partsReason;

    @Column(name = "material_reason")
    private String materialReason;

    @Column(name = "processing_reason")
    private String processingReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    protected ValuationEntity() {
    }

    private ValuationEntity(SwapRequestEntity swapRequest, int minAmount, int maxAmount, String summaryReason) {
        this.swapRequest = swapRequest;
        this.valuationType = "PRE";
        this.status = "READY";
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.currency = "INR";
        this.summaryReason = summaryReason;
        this.exteriorReason = "사진상 외관 사용 흔적은 있으나 주요 파손은 확인되지 않았습니다.";
        this.partsReason = "일부 부품은 재사용 가능성이 있어 예상 보상가에 반영했습니다.";
        this.materialReason = "금속/플라스틱 회수 가능 가치를 기준으로 기본 금액을 산정했습니다.";
        this.processingReason = "최종 금액은 수거 후 분류와 안전 해체 비용을 반영해 확정됩니다.";
        this.createdAt = OffsetDateTime.now();
    }

    public static ValuationEntity preValuation(SwapRequestEntity swapRequest, int minAmount, int maxAmount, String summaryReason) {
        return new ValuationEntity(swapRequest, minAmount, maxAmount, summaryReason);
    }
}