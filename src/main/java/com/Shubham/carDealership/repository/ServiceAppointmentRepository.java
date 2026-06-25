// src/main/java/com/Shubham/carDealership/repository/ServiceAppointmentRepository.java
package com.Shubham.carDealership.repository;

import com.Shubham.carDealership.model.ServiceAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceAppointmentRepository extends JpaRepository<ServiceAppointment, Long> {
    List<ServiceAppointment> findByUserId(Long userId);
    List<ServiceAppointment> findByTechnicianId(Long technicianId);
    List<ServiceAppointment> findByStatus(String status);

    // NEW — does this user already have an active (pending/confirmed) booking
    // for this specific car? Used to block duplicate bookings and to show the
    // "you already have a booking" state instead of a blank form.
    List<ServiceAppointment> findByUserIdAndCarIdAndStatusIn(Long userId, Long carId, List<String> statuses);

    // NEW — all active bookings for a car, regardless of which user made them.
    // Used to lock out a time slot once anyone has it pending or confirmed.
    List<ServiceAppointment> findByCarIdAndStatusIn(Long carId, List<String> statuses);
}