package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import com.fpt.producerworkbench.dto.response.PageResponse;
import com.fpt.producerworkbench.dto.response.UserDetailResponse;
import com.fpt.producerworkbench.dto.response.UserListItemResponse;

public interface UserManagementService {
    PageResponse<UserListItemResponse> getUsers(
            Integer page,
            Integer size,
            String sortBy,
            String direction,
            String keyword,
            UserRole role,
            UserStatus status
    );

    UserDetailResponse getUserDetail(Long id);
    UserDetailResponse activateUser(Long id);
    UserDetailResponse deactivateUser(Long id);
}
