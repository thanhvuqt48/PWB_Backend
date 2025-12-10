package com.fpt.producerworkbench.mapper;

import com.fpt.producerworkbench.dto.response.BankResponse;
import com.fpt.producerworkbench.entity.Bank;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface BankMapper {

    BankResponse toBankResponse(Bank bank);

    List<BankResponse> toBankResponseList(List<Bank> banks);
}

