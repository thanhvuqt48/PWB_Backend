package com.fpt.producerworkbench.controller;

import com.fpt.producerworkbench.dto.request.ProPackageRequest;
import com.fpt.producerworkbench.dto.response.ApiResponse;
import com.fpt.producerworkbench.dto.response.ProPackageResponse;
import com.fpt.producerworkbench.entity.ProPackage;
import com.fpt.producerworkbench.service.ProPackageService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api/v1/pro-packages")
@Slf4j
public class ProPackageController {

    ProPackageService proPackageService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    ApiResponse<ProPackageResponse> create(@Valid @RequestBody ProPackageRequest request) {
        log.info("Creating Pro package: {}", request.getName());

        var result = proPackageService.create(request);

        return ApiResponse.<ProPackageResponse>builder()
                .code(HttpStatus.CREATED.value())
                .message("Tạo gói PRO thành công")
                .result(result)
                .build();
    }

    @GetMapping("/{id}")
    ApiResponse<ProPackageResponse> findById(@PathVariable Long id) {
        log.info("Finding Pro package by ID: {}", id);

        var result = proPackageService.findById(id);

        return ApiResponse.<ProPackageResponse>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping
    ApiResponse<Page<ProPackageResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Finding all Pro packages - page: {}, size: {}", page, size);

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        var result = proPackageService.findAll(pageable);

        return ApiResponse.<Page<ProPackageResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/active")
    ApiResponse<List<ProPackageResponse>> findAllActive() {
        log.info("Finding all active Pro packages");

        var result = proPackageService.findAllActive();

        return ApiResponse.<List<ProPackageResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/search")
    ApiResponse<Page<ProPackageResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) ProPackage.ProPackageType packageType,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        log.info("Searching Pro packages with filters - name: {}, packageType: {}, isActive: {}", 
                name, packageType, isActive);

        Sort sort = sortDir.equalsIgnoreCase("desc") ?
                Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        var result = proPackageService.findWithFilters(name, packageType, isActive, pageable);

        return ApiResponse.<Page<ProPackageResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/type/{packageType}")
    ApiResponse<List<ProPackageResponse>> findByPackageType(@PathVariable ProPackage.ProPackageType packageType) {
        log.info("Finding Pro packages by type: {}", packageType);

        var result = proPackageService.findByPackageType(packageType);

        return ApiResponse.<List<ProPackageResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @GetMapping("/type/{packageType}/active")
    ApiResponse<List<ProPackageResponse>> findByPackageTypeAndActive(@PathVariable ProPackage.ProPackageType packageType) {
        log.info("Finding active Pro packages by type: {}", packageType);

        var result = proPackageService.findByPackageTypeAndActive(packageType);

        return ApiResponse.<List<ProPackageResponse>>builder()
                .code(HttpStatus.OK.value())
                .result(result)
                .build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    ApiResponse<ProPackageResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody ProPackageRequest request) {
        log.info("Updating Pro package with ID: {}", id);

        var result = proPackageService.update(id, request);

        return ApiResponse.<ProPackageResponse>builder()
                .code(HttpStatus.OK.value())
                .message("Cập nhật gói PRO thành công")
                .result(result)
                .build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    ApiResponse<Void> delete(@PathVariable Long id) {
        log.info("Deleting Pro package with ID: {}", id);

        proPackageService.delete(id);

        return ApiResponse.<Void>builder()
                .code(HttpStatus.OK.value())
                .message("Xóa gói PRO thành công")
                .build();
    }
}
