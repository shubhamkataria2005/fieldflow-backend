package com.Shubham.carDealership.fsm.repository;

import com.Shubham.carDealership.fsm.model.JobMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JobMessageRepository extends JpaRepository<JobMessage, Long> {

    List<JobMessage> findByTrackingKeyOrderBySentAtAsc(String trackingKey);

    @Query("SELECT COUNT(m) FROM JobMessage m WHERE m.jobId IN :jobIds AND m.senderType IN :types AND m.sentAt > :since")
    long countNewMessages(
        @Param("jobIds") List<Long> jobIds,
        @Param("types")  List<String> types,
        @Param("since")  LocalDateTime since
    );
}
