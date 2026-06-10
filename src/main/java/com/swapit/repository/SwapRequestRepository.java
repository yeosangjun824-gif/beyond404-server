package com.swapit.repository;

import com.swapit.domain.entity.SwapRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SwapRequestRepository extends JpaRepository<SwapRequestEntity, Long> {
}