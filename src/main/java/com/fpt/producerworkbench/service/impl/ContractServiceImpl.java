package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.dto.request.ContractCreateRequest;
import com.fpt.producerworkbench.dto.request.MilestoneRequest;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.ProjectRepository;
import com.fpt.producerworkbench.service.ContractService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ProjectRepository projectRepository;
    private final ContractRepository contractRepository;
    private final MilestoneRepository milestoneRepository;

    @Override
    @Transactional
    public Contract createDraftContract(Authentication auth, Long projectId, ContractCreateRequest req) {
        log.info("Bắt đầu tạo hợp đồng nháp cho dự án ID: {}", projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));

        if (contractRepository.findByProjectId(projectId).isPresent()) {
            log.warn("Dự án ID: {} đã có hợp đồng. Yêu cầu bị từ chối.", projectId);
            throw new AppException(ErrorCode.CONTRACT_ALREADY_EXISTS);
        }

        Contract contract = Contract.builder()
                .project(project)
                .contractDetails(req.getContractDetails())
                .totalAmount(req.getTotalAmount())
                .paymentType(req.getPaymentType())
                .status(ContractStatus.DRAFT)
                .signnowStatus(ContractStatus.DRAFT)
                .build();

        Contract savedContract = contractRepository.save(contract);
        log.info("Đã lưu hợp đồng nháp ID: {} cho dự án ID: {}", savedContract.getId(), projectId);

        if (req.getPaymentType() == PaymentType.MILESTONE) {
            if (req.getMilestones() == null || req.getMilestones().isEmpty()) {
                throw new AppException(ErrorCode.MILESTONES_REQUIRED_FOR_PAYMENT_TYPE);
            }
            int sequence = 1;
            for (MilestoneRequest mReq : req.getMilestones()) {
                Milestone milestone = Milestone.builder()
                        .contract(savedContract)
                        .title(mReq.getTitle())
                        .description(mReq.getDescription())
                        .amount(mReq.getAmount())
                        .dueDate(mReq.getDueDate())
                        .status(MilestoneStatus.PENDING)
                        .sequence(sequence++)
                        .build();
                milestoneRepository.save(milestone);
            }
            log.info("Đã lưu {} cột mốc cho hợp đồng ID: {}", req.getMilestones().size(), savedContract.getId());
        }

        return savedContract;
    }
}