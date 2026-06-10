package com.swapit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "appliances")
public class ApplianceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appliance_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "swap_request_id", nullable = false)
    private SwapRequestEntity swapRequest;

    @Column(name = "appliance_type", nullable = false, length = 30)
    private String applianceType;

    @Column(name = "brand", length = 50)
    private String brand;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "estimated_age", length = 30)
    private String estimatedAge;

    @Column(name = "exterior_condition", length = 50)
    private String exteriorCondition;

    @Column(name = "confirmed_by_customer", nullable = false)
    private boolean confirmedByCustomer;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ApplianceEntity() {
    }

    private ApplianceEntity(SwapRequestEntity swapRequest, String applianceType) {
        OffsetDateTime now = OffsetDateTime.now();
        this.swapRequest = swapRequest;
        this.applianceType = applianceType;
        this.brand = "LG";
        this.modelName = "Unknown";
        this.estimatedAge = "?뺤씤 ?꾩슂";
        this.exteriorCondition = "?뺤씤 ?꾩슂";
        this.confirmedByCustomer = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ApplianceEntity create(SwapRequestEntity swapRequest, String applianceType) {
        return new ApplianceEntity(swapRequest, applianceType);
    }

    public void applyMockInspection(
            String applianceType,
            String brand,
            String modelName,
            String estimatedAge,
            String exteriorCondition
    ) {
        this.applianceType = applianceType;
        this.brand = brand;
        this.modelName = modelName;
        this.estimatedAge = estimatedAge;
        this.exteriorCondition = exteriorCondition;
        this.updatedAt = OffsetDateTime.now();
    }

    public void confirmByCustomer(
            String applianceType,
            String brand,
            String modelName,
            String estimatedAge,
            String exteriorCondition
    ) {
        applyMockInspection(applianceType, brand, modelName, estimatedAge, exteriorCondition);
        this.confirmedByCustomer = true;
    }
    public Long getId() {
        return id;
    }

    public String getApplianceType() {
        return applianceType;
    }
}