package com.fpt.producerworkbench.service.impl;

import com.fpt.producerworkbench.common.AddendumDocumentType;
import com.fpt.producerworkbench.common.ContractStatus;
import com.fpt.producerworkbench.common.MilestoneStatus;
import com.fpt.producerworkbench.common.PaymentType;
import com.fpt.producerworkbench.common.NotificationType;
import com.fpt.producerworkbench.common.RelatedEntityType;
import com.fpt.producerworkbench.dto.event.NotificationEvent;
import com.fpt.producerworkbench.dto.request.SendNotificationRequest;
import com.fpt.producerworkbench.entity.Contract;
import com.fpt.producerworkbench.entity.ContractAddendum;
import com.fpt.producerworkbench.entity.ContractAddendumMilestone;
import com.fpt.producerworkbench.entity.Milestone;
import com.fpt.producerworkbench.entity.MilestoneMember;
import com.fpt.producerworkbench.entity.Project;
import com.fpt.producerworkbench.entity.User;
import com.fpt.producerworkbench.exception.AppException;
import com.fpt.producerworkbench.exception.ErrorCode;
import com.fpt.producerworkbench.repository.AddendumDocumentRepository;
import com.fpt.producerworkbench.repository.ContractAddendumMilestoneRepository;
import com.fpt.producerworkbench.repository.ContractAddendumRepository;
import com.fpt.producerworkbench.repository.ContractRepository;
import com.fpt.producerworkbench.repository.MilestoneRepository;
import com.fpt.producerworkbench.repository.MilestoneMemberRepository;
import com.fpt.producerworkbench.service.ContractAddendumService;
import com.fpt.producerworkbench.service.EmailService;
import com.fpt.producerworkbench.service.FileStorageService;
import com.fpt.producerworkbench.service.NotificationService;
import com.fpt.producerworkbench.service.ProjectPermissionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ContractAddendumServiceImpl implements ContractAddendumService {

    ContractRepository contractRepository;
    ContractAddendumRepository addendumRepository;
    AddendumDocumentRepository addendumDocumentRepository;
    FileStorageService fileStorageService;
    ProjectPermissionService projectPermissionService;
    EmailService emailService;
    KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    MilestoneRepository milestoneRepository;
    ContractAddendumMilestoneRepository addendumMilestoneRepository;
    MilestoneMemberRepository milestoneMemberRepository;
    NotificationService notificationService;

    @Override
    public List<Map<String, Object>> getAllAddendumsByContract(Long contractId) {
        contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        int maxAddendumNumber = addendumRepository.findMaxAddendumNumber(contractId);
        
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        
        for (int addendumNum = 1; addendumNum <= maxAddendumNumber; addendumNum++) {
            ContractAddendum addendum = addendumRepository
                    .findFirstByContractIdAndAddendumNumberOrderByVersionDesc(contractId, addendumNum)
                    .orElse(null);
            
            if (addendum == null) continue;
            Map<String, Object> addendumInfo = new HashMap<>();
            addendumInfo.put("id", addendum.getId());
            addendumInfo.put("addendumNumber", addendum.getAddendumNumber());
            addendumInfo.put("version", addendum.getVersion());
            addendumInfo.put("title", addendum.getTitle());
            addendumInfo.put("effectiveDate", addendum.getEffectiveDate());
            addendumInfo.put("signnowStatus", addendum.getSignnowStatus());
            
            boolean isPaid = addendum.getSignnowStatus() == ContractStatus.PAID || addendum.getSignnowStatus() == ContractStatus.COMPLETED;
            addendumInfo.put("isPaid", isPaid);

            var signed = addendumDocumentRepository
                    .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.SIGNED)
                    .orElse(null);
            if (signed != null) {
                addendumInfo.put("documentType", "SIGNED");
                addendumInfo.put("documentUrl", fileStorageService.generatePresignedUrl(signed.getStorageUrl(), false, null));
            } else {
                var filledDoc = addendumDocumentRepository
                        .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                        .orElse(null);
                if (filledDoc != null) {
                    addendumInfo.put("documentType", "FILLED");
                    addendumInfo.put("documentUrl", fileStorageService.generatePresignedUrl(filledDoc.getStorageUrl(), false, null));
                }
            }

            result.add(addendumInfo);
        }

        return result;
    }

    @Override
    public Map<String, Object> getAddendumByContract(Long contractId) {
        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElse(null);

        var resp = new HashMap<String, Object>();
        
        if (addendum == null) {
            resp.put("exists", false);
            return resp;
        }

        resp.put("exists", true);
        resp.put("id", addendum.getId());
        resp.put("addendumNumber", addendum.getAddendumNumber());
        resp.put("title", addendum.getTitle());
        resp.put("version", addendum.getVersion());
        resp.put("effectiveDate", addendum.getEffectiveDate());
        resp.put("signnowStatus", addendum.getSignnowStatus());
        resp.put("numOfMoney", addendum.getNumOfMoney());
        resp.put("numOfEdit", addendum.getNumOfEdit());
        resp.put("numOfRefresh", addendum.getNumOfRefresh());
        resp.put("pitTax", addendum.getPitTax());
        resp.put("vatTax", addendum.getVatTax());
        
        boolean isPaid = addendum.getSignnowStatus() == ContractStatus.PAID || addendum.getSignnowStatus() == ContractStatus.COMPLETED;
        resp.put("isPaid", isPaid);

        var signed = addendumDocumentRepository
                .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.SIGNED)
                .orElse(null);
        if (signed != null) {
            resp.put("documentVersion", signed.getVersion());
            resp.put("documentType", "SIGNED");
            resp.put("documentUrl", fileStorageService.generatePresignedUrl(signed.getStorageUrl(), false, null));
        } else {
            var filledDoc = addendumDocumentRepository
                    .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                    .orElse(null);
            if (filledDoc != null) {
                resp.put("documentVersion", filledDoc.getVersion());
                resp.put("documentType", "FILLED");
                resp.put("documentUrl", fileStorageService.generatePresignedUrl(filledDoc.getStorageUrl(), false, null));
            }
        }

        return resp;
    }

    @Override
    public String getAddendumFileUrl(Long contractId, Authentication auth) {
        ensureCanViewAddendum(auth, contractId);

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        var doc = addendumDocumentRepository
                .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.FILLED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        return fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
    }

    @Override
    public String getSignedAddendumFileUrl(Long contractId) {
        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        var doc = addendumDocumentRepository
                .findFirstByAddendumIdAndTypeOrderByVersionDesc(addendum.getId(), AddendumDocumentType.SIGNED)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_DOC_NOT_FOUND));

        return fileStorageService.generatePresignedUrl(doc.getStorageUrl(), false, null);
    }

    @Override
    public String declineAddendum(Long contractId, String reason, Authentication auth) throws Exception {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        String email = auth == null ? null : auth.getName();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equalsIgnoreCase(a.getAuthority()));
        boolean isClient = contract.getProject() != null && contract.getProject().getClient() != null
                && email.equalsIgnoreCase(contract.getProject().getClient().getEmail());
        if (!isAdmin && !isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (addendum.getSignnowStatus() == ContractStatus.SIGNED || 
            addendum.getSignnowStatus() == ContractStatus.PAID || 
            addendum.getSignnowStatus() == ContractStatus.COMPLETED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_COMPLETED);
        }
        if (addendum.getSignnowStatus() == ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_ALREADY_DECLINED);
        }

        addendum.setSignnowStatus(ContractStatus.DECLINED);
        addendum.setDeclineReason(reason);
        addendumRepository.save(addendum);
        
        log.info("Addendum {} (contract {}) đã được từ chối với lý do: {}", addendum.getId(), contractId, reason);

        String ownerEmail = contract.getProject() != null && contract.getProject().getCreator() != null
                ? contract.getProject().getCreator().getEmail()
                : null;
        
        log.info("Owner email để gửi thông báo: {}", ownerEmail);
        if (ownerEmail != null) {
            try {
                NotificationEvent evt = NotificationEvent.builder()
                        .subject("Phụ lục hợp đồng bị từ chối - Contract #" + contractId)
                        .recipient(ownerEmail)
                        .templateCode("contract-addendum-declined")
                        .param(new HashMap<>())
                        .build();
                evt.getParam().put("recipient", ownerEmail);
                evt.getParam().put("projectId", String.valueOf(contract.getProject().getId()));
                evt.getParam().put("projectTitle", contract.getProject().getTitle());
                evt.getParam().put("contractId", String.valueOf(contractId));
                evt.getParam().put("addendumId", String.valueOf(addendum.getId()));
                evt.getParam().put("addendumTitle", addendum.getTitle() != null ? addendum.getTitle() : "Phụ lục hợp đồng");
                evt.getParam().put("reason", reason == null ? "(không cung cấp)" : reason);

                kafkaTemplate.send("notification-delivery", evt);
                log.info("Đã gửi notification event qua Kafka cho owner: {}", ownerEmail);
            } catch (Exception ex) {
                log.error("Lỗi khi gửi email qua Kafka, thử gửi trực tiếp: {}", ex.getMessage());
                try {
                    String subject = "Phụ lục hợp đồng bị từ chối - Contract #" + contractId;
                    String content = "<p>Phụ lục hợp đồng đã bị từ chối với lý do:</p><p>" + (reason == null ? "(không cung cấp)" : reason) + "</p>"
                            + "<p>Vui lòng chỉnh sửa và gửi lại để khách hàng duyệt tiếp.</p>";
                    emailService.sendEmail(subject, content, List.of(ownerEmail));
                    log.info("Đã gửi email trực tiếp cho owner: {}", ownerEmail);
                } catch (Exception emailEx) {
                    log.error("Lỗi khi gửi email trực tiếp: {}", emailEx.getMessage());
                }
            }
        } else {
            log.warn("Không tìm thấy email của owner để gửi thông báo từ chối phụ lục hợp đồng");
        }

        try {
            User owner = contract.getProject() != null && contract.getProject().getCreator() != null
                    ? contract.getProject().getCreator()
                    : null;
            
            if (owner != null) {
                String actionUrl = String.format("/contractId=%d", contract.getProject().getId());
                String addendumTitle = addendum.getTitle() != null ? addendum.getTitle() : "Phụ lục hợp đồng";
                String declineReason = reason != null ? reason : "(không cung cấp)";
                
                notificationService.sendNotification(
                        SendNotificationRequest.builder()
                                .userId(owner.getId())
                                .type(NotificationType.CONTRACT_SIGNING)
                                .title("Phụ lục hợp đồng bị từ chối")
                                .message(String.format("Phụ lục hợp đồng \"%s\" của dự án \"%s\" đã bị từ chối.%s",
                                        addendumTitle,
                                        contract.getProject().getTitle(),
                                        " Lý do: " + declineReason))
                                .relatedEntityType(RelatedEntityType.CONTRACT)
                                .relatedEntityId(contractId)
                                .actionUrl(actionUrl)
                                .build());
            }
        } catch (Exception e) {
            log.error("Gặp lỗi khi gửi notification realtime cho owner khi decline addendum: {}", e.getMessage());
        }

        return "DECLINED";
    }

    @Override
    public String getDeclineReason(Long contractId, Authentication auth) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        ContractAddendum addendum = addendumRepository
                .findFirstByContractIdOrderByAddendumNumberDescVersionDesc(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));

        String email = auth == null ? null : auth.getName();
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equalsIgnoreCase(a.getAuthority()));
        boolean isOwner = contract.getProject() != null && contract.getProject().getCreator() != null
                && email.equalsIgnoreCase(contract.getProject().getCreator().getEmail());
        boolean isClient = contract.getProject() != null && contract.getProject().getClient() != null
                && email.equalsIgnoreCase(contract.getProject().getClient().getEmail());
        
        if (!isAdmin && !isOwner && !isClient) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (addendum.getSignnowStatus() != ContractStatus.DECLINED) {
            throw new AppException(ErrorCode.CONTRACT_NOT_DECLINED);
        }

        return addendum.getDeclineReason() != null ? addendum.getDeclineReason() : "Không có lý do cụ thể";
    }

    @Override
    public void ensureCanViewAddendum(Authentication auth, Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        Project project = contract.getProject();
        if (project == null) {
            throw new AppException(ErrorCode.PROJECT_NOT_FOUND);
        }

        var permissions = projectPermissionService.checkContractPermissions(auth, project.getId());
        if (!permissions.isCanViewContract()) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void updateContractAndMilestonesOnAddendumPaid(Long addendumId) {
        ContractAddendum addendum = addendumRepository.findById(addendumId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        
        // Chỉ cập nhật khi phụ lục có trạng thái PAID (đã thanh toán)
        if (addendum.getSignnowStatus() != ContractStatus.PAID) {
            log.warn("Addendum {} không ở trạng thái PAID (status: {}), bỏ qua cập nhật contract và milestones", 
                    addendumId, addendum.getSignnowStatus());
            return;
        }
        
        Contract contract = addendum.getContract();
        if (contract == null) {
            throw new AppException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        
        PaymentType paymentType = contract.getPaymentType();
        if (paymentType == null) {
            log.warn("Contract {} không có paymentType, bỏ qua cập nhật", contract.getId());
            return;
        }
        
        log.info("Bắt đầu cập nhật contract {} và milestones khi addendum {} được thanh toán (PaymentType: {})", 
                contract.getId(), addendumId, paymentType);
        
        if (paymentType == PaymentType.FULL) {
            updateContractForFullPaymentType(contract, addendum);
        } else if (paymentType == PaymentType.MILESTONE) {
            updateContractAndMilestonesForMilestonePaymentType(contract, addendum);
        } else {
            log.warn("PaymentType {} không được hỗ trợ, bỏ qua cập nhật", paymentType);
        }
        
        log.info("Hoàn thành cập nhật contract {} và milestones khi addendum {} được thanh toán", 
                contract.getId(), addendumId);
    }
    
    /**
     * Cập nhật contract cho PaymentType = FULL
     */
    private void updateContractForFullPaymentType(Contract contract, ContractAddendum addendum) {
        // Lấy giá trị hiện tại từ Contract
        BigDecimal currentTotalAmount = contract.getTotalAmount() != null ? contract.getTotalAmount() : BigDecimal.ZERO;
        Integer currentEditCount = contract.getFpEditAmount() != null ? contract.getFpEditAmount() : 0;
        Integer currentProductCount = contract.getProductCount() != null ? contract.getProductCount() : 0;
        BigDecimal currentPitTax = contract.getPitTax() != null ? contract.getPitTax() : BigDecimal.ZERO;
        BigDecimal currentVatTax = contract.getVatTax() != null ? contract.getVatTax() : BigDecimal.ZERO;
        
        // Lấy giá trị điều chỉnh từ Addendum
        BigDecimal adjustmentAmount = addendum.getNumOfMoney() != null ? addendum.getNumOfMoney() : BigDecimal.ZERO;
        Integer adjustmentEditCount = addendum.getNumOfEdit() != null ? addendum.getNumOfEdit() : 0;
        Integer adjustmentRefreshCount = addendum.getNumOfRefresh() != null ? addendum.getNumOfRefresh() : 0;
        BigDecimal adjustmentPitTax = addendum.getPitTax() != null ? addendum.getPitTax() : BigDecimal.ZERO;
        BigDecimal adjustmentVatTax = addendum.getVatTax() != null ? addendum.getVatTax() : BigDecimal.ZERO;
        
        // Tính giá trị mới
        BigDecimal newTotalAmount = currentTotalAmount.add(adjustmentAmount);
        Integer newEditCount = currentEditCount + adjustmentEditCount;
        Integer newProductCount = currentProductCount + adjustmentRefreshCount;
        BigDecimal newPitTax = currentPitTax.add(adjustmentPitTax);
        BigDecimal newVatTax = currentVatTax.add(adjustmentVatTax);
        
        // Cập nhật Contract
        contract.setTotalAmount(newTotalAmount);
        contract.setFpEditAmount(newEditCount);
        contract.setProductCount(newProductCount);
        contract.setPitTax(newPitTax);
        contract.setVatTax(newVatTax);
        contractRepository.save(contract);
        
        log.info("Đã cập nhật contract {} (FULL): totalAmount={}, editCount={}, productCount={}, pitTax={}, vatTax={}", 
                contract.getId(), newTotalAmount, newEditCount, newProductCount, newPitTax, newVatTax);
    }
    
    /**
     * Cập nhật milestones và contract cho PaymentType = MILESTONE
     */
    private void updateContractAndMilestonesForMilestonePaymentType(Contract contract, ContractAddendum addendum) {
        // Bước 1: Xử lý từng mục trong phụ lục
        List<ContractAddendumMilestone> addendumMilestones = 
                addendumMilestoneRepository.findByAddendumIdOrderByItemIndexAsc(addendum.getId());
        
        if (addendumMilestones.isEmpty()) {
            log.warn("Addendum {} không có milestone items, bỏ qua cập nhật milestones", addendum.getId());
            return;
        }
        
        // Lấy số sequence lớn nhất hiện có để tạo milestone mới
        List<Milestone> existingMilestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());
        int maxSequence = existingMilestones.stream()
                .mapToInt(m -> m.getSequence() != null ? m.getSequence() : 0)
                .max()
                .orElse(0);
        
        for (ContractAddendumMilestone addendumMilestone : addendumMilestones) {
            if (addendumMilestone.getMilestone() != null) {
                // Điều chỉnh milestone có sẵn
                updateExistingMilestone(addendumMilestone);
            } else {
                // Tạo milestone mới
                createNewMilestone(contract, addendumMilestone, maxSequence + 1);
                maxSequence++;
            }
        }
        
        // Bước 2: Cập nhật Contract tổng hợp từ tất cả milestones
        updateContractFromMilestones(contract);
    }
    
    /**
     * Cập nhật milestone có sẵn
     */
    private void updateExistingMilestone(ContractAddendumMilestone addendumMilestone) {
        Milestone milestone = addendumMilestone.getMilestone();
        if (milestone == null) {
            log.warn("ContractAddendumMilestone {} có milestone_id nhưng milestone không tồn tại", 
                    addendumMilestone.getId());
            return;
        }
        
        // Lấy giá trị hiện tại của milestone
        BigDecimal currentAmount = milestone.getAmount() != null ? milestone.getAmount() : BigDecimal.ZERO;
        Integer currentEditCount = milestone.getEditCount() != null ? milestone.getEditCount() : 0;
        Integer currentProductCount = milestone.getProductCount() != null ? milestone.getProductCount() : 0;
        BigDecimal currentPitTax = milestone.getPitTax() != null ? milestone.getPitTax() : BigDecimal.ZERO;
        BigDecimal currentVatTax = milestone.getVatTax() != null ? milestone.getVatTax() : BigDecimal.ZERO;
        
        // Lấy giá trị điều chỉnh từ phụ lục
        BigDecimal adjustmentAmount = addendumMilestone.getNumOfMoney() != null ? 
                addendumMilestone.getNumOfMoney() : BigDecimal.ZERO;
        Integer adjustmentEditCount = addendumMilestone.getNumOfEdit() != null ? 
                addendumMilestone.getNumOfEdit() : 0;
        Integer adjustmentRefreshCount = addendumMilestone.getNumOfRefresh() != null ? 
                addendumMilestone.getNumOfRefresh() : 0;
        BigDecimal adjustmentPitTax = addendumMilestone.getPitTax() != null ? 
                addendumMilestone.getPitTax() : BigDecimal.ZERO;
        BigDecimal adjustmentVatTax = addendumMilestone.getVatTax() != null ? 
                addendumMilestone.getVatTax() : BigDecimal.ZERO;
        
        // Tính giá trị mới
        BigDecimal newAmount = currentAmount.add(adjustmentAmount);
        Integer newEditCount = currentEditCount + adjustmentEditCount;
        Integer newProductCount = currentProductCount + adjustmentRefreshCount;
        BigDecimal newPitTax = currentPitTax.add(adjustmentPitTax);
        BigDecimal newVatTax = currentVatTax.add(adjustmentVatTax);
        
        // Cập nhật milestone
        milestone.setAmount(newAmount);
        milestone.setEditCount(newEditCount);
        milestone.setProductCount(newProductCount);
        milestone.setPitTax(newPitTax);
        milestone.setVatTax(newVatTax);
        
        // Cập nhật title/description nếu có thay đổi
        if (addendumMilestone.getTitle() != null && !addendumMilestone.getTitle().isBlank()) {
            milestone.setTitle(addendumMilestone.getTitle());
        }
        if (addendumMilestone.getDescription() != null) {
            milestone.setDescription(addendumMilestone.getDescription());
        }
        
        // Nếu milestone đã hoàn thành (COMPLETED) thì đổi status về IN_PROGRESS để làm tiếp
        if (milestone.getStatus() == MilestoneStatus.COMPLETED) {
            milestone.setStatus(MilestoneStatus.IN_PROGRESS);
        }
        
        milestoneRepository.save(milestone);
        
        log.info("Đã cập nhật milestone {}: amount={}, editCount={}, productCount={}, pitTax={}, vatTax={}", 
                milestone.getId(), newAmount, newEditCount, newProductCount, newPitTax, newVatTax);
    }
    
    /**
     * Tạo milestone mới
     */
    private void createNewMilestone(Contract contract, ContractAddendumMilestone addendumMilestone, int sequence) {
        BigDecimal amount = addendumMilestone.getNumOfMoney() != null ? 
                addendumMilestone.getNumOfMoney() : BigDecimal.ZERO;
        Integer editCount = addendumMilestone.getNumOfEdit() != null ? 
                addendumMilestone.getNumOfEdit() : 0;
        Integer productCount = addendumMilestone.getNumOfRefresh() != null ? 
                addendumMilestone.getNumOfRefresh() : 0;
        BigDecimal pitTax = addendumMilestone.getPitTax() != null ? 
                addendumMilestone.getPitTax() : BigDecimal.ZERO;
        BigDecimal vatTax = addendumMilestone.getVatTax() != null ? 
                addendumMilestone.getVatTax() : BigDecimal.ZERO;
        
        String title = addendumMilestone.getTitle() != null && !addendumMilestone.getTitle().isBlank() ?
                addendumMilestone.getTitle() : "Cột mốc " + sequence;
        String description = addendumMilestone.getDescription();
        
        Milestone newMilestone = Milestone.builder()
                .contract(contract)
                .title(title)
                .description(description)
                .amount(amount)
                .editCount(editCount)
                .productCount(productCount)
                .pitTax(pitTax)
                .vatTax(vatTax)
                .status(MilestoneStatus.PENDING)
                .sequence(sequence)
                .build();
        
        newMilestone = milestoneRepository.save(newMilestone);
        
        // Cập nhật liên kết trong phụ lục: ghi milestone_id vào ContractAddendumMilestone
        addendumMilestone.setMilestone(newMilestone);
        addendumMilestoneRepository.save(addendumMilestone);
        
        // Tự động thêm owner và client vào milestone mới
        Project project = contract.getProject();
        if (project != null) {
            User owner = project.getCreator();
            User client = project.getClient();

            // Thêm owner nếu chưa có
            if (owner != null
                    && !milestoneMemberRepository.existsByMilestoneIdAndUserId(newMilestone.getId(), owner.getId())) {
                MilestoneMember ownerMember = MilestoneMember.builder()
                        .milestone(newMilestone)
                        .user(owner)
                        .build();
                milestoneMemberRepository.save(ownerMember);
                log.info("Đã thêm owner vào milestone mới {} sau khi thanh toán phụ lục", newMilestone.getId());
            }

            // Thêm client nếu khác owner và chưa có
            if (client != null) {
                boolean sameAsOwner = owner != null && owner.getId().equals(client.getId());
                if (!sameAsOwner
                        && !milestoneMemberRepository.existsByMilestoneIdAndUserId(newMilestone.getId(), client.getId())) {
                    MilestoneMember clientMember = MilestoneMember.builder()
                            .milestone(newMilestone)
                            .user(client)
                            .build();
                    milestoneMemberRepository.save(clientMember);
                    log.info("Đã thêm client vào milestone mới {} sau khi thanh toán phụ lục", newMilestone.getId());
                }
            }
        }
        
        log.info("Đã tạo milestone mới {}: title={}, amount={}, editCount={}, productCount={}, sequence={}", 
                newMilestone.getId(), title, amount, editCount, productCount, sequence);
    }
    
    /**
     * Cập nhật Contract tổng hợp từ tất cả milestones
     */
    private void updateContractFromMilestones(Contract contract) {
        List<Milestone> allMilestones = milestoneRepository.findByContractIdOrderBySequenceAsc(contract.getId());
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        Integer totalEditCount = 0;
        Integer totalProductCount = 0;
        BigDecimal totalPitTax = BigDecimal.ZERO;
        BigDecimal totalVatTax = BigDecimal.ZERO;
        
        for (Milestone milestone : allMilestones) {
            if (milestone.getAmount() != null) {
                totalAmount = totalAmount.add(milestone.getAmount());
            }
            if (milestone.getEditCount() != null) {
                totalEditCount += milestone.getEditCount();
            }
            if (milestone.getProductCount() != null) {
                totalProductCount += milestone.getProductCount();
            }
            if (milestone.getPitTax() != null) {
                totalPitTax = totalPitTax.add(milestone.getPitTax());
            }
            if (milestone.getVatTax() != null) {
                totalVatTax = totalVatTax.add(milestone.getVatTax());
            }
        }
        
        // Cập nhật Contract
        contract.setTotalAmount(totalAmount);
        contract.setFpEditAmount(totalEditCount);
        contract.setProductCount(totalProductCount);
        contract.setPitTax(totalPitTax);
        contract.setVatTax(totalVatTax);
        contractRepository.save(contract);
        
        log.info("Đã cập nhật contract {} từ milestones: totalAmount={}, editCount={}, productCount={}, pitTax={}, vatTax={}", 
                contract.getId(), totalAmount, totalEditCount, totalProductCount, totalPitTax, totalVatTax);
    }
}

