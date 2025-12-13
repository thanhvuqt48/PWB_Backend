package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.common.TransactionType;
import com.fpt.producerworkbench.configuration.PayosProperties;
import com.fpt.producerworkbench.dto.request.PaymentRequest;
import com.fpt.producerworkbench.dto.response.PaymentResponse;
import com.fpt.producerworkbench.dto.response.PaymentStatusResponse;
import com.fpt.producerworkbench.dto.response.PaymentLatestResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.ContractAddendumService;
import com.fpt.producerworkbench.service.ContractTerminationService;
import com.fpt.producerworkbench.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PayOS payOS;
    private final PayosProperties payosProperties;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ContractRepository contractRepository;
    private final MilestoneRepository milestoneRepository;
    private final MilestoneMemberRepository milestoneMemberRepository;
    private final MilestoneMoneySplitRepository milestoneMoneySplitRepository;
    private final TransactionRepository transactionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ContractAddendumRepository contractAddendumRepository;
    private final ContractAddendumService contractAddendumService;
    private final com.fpt.producerworkbench.service.ProSubscriptionService proSubscriptionService;
    private final OwnerCompensationPaymentRepository ownerCompensationPaymentRepository;
    @org.springframework.beans.factory.annotation.Autowired
    @Lazy
    private ContractTerminationService contractTerminationService;

    @Override
    @Transactional
    public PaymentResponse createPayment(Long userId, Long projectId, Long contractId, PaymentRequest paymentRequest) {
        log.info("Tạo thanh toán cho người dùng: {}, dự án: {}, hợp đồng: {}", userId, projectId, contractId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        ProjectMember projectMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        if (!ProjectRole.CLIENT.equals(projectMember.getProjectRole())) throw new AppException(ErrorCode.ACCESS_DENIED);
        // Kiểm tra hợp đồng đã được thanh toán chưa (PAID hoặc COMPLETED)
        if (contract.getSignnowStatus() == ContractStatus.PAID || contract.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.PROJECT_ALREADY_FUNDED);
        }
        // Kiểm tra hợp đồng đã được ký chưa (phải SIGNED mới được thanh toán)
        if (contract.getSignnowStatus() != ContractStatus.SIGNED) {
            throw new AppException(ErrorCode.CONTRACT_NOT_READY_FOR_PAYMENT);
        }

        BigDecimal amount = calculatePaymentAmount(contract);

        Transaction tx = Transaction.builder()
                .user(user)
                .relatedContract(contract)
                .amount(amount)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.PENDING)
                .build();

        if (PaymentType.MILESTONE.equals(contract.getPaymentType())) {
            Milestone first = milestoneRepository.findFirstByContractIdOrderBySequenceAsc(contractId)
                    .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));
            tx.setRelatedMilestone(first);
        }
        Transaction saved = transactionRepository.save(tx);

        Long orderCode = com.fpt.producerworkbench.utils.OrderCodeGenerator.generate();
        saved.setTransactionCode(String.valueOf(orderCode));
        transactionRepository.save(saved);

        String returnUrl = paymentRequest.getReturnUrl() != null ? paymentRequest.getReturnUrl() : payosProperties.getReturnUrl();
        String cancelUrl = paymentRequest.getCancelUrl() != null ? paymentRequest.getCancelUrl() : payosProperties.getCancelUrl();
        String description = createPaymentDescription(contract, project);

        try {
            CreatePaymentLinkRequest req = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(toPayOSAmountVND(amount))
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CreatePaymentLinkResponse res = payOS.paymentRequests().create(req);
            String checkoutUrl = res.getCheckoutUrl();

            log.info("Tạo link thanh toán thành công. Mã đơn hàng: {}", orderCode);

            return PaymentResponse.builder()
                    .paymentUrl(checkoutUrl)
                    .orderCode(String.valueOf(orderCode))
                    .amount(amount)
                    .status("PENDING")
                    .build();

        } catch (Exception e) {
            log.error("Tạo link thanh toán PayOS thất bại. Mã đơn hàng: {}", orderCode, e);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED);
        }
    }

    @Override
    @Transactional
    public PaymentResponse createAddendumPayment(Long userId, Long projectId, Long contractId, PaymentRequest paymentRequest) {
        log.info("Tạo thanh toán phụ lục cho người dùng: {}, dự án: {}, hợp đồng: {}", userId, projectId, contractId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_NOT_FOUND));
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        ProjectMember projectMember = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        if (!ProjectRole.CLIENT.equals(projectMember.getProjectRole())) throw new AppException(ErrorCode.ACCESS_DENIED);

        // Lấy phụ lục mới nhất của contract
        ContractAddendum addendum = contractAddendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        // Kiểm tra phụ lục đã được thanh toán chưa (PAID hoặc COMPLETED)
        if (addendum.getSignnowStatus() == ContractStatus.PAID || addendum.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        
        // Kiểm tra phụ lục đã được ký chưa (phải SIGNED mới được thanh toán)
        if (addendum.getSignnowStatus() != ContractStatus.SIGNED) {
            throw new AppException(ErrorCode.CONTRACT_NOT_READY_FOR_PAYMENT);
        }

        // Kiểm tra số tiền có hợp lệ không
        if (addendum.getNumOfMoney() == null || addendum.getNumOfMoney().compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        // Thanh toán toàn bộ số tiền trong phụ lục (numOfMoney)
        BigDecimal amount = addendum.getNumOfMoney();

        Transaction tx = Transaction.builder()
                .user(user)
                .relatedContract(contract)
                .relatedAddendum(addendum)
                .amount(amount)
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.PENDING)
                .build();

        Transaction saved = transactionRepository.save(tx);

        Long orderCode = com.fpt.producerworkbench.utils.OrderCodeGenerator.generate();
        saved.setTransactionCode(String.valueOf(orderCode));
        transactionRepository.save(saved);

        String returnUrl = paymentRequest.getReturnUrl() != null ? paymentRequest.getReturnUrl() : payosProperties.getReturnUrl();
        String cancelUrl = paymentRequest.getCancelUrl() != null ? paymentRequest.getCancelUrl() : payosProperties.getCancelUrl();
        String description = createAddendumPaymentDescription(addendum, project);

        try {
            CreatePaymentLinkRequest req = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(toPayOSAmountVND(amount))
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CreatePaymentLinkResponse res = payOS.paymentRequests().create(req);
            String checkoutUrl = res.getCheckoutUrl();

            log.info("Tạo link thanh toán phụ lục thành công. Mã đơn hàng: {}", orderCode);

            return PaymentResponse.builder()
                    .paymentUrl(checkoutUrl)
                    .orderCode(String.valueOf(orderCode))
                    .amount(amount)
                    .status("PENDING")
                    .build();

        } catch (Exception e) {
            log.error("Tạo link thanh toán PayOS thất bại. Mã đơn hàng: {}", orderCode, e);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED);
        }
    }

    @Override
    @Transactional
    public OwnerCompensationPayment createOwnerCompensationPayment(
            Long contractId,
            Long ownerId,
            BigDecimal amount,
            String description,
            String returnUrl,
            String cancelUrl,
            String terminationReason
    ) {
        log.info("Creating owner compensation payment order. Contract: {}, Owner: {}, Amount: {}",
                contractId, ownerId, amount);
        
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        
        // Tạo order code unique
        Long orderCode = com.fpt.producerworkbench.utils.OrderCodeGenerator.generate();
        String orderCodeStr = "OCP-" + orderCode;
        
        // Tạo OwnerCompensationPayment entity
        OwnerCompensationPayment payment = OwnerCompensationPayment.builder()
                .contract(contract)
                .owner(owner)
                .totalAmount(amount)
                .paymentOrderCode(orderCodeStr)
                .status(com.fpt.producerworkbench.common.PaymentStatus.PENDING)
                .expiredAt(java.time.LocalDateTime.now().plusHours(24))
                .description(description)
                .terminationReason(terminationReason) // Lưu reason từ FE request
                .build();
        
        payment = ownerCompensationPaymentRepository.save(payment);
        
        // Tạo PayOS payment link
        try {
            long amountInVND = amount.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
            
            // PayOS yêu cầu description tối đa 25 ký tự
            String shortDescription = "Comp #" + contractId;
            if (shortDescription.length() > 25) {
                shortDescription = shortDescription.substring(0, 25);
            }
            
            // Dùng returnUrl và cancelUrl từ FE, fallback về payosProperties nếu null
            // Giống như các payment khác (contract, addendum, subscription)
            String finalReturnUrl = returnUrl != null ? returnUrl : payosProperties.getReturnUrl();
            String finalCancelUrl = cancelUrl != null ? cancelUrl : payosProperties.getCancelUrl();
            
            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amountInVND)
                    .description(shortDescription)
                    .returnUrl(finalReturnUrl)
                    .cancelUrl(finalCancelUrl)
                    .build();
            
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);
            String checkoutUrl = response.getCheckoutUrl();
            
            payment.setPaymentUrl(checkoutUrl);
            payment.setPaymentOrderId(response.getPaymentLinkId());
            ownerCompensationPaymentRepository.save(payment);
            
            log.info("Created PayOS payment link successfully. URL: {}", checkoutUrl);
            
            return payment;
            
        } catch (Exception e) {
            log.error("Failed to create PayOS payment link for owner compensation", e);
            payment.setStatus(com.fpt.producerworkbench.common.PaymentStatus.FAILED);
            payment.setFailureReason("Failed to create PayOS payment link: " + e.getMessage());
            ownerCompensationPaymentRepository.save(payment);
            throw new AppException(ErrorCode.PAYMENT_LINK_CREATION_FAILED);
        }
    }

    @Override
    @Transactional
    public void handlePaymentWebhook(String body) {
        log.info("Xử lý webhook từ PayOS");

        try {
            // Kiểm tra tính hợp lệ của body
            if (body == null || body.trim().isEmpty()) {
                log.error("Webhook body trống hoặc null");
                throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
            }

            WebhookData verified = payOS.webhooks().verify(body);

            long orderCodeLong = verified.getOrderCode();
            String orderCode = String.valueOf(orderCodeLong);
            
            // Kiểm tra tính hợp lệ của orderCode
            if (orderCodeLong <= 0) {
                log.error("Mã đơn hàng không hợp lệ: {}", orderCodeLong);
                throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
            }


            if ("123".equals(orderCode)) {
                log.warn("Bỏ qua webhook cũ với mã đơn hàng=123 để xóa hàng đợi thử lại PayOS.");
                return;
            }

            String payosStatusCode = verified.getCode();

            // Thử tìm Transaction trước
            java.util.Optional<Transaction> txOpt = transactionRepository.findByTransactionCode(orderCode);
            
            if (txOpt.isPresent()) {
                // Xử lý webhook cho Transaction (contract payment, subscription, etc.)
                Transaction tx = txOpt.get();
            log.info("[Webhook/PayOS] orderCode={} resolved type={}, status={}", orderCode, tx.getType(), tx.getStatus());

            // Delegate subscription webhooks to ProSubscriptionService to keep logic in one place
            if (TransactionType.SUBSCRIPTION.equals(tx.getType())) {
                log.info("[Webhook/PayOS] Routing to SUBSCRIPTION handler. orderCode={}", orderCode);
                proSubscriptionService.handleWebhook(body);
                return;
            }
            
            log.info("[Webhook/PayOS] Routing to CONTRACT PAYMENT handler. orderCode={}", orderCode);

            if (!TransactionStatus.PENDING.equals(tx.getStatus())) {
                log.warn("Webhook đã được xử lý. Mã đơn hàng: {}", orderCode);
                return;
            }

            if ("00".equals(payosStatusCode)) {
                tx.setStatus(TransactionStatus.SUCCESSFUL);

                // Xử lý thanh toán cho contract hoặc addendum
                if (tx.getRelatedAddendum() != null) {
                    // Thanh toán phụ lục: chuyển từ SIGNED sang PAID
                    ContractAddendum addendum = tx.getRelatedAddendum();
                    if (addendum.getSignnowStatus() == ContractStatus.SIGNED) {
                        addendum.setSignnowStatus(ContractStatus.PAID);
                        contractAddendumRepository.save(addendum);
                        log.info("Đánh dấu THÀNH CÔNG thanh toán phụ lục. Mã đơn hàng: {}, Addendum ID: {}, Status: PAID", orderCode, addendum.getId());
                        
                        // Cập nhật contract và milestones khi phụ lục được thanh toán
                        try {
                            contractAddendumService.updateContractAndMilestonesOnAddendumPaid(addendum.getId());
                            log.info("Đã cập nhật contract và milestones cho addendum {} sau khi thanh toán thành công", addendum.getId());
                        } catch (Exception e) {
                            log.error("Lỗi khi cập nhật contract và milestones cho addendum {}: {}", 
                                    addendum.getId(), e.getMessage(), e);
                            // Không throw exception để không ảnh hưởng đến việc đánh dấu thanh toán
                        }
                    } else {
                        log.warn("Addendum {} không ở trạng thái SIGNED khi thanh toán thành công, status hiện tại: {}", addendum.getId(), addendum.getSignnowStatus());
                    }
                } else if (tx.getRelatedContract() != null) {
                    // Thanh toán hợp đồng: chuyển từ SIGNED sang PAID
                    Contract contract = tx.getRelatedContract();
                    if (contract.getSignnowStatus() == ContractStatus.SIGNED) {
                        contract.setSignnowStatus(ContractStatus.PAID);
                        contractRepository.save(contract);
                        log.info("Đánh dấu THÀNH CÔNG thanh toán hợp đồng. Mã đơn hàng: {}, Contract ID: {}, Status: PAID", orderCode, contract.getId());

                        // Nếu hợp đồng là dạng thanh toán theo MILESTONE,
                        // tự động thêm chủ dự án (owner) và khách hàng (client)
                        // vào thành viên của TẤT CẢ các milestones
                        if (PaymentType.MILESTONE.equals(contract.getPaymentType())) {
                            Project project = contract.getProject();

                            if (project != null) {
                                User owner = project.getCreator();
                                User client = project.getClient();

                                // Lấy tất cả milestones của contract
                                List<Milestone> milestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());

                                for (Milestone milestone : milestones) {
                                    // Thêm owner nếu chưa có
                                    if (owner != null
                                            && !milestoneMemberRepository.existsByMilestoneIdAndUserId(milestone.getId(), owner.getId())) {
                                        MilestoneMember ownerMember = MilestoneMember.builder()
                                                .milestone(milestone)
                                                .user(owner)
                                                .build();
                                        milestoneMemberRepository.save(ownerMember);
                                        log.info("Đã thêm owner vào milestone {} sau khi thanh toán", milestone.getId());
                                    }

                                    // Thêm client nếu khác owner và chưa có
                                    if (client != null) {
                                        boolean sameAsOwner = owner != null && owner.getId().equals(client.getId());
                                        if (!sameAsOwner
                                                && !milestoneMemberRepository.existsByMilestoneIdAndUserId(milestone.getId(), client.getId())) {
                                            MilestoneMember clientMember = MilestoneMember.builder()
                                                    .milestone(milestone)
                                                    .user(client)
                                                    .build();
                                            milestoneMemberRepository.save(clientMember);
                                            log.info("Đã thêm client vào milestone {} sau khi thanh toán", milestone.getId());
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        log.warn("Contract {} không ở trạng thái SIGNED khi thanh toán thành công, status hiện tại: {}", contract.getId(), contract.getSignnowStatus());
                    }
                }

            } else {
                tx.setStatus(TransactionStatus.FAILED);
                log.warn("Webhook báo cáo trạng thái không thành công. Mã đơn hàng: {}, mã PayOS: {}", orderCode, payosStatusCode);
            }

            transactionRepository.save(tx);
                return; // Đã xử lý xong Transaction
            }
            
            // Nếu không tìm thấy Transaction, thử tìm Owner Compensation Payment
            String ownerCompOrderCode = "OCP-" + orderCode;
            java.util.Optional<com.fpt.producerworkbench.entity.OwnerCompensationPayment> ownerCompOpt = 
                    ownerCompensationPaymentRepository.findByPaymentOrderCode(ownerCompOrderCode);
            
            if (ownerCompOpt.isPresent()) {
                log.info("[Webhook/PayOS] Routing to OWNER COMPENSATION PAYMENT handler. orderCode={}, ownerCompOrderCode={}", 
                        orderCode, ownerCompOrderCode);
                
                OwnerCompensationPayment payment = ownerCompOpt.get();
                
                // Xử lý trạng thái không thành công
                if (!"00".equals(payosStatusCode)) {
                    if ("CANCELLED".equals(payosStatusCode) || "EXPIRED".equals(payosStatusCode)) {
                        payment.setStatus(com.fpt.producerworkbench.common.PaymentStatus.EXPIRED);
                        payment.setFailureReason("Payment cancelled or expired: " + payosStatusCode);
                    } else {
                        payment.setStatus(com.fpt.producerworkbench.common.PaymentStatus.FAILED);
                        payment.setFailureReason("Payment failed with status: " + payosStatusCode);
                    }
                    ownerCompensationPaymentRepository.save(payment);
                    return;
                }
                
                // Payment successful → Route đến ContractTerminationService
                if (payment.getStatus() == com.fpt.producerworkbench.common.PaymentStatus.COMPLETED) {
                    log.info("Owner compensation payment already processed");
                    return;
                }
                
                contractTerminationService.handleOwnerCompensationWebhook(ownerCompOrderCode, "SUCCESS");
                return;
            }
            
            // Không tìm thấy cả Transaction lẫn Owner Compensation Payment
            log.error("Không tìm thấy giao dịch. Mã đơn hàng: {} (đã thử Transaction và OwnerCompensationPayment)", orderCode);
            throw new AppException(ErrorCode.TRANSACTION_NOT_FOUND);

        } catch (Exception e) {
            log.error("Lỗi xử lý webhook PayOS", e);
            throw e;
        }
    }


    @Override
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String orderCode) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PARAMETER_FORMAT);
        }

        // Normalize input: trim
        orderCode = orderCode.trim();
        
        // Extract raw numeric code if starts with "OCP-"
        String rawOrderCode = orderCode;
        boolean hasOcpPrefix = orderCode.startsWith("OCP-");
        if (hasOcpPrefix) {
            rawOrderCode = orderCode.substring(4); // Remove "OCP-" prefix
        }
        
        // Try Transaction first (using raw numeric code)
        // Transaction stores transactionCode as String.valueOf(orderCode)
        java.util.Optional<Transaction> txOpt = transactionRepository.findByTransactionCode(rawOrderCode);
        if (txOpt.isPresent()) {
            Transaction tx = txOpt.get();
            Project project = null;
            Contract contract = tx.getRelatedContract();
            ContractAddendum addendum = tx.getRelatedAddendum();
            
            if (contract != null) {
                project = contract.getProject();
            } else if (addendum != null && addendum.getContract() != null) {
                project = addendum.getContract().getProject();
            }

            return PaymentStatusResponse.builder()
                    .orderCode(orderCode) // Return original orderCode from request
                    .status(mapTransactionStatusToString(tx.getStatus()))
                    .amount(tx.getAmount())
                    .projectId(project != null ? project.getId() : null)
                    .contractId(contract != null ? contract.getId() : null)
                    .addendumId(addendum != null ? addendum.getId() : null)
                    .build();
        }
        
        // If no Transaction found, try OwnerCompensationPayment
        String ownerCompOrderCode;
        if (hasOcpPrefix) {
            // Request is "OCP-xxx" → lookup directly
            ownerCompOrderCode = orderCode;
        } else {
            // Request is "xxx" → lookup with "OCP-" prefix
            ownerCompOrderCode = "OCP-" + orderCode;
        }
        
        java.util.Optional<OwnerCompensationPayment> ownerCompOpt = 
                ownerCompensationPaymentRepository.findByPaymentOrderCode(ownerCompOrderCode);
        
        if (ownerCompOpt.isPresent()) {
            OwnerCompensationPayment payment = ownerCompOpt.get();
            Contract contract = payment.getContract();
            Project project = contract != null ? contract.getProject() : null;
            
            return PaymentStatusResponse.builder()
                    .orderCode(orderCode) // Return original orderCode from request
                    .status(mapPaymentStatusToString(payment.getStatus()))
                    .amount(payment.getTotalAmount())
                    .projectId(project != null ? project.getId() : null)
                    .contractId(contract != null ? contract.getId() : null)
                    .addendumId(null) // OwnerCompensationPayment không liên quan đến addendum
                    .build();
        }
        
        // Not found in both Transaction and OwnerCompensationPayment
        throw new AppException(ErrorCode.TRANSACTION_NOT_FOUND);
    }
    
    /**
     * Map TransactionStatus to string status for FE
     * FE expects: PENDING | SUCCESSFUL | FAILED
     */
    private String mapTransactionStatusToString(TransactionStatus txStatus) {
        return switch (txStatus) {
            case PENDING -> "PENDING";
            case SUCCESSFUL -> "SUCCESSFUL";
            case FAILED -> "FAILED";
        };
    }
    
    /**
     * Map PaymentStatus to string status for FE
     * FE expects: PENDING | SUCCESSFUL | FAILED | EXPIRED
     */
    private String mapPaymentStatusToString(com.fpt.producerworkbench.common.PaymentStatus paymentStatus) {
        return switch (paymentStatus) {
            case PENDING -> "PENDING";
            case PROCESSING -> "PENDING"; // PROCESSING coi như PENDING
            case COMPLETED -> "SUCCESSFUL"; // COMPLETED → SUCCESSFUL (FE check status === "SUCCESSFUL")
            case FAILED -> "FAILED";
            case EXPIRED -> "EXPIRED";
        };
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentLatestResponse getLatestPaymentByContract(Long userId, Long projectId, Long contractId) {
        // Authorize: user must be a project member
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Transaction tx = transactionRepository.findTopByRelatedContract_IdOrderByCreatedAtDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.TRANSACTION_NOT_FOUND));

        Milestone ms = tx.getRelatedMilestone();

        return PaymentLatestResponse.builder()
                .orderCode(tx.getTransactionCode())
                .status(tx.getStatus().name())
                .amount(tx.getAmount())
                .projectId(projectId)
                .contractId(contractId)
                .addendumId(tx.getRelatedAddendum() != null ? tx.getRelatedAddendum().getId() : null)
                .paymentType(contract.getPaymentType().name())
                .milestoneId(ms != null ? ms.getId() : null)
                .milestoneSequence(ms != null ? ms.getSequence() : null)
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentLatestResponse getLatestPaymentByAddendum(Long userId, Long projectId, Long contractId) {
        // Authorize: user must be a project member
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.getProject().getId().equals(projectId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // Lấy phụ lục mới nhất
        ContractAddendum addendum = contractAddendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        Transaction tx = transactionRepository.findTopByRelatedAddendum_IdOrderByCreatedAtDesc(addendum.getId())
                .orElseThrow(() -> new AppException(ErrorCode.TRANSACTION_NOT_FOUND));

        return PaymentLatestResponse.builder()
                .orderCode(tx.getTransactionCode())
                .status(tx.getStatus().name())
                .amount(tx.getAmount())
                .projectId(projectId)
                .contractId(contractId)
                .addendumId(addendum.getId())
                .paymentType(null) // Không áp dụng cho addendum
                .milestoneId(null) // Không áp dụng cho addendum
                .milestoneSequence(null) // Không áp dụng cho addendum
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }

    private BigDecimal calculatePaymentAmount(Contract contract) {
        if (PaymentType.FULL.equals(contract.getPaymentType())) {
            return contract.getTotalAmount();
        } else if (PaymentType.MILESTONE.equals(contract.getPaymentType())) {
            Milestone first = milestoneRepository
                    .findFirstByContractIdOrderBySequenceAsc(contract.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.MILESTONE_NOT_FOUND));
            return first.getAmount();
        } else {
            throw new AppException(ErrorCode.INVALID_PAYMENT_TYPE);
        }
    }

    private long toPayOSAmountVND(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private String createPaymentDescription(Contract contract, Project project) {
        return String.format("PWB-%d", project.getId());
    }

    private String createAddendumPaymentDescription(ContractAddendum addendum, Project project) {
        return String.format("PWB-Addendum-%d", project.getId());
    }

}
