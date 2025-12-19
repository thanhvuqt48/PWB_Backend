package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.PayoutMethod;
import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;
import com.fpt.producerworkbench.common.TaxStatus;
import com.fpt.producerworkbench.common.TerminatedBy;
import com.fpt.producerworkbench.common.TerminationStatus;
import com.fpt.producerworkbench.common.TerminationType;
import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.ContractTerminationService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of Contract Termination Service
 * Implement theo logic document: CONTRACT_TERMINATION_LOGIC.md
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContractTerminationServiceImpl implements ContractTerminationService {
    
    // Repositories
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneMoneySplitRepository milestoneMoneySplitRepository;
    private final MilestoneMemberRepository milestoneMemberRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final ContractTerminationRepository contractTerminationRepository;
    private final OwnerCompensationPaymentRepository ownerCompensationPaymentRepository;
    private final TaxPayoutRecordRepository taxPayoutRecordRepository;
    private final com.fpt.producerworkbench.service.OwnerCompensationPaymentService ownerCompensationPaymentService;
    
    // Constants
    private static final BigDecimal TAX_RATE = new BigDecimal("0.07"); // 7%
    private static final int TAX_DECLARATION_DAY = 20;
    
    @Override
    @Transactional(readOnly = true)
    public Object previewTermination(
            Long contractId,
            Authentication auth
    ) {
        log.info("Preview termination for contract: {}", contractId);
        
        // Validate & detect who is terminating
        Contract contract = getContractOrThrow(contractId);
        User currentUser = getCurrentUser(auth);
        validateTerminationRequest(contract, currentUser);
        
        // Auto-detect terminatedBy based on current user
        TerminatedBy terminatedBy = detectTerminatedBy(contract, currentUser);
        log.info("Detected terminatedBy: {}", terminatedBy);
        
        // Calculate
        TerminationCalculation calc = calculateTermination(contract, terminatedBy);
        
        // Build response based on who is terminating
        if (terminatedBy == TerminatedBy.CLIENT) {
            return buildClientPreviewResponse(calc);
        } else {
            return buildOwnerPreviewResponse(calc, contract);
        }
    }
    
    /**
     * Build preview response cho Client chấm dứt
     */
    private ClientTerminationPreviewResponse buildClientPreviewResponse(TerminationCalculation calc) {
        LocalDate now = LocalDate.now();
        LocalDate secondPaymentDate = calc.isAfterDay20() ? 
                now.plusMonths(1).withDayOfMonth(TAX_DECLARATION_DAY) : null;
        
        return ClientTerminationPreviewResponse.builder()
                .totalAmount(calc.getTotalAmount())
                .compensationAmount(calc.getOwnerCompensation()) // Số tiền đền bù
                .clientWillReceive(calc.getClientRefund())
                .hasTwoPayments(calc.isAfterDay20())
                .secondPaymentDate(secondPaymentDate)
                .secondPaymentAmount(calc.getRefundedTax())
                .warning(calc.getWarning())
                .build();
    }
    
    /**
     * Build preview response cho Owner chấm dứt
     */
    private OwnerTerminationPreviewResponse buildOwnerPreviewResponse(
            TerminationCalculation calc, 
            Contract contract
    ) {
        User owner = contract.getProject().getCreator();
        Long projectId = contract.getProject().getId();
        
        // Build danh sách team members với thông tin chi tiết
        List<TeamMemberCompensationInfo> teamMembers = buildTeamMemberCompensationList(
                calc.getTeamSplits(), 
                projectId
        );
        
        return OwnerTerminationPreviewResponse.builder()
                .totalAmount(calc.getTotalAmount())
                .totalTeamCompensation(calc.getTotalTeamGross()) // Tổng đền bù (gross)
                .clientWillReceive(calc.getClientRefund())
                .ownerWillReceive(calc.getOwnerActualReceive()) // Số tiền Owner nhận được (trước thuế, gross)
                .requiredPaymentAmount(calc.getTotalTeamGross()) // Owner phải chuyển gross
                .currentOwnerBalance(owner.getBalance())
                .ownerHasSufficientBalance(
                        owner.getBalance().compareTo(calc.getTotalTeamGross()) >= 0
                )
                .hasTwoPayments(calc.isAfterDay20())
                .teamMembers(teamMembers)
                .warning(calc.getWarning())
                .build();
    }
    
    /**
     * Build danh sách thông tin đền bù cho từng team member
     */
    private List<TeamMemberCompensationInfo> buildTeamMemberCompensationList(
            List<MilestoneMoneySplit> teamSplits,
            Long projectId
    ) {
        // Lấy danh sách milestone members để có description
        Map<String, MilestoneMember> milestoneMemberMap = new HashMap<>();
        for (MilestoneMoneySplit split : teamSplits) {
            Long milestoneId = split.getMilestone().getId();
            Long userId = split.getUser().getId();
            milestoneMemberRepository.findByMilestoneIdAndUserId(milestoneId, userId)
                    .ifPresent(member -> milestoneMemberMap.put(milestoneId + "_" + userId, member));
        }
        
        return teamSplits.stream()
                .map(split -> {
                    User user = split.getUser();
                    Milestone milestone = split.getMilestone();
                    String key = milestone.getId() + "_" + user.getId();
                    MilestoneMember milestoneMember = milestoneMemberMap.get(key);
                    
                    return TeamMemberCompensationInfo.builder()
                            .userId(user.getId())
                            .userName(user.getFullName())
                            .userEmail(user.getEmail())
                            .avatarUrl(user.getAvatarUrl())
                            .description(milestoneMember != null ? milestoneMember.getDescription() : null)
                            .milestoneId(milestone.getId())
                            .milestoneName(milestone.getTitle())
                            .amount(split.getAmount()) // Gross, chưa trừ thuế
                            .build();
                })
                .collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    @Transactional
    public TerminationResponse terminateContract(
            Long contractId,
            TerminationRequest request,
            Authentication auth
    ) {
        // === STEP 1: Validation ===
        Contract contract = getContractOrThrow(contractId);
        User currentUser = getCurrentUser(auth);
        validateTerminationRequest(contract, currentUser);
        
        // Auto-detect terminatedBy based on current user
        TerminatedBy terminatedBy = detectTerminatedBy(contract, currentUser);
        log.info("Terminating contract: {} by {}", contractId, terminatedBy);
        
        // Kiểm tra đã chấm dứt chưa
        if (contractTerminationRepository.existsByContractId(contractId)) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_TERMINATED);
        }
        
        // === STEP 2: Calculate ===
        TerminationCalculation calc = calculateTermination(contract, terminatedBy);
        
        // === STEP 3: Process based on who terminates ===
        if (terminatedBy == TerminatedBy.OWNER) {
            return handleOwnerTermination(contract, request, calc, terminatedBy);
        } else {
            return handleClientTermination(contract, request, calc, terminatedBy);
        }
    }
    
    /**
     * Xử lý khi CLIENT chấm dứt hợp đồng
     */
    private TerminationResponse handleClientTermination(
            Contract contract,
            TerminationRequest request,
            TerminationCalculation calc,
            TerminatedBy terminatedBy
    ) {
        log.info("Handling CLIENT termination for contract: {}", contract.getId());
        
        // === Thanh toán lần 1 ===
        
        // 1. Team Members nhận đền bù (sau thuế 7%)
        List<MilestoneMoneySplit> teamSplits = calc.getTeamSplits();
        for (MilestoneMoneySplit split : teamSplits) {
            BigDecimal grossAmount = split.getAmount();
            BigDecimal tax = grossAmount.multiply(TAX_RATE);
            BigDecimal netAmount = grossAmount.subtract(tax);
            
            // Cộng vào balance
            User member = split.getUser();
            updateUserBalance(member, netAmount, 
                    com.fpt.producerworkbench.common.TransactionType.TERMINATION_TEAM_COMPENSATION,
                    contract, "Team compensation from termination");
            
            // Tạo TaxPayoutRecord
            createTaxPayoutRecord(member, grossAmount, netAmount, tax, 
                    PayoutSource.TERMINATION_COMPENSATION, contract, null);
        }
        
        // 2. Owner nhận đền bù
        User owner = contract.getProject().getCreator();
        if (calc.isAfterDay20()) {
            // Sau ngày 20: Owner nhận đợt 1 = ownerActualReceive - ownerOriginalTax
            BigDecimal ownerOriginalTax = calc.getOriginalTax().subtract(calc.getTeamTax());
            BigDecimal ownerRound1 = calc.getOwnerActualReceive().subtract(ownerOriginalTax);
            
            if (ownerRound1.compareTo(BigDecimal.ZERO) > 0) {
                updateUserBalance(owner, ownerRound1, 
                        com.fpt.producerworkbench.common.TransactionType.TERMINATION_OWNER_COMPENSATION,
                        contract, "Owner compensation round 1");
            }
            
            // Schedule round 2: Hoàn lại thuế = ownerOriginalTax - ownerActualTax
            scheduleSecondPayment(contract, calc);
        } else {
            // Trước ngày 20: Trừ thuế 7% ngay
            BigDecimal ownerTax = calc.getOwnerActualReceive().multiply(TAX_RATE);
            BigDecimal ownerNet = calc.getOwnerActualReceive().subtract(ownerTax);
            
            if (ownerNet.compareTo(BigDecimal.ZERO) > 0) {
                updateUserBalance(owner, ownerNet,
                        com.fpt.producerworkbench.common.TransactionType.TERMINATION_OWNER_COMPENSATION,
                        contract, "Owner compensation (after 7% tax)");
                
                createTaxPayoutRecord(owner, calc.getOwnerActualReceive(), ownerNet, 
                        ownerTax, PayoutSource.TERMINATION_COMPENSATION, contract, null);
            }
        }
        
        // 3. Client nhận hoàn
        User client = contract.getProject().getClient();
        BigDecimal clientRefund = calc.getClientRefund();
        updateUserBalance(client, clientRefund,
                com.fpt.producerworkbench.common.TransactionType.TERMINATION_CLIENT_REFUND,
                contract, "Client refund from termination");
        
        // === Lưu records ===
        TaxRecord taxRecord = saveTaxRecord(contract, calc);
        ContractTermination termination = saveContractTermination(contract, request, calc, taxRecord, terminatedBy);
        
        // === Update contract status ===
        contract.setSignnowStatus(ContractStatus.TERMINATED);
        contractRepository.save(contract);
        
        // === Build response ===
        return buildTerminationResponse(termination, calc, null);
    }
    
    /**
     * Xử lý khi OWNER chấm dứt hợp đồng
     * Owner phải chuyển tiền TỪ TÚI qua PayOS trước
     */
    private TerminationResponse handleOwnerTermination(
            Contract contract,
            TerminationRequest request,
            TerminationCalculation calc,
            TerminatedBy terminatedBy
    ) {
        log.info("Handling OWNER termination for contract: {}", contract.getId());
        
        // Tạo yêu cầu Owner chuyển tiền qua PayOS
        OwnerCompensationPayment payment = ownerCompensationPaymentService.createPaymentOrder(
                contract.getId(),
                contract.getProject().getCreator().getId(),
                calc.getTotalTeamGross(),
                "Owner compensation for contract termination"
        );
        
        // TODO: Gửi notification cho Owner
        
        log.info("Created owner compensation payment. Waiting for payment confirmation.");
        
        // Return response với payment info
        return TerminationResponse.builder()
                .terminationId(null) // Chưa hoàn tất
                .contractId(contract.getId())
                .newStatus(null) // Đang chờ Owner thanh toán
                .ownerCompensationPaymentId(payment.getId())
                .paymentUrl(payment.getPaymentUrl())
                .paymentOrderCode(payment.getPaymentOrderCode())
                .teamCompensation(calc.getTotalTeamGross())
                .message("Owner cần hoàn tất thanh toán để chấm dứt hợp đồng")
                .build();
    }
    
    @Override
    @Transactional
    public void handleOwnerCompensationWebhook(String orderCode, String status) {
        log.info("Handling owner compensation webhook. OrderCode: {}, Status: {}", 
                orderCode, status);
        
        if (!"SUCCESS".equals(status)) {
            log.warn("Payment not successful: {}", status);
            return;
        }
        
        // Tìm payment
        OwnerCompensationPayment payment = ownerCompensationPaymentRepository
                .findByPaymentOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Owner compensation payment not found"));
        
        if (payment.getStatus() == com.fpt.producerworkbench.common.PaymentStatus.COMPLETED) {
            log.info("Payment already processed");
            return;
        }
        
        Contract contract = payment.getContract();
        
        // Cộng NET vào balance Team
        List<MilestoneMoneySplit> teamSplits = getTeamSplits(contract);
        for (MilestoneMoneySplit split : teamSplits) {
            BigDecimal grossAmount = split.getAmount();
            BigDecimal tax = grossAmount.multiply(TAX_RATE);
            BigDecimal netAmount = grossAmount.subtract(tax);
            
            User member = split.getUser();
            updateUserBalance(member, netAmount, 
                    com.fpt.producerworkbench.common.TransactionType.OWNER_COMPENSATE_TEAM,
                    contract, "Owner compensation to team");
            
            createTaxPayoutRecord(member, grossAmount, netAmount, tax,
                    PayoutSource.TERMINATION_COMPENSATION, contract, null);
        }
        
        // Cập nhật payment status
        payment.setStatus(com.fpt.producerworkbench.common.PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());
        ownerCompensationPaymentRepository.save(payment);
        
        // Tiếp tục xử lý chấm dứt hợp đồng
        continueOwnerTermination(contract, payment);
        
        log.info("Owner compensation webhook processed successfully");
    }
    
    /**
     * Tiếp tục chấm dứt hợp đồng sau khi Owner đã thanh toán
     */
    private void continueOwnerTermination(Contract contract, OwnerCompensationPayment payment) {
        log.info("Continuing owner termination for contract: {}", contract.getId());
        
        // Calculate với terminatedBy = OWNER
        TerminationCalculation calc = calculateTermination(contract, TerminatedBy.OWNER);
        
        // Build simple request for reason
        TerminationRequest request = TerminationRequest.builder()
                .reason("Owner termination - payment completed")
                .build();
        
        // Client nhận 100% hoàn
        User client = contract.getProject().getClient();
        if (calc.isAfterDay20()) {
            // Lần 1: Total - Thuế gốc
            BigDecimal clientRound1 = calc.getTotalAmount().subtract(calc.getOriginalTax());
            updateUserBalance(client, clientRound1,
                    com.fpt.producerworkbench.common.TransactionType.TERMINATION_CLIENT_REFUND,
                    contract, "Client refund round 1 (owner termination)");
            
            // Schedule lần 2: Hoàn thuế
            scheduleSecondPaymentForClient(contract, calc);
        } else {
            // Trước ngày 20: Client nhận 100%
            updateUserBalance(client, calc.getTotalAmount(),
                    com.fpt.producerworkbench.common.TransactionType.TERMINATION_CLIENT_REFUND,
                    contract, "Client full refund (owner termination)");
        }
        
        // Lưu records
        TaxRecord taxRecord = saveTaxRecord(contract, calc);
        taxRecord.setOwnerCompensationPaymentId(payment.getId());
        taxRecordRepository.save(taxRecord);
        
        saveContractTermination(contract, request, calc, taxRecord, TerminatedBy.OWNER);
        
        // Update contract status
        contract.setSignnowStatus(ContractStatus.TERMINATED);
        contractRepository.save(contract);
        
        log.info("Owner termination completed for contract: {}", contract.getId());
    }
    
    @Override
    @Transactional(readOnly = true)
    public TerminationDetailResponse getTerminationDetail(Long contractId, Authentication auth) {
        Contract contract = getContractOrThrow(contractId);
        User currentUser = getCurrentUser(auth);
        
        // Check permission
        validateAccessPermission(contract, currentUser);
        
        ContractTermination termination = contractTerminationRepository
                .findByContractId(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Contract termination not found"));
        
        return mapToDetailResponse(termination);
    }
    
    // ===== CALCULATION METHODS =====
    
    /**
     * Tính toán chi tiết chấm dứt hợp đồng
     */
    private TerminationCalculation calculateTermination(
            Contract contract,
            TerminatedBy terminatedBy
    ) {
        TerminationCalculation calc = new TerminationCalculation();
        calc.setTerminatedBy(terminatedBy);
        
        // Xác định thời điểm (trước/sau ngày 20)
        LocalDate now = LocalDate.now();
        boolean isAfterDay20 = now.getDayOfMonth() >= TAX_DECLARATION_DAY;
        calc.setAfterDay20(isAfterDay20);
        calc.setTerminationType(isAfterDay20 ? TerminationType.AFTER_DAY_20 : 
                TerminationType.BEFORE_DAY_20);
        
        // Xác định scope
        BigDecimal totalAmount;
        BigDecimal originalTax = BigDecimal.ZERO;
        List<MilestoneMoneySplit> teamSplits;
        
        if (contract.getPaymentType() == PaymentType.FULL) {
            // FULL: Toàn bộ hợp đồng
            totalAmount = contract.getTotalAmount();
            if (isAfterDay20) {
                originalTax = (contract.getPitTax() != null ? contract.getPitTax() : BigDecimal.ZERO)
                        .add(contract.getVatTax() != null ? contract.getVatTax() : BigDecimal.ZERO);
            }
            teamSplits = milestoneMoneySplitRepository.findApprovedByContractId(contract.getId());
        } else {
            // MILESTONE: Chỉ các milestone IN_PROGRESS
            List<Milestone> inProgressMilestones = milestoneRepository
                    .findByContractIdAndStatus(contract.getId(), MilestoneStatus.IN_PROGRESS);
            
            totalAmount = inProgressMilestones.stream()
                    .map(Milestone::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (isAfterDay20) {
                originalTax = inProgressMilestones.stream()
                        .map(m -> (m.getPitTax() != null ? m.getPitTax() : BigDecimal.ZERO)
                                .add(m.getVatTax() != null ? m.getVatTax() : BigDecimal.ZERO))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            
            teamSplits = milestoneMoneySplitRepository
                    .findApprovedByMilestoneIds(
                            inProgressMilestones.stream()
                                    .map(Milestone::getId)
                                    .toList()
                    );
        }
        
        calc.setTotalAmount(totalAmount);
        calc.setOriginalTax(originalTax);
        calc.setTeamSplits(teamSplits);
        
        // Tính tiền đền bù Team
        BigDecimal totalTeamGross = teamSplits.stream()
                .map(MilestoneMoneySplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        calc.setTotalTeamGross(totalTeamGross);
        
        BigDecimal teamTax = totalTeamGross.multiply(TAX_RATE);
        BigDecimal teamNet = totalTeamGross.subtract(teamTax);
        calc.setTeamTax(teamTax);
        calc.setTeamNetAmount(teamNet);
        
        // Tính tiền đền bù Owner
        BigDecimal compensationPercentage = contract.getCompensationPercentage() != null ?
                contract.getCompensationPercentage() : BigDecimal.ZERO;
        BigDecimal ownerCompensation = totalAmount.multiply(compensationPercentage)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        calc.setOwnerCompensation(ownerCompensation);
        
        BigDecimal ownerActualReceive;
        if (terminatedBy == TerminatedBy.OWNER) {
            // Owner chấm dứt: không nhận gì
            ownerActualReceive = BigDecimal.ZERO;
        } else {
            // Client chấm dứt: tính như bình thường
            ownerActualReceive = ownerCompensation.subtract(totalTeamGross);
        }
        calc.setOwnerActualReceive(ownerActualReceive);
        
        // Tính tiền Owner nhận (tùy trước/sau ngày 20)
        BigDecimal ownerNetAmount;
        BigDecimal ownerOriginalTax = BigDecimal.ZERO;
        if (terminatedBy == TerminatedBy.CLIENT) {
            if (isAfterDay20) {
                // Sau ngày 20: Tính thuế gốc của Owner
                // ownerOriginalTax = originalTax - teamTax (phần thuế gốc của Owner)
                ownerOriginalTax = originalTax.subtract(teamTax);
                
                // Lần 1: Owner nhận = ownerActualReceive - ownerOriginalTax
                ownerNetAmount = ownerActualReceive.subtract(ownerOriginalTax);
                
                // Tính thuế thực tế phải nộp của Owner (7% của ownerActualReceive)
                BigDecimal ownerActualTax = ownerActualReceive.multiply(TAX_RATE);
                // Lần 2: Hoàn lại = ownerOriginalTax - ownerActualTax
                BigDecimal refundTax = ownerOriginalTax.subtract(ownerActualTax);
                calc.setRefundedTax(refundTax);
                calc.setOwnerTax(ownerActualTax);
            } else {
                // Trước ngày 20: Trừ 7%
                BigDecimal ownerTax = ownerActualReceive.multiply(TAX_RATE);
                ownerNetAmount = ownerActualReceive.subtract(ownerTax);
                calc.setOwnerTax(ownerTax);
            }
        } else {
            // Owner chấm dứt: không nhận gì
            ownerNetAmount = BigDecimal.ZERO;
        }
        calc.setOwnerNetAmount(ownerNetAmount);
        
        // Client refund
        BigDecimal clientRefund = terminatedBy == TerminatedBy.CLIENT ?
                totalAmount.subtract(ownerCompensation) : totalAmount;
        calc.setClientRefund(clientRefund);
        
        // Tính refundedTax cho OWNER chấm dứt sau ngày 20
        if (terminatedBy == TerminatedBy.OWNER && isAfterDay20) {
            // OWNER chấm dứt: Client nhận hoàn lại toàn bộ thuế gốc
            calc.setRefundedTax(originalTax);
        }
        
        // Total tax
        BigDecimal totalTax = teamTax.add(calc.getOwnerTax() != null ? 
                calc.getOwnerTax() : BigDecimal.ZERO);
        calc.setTotalTax(totalTax);
        
        return calc;
    }
    
    private List<MilestoneMoneySplit> getTeamSplits(Contract contract) {
        if (contract.getPaymentType() == PaymentType.FULL) {
            return milestoneMoneySplitRepository.findApprovedByContractId(contract.getId());
        } else {
            List<Milestone> inProgressMilestones = milestoneRepository
                    .findByContractIdAndStatus(contract.getId(), MilestoneStatus.IN_PROGRESS);
            return milestoneMoneySplitRepository.findApprovedByMilestoneIds(
                    inProgressMilestones.stream().map(Milestone::getId).toList());
        }
    }
    
    // ===== HELPER METHODS =====
    
    private Contract getContractOrThrow(Long contractId) {
        return contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, 
                        "Contract not found"));
    }
    
    private User getCurrentUser(Authentication auth) {
        String email = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
    
    /**
     * Tự động xác định ai đang chấm dứt hợp đồng dựa trên user đăng nhập
     */
    private TerminatedBy detectTerminatedBy(Contract contract, User currentUser) {
        User owner = contract.getProject().getCreator();
        User client = contract.getProject().getClient();
        
        if (currentUser.getId().equals(owner.getId())) {
            return TerminatedBy.OWNER;
        } else if (currentUser.getId().equals(client.getId())) {
            return TerminatedBy.CLIENT;
        } else {
            throw new AppException(ErrorCode.ACCESS_DENIED, 
                    "Only contract owner or client can terminate the contract");
        }
    }
    
    private void validateTerminationRequest(Contract contract, User currentUser) {
        // Check contract status
        if (contract.getSignnowStatus() == ContractStatus.TERMINATED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_TERMINATED);
        }
        
        // Check permission - user phải là owner hoặc client
        User owner = contract.getProject().getCreator();
        User client = contract.getProject().getClient();
        
        if (!currentUser.getId().equals(owner.getId()) && 
            !currentUser.getId().equals(client.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED,
                    "Only contract owner or client can terminate the contract");
        }
    }
    
    private void validateAccessPermission(Contract contract, User currentUser) {
        User owner = contract.getProject().getCreator();
        User client = contract.getProject().getClient();
        
        if (!currentUser.getId().equals(owner.getId()) && 
            !currentUser.getId().equals(client.getId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
    
    private void updateUserBalance(User user, BigDecimal amount, 
                                  com.fpt.producerworkbench.common.TransactionType type, 
                                  Contract contract, String description) {
        // Handle null balance - nếu balance là null thì coi như 0
        BigDecimal oldBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalance = oldBalance.add(amount);
        user.setBalance(newBalance);
        userRepository.save(user);
        
        // Log transaction
        BalanceTransaction transaction = BalanceTransaction.builder()
                .user(user)
                .contract(contract)
                .type(type)
                .amount(amount)
                .balanceBefore(oldBalance)
                .balanceAfter(newBalance)
                .status(TransactionStatus.SUCCESSFUL)
                .description(description)
                .completedAt(LocalDateTime.now())
                .build();
        balanceTransactionRepository.save(transaction);
    }
    
    /**
     * Public method for scheduler to update user balance
     */
    public void updateUserBalancePublic(User user, BigDecimal amount,
                                       com.fpt.producerworkbench.common.TransactionType type, 
                                       Contract contract, String description) {
        updateUserBalance(user, amount, type, contract, description);
    }
    
    private void createTaxPayoutRecord(User user, BigDecimal grossAmount, BigDecimal netAmount,
                                      BigDecimal taxAmount, PayoutSource source, 
                                      Contract contract, Milestone milestone) {
        LocalDate now = LocalDate.now();
        
        TaxPayoutRecord record = TaxPayoutRecord.builder()
                .user(user)
                .userCccd(user.getCccdNumber())
                .userFullName(user.getFullName())
                .payoutSource(source)
                .contract(contract)
                .milestone(milestone)
                .grossAmount(grossAmount)
                .taxAmount(taxAmount)
                .netAmount(netAmount)
                .taxRate(TAX_RATE)
                .taxCategory("Tiền công, tiền lương")
                .payoutDate(now)
                .taxPeriodYear(now.getYear())
                .taxPeriodMonth(now.getMonthValue())
                .taxPeriodQuarter((now.getMonthValue() - 1) / 3 + 1)
                .payoutMethod(PayoutMethod.TO_BALANCE)
                .status(PayoutStatus.COMPLETED)
                .completedAt(LocalDateTime.now())
                .isTaxDeclared(false)
                .taxPaid(false)
                .description("Payout from contract termination")
                .build();
        
        taxPayoutRecordRepository.save(record);
    }
    
    private TaxRecord saveTaxRecord(Contract contract, TerminationCalculation calc) {
        LocalDate refundDate = calc.isAfterDay20() ? 
                LocalDate.now().plusMonths(1).withDayOfMonth(TAX_DECLARATION_DAY) : null;
        
        TaxRecord record = TaxRecord.builder()
                .contract(contract)
                .terminationType(calc.getTerminationType())
                .terminatedBy(calc.getTerminatedBy())
                .paymentType(contract.getPaymentType())
                .terminationDate(LocalDate.now())
                .originalTax(calc.getOriginalTax())
                .actualTax(calc.getTotalTax())
                .refundedTax(calc.getRefundedTax())
                .ownerActualReceive(calc.getOwnerActualReceive())
                .teamCompensation(calc.getTotalTeamGross())
                .taxPaidByOwner(calc.getOwnerTax())
                .taxPaidByTeam(calc.getTeamTax())
                .refundScheduledDate(refundDate)
                .status(calc.isAfterDay20() ? TaxStatus.WAITING_REFUND : TaxStatus.COMPLETED)
                .build();
        
        return taxRecordRepository.save(record);
    }
    
    private ContractTermination saveContractTermination(Contract contract, 
                                                       TerminationRequest request,
                                                       TerminationCalculation calc,
                                                       TaxRecord taxRecord,
                                                       TerminatedBy terminatedBy) {
        ContractTermination termination = ContractTermination.builder()
                .contract(contract)
                .terminatedBy(terminatedBy)
                .terminationType(calc.getTerminationType())
                .terminationDate(LocalDateTime.now())
                .totalContractAmount(calc.getTotalAmount())
                .totalTeamCompensation(calc.getTotalTeamGross())
                .totalOwnerCompensation(calc.getOwnerActualReceive()) // Owner chỉ nhận phần đền bù trừ đi phần đã chia cho team
                .totalClientRefund(calc.getClientRefund())
                .totalTaxDeducted(calc.getTotalTax())
                .taxRecord(taxRecord)
                .status(calc.isAfterDay20() ? TerminationStatus.PARTIAL_COMPLETED : 
                        TerminationStatus.COMPLETED)
                .reason(request.getReason())
                .build();
        
        return contractTerminationRepository.save(termination);
    }
    
    private void scheduleSecondPayment(Contract contract, TerminationCalculation calc) {
        // Second payment will be handled by scheduler
        log.info("Scheduled second payment for contract: {} on date: {}",
                contract.getId(), 
                LocalDate.now().plusMonths(1).withDayOfMonth(TAX_DECLARATION_DAY));
    }
    
    private void scheduleSecondPaymentForClient(Contract contract, TerminationCalculation calc) {
        // Second payment will be handled by scheduler
        log.info("Scheduled second payment for client. Contract: {} on date: {}",
                contract.getId(),
                LocalDate.now().plusMonths(1).withDayOfMonth(TAX_DECLARATION_DAY));
    }
    
    private Map<String, BigDecimal> buildBreakdown(TerminationCalculation calc) {
        Map<String, BigDecimal> breakdown = new HashMap<>();
        breakdown.put("totalAmount", calc.getTotalAmount());
        breakdown.put("teamGross", calc.getTotalTeamGross());
        breakdown.put("teamTax", calc.getTeamTax());
        breakdown.put("teamNet", calc.getTeamNetAmount());
        breakdown.put("ownerCompensation", calc.getOwnerCompensation());
        breakdown.put("ownerActualReceive", calc.getOwnerActualReceive());
        breakdown.put("ownerTax", calc.getOwnerTax());
        breakdown.put("ownerNet", calc.getOwnerNetAmount());
        breakdown.put("clientRefund", calc.getClientRefund());
        breakdown.put("totalTax", calc.getTotalTax());
        return breakdown;
    }
    
    private TerminationResponse buildTerminationResponse(ContractTermination termination,
                                                        TerminationCalculation calc,
                                                        OwnerCompensationPayment payment) {
        return TerminationResponse.builder()
                .terminationId(termination.getId())
                .contractId(termination.getContract().getId())
                .newStatus(ContractStatus.TERMINATED)
                .terminationType(calc.getTerminationType())
                .teamCompensation(calc.getTotalTeamGross())
                .ownerCompensation(calc.getOwnerActualReceive()) // Owner chỉ nhận phần đền bù trừ đi phần đã chia cho team
                .clientRefund(calc.getClientRefund())
                .taxDeducted(calc.getTotalTax())
                .hasSecondPayment(calc.isAfterDay20())
                .secondPaymentDate(calc.isAfterDay20() ?
                        LocalDate.now().plusMonths(1).withDayOfMonth(TAX_DECLARATION_DAY) : null)
                .secondPaymentAmount(calc.getRefundedTax())
                .ownerCompensationPaymentId(payment != null ? payment.getId() : null)
                .message("Hợp đồng đã được chấm dứt thành công")
                .build();
    }
    
    private TerminationDetailResponse mapToDetailResponse(ContractTermination termination) {
        return TerminationDetailResponse.builder()
                .terminationId(termination.getId())
                .contractId(termination.getContract().getId())
                .terminatedBy(termination.getTerminatedBy())
                .terminationType(termination.getTerminationType())
                .status(termination.getStatus())
                .terminationDate(termination.getTerminationDate())
                .totalContractAmount(termination.getTotalContractAmount())
                .totalTeamCompensation(termination.getTotalTeamCompensation())
                .totalOwnerCompensation(termination.getTotalOwnerCompensation())
                .totalClientRefund(termination.getTotalClientRefund())
                .totalTaxDeducted(termination.getTotalTaxDeducted())
                .originalTax(termination.getTaxRecord().getOriginalTax())
                .actualTax(termination.getTaxRecord().getActualTax())
                .refundedTax(termination.getTaxRecord().getRefundedTax())
                .reason(termination.getReason())
                .notes(termination.getNotes())
                .build();
    }
    
    // ===== INNER CLASS =====
    
    /**
     * Internal class for calculation results
     */
    @lombok.Data
    private static class TerminationCalculation {
        private boolean afterDay20;
        private TerminationType terminationType;
        private TerminatedBy terminatedBy;
        
        private BigDecimal totalAmount;
        private BigDecimal originalTax;
        
        private List<MilestoneMoneySplit> teamSplits;
        private BigDecimal totalTeamGross;
        private BigDecimal teamTax;
        private BigDecimal teamNetAmount;
        
        private BigDecimal ownerCompensation;
        private BigDecimal ownerActualReceive;
        private BigDecimal ownerTax;
        private BigDecimal ownerNetAmount;
        
        private BigDecimal clientRefund;
        
        private BigDecimal totalTax;
        private BigDecimal refundedTax;
        
        private String warning;
    }
}
