package com.swapit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "thinq_user_key", nullable = false, unique = true, length = 100)
    private String thinqUserKey;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserEntity() {
    }

    private UserEntity(String thinqUserKey, String name, String phoneNumber) {
        OffsetDateTime now = OffsetDateTime.now();
        this.thinqUserKey = thinqUserKey;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static UserEntity create(String thinqUserKey, String name, String phoneNumber) {
        return new UserEntity(thinqUserKey, name, phoneNumber);
    }

    public void updateProfile(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getThinqUserKey() {
        return thinqUserKey;
    }
}
