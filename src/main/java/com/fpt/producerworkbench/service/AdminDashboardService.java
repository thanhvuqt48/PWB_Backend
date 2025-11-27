package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.AdminDashboardResponse;
import java.time.LocalDate;

public interface AdminDashboardService {

    AdminDashboardResponse getDashboardStats(LocalDate fromDate, LocalDate toDate, String currentEmail);
}