package com.Shubham.carDealership.fsm.repository;

import com.Shubham.carDealership.fsm.model.FsmInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FsmInvoiceRepository extends JpaRepository<FsmInvoice, Long> {

    List<FsmInvoice> findByBusinessOwnerIdOrderByIssuedAtDesc(Long businessOwnerId);

    List<FsmInvoice> findByBusinessOwnerIdAndStatusOrderByIssuedAtDesc(Long businessOwnerId, String status);

    Optional<FsmInvoice> findByJobId(Long jobId);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM FsmInvoice i WHERE i.businessOwnerId = :ownerId AND i.status = :status")
    BigDecimal sumByOwnerAndStatus(@Param("ownerId") Long ownerId, @Param("status") String status);

    List<FsmInvoice> findByBusinessOwnerIdAndStatusAndIssuedAtBetween(
            Long businessOwnerId, String status, java.time.LocalDateTime from, java.time.LocalDateTime to);
}
