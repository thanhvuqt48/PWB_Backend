package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.dto.request.UserCreationRequest;
import com.fpt.producerworkbench.dto.response.UserProfileResponse;
import com.fpt.producerworkbench.dto.response.UserResponse;
import com.fpt.producerworkbench.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);

    @Mapping(target = "role", source = "role", qualifiedByName = "roleToString")
    UserProfileResponse toUserProfileResponse(User user);

    @Named("roleToString")
    default String roleToString(UserRole role) {
        return role != null ? role.name() : null;
    }
}
