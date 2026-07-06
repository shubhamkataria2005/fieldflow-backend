package com.Shubham.carDealership.fsm.repository;

import com.Shubham.carDealership.fsm.model.FsmQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FsmQuoteRepository extends JpaRepository<FsmQuote, Long> {
    List<FsmQuote> findByBusinessOwnerIdOrderByCreatedAtDesc(Long businessOwnerId);
    List<FsmQuote> findByBusinessOwnerIdAndStatusOrderByCreatedAtDesc(Long businessOwnerId, String status);
    long countByBusinessOwnerIdAndStatus(Long businessOwnerId, String status);
}
