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
@Table(name = "appliance_images")
public class ApplianceImageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appliance_image_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "swap_request_id", nullable = false)
    private SwapRequestEntity swapRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appliance_id")
    private ApplianceEntity appliance;

    @Column(name = "image_type", nullable = false, length = 30)
    private String imageType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "storage_key", length = 255)
    private String storageKey;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;

    protected ApplianceImageEntity() {
    }

    private ApplianceImageEntity(SwapRequestEntity swapRequest, ApplianceEntity appliance, String fileName, String imageUrl) {
        this.swapRequest = swapRequest;
        this.appliance = appliance;
        this.imageType = "CUSTOMER_CAPTURE";
        this.fileName = fileName;
        this.imageUrl = imageUrl;
        this.storageKey = imageUrl;
        this.uploadedAt = OffsetDateTime.now();
    }

    public static ApplianceImageEntity customerCapture(
            SwapRequestEntity swapRequest,
            ApplianceEntity appliance,
            String fileName,
            String imageUrl
    ) {
        return new ApplianceImageEntity(swapRequest, appliance, fileName, imageUrl);
    }
}