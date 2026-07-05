package com.Shubham.carDealership.fsm.repository;

import com.Shubham.carDealership.fsm.model.FsmTechnician;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FsmTechnicianRepository extends JpaRepository<FsmTechnician, Long> {
    List<FsmTechnician> findByBusinessOwnerIdOrderByNameAsc(Long businessOwnerId);
    boolean existsByIdAndBusinessOwnerId(Long id, Long businessOwnerId);
    long countByBusinessOwnerIdAndStatusNot(Long businessOwnerId, String status);
    long countByBusinessOwnerId(Long businessOwnerId);
    Optional<FsmTechnician> findByUserId(Long userId);
}
