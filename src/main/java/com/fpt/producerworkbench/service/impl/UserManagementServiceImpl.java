package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.response.UserDetailResponse;
import com.fpt.producerworkbench.dto.response.UserListItemResponse;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.UserRepository;
import com.fpt.producerworkbench.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserManagementServiceImpl implements UserManagementService {

    UserRepository userRepository;

    @Override
    public PageResponse<UserListItemResponse> getUsers(
            Integer page, Integer size, String sortBy, String direction,
            String keyword, UserRole role, UserStatus status
    ) {
        int p = (page == null || page < 0) ? 0 : page;
        int s = (size == null || size <= 0) ? 20 : size;
        String sortField = (sortBy == null || sortBy.isBlank()) ? "id" : sortBy;
        Sort.Direction dir = "ASC".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(p, s, Sort.by(dir, sortField));

        Page<User> users = userRepository.search(
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                role, status, pageable
        );

        Page<UserListItemResponse> dtoPage = users.map(this::toItem);
        return PageResponse.fromPage(dtoPage);
    }

    @Override
    public UserDetailResponse getUserDetail(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)); // dùng ErrorCode của bạn

        return toDetail(u);
    }

    @Transactional
    @Override
    public UserDetailResponse activateUser(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (u.getRole() == UserRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (u.getStatus() != UserStatus.ACTIVE) {
            u.setStatus(UserStatus.ACTIVE);
            userRepository.save(u);
        }
        return toDetail(u);
    }

    @Transactional
    @Override
    public UserDetailResponse deactivateUser(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (u.getRole() == UserRole.ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (u.getStatus() != UserStatus.SUSPENDED) {
            u.setStatus(UserStatus.SUSPENDED);
            userRepository.save(u);
        }
        return toDetail(u);
    }

    private UserListItemResponse toItem(User u) {
        return UserListItemResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .dateOfBirth(u.getDateOfBirth())
                .avatarUrl(u.getAvatarUrl())
                .location(u.getLocation())
                .role(u.getRole())
                .status(u.getStatus())
                .balance(u.getBalance())
                .build();
    }

    private UserDetailResponse toDetail(User u) {
        return UserDetailResponse.builder()
                .id(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .phoneNumber(u.getPhoneNumber())
                .dateOfBirth(u.getDateOfBirth())
                .avatarUrl(u.getAvatarUrl())
                .location(u.getLocation())
                .role(u.getRole())
                .status(u.getStatus())
                .balance(u.getBalance())
                .build();
    }
}