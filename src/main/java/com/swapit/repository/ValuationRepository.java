package com.swapit.repository;

import com.swapit.domain.entity.ValuationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValuationRepository extends JpaRepository<ValuationEntity, Long> {
}