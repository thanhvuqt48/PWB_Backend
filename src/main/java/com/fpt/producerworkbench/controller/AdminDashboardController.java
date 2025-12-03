package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.response.AdminDashboardResponse;
import com.fpt.producerworkbench.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ResponseEntity<AdminDashboardResponse> getDashboardStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "day") String groupBy,
            Principal principal
    ) {
        return ResponseEntity.ok(adminDashboardService.getDashboardStats(fromDate, toDate, groupBy, principal.getName()));
    }
}