package com.Shubham.carDealership.fsm.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class DashboardStats {
    private long jobsToday;
    private long jobsCompleted;
    private long jobsInProgress;
    private long unassignedJobs;
    private BigDecimal revenueToday;
    private BigDecimal revenueMonth;
    private BigDecimal revenuePrevMonth;
    private long jobsMonth;
    private long activeTechnicians;
    private long totalTechnicians;
    private BigDecimal outstandingInvoices;
}
