package com.swapit.repository;

import com.swapit.domain.entity.PickupRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PickupRequestRepository extends JpaRepository<PickupRequestEntity, Long> {
    Optional<PickupRequestEntity> findFirstBySwapRequest_IdOrderByCreatedAtDesc(Long swapRequestId);

    List<PickupRequestEntity> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    List<PickupRequestEntity> findByPickupTypeAndBookingDateAndStatusIn(
            String pickupType,
            LocalDate bookingDate,
            Collection<String> statuses
    );

    @Query("""
            select p
            from PickupRequestEntity p
            join fetch p.swapRequest s
            where p.status in :statuses
            order by p.createdAt desc
            """)
    List<PickupRequestEntity> findAllWithSwapRequestByStatuses(@Param("statuses") Collection<String> statuses);
}

