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
@Table(name = "swap_requests")
public class SwapRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "swap_request_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "appliance_type", nullable = false, length = 30)
    private String applianceType;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "request_channel", nullable = false, length = 30)
    private String requestChannel;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    protected SwapRequestEntity() {
    }

    private SwapRequestEntity(UserEntity user, String applianceType, String status) {
        OffsetDateTime now = OffsetDateTime.now();
        this.user = user;
        this.applianceType = applianceType;
        this.status = status;
        this.requestChannel = "THINQ_APP";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SwapRequestEntity create(UserEntity user, String applianceType, String status) {
        return new SwapRequestEntity(user, applianceType, status);
    }

    public void changeStatus(String status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }
    public Long getId() {
        return id;
    }

    public UserEntity getUser() {
        return user;
    }

    public String getApplianceType() {
        return applianceType;
    }
}