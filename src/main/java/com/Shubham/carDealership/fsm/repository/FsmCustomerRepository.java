package com.Shubham.carDealership.fsm.repository;

import com.Shubham.carDealership.fsm.model.FsmCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FsmCustomerRepository extends JpaRepository<FsmCustomer, Long> {
    List<FsmCustomer> findByBusinessOwnerIdOrderByNameAsc(Long businessOwnerId);
    boolean existsByIdAndBusinessOwnerId(Long id, Long businessOwnerId);
    long countByBusinessOwnerId(Long businessOwnerId);
}
