package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.request.ProPackageRequest;
import com.fpt.producerworkbench.dto.response.ProPackageResponse;
import com.fpt.producerworkbench.entity.ProPackage;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.mapper.ProPackageMapper;
import com.fpt.producerworkbench.repository.ProPackageRepository;
import com.fpt.producerworkbench.service.ProPackageService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProPackageServiceImpl implements ProPackageService {

    ProPackageRepository proPackageRepository;
    ProPackageMapper proPackageMapper;

    @Override
    @Transactional
    public ProPackageResponse create(ProPackageRequest request) {
        log.info("Tạo gói Pro có tên: {}", request.getName());

        if (proPackageRepository.existsByName(request.getName())) {
            log.warn("Gói Pro có tên {} đã tồn tại", request.getName());
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE);
        }

        ProPackage proPackage = proPackageMapper.toEntity(request);

        ProPackage savedPackage = proPackageRepository.save(proPackage);

        log.info("Đã tạo thành công gói Pro với ID: {}", savedPackage.getId());
        return proPackageMapper.toResponse(savedPackage);
    }

    @Override
    @Transactional(readOnly = true)
    public ProPackageResponse findById(Long id) {
        log.info("Tìm gói Pro theo ID: {}", id);

        ProPackage proPackage = proPackageRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Không tìm thấy gói Pro có ID: {}", id);
                    return new AppException(ErrorCode.RESOURCE_NOT_FOUND);
                });

        return proPackageMapper.toResponse(proPackage);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProPackageResponse> findAll(Pageable pageable) {
        log.info("Tìm tất cả các gói Pro có phân trang: {}", pageable);

        Page<ProPackage> proPackages = proPackageRepository.findAll(pageable);
        return proPackages.map(proPackageMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProPackageResponse> findWithFilters(String name, 
                                                   ProPackage.ProPackageType packageType, 
                                                   Boolean isActive, 
                                                   Pageable pageable) {
        log.info("Tìm các gói Pro có bộ lọc - name: {}, packageType: {}, isActive: {}",
                name, packageType, isActive);

        Page<ProPackage> proPackages = proPackageRepository
                .findByFilters(name, packageType, isActive, pageable);

        return proPackages.map(proPackageMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProPackageResponse> findAllActive() {
        log.info("Đang tìm tất cả các gói Pro đang hoạt động");

        List<ProPackage> proPackages = proPackageRepository.findByIsActiveTrue();
        return proPackages.stream()
                .map(proPackageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProPackageResponse> findByPackageType(ProPackage.ProPackageType packageType) {
        log.info("Tìm gói Pro theo loại: {}", packageType);

        List<ProPackage> proPackages = proPackageRepository.findByPackageType(packageType);
        return proPackages.stream()
                .map(proPackageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProPackageResponse> findByPackageTypeAndActive(ProPackage.ProPackageType packageType) {
        log.info("Tìm các gói Pro đang hoạt động theo loại: {}", packageType);

        List<ProPackage> proPackages = proPackageRepository.findByPackageTypeAndIsActiveTrue(packageType);
        return proPackages.stream()
                .map(proPackageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProPackageResponse update(Long id, ProPackageRequest request) {
        log.info("Đang cập nhật gói Pro với ID: {}", id);

        ProPackage existingPackage = proPackageRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Không tìm thấy gói Pro có ID: {}", id);
                    return new AppException(ErrorCode.RESOURCE_NOT_FOUND);
                });

        if (proPackageRepository.existsByNameAndIdNot(request.getName(), id)) {
            log.warn("Gói Pro có tên {} đã tồn tại", request.getName());
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE);
        }

        proPackageMapper.updateEntityFromRequest(request, existingPackage);

        ProPackage updatedPackage = proPackageRepository.save(existingPackage);

        log.info("Đã cập nhật thành công gói Pro với ID: {}", id);
        return proPackageMapper.toResponse(updatedPackage);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("Đang xóa gói Pro có ID: {}", id);

        if (!proPackageRepository.existsById(id)) {
            log.warn("Không tìm thấy gói Pro có ID: {}", id);
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        proPackageRepository.deleteById(id);
        log.info("Đã xóa thành công gói Pro có ID: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return proPackageRepository.existsByName(name);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByNameAndIdNot(String name, Long id) {
        return proPackageRepository.existsByNameAndIdNot(name, id);
    }
}
