package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.request.UserCreationRequest;
import com.fpt.producerworkbench.dto.response.UserResponse;
import com.fpt.producerworkbench.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toUser(UserCreationRequest request);

    UserResponse toUserResponse(User user);
}
