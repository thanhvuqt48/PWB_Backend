package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.ProjectRole;
import com.fpt.producerworkbench.common.TransactionStatus;
import com.fpt.producerworkbench.common.TransactionType;
import com.fpt.producerworkbench.configuration.PayosProperties;
import com.fpt.producerworkbench.dto.request.PaymentRequest;
import com.fpt.producerworkbench.dto.response.PaymentResponse;
import com.fpt.producerworkbench.entity.*;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.*;
import com.fpt.producerworkbench.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
    private final TransactionRepository transactionRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final com.fpt.producerworkbench.service.ProSubscriptionService proSubscriptionService;

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
        if (Boolean.TRUE.equals(project.getIsFunded())) throw new AppException(ErrorCode.PROJECT_ALREADY_FUNDED);
        if (!ContractStatus.COMPLETED.equals(contract.getStatus())) throw new AppException(ErrorCode.CONTRACT_NOT_READY_FOR_PAYMENT);

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

            Transaction tx = transactionRepository.findByTransactionCode(orderCode)
                    .orElseThrow(() -> {
                        log.error("Không tìm thấy giao dịch. Mã đơn hàng: {}", orderCode);
                        return new AppException(ErrorCode.TRANSACTION_NOT_FOUND);
                    });

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

                Project project = tx.getRelatedContract().getProject();
                project.setIsFunded(true);
                projectRepository.save(project);

                log.info("Đánh dấu THÀNH CÔNG và tài trợ dự án. Mã đơn hàng: {}, ID dự án: {}", orderCode, project.getId());

            } else {
                tx.setStatus(TransactionStatus.FAILED);
                log.warn("Webhook báo cáo trạng thái không thành công. Mã đơn hàng: {}, mã PayOS: {}", orderCode, payosStatusCode);
            }

            transactionRepository.save(tx);

        } catch (Exception e) {
            log.error("Lỗi xử lý webhook PayOS", e);
            throw e;
        }
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
}
