package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.dto.response.BankResponse;
import com.fpt.producerworkbench.mapper.BankMapper;
import com.fpt.producerworkbench.repository.BankRepository;
import com.fpt.producerworkbench.service.BankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankServiceImpl implements BankService {

    private final BankRepository bankRepository;
    private final BankMapper bankMapper;

    @Override
    public List<BankResponse> getAllBanks() {
        return bankMapper.toBankResponseList(bankRepository.findAll());
    }

    @Override
    public List<BankResponse> getTransferSupportedBanks() {
        return bankMapper.toBankResponseList(
                bankRepository.findByTransferSupportedTrueOrderByNameAsc()
        );
    }

    @Override
    public List<BankResponse> searchBanks(String keyword) {
        return bankMapper.toBankResponseList(
                bankRepository.searchBanks(keyword)
        );
    }
}

