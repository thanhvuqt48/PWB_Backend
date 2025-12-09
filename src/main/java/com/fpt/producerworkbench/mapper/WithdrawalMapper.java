package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.WithdrawalResponse;
import com.fpt.producerworkbench.entity.Withdrawal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {BankMapper.class})
public interface WithdrawalMapper {

    @Mapping(source = "withdrawal.status", target = "status", qualifiedByName = "statusToString")
    @Mapping(source = "withdrawal.bank", target = "bank")
    @Mapping(source = "remainingBalance", target = "remainingBalance")
    WithdrawalResponse toWithdrawalResponse(Withdrawal withdrawal, java.math.BigDecimal remainingBalance);

    @org.mapstruct.Named("statusToString")
    default String statusToString(com.fpt.producerworkbench.common.WithdrawalStatus status) {
        return status != null ? status.name() : null;
    }
}

