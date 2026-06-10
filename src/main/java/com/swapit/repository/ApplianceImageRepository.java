package com.swapit.repository;

import com.swapit.domain.entity.ApplianceImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceImageRepository extends JpaRepository<ApplianceImageEntity, Long> {
}