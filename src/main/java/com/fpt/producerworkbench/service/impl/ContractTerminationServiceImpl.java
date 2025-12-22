package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.PayoutMethod;
import com.fpt.producerworkbench.common.PayoutSource;
import com.fpt.producerworkbench.common.PayoutStatus;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.common.TaxStatus;
import com.fpt.producerworkbench.common.TerminatedBy;
import com.fpt.producerworkbench.common.TerminationStatus;
import com.fpt.producerworkbench.common.TerminationType;
import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.configuration.FrontendProperties;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.dto.response.*;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.ContractTerminationService;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.PaymentService;
import com.fpt.producerworkbench.utils.SecurityUtils;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final FrontendProperties frontendProperties;
    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private PaymentService paymentService;
    
    // Constants
    private static final BigDecimal TAX_RATE = new BigDecimal("0.07"); // 7%
    private static final int TAX_DECLARATION_DAY = 20;
    private static final String NOTIFICATION_TOPIC = "notification-delivery";
    
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
        return ClientTerminationPreviewResponse.builder()
                .totalAmount(calc.getTotalAmount())
                .compensationAmount(calc.getOwnerCompensation()) // Số tiền đền bù
                .clientWillReceive(calc.getClientRefund())
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
        
        // === Send notifications ===
        sendClientTerminationNotifications(contract, calc, teamSplits);
        
        // === Build response ===
        return buildClientTerminationResponse(termination, calc);
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
        // Truyền returnUrl, cancelUrl và reason từ request (giống các payment khác)
        OwnerCompensationPayment payment = paymentService.createOwnerCompensationPayment(
                contract.getId(),
                contract.getProject().getCreator().getId(),
                calc.getTotalTeamGross(),
                "Owner compensation for contract termination",
                request.getReturnUrl(),  // Từ FE request
                request.getCancelUrl(),   // Từ FE request
                request.getReason()       // Lưu reason từ FE request
        );
        
        // === Send notifications ===
        sendOwnerTerminationRequestNotifications(contract, calc, payment);
        
        log.info("Created owner compensation payment. Waiting for payment confirmation.");
        
        // Return response với payment info - chỉ set các field có giá trị
        return TerminationResponse.builder()
                .contractId(contract.getId())
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
        
        // Lấy reason từ payment (đã lưu từ request ban đầu), nếu không có thì để null
        TerminationRequest request = TerminationRequest.builder()
                .reason(payment.getTerminationReason()) // Lấy reason đã lưu, có thể null
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
        
        // === Send notifications ===
        List<MilestoneMoneySplit> teamSplits = getTeamSplits(contract);
        sendOwnerTerminationCompletedNotifications(contract, calc, teamSplits);
        
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
    
    /**
     * Build response cho CLIENT chấm dứt hợp đồng
     */
    private TerminationResponse buildClientTerminationResponse(ContractTermination termination,
                                                              TerminationCalculation calc) {
        return TerminationResponse.builder()
                .terminationId(termination.getId())
                .contractId(termination.getContract().getId())
                .newStatus(ContractStatus.TERMINATED)
                .terminationType(calc.getTerminationType())
                .compensationAmount(calc.getOwnerCompensation()) // Tổng số tiền đền bù
                .clientRefund(calc.getClientRefund())
                .message("Hợp đồng đã được chấm dứt thành công")
                .build();
    }
    
    /**
     * Build response cho OWNER chấm dứt hợp đồng
     * Chỉ set các field có giá trị, không set null
     */
    private TerminationResponse buildOwnerTerminationResponse(ContractTermination termination,
                                                            TerminationCalculation calc,
                                                            OwnerCompensationPayment payment) {
        TerminationResponse.TerminationResponseBuilder builder = TerminationResponse.builder()
                .terminationId(termination.getId())
                .contractId(termination.getContract().getId())
                .newStatus(ContractStatus.TERMINATED)
                .terminationType(calc.getTerminationType())
                .teamCompensation(calc.getTotalTeamGross())
                .clientRefund(calc.getClientRefund())
                .taxDeducted(calc.getTotalTax())
                .message("Hợp đồng đã được chấm dứt thành công");
        
        // Chỉ set ownerCompensation nếu có giá trị > 0
        if (calc.getOwnerActualReceive() != null && 
            calc.getOwnerActualReceive().compareTo(BigDecimal.ZERO) > 0) {
            builder.ownerCompensation(calc.getOwnerActualReceive());
        }
        
        // Chỉ set các field liên quan đến second payment nếu có
        if (calc.isAfterDay20()) {
            builder.hasSecondPayment(true)
                    .secondPaymentDate(LocalDate.now().plusMonths(1).withDayOfMonth(TAX_DECLARATION_DAY));
            
            if (calc.getRefundedTax() != null && 
                calc.getRefundedTax().compareTo(BigDecimal.ZERO) > 0) {
                builder.secondPaymentAmount(calc.getRefundedTax());
            }
        }
        
        // Chỉ set ownerCompensationPaymentId nếu có payment
        if (payment != null && payment.getId() != null) {
            builder.ownerCompensationPaymentId(payment.getId());
        }
        
        return builder.build();
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
    
    // ===== NOTIFICATION METHODS =====
    
    /**
     * Gửi thông báo khi CLIENT chấm dứt hợp đồng
     */
    private void sendClientTerminationNotifications(
            Contract contract,
            TerminationCalculation calc,
            List<MilestoneMoneySplit> teamSplits
    ) {
        try {
            User owner = contract.getProject().getCreator();
            User client = contract.getProject().getClient();
            Project project = contract.getProject();
            
            // 1. Thông báo cho Owner
            if (owner != null && owner.getEmail() != null && !owner.getEmail().isBlank()) {
                sendTerminationEmailToOwner(owner, project, contract, calc, TerminatedBy.CLIENT);
                sendTerminationRealtimeNotification(owner, project, contract, 
                        "Client đã chấm dứt hợp đồng", 
                        String.format("Client \"%s\" đã chấm dứt hợp đồng cho dự án \"%s\". Bạn đã nhận đền bù %s VNĐ.",
                                client.getFullName() != null ? client.getFullName() : client.getEmail(),
                                project.getTitle(),
                                calc.getOwnerActualReceive() != null ? calc.getOwnerActualReceive().toString() : "0"));
            }
            
            // 2. Thông báo cho Client
            if (client != null && client.getEmail() != null && !client.getEmail().isBlank()) {
                sendTerminationEmailToClient(client, project, contract, calc, TerminatedBy.CLIENT);
                sendTerminationRealtimeNotification(client, project, contract,
                        "Hợp đồng đã được chấm dứt",
                        String.format("Hợp đồng cho dự án \"%s\" đã được chấm dứt. Bạn đã nhận hoàn tiền %s VNĐ.",
                                project.getTitle(),
                                calc.getClientRefund() != null ? calc.getClientRefund().toString() : "0"));
            }
            
            // 3. Thông báo cho Team Members
            for (MilestoneMoneySplit split : teamSplits) {
                User member = split.getUser();
                if (member != null && member.getEmail() != null && !member.getEmail().isBlank()) {
                    sendTerminationEmailToTeamMember(member, project, contract, split, calc);
                    sendTerminationRealtimeNotification(member, project, contract,
                            "Bạn đã nhận đền bù từ chấm dứt hợp đồng",
                            String.format("Bạn đã nhận đền bù %s VNĐ từ chấm dứt hợp đồng cho dự án \"%s\".",
                                    split.getAmount().subtract(split.getAmount().multiply(TAX_RATE)).toString(),
                                    project.getTitle()));
                }
            }
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo chấm dứt hợp đồng (Client termination): {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gửi thông báo khi OWNER yêu cầu chấm dứt hợp đồng (trước khi thanh toán)
     */
    private void sendOwnerTerminationRequestNotifications(
            Contract contract,
            TerminationCalculation calc,
            OwnerCompensationPayment payment
    ) {
        try {
            User owner = contract.getProject().getCreator();
            User client = contract.getProject().getClient();
            Project project = contract.getProject();
            List<MilestoneMoneySplit> teamSplits = calc.getTeamSplits();
            
            // 1. Thông báo cho Owner - cần thanh toán
            if (owner != null && owner.getEmail() != null && !owner.getEmail().isBlank()) {
                sendOwnerPaymentRequiredEmail(owner, project, contract, payment, calc);
                sendTerminationRealtimeNotification(owner, project, contract,
                        "Yêu cầu thanh toán đền bù cho team",
                        String.format("Bạn cần thanh toán %s VNĐ để hoàn tất chấm dứt hợp đồng cho dự án \"%s\".",
                                calc.getTotalTeamGross().toString(),
                                project.getTitle()));
            }
            
            // 2. Thông báo cho Client
            if (client != null && client.getEmail() != null && !client.getEmail().isBlank()) {
                sendOwnerTerminationRequestEmailToClient(client, project, contract, calc);
                sendTerminationRealtimeNotification(client, project, contract,
                        "Owner đã yêu cầu chấm dứt hợp đồng",
                        String.format("Owner đã yêu cầu chấm dứt hợp đồng cho dự án \"%s\". Đang chờ thanh toán đền bù cho team.",
                                project.getTitle()));
            }
            
            // 3. Thông báo cho Team Members
            for (MilestoneMoneySplit split : teamSplits) {
                User member = split.getUser();
                if (member != null && member.getEmail() != null && !member.getEmail().isBlank()) {
                    sendOwnerTerminationRequestEmailToTeamMember(member, project, contract, split, calc);
                    sendTerminationRealtimeNotification(member, project, contract,
                            "Owner đã yêu cầu chấm dứt hợp đồng",
                            String.format("Owner đã yêu cầu chấm dứt hợp đồng cho dự án \"%s\". Đang chờ thanh toán đền bù.",
                                    project.getTitle()));
                }
            }
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo yêu cầu chấm dứt hợp đồng (Owner termination request): {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gửi thông báo khi OWNER đã hoàn tất thanh toán và hợp đồng đã được chấm dứt
     */
    private void sendOwnerTerminationCompletedNotifications(
            Contract contract,
            TerminationCalculation calc,
            List<MilestoneMoneySplit> teamSplits
    ) {
        try {
            User owner = contract.getProject().getCreator();
            User client = contract.getProject().getClient();
            Project project = contract.getProject();
            
            // 1. Thông báo cho Owner
            if (owner != null && owner.getEmail() != null && !owner.getEmail().isBlank()) {
                sendOwnerTerminationCompletedEmail(owner, project, contract, calc);
                sendTerminationRealtimeNotification(owner, project, contract,
                        "Hợp đồng đã được chấm dứt",
                        String.format("Thanh toán đã hoàn tất, hợp đồng cho dự án \"%s\" đã được chấm dứt thành công.",
                                project.getTitle()));
            }
            
            // 2. Thông báo cho Client
            if (client != null && client.getEmail() != null && !client.getEmail().isBlank()) {
                sendTerminationEmailToClient(client, project, contract, calc, TerminatedBy.OWNER);
                sendTerminationRealtimeNotification(client, project, contract,
                        "Hợp đồng đã được chấm dứt",
                        String.format("Hợp đồng cho dự án \"%s\" đã được chấm dứt. Bạn đã nhận hoàn tiền %s VNĐ.",
                                project.getTitle(),
                                calc.getClientRefund() != null ? calc.getClientRefund().toString() : "0"));
            }
            
            // 3. Thông báo cho Team Members
            for (MilestoneMoneySplit split : teamSplits) {
                User member = split.getUser();
                if (member != null && member.getEmail() != null && !member.getEmail().isBlank()) {
                    sendTerminationEmailToTeamMember(member, project, contract, split, calc);
                    sendTerminationRealtimeNotification(member, project, contract,
                            "Bạn đã nhận đền bù từ chấm dứt hợp đồng",
                            String.format("Bạn đã nhận đền bù %s VNĐ từ chấm dứt hợp đồng cho dự án \"%s\".",
                                    split.getAmount().subtract(split.getAmount().multiply(TAX_RATE)).toString(),
                                    project.getTitle()));
                }
            }
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo hoàn tất chấm dứt hợp đồng (Owner termination completed): {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gửi email cho Owner khi Client chấm dứt hợp đồng
     */
    private void sendTerminationEmailToOwner(User owner, Project project, Contract contract,
                                            TerminationCalculation calc, TerminatedBy terminatedBy) {
        try {
            String contractUrl = String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId());
            
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail());
            params.put("projectName", project.getTitle());
            params.put("contractUrl", contractUrl);
            params.put("compensationAmount", calc.getOwnerActualReceive() != null ? 
                    calc.getOwnerActualReceive().toString() : "0");
            params.put("terminatedBy", terminatedBy == TerminatedBy.CLIENT ? "Client" : "Owner");
            
            String subject = terminatedBy == TerminatedBy.CLIENT 
                    ? "Client đã chấm dứt hợp đồng - " + project.getTitle()
                    : "Hợp đồng đã được chấm dứt - " + project.getTitle();
            
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject(subject)
                    .templateCode("contract-termination-owner-template")
                    .param(params)
                    .build();
            
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo chấm dứt hợp đồng cho Owner: {}", owner.getEmail());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi email cho Owner: {}", e.getMessage(), e);
            // Fallback: gửi email trực tiếp
            try {
                String subject = "Client đã chấm dứt hợp đồng - " + project.getTitle();
                String content = String.format("Xin chào %s,\n\nClient đã chấm dứt hợp đồng cho dự án \"%s\". " +
                        "Bạn đã nhận đền bù %s VNĐ.\n\nXem chi tiết: %s",
                        owner.getFullName() != null ? owner.getFullName() : owner.getEmail(),
                        project.getTitle(),
                        calc.getOwnerActualReceive() != null ? calc.getOwnerActualReceive().toString() : "0",
                        String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId()));
                emailService.sendEmail(subject, content, List.of(owner.getEmail()));
            } catch (Exception emailEx) {
                log.error("Lỗi khi gửi email trực tiếp cho Owner: {}", emailEx.getMessage(), e);
            }
        }
    }
    
    /**
     * Gửi email cho Client khi hợp đồng được chấm dứt
     */
    private void sendTerminationEmailToClient(User client, Project project, Contract contract,
                                             TerminationCalculation calc, TerminatedBy terminatedBy) {
        try {
            String contractUrl = String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId());
            
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", client.getFullName() != null ? client.getFullName() : client.getEmail());
            params.put("projectName", project.getTitle());
            params.put("contractUrl", contractUrl);
            params.put("refundAmount", calc.getClientRefund() != null ? calc.getClientRefund().toString() : "0");
            params.put("terminatedBy", terminatedBy == TerminatedBy.CLIENT ? "Bạn" : "Owner");
            
            String subject = "Hợp đồng đã được chấm dứt - " + project.getTitle();
            
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(client.getEmail())
                    .subject(subject)
                    .templateCode("contract-termination-client-template")
                    .param(params)
                    .build();
            
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo chấm dứt hợp đồng cho Client: {}", client.getEmail());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi email cho Client: {}", e.getMessage(), e);
            // Fallback: gửi email trực tiếp
            try {
                String subject = "Hợp đồng đã được chấm dứt - " + project.getTitle();
                String content = String.format("Xin chào %s,\n\nHợp đồng cho dự án \"%s\" đã được chấm dứt. " +
                        "Bạn đã nhận hoàn tiền %s VNĐ.\n\nXem chi tiết: %s",
                        client.getFullName() != null ? client.getFullName() : client.getEmail(),
                        project.getTitle(),
                        calc.getClientRefund() != null ? calc.getClientRefund().toString() : "0",
                        String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId()));
                emailService.sendEmail(subject, content, List.of(client.getEmail()));
            } catch (Exception emailEx) {
                log.error("Lỗi khi gửi email trực tiếp cho Client: {}", emailEx.getMessage(), e);
            }
        }
    }
    
    /**
     * Gửi email cho Team Member khi nhận đền bù từ chấm dứt hợp đồng
     */
    private void sendTerminationEmailToTeamMember(User member, Project project, Contract contract,
                                                   MilestoneMoneySplit split, TerminationCalculation calc) {
        try {
            BigDecimal netAmount = split.getAmount().subtract(split.getAmount().multiply(TAX_RATE));
            String contractUrl = String.format("%s/projectDetail?id=%d", frontendProperties.getUrl(), project.getId());
            
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", member.getFullName() != null ? member.getFullName() : member.getEmail());
            params.put("projectName", project.getTitle());
            params.put("contractUrl", contractUrl);
            params.put("grossAmount", split.getAmount().toString());
            params.put("taxAmount", split.getAmount().multiply(TAX_RATE).toString());
            params.put("netAmount", netAmount.toString());
            params.put("milestoneTitle", split.getMilestone().getTitle());
            
            String subject = "Bạn đã nhận đền bù từ chấm dứt hợp đồng - " + project.getTitle();
            
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(member.getEmail())
                    .subject(subject)
                    .templateCode("contract-termination-team-member-template")
                    .param(params)
                    .build();
            
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo đền bù cho Team Member: {}", member.getEmail());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi email cho Team Member: {}", e.getMessage(), e);
            // Fallback: gửi email trực tiếp
            try {
                BigDecimal netAmount = split.getAmount().subtract(split.getAmount().multiply(TAX_RATE));
                String subject = "Bạn đã nhận đền bù từ chấm dứt hợp đồng - " + project.getTitle();
                String content = String.format("Xin chào %s,\n\nBạn đã nhận đền bù %s VNĐ (sau thuế) " +
                        "từ chấm dứt hợp đồng cho dự án \"%s\".\n\nXem chi tiết: %s",
                        member.getFullName() != null ? member.getFullName() : member.getEmail(),
                        netAmount.toString(),
                        project.getTitle(),
                        String.format("%s/projectDetail?id=%d", frontendProperties.getUrl(), project.getId()));
                emailService.sendEmail(subject, content, List.of(member.getEmail()));
            } catch (Exception emailEx) {
                log.error("Lỗi khi gửi email trực tiếp cho Team Member: {}", emailEx.getMessage(), e);
            }
        }
    }
    
    /**
     * Gửi email cho Owner khi cần thanh toán đền bù
     */
    private void sendOwnerPaymentRequiredEmail(User owner, Project project, Contract contract,
                                              OwnerCompensationPayment payment, TerminationCalculation calc) {
        try {
            String contractUrl = String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId());
            String paymentUrl = payment.getPaymentUrl() != null ? payment.getPaymentUrl() : contractUrl;
            
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail());
            params.put("projectName", project.getTitle());
            params.put("contractUrl", contractUrl);
            params.put("paymentUrl", paymentUrl);
            params.put("amount", calc.getTotalTeamGross().toString());
            
            String subject = "Bạn cần thanh toán đền bù cho team - " + project.getTitle();
            
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject(subject)
                    .templateCode("owner-compensation-payment-required-template")
                    .param(params)
                    .build();
            
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email yêu cầu thanh toán cho Owner: {}", owner.getEmail());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi email yêu cầu thanh toán cho Owner: {}", e.getMessage(), e);
            // Fallback: gửi email trực tiếp
            try {
                String subject = "Bạn cần thanh toán đền bù cho team - " + project.getTitle();
                String content = String.format("Xin chào %s,\n\nBạn cần thanh toán %s VNĐ để hoàn tất " +
                        "chấm dứt hợp đồng cho dự án \"%s\".\n\nThanh toán: %s\n\nXem chi tiết: %s",
                        owner.getFullName() != null ? owner.getFullName() : owner.getEmail(),
                        calc.getTotalTeamGross().toString(),
                        project.getTitle(),
                        payment.getPaymentUrl() != null ? payment.getPaymentUrl() : "N/A",
                        String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId()));
                emailService.sendEmail(subject, content, List.of(owner.getEmail()));
            } catch (Exception emailEx) {
                log.error("Lỗi khi gửi email trực tiếp cho Owner: {}", emailEx.getMessage(), e);
            }
        }
    }
    
    /**
     * Gửi email cho Client khi Owner yêu cầu chấm dứt hợp đồng
     */
    private void sendOwnerTerminationRequestEmailToClient(User client, Project project, Contract contract,
                                                         TerminationCalculation calc) {
        try {
            String contractUrl = String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId());
            
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", client.getFullName() != null ? client.getFullName() : client.getEmail());
            params.put("projectName", project.getTitle());
            params.put("contractUrl", contractUrl);
            
            String subject = "Owner đã yêu cầu chấm dứt hợp đồng - " + project.getTitle();
            
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(client.getEmail())
                    .subject(subject)
                    .templateCode("owner-termination-request-client-template")
                    .param(params)
                    .build();
            
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo yêu cầu chấm dứt cho Client: {}", client.getEmail());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi email cho Client (owner termination request): {}", e.getMessage(), e);
            // Fallback: gửi email trực tiếp
            try {
                String subject = "Owner đã yêu cầu chấm dứt hợp đồng - " + project.getTitle();
                String content = String.format("Xin chào %s,\n\nOwner đã yêu cầu chấm dứt hợp đồng cho dự án \"%s\". " +
                        "Đang chờ thanh toán đền bù cho team.\n\nXem chi tiết: %s",
                        client.getFullName() != null ? client.getFullName() : client.getEmail(),
                        project.getTitle(),
                        String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId()));
                emailService.sendEmail(subject, content, List.of(client.getEmail()));
            } catch (Exception emailEx) {
                log.error("Lỗi khi gửi email trực tiếp cho Client: {}", emailEx.getMessage(), e);
            }
        }
    }
    
    /**
     * Gửi email cho Team Member khi Owner yêu cầu chấm dứt hợp đồng
     */
    private void sendOwnerTerminationRequestEmailToTeamMember(User member, Project project, Contract contract,
                                                             MilestoneMoneySplit split, TerminationCalculation calc) {
        try {
            String contractUrl = String.format("%s/projectDetail?id=%d", frontendProperties.getUrl(), project.getId());
            
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", member.getFullName() != null ? member.getFullName() : member.getEmail());
            params.put("projectName", project.getTitle());
            params.put("contractUrl", contractUrl);
            params.put("expectedAmount", split.getAmount().subtract(split.getAmount().multiply(TAX_RATE)).toString());
            params.put("milestoneTitle", split.getMilestone().getTitle());
            
            String subject = "Owner đã yêu cầu chấm dứt hợp đồng - " + project.getTitle();
            
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(member.getEmail())
                    .subject(subject)
                    .templateCode("owner-termination-request-team-member-template")
                    .param(params)
                    .build();
            
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo yêu cầu chấm dứt cho Team Member: {}", member.getEmail());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi email cho Team Member (owner termination request): {}", e.getMessage(), e);
            // Fallback: gửi email trực tiếp
            try {
                BigDecimal netAmount = split.getAmount().subtract(split.getAmount().multiply(TAX_RATE));
                String subject = "Owner đã yêu cầu chấm dứt hợp đồng - " + project.getTitle();
                String content = String.format("Xin chào %s,\n\nOwner đã yêu cầu chấm dứt hợp đồng cho dự án \"%s\". " +
                        "Đang chờ thanh toán đền bù. Bạn sẽ nhận %s VNĐ (sau thuế).\n\nXem chi tiết: %s",
                        member.getFullName() != null ? member.getFullName() : member.getEmail(),
                        project.getTitle(),
                        netAmount.toString(),
                        String.format("%s/projectDetail?id=%d", frontendProperties.getUrl(), project.getId()));
                emailService.sendEmail(subject, content, List.of(member.getEmail()));
            } catch (Exception emailEx) {
                log.error("Lỗi khi gửi email trực tiếp cho Team Member: {}", emailEx.getMessage(), e);
            }
        }
    }
    
    /**
     * Gửi email cho Owner khi đã hoàn tất chấm dứt hợp đồng
     */
    private void sendOwnerTerminationCompletedEmail(User owner, Project project, Contract contract,
                                                    TerminationCalculation calc) {
        try {
            String contractUrl = String.format("%s/projectDetail?id=%d", frontendProperties.getUrl(), project.getId());
            
            Map<String, Object> params = new HashMap<>();
            params.put("recipientName", owner.getFullName() != null ? owner.getFullName() : owner.getEmail());
            params.put("projectName", project.getTitle());
            params.put("contractUrl", contractUrl);
            params.put("teamCompensation", calc.getTotalTeamGross().toString());
            
            String subject = "Hợp đồng đã được chấm dứt thành công - " + project.getTitle();
            
            NotificationEvent event = NotificationEvent.builder()
                    .recipient(owner.getEmail())
                    .subject(subject)
                    .templateCode("owner-termination-completed-template")
                    .param(params)
                    .build();
            
            kafkaTemplate.send(NOTIFICATION_TOPIC, event);
            log.info("Đã gửi email thông báo hoàn tất chấm dứt cho Owner: {}", owner.getEmail());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi email hoàn tất chấm dứt cho Owner: {}", e.getMessage(), e);
            // Fallback: gửi email trực tiếp
            try {
                String subject = "Hợp đồng đã được chấm dứt thành công - " + project.getTitle();
                String content = String.format("Xin chào %s,\n\nThanh toán đã hoàn tất, hợp đồng cho dự án \"%s\" " +
                        "đã được chấm dứt thành công.\n\nXem chi tiết: %s",
                        owner.getFullName() != null ? owner.getFullName() : owner.getEmail(),
                        project.getTitle(),
                        String.format("%s/projectDetail?id=%d", frontendProperties.getUrl(), project.getId()));
                emailService.sendEmail(subject, content, List.of(owner.getEmail()));
            } catch (Exception emailEx) {
                log.error("Lỗi khi gửi email trực tiếp cho Owner: {}", emailEx.getMessage(), e);
            }
        }
    }
    
    /**
     * Gửi thông báo realtime (in-app notification)
     */
    private void sendTerminationRealtimeNotification(User user, Project project, Contract contract,
                                                     String title, String message) {
        try {
            if (user.getId() == null) {
                log.warn("Không thể gửi notification: user không có ID");
                return;
            }
            
            String contractUrl = String.format("%s/contractSpace?id=%d", frontendProperties.getUrl(), project.getId());
            
            notificationService.sendNotification(
                    SendNotificationRequest.builder()
                            .userId(user.getId())
                            .type(NotificationType.SYSTEM)
                            .title(title)
                            .message(message)
                            .relatedEntityType(RelatedEntityType.CONTRACT)
                            .relatedEntityId(contract.getId())
                            .actionUrl(contractUrl)
                            .build()
            );
            
            log.info("Đã gửi notification realtime cho user: {}", user.getId());
            
        } catch (Exception e) {
            log.error("Lỗi khi gửi notification realtime: {}", e.getMessage(), e);
        }
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
