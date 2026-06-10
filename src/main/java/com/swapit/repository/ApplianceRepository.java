package com.swapit.repository;

import com.swapit.domain.entity.ApplianceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplianceRepository extends JpaRepository<ApplianceEntity, Long> {
    Optional<ApplianceEntity> findBySwapRequest_Id(Long swapRequestId);
}