package com.swapit.repository;

import com.swapit.domain.entity.ApplianceSpecEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ApplianceSpecsRepository extends JpaRepository<ApplianceSpecEntity, Long> {

    @Query("SELECT s FROM ApplianceSpecEntity s WHERE LOWER(s.modelName) = LOWER(:modelName)")
    Optional<ApplianceSpecEntity> findByModelNameIgnoreCase(String modelName);
}
