package com.Shubham.carDealership.fsm.repository;

import com.Shubham.carDealership.fsm.model.FsmJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface FsmJobRepository extends JpaRepository<FsmJob, Long> {

    List<FsmJob> findByBusinessOwnerIdOrderByScheduledAtDesc(Long businessOwnerId);

    List<FsmJob> findByBusinessOwnerIdAndStatusOrderByScheduledAtAsc(Long businessOwnerId, String status);

    List<FsmJob> findByBusinessOwnerIdAndScheduledAtBetweenOrderByScheduledAtAsc(
            Long businessOwnerId, LocalDateTime from, LocalDateTime to);

    long countByBusinessOwnerIdAndStatus(Long businessOwnerId, String status);

    long countByBusinessOwnerIdAndTechnicianIsNull(Long businessOwnerId);

    @Query("SELECT COALESCE(SUM(j.amount), 0) FROM FsmJob j WHERE j.businessOwnerId = :ownerId AND j.status = 'COMPLETED' AND j.scheduledAt BETWEEN :from AND :to")
    BigDecimal sumRevenueByOwnerAndDateRange(@Param("ownerId") Long ownerId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    List<FsmJob> findByTechnician_IdOrderByScheduledAtDesc(Long technicianId);

    List<FsmJob> findByTechnician_IdAndScheduledAtBetweenOrderByScheduledAtAsc(
            Long technicianId, LocalDateTime from, LocalDateTime to);

    java.util.Optional<FsmJob> findByTrackingKey(String trackingKey);

    List<FsmJob> findTop20ByBusinessOwnerIdOrderByCreatedAtDesc(Long businessOwnerId);

    List<FsmJob> findByBusinessOwnerIdAndCreatedAtBetween(
            Long businessOwnerId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT j.status, COUNT(j) FROM FsmJob j WHERE j.businessOwnerId = :ownerId GROUP BY j.status")
    List<Object[]> countByStatusForOwner(@Param("ownerId") Long ownerId);

    @Query("SELECT j.jobType, COUNT(j) FROM FsmJob j WHERE j.businessOwnerId = :ownerId GROUP BY j.jobType ORDER BY COUNT(j) DESC")
    List<Object[]> countByJobTypeForOwner(@Param("ownerId") Long ownerId);

    @Query("SELECT j.technician.name, COUNT(j) FROM FsmJob j WHERE j.businessOwnerId = :ownerId AND j.status IN ('COMPLETED','INVOICED') AND j.technician IS NOT NULL GROUP BY j.technician.name ORDER BY COUNT(j) DESC")
    List<Object[]> completedJobsPerTech(@Param("ownerId") Long ownerId);
}
