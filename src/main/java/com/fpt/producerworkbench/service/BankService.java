package com.fpt.producerworkbench.service;

import com.fpt.producerworkbench.dto.response.BankResponse;

import java.util.List;

public interface BankService {
    List<BankResponse> getAllBanks();
    List<BankResponse> getTransferSupportedBanks();
    List<BankResponse> searchBanks(String keyword);
}

