package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.UserBankResponse;
import com.fpt.producerworkbench.entity.UserBank;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {BankMapper.class})
public interface UserBankMapper {
    
    @Mapping(source = "bank", target = "bank")
    UserBankResponse toUserBankResponse(UserBank userBank);
}

