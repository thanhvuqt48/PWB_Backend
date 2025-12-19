# LOGIC LUỒNG CHẤM DỨT HỢP ĐỒNG VÀ ĐỀN BÙ

> **Phiên bản:** 1.0  
> **Ngày cập nhật:** 10/12/2025  
> **Hệ thống:** Producer Workbench Backend

---

## MỤC LỤC

1. [Các khái niệm cơ bản](#i-các-khái-niệm-cơ-bản)
2. [Nguyên tắc chung](#ii-nguyên-tắc-chung)
3. [Luồng chấm dứt trước ngày 20](#iii-luồng-chấm-dứt-trước-ngày-20)
4. [Luồng chấm dứt sau ngày 20](#iv-luồng-chấm-dứt-sau-ngày-20)
5. [Bảng tổng hợp](#v-bảng-tổng-hợp)
6. [Lưu ý khi triển khai](#vi-lưu-ý-khi-triển-khai)
7. [Sơ đồ luồng](#vii-sơ-đồ-luồng)

---

## I. CÁC KHÁI NIỆM CƠ BẢN

### 1. Ba đối tượng trong hệ thống

| Đối tượng | Vai trò | Balance |
|-----------|---------|---------|
| **Client** | Người thuê, đã thanh toán tiền cho hệ thống | Có (nhận tiền hoàn vào balance) |
| **Owner** | Chủ dự án (Producer), người nhận việc và quản lý team | Có (nhận tiền đền bù vào balance) |
| **Team Members** | Các thành viên làm cùng Owner | Có (nhận phần chia vào balance) |

### 2. Các loại thanh toán

- **FULL**: Thanh toán toàn bộ một lần
- **MILESTONE**: Thanh toán theo từng cột mốc

### 3. Thời điểm chấm dứt

- **Trước ngày 20 của tháng**: Chưa kê khai thuế với chi cục thuế
- **Sau ngày 20 của tháng**: Đã kê khai thuế với chi cục thuế

### 4. Các trạng thái Milestone

| Trạng thái | Ý nghĩa | Tính đền bù? |
|------------|---------|--------------|
| **PENDING** | Chưa bắt đầu | ❌ Không |
| **IN_PROGRESS** | Đang làm | ✅ Có |
| **COMPLETED** | Đã hoàn thành | ❌ Không |
| **PAID** | Đã thanh toán | ❌ Không |

### 5. Cơ chế Balance

**Tất cả các khoản tiền đều được cộng vào balance của người dùng:**
- Không chuyển tiền trực tiếp qua PayOS nữa
- Tất cả tiền đền bù, hoàn trả đều vào balance
- Người dùng muốn rút tiền thì tạo yêu cầu rút tiền từ balance
- Thuế đã được khấu trừ tại nguồn (7%) khi nhận thu nhập

---

## II. NGUYÊN TẮC CHUNG

### Nguyên tắc 1: Thứ tự ưu tiên thanh toán khi chấm dứt hợp đồng

```
1. Team Members (ưu tiên cao nhất - bảo vệ quyền lợi người lao động)
2. Owner (đền bù theo compensationPercentage)
3. Client (nhận lại phần còn lại)
```

### Nguyên tắc 2: Công thức tính tiền cơ bản

#### Đối với FULL Payment:
```
Số tiền cần xử lý = Contract.totalAmount
Thuế gốc = Contract.pitTax + Contract.vatTax
```

#### Đối với MILESTONE Payment:
```
Số tiền cần xử lý = Tổng Milestone.amount (các milestone IN_PROGRESS)
Thuế gốc = Tổng (Milestone.pitTax + Milestone.vatTax) của các milestone IN_PROGRESS
```

### Nguyên tắc 3: Tính tiền đền bù cho Team Members

```java
// Chỉ tính các MilestoneMoneySplit có status = APPROVED
Tiền đền bù Team = SUM(MilestoneMoneySplit.amount) 
                   WHERE status = APPROVED
                   AND milestone IN (milestones cần xử lý)
```

**Quy tắc:**
- **FULL Payment**: Tính tất cả MilestoneMoneySplit (APPROVED) trong toàn bộ contract
- **MILESTONE Payment**: Chỉ tính MilestoneMoneySplit (APPROVED) của các milestone IN_PROGRESS

### Nguyên tắc 4: Xử lý thuế theo thời điểm

#### Phân loại thuế:

**Trong hệ thống này:**
```
✓ Tổng thuế = 7% (bao gồm tất cả các loại thuế)
✓ Áp dụng đồng nhất cho:
  - Contract gốc: 7%
  - Khi chấm dứt hợp đồng: 7%
  - Khi đền bù: 7%
✓ Đơn giản: Tất cả đều 7%
```

**Lưu ý:**
- Tổng thuế 7% bao gồm: PIT (Thuế TNCN) + VAT (Thuế GTGT) + các loại thuế khác
- Không cần phân biệt từng loại thuế riêng lẻ
- Áp dụng thống nhất cho mọi trường hợp

#### Trước ngày 20 (Chưa kê khai):
```
Thuế khấu trừ = 7% × Số tiền thực nhận (áp dụng cho cả Owner và Team)
```
- **Khấu trừ tại nguồn:** Trừ 7% ngay khi cộng vào balance
- **Owner:** Thuế tính trên số tiền đền bù (đã trừ phần Team)
- **Team Members:** Thuế tính trên từng phần chia của họ
- **Lợi ích:** User không phải lo thuế khi rút tiền

#### Sau ngày 20 (Đã kê khai):
```
Thanh toán lần 1: Trừ toàn bộ Thuế gốc đã kê khai (7%)
Thanh toán lần 2: Hoàn lại = Thuế gốc - Thuế thực tế
```
- Lần 1: Trừ thuế gốc 7% (vì đã kê khai với chi cục thuế)
- Lần 2 (tháng sau): Hoàn lại phần chênh lệch

---

### Nguyên tắc 4.1: Cấu hình thuế trong hệ thống

```java
// Config file hoặc admin setting
@Configuration
public class TaxConfiguration {
    
    // === CẤU HÌNH THUẾ ===
    
    // Tổng thuế - áp dụng đồng nhất cho mọi trường hợp
    private BigDecimal taxRate = new BigDecimal("0.07"); // 7%
    
    // === TÍNH THUẾ CHO CONTRACT ===
    public BigDecimal calculateContractTax(BigDecimal amount) {
        return amount.multiply(taxRate); // 7%
    }
    
    // === TÍNH THUẾ KHI CHẤM DỨT ===
    public BigDecimal calculateTerminationTax(BigDecimal amount) {
        return amount.multiply(taxRate); // 7%
    }
    
    // === TÍNH THUẾ KHI ĐỀN BÙ ===
    public BigDecimal calculateCompensationTax(BigDecimal amount) {
        return amount.multiply(taxRate); // 7%
    }
    
    // === LẤY TỶ LỆ THUẾ ===
    public BigDecimal getTaxRate() {
        return taxRate; // 7%
    }
}
```

**Đơn giản:**
- ✅ Tất cả đều 7%
- ✅ Không cần phân biệt PIT, VAT hay các loại thuế khác
- ✅ Dễ tính toán, dễ hiểu, dễ bảo trì

---

### Nguyên tắc 5: Cơ chế Balance

**Tất cả giao dịch đều qua balance:**
```
1. Cộng tiền vào balance người nhận
2. Ghi log transaction
3. Gửi notification
4. Người dùng tự tạo yêu cầu rút tiền khi cần
5. Khi rút tiền mới xử lý thuế thu nhập cá nhân
```

---

## III. LUỒNG CHẤM DỨT TRƯỚC NGÀY 20

> **Đặc điểm:** Chưa kê khai thuế → Tính thuế 7% trên số tiền thực nhận

---

### A. Trường hợp A1: FULL - CLIENT chấm dứt

#### Bước 1: Tính toán số tiền
```
Số tiền gốc = Contract.totalAmount
Tiền đền bù Team (gross) = SUM(MilestoneMoneySplit.amount WHERE status = APPROVED)
Tiền đền bù Owner (gross) = Số tiền gốc × Contract.compensationPercentage
Tiền Owner thực nhận = Tiền đền bù Owner - Tiền đền bù Team

// Tính thuế 7% cho từng bên
Thuế Team = Tiền đền bù Team × 7%
Thuế Owner = Tiền Owner thực nhận × 7%

// Tính net (sau thuế 7%)
Team net (tổng) = Tiền đền bù Team × 93%
Owner net = Tiền Owner thực nhận × 93%
```

**Ví dụ cụ thể:**
```
Contract.totalAmount = 100,000,000đ
Contract.totalTax = 7,000,000đ (7%)
compensationPercentage = 50%
Team splits (APPROVED) = 10,000,000đ

→ Tiền đền bù Owner (gross) = 100tr × 50% = 50,000,000đ
→ Tiền Owner thực nhận = 50tr - 10tr = 40,000,000đ

→ Thuế Team (7%) = 10tr × 7% = 700,000đ
→ Thuế Owner (7%) = 40tr × 7% = 2,800,000đ
→ Tổng thuế = 3,500,000đ

→ Team net = 10tr - 700k = 9,300,000đ vào balance
→ Owner net = 40tr - 2.8tr = 37,200,000đ vào balance
→ Client nhận hoàn = 100tr - 50tr = 50,000,000đ vào balance

Kiểm tra:
Team net + Owner net + Client refund + Thuế = 9.3tr + 37.2tr + 50tr + 3.5tr = 100tr ✓
```

#### Bước 2: Phân phối tiền vào Balance
```java
// Cấu hình thuế
final BigDecimal TAX_RATE = new BigDecimal("0.07"); // 7% tổng

// 1. Team Members (ưu tiên cao nhất)
BigDecimal totalTeamTax = BigDecimal.ZERO;
for (MilestoneMoneySplit split : approvedSplits) {
    User member = split.getUser();
    BigDecimal grossAmount = split.getAmount();
    BigDecimal tax = grossAmount.multiply(TAX_RATE); // 7%
    BigDecimal netAmount = grossAmount.subtract(tax);
    
    member.setBalance(member.getBalance().add(netAmount));
    totalTeamTax = totalTeamTax.add(tax);
    // Ghi log: TERMINATION_COMPENSATION_TEAM (đã khấu trừ 7%)
}

// 2. Owner
BigDecimal ownerTax = tienOwnerThucNhan.multiply(TAX_RATE); // 7%
BigDecimal ownerNet = tienOwnerThucNhan.subtract(ownerTax);
owner.setBalance(owner.getBalance().add(ownerNet));
// Ghi log: TERMINATION_COMPENSATION_OWNER (đã khấu trừ 7%)

// 3. Client
BigDecimal clientRefund = soTienGoc - tienDenBuOwner;
client.setBalance(client.getBalance().add(clientRefund));
// Ghi log: TERMINATION_REFUND_CLIENT (Client không chịu thêm thuế)
```

#### Bước 3: Ghi nhận thuế
```java
TaxRecord taxRecord = TaxRecord.builder()
    .contractId(contract.getId())
    .terminationType(TerminationType.BEFORE_DAY_20)
    .terminatedBy(TerminatedBy.CLIENT)
    .paymentType(PaymentType.FULL)
    // Thuế gốc = 0 (chưa kê khai)
    .originalTax(BigDecimal.ZERO)
    // Thuế thực tế = 7%
    .actualTax(ownerTax.add(totalTeamTax)) // 7% của Owner + Team
    // Chi tiết
    .ownerActualReceive(tienOwnerThucNhan)
    .teamCompensation(tienDenBuTeam)
    .taxPaidByOwner(ownerTax) // 7% của Owner
    .taxPaidByTeam(totalTeamTax) // 7% của Team
    .status(TaxStatus.COMPLETED)
    .build();

// Tạo TaxPayoutRecord cho từng team member
for (MilestoneMoneySplit split : approvedSplits) {
    BigDecimal grossAmount = split.getAmount();
    BigDecimal tax = grossAmount.multiply(TAX_RATE);
    
    TaxPayoutRecord record = TaxPayoutRecord.builder()
        .user(split.getUser())
        .grossAmount(grossAmount)
        .taxAmount(tax) // 7%
        .netAmount(grossAmount.subtract(tax))
        .taxRate(TAX_RATE)
        .payoutSource(PayoutSource.TERMINATION_COMPENSATION)
        .contract(contract)
        .build();
    taxPayoutRecordRepository.save(record);
}

// Tạo TaxPayoutRecord cho Owner (tương tự)
```

#### Tóm tắt:
- ✅ Team Members: Nhận phần chia **sau khi khấu trừ 7%** vào balance
- ✅ Owner: Nhận (Đền bù - Phần Team) **sau khi khấu trừ 7%** vào balance
- ✅ Client: Nhận (Tổng tiền - Đền bù Owner gross) vào balance
- 📊 Thuế: 7% × (Tiền đền bù Team + Tiền Owner thực nhận)
- 🔄 Khi rút tiền: Không trừ thuế nữa (đã khấu trừ 7% tại nguồn)

---

### B. Trường hợp A2: FULL - OWNER chấm dứt

#### Điều kiện tiên quyết:
```
✅ Owner phải thanh toán đền bù cho Team Members trước
✅ Kiểm tra Owner có đủ balance để đền bù không
```

#### Bước 1: Xử lý Team Members (Owner đền bù)

**Lưu ý:** Owner phải **chuyển tiền từ túi** (qua PayOS), KHÔNG lấy từ balance trong hệ thống.

```java
// Tính tổng tiền đền bù Team (gross - chưa trừ thuế)
BigDecimal totalTeamGross = 
    SUM(MilestoneMoneySplit.amount WHERE status = APPROVED);

// Tính tổng thuế Team (7%)
BigDecimal totalTeamTax = totalTeamGross.multiply(new BigDecimal("0.07"));

// Tổng tiền Owner phải trả = gross (thuế Team do Owner chịu thay)
BigDecimal totalOwnerMustPay = totalTeamGross;

// === BƯỚC 1.1: Tạo yêu cầu thanh toán qua PayOS ===
// Owner phải chuyển tiền TỪ TÚI vào tài khoản hệ thống
OwnerCompensationPayment payment = OwnerCompensationPayment.builder()
    .contractId(contract.getId())
    .ownerId(owner.getId())
    .totalAmount(totalOwnerMustPay)
    .status(PaymentStatus.PENDING)
    .build();
ownerCompensationPaymentRepository.save(payment);

// Tạo PayOS payment order
PaymentOrder paymentOrder = createPayOSPaymentForOwnerCompensation(
    owner,
    totalOwnerMustPay,
    payment.getId(),
    "Đền bù Team khi chấm dứt hợp đồng #" + contract.getId()
);

// Gửi thông báo cho Owner
sendNotificationToOwner(owner, 
    "Vui lòng thanh toán " + totalOwnerMustPay + " đ để đền bù Team. " +
    "Link thanh toán: " + paymentOrder.getPaymentUrl()
);

// === BƯỚC 1.2: Chờ Owner thanh toán ===
// Webhook PayOS sẽ xử lý khi Owner chuyển tiền thành công
// → Cộng tiền vào balance Team
// → Cập nhật payment.status = COMPLETED
// → Trigger tiếp bước chấm dứt hợp đồng

// === LƯU Ý ===
// - Hợp đồng chưa được chấm dứt ngay
// - Phải đợi Owner chuyển tiền xong
// - Sau khi webhook confirm → Mới xử lý tiếp bước 2, 3
```

**Webhook xử lý khi Owner đã chuyển tiền:**
```java
@Transactional
public void handleOwnerCompensationWebhook(String orderCode, String status) {
    if (!"SUCCESS".equals(status)) {
        return; // Owner chưa chuyển tiền thành công
    }
    
    // Lấy thông tin payment
    OwnerCompensationPayment payment = paymentRepository
        .findByPaymentOrderCode(orderCode)
        .orElseThrow();
    
    if (payment.getStatus() == PaymentStatus.COMPLETED) {
        return; // Đã xử lý rồi
    }
    
    // Cộng vào balance từng thành viên (AFTER TAX)
    List<MilestoneMoneySplit> approvedSplits = 
        milestoneMoneySplitRepository.findApprovedByContractId(
            payment.getContractId()
        );
    
    for (MilestoneMoneySplit split : approvedSplits) {
        User member = split.getUser();
        BigDecimal grossAmount = split.getAmount();
        BigDecimal pitTax = grossAmount.multiply(new BigDecimal("0.07"));
        BigDecimal netAmount = grossAmount.subtract(pitTax);
        
        member.setBalance(member.getBalance().add(netAmount));
        // Ghi log: TEAM_RECEIVE_COMPENSATION (đã trừ 7%)
        
        // Tạo TaxPayoutRecord
        createPayoutRecord(member, grossAmount, 
            PayoutSource.TERMINATION_COMPENSATION, 
            payment.getContract(), null);
    }
    
    // Cập nhật payment status
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setCompletedAt(LocalDateTime.now());
    paymentRepository.save(payment);
    
    // Gửi notification cho Team
    sendNotificationToTeam(approvedSplits, "Đã nhận đền bù vào balance");
    
    // === Tiếp tục xử lý chấm dứt hợp đồng ===
    // Chuyển sang bước 2: Hoàn tiền cho Client
    continueContractTermination(payment.getContractId());
}
```

#### Bước 2: Sau khi Team đã được đền bù (webhook đã xử lý)

**Điều kiện:** Owner đã chuyển tiền thành công → Team đã nhận vào balance

```java
// Kiểm tra Owner đã thanh toán chưa
OwnerCompensationPayment payment = paymentRepository
    .findByContractId(contractId)
    .orElseThrow();

if (payment.getStatus() != PaymentStatus.COMPLETED) {
    throw new OwnerHasNotPaidException(
        "Owner chưa thanh toán đền bù cho Team. Không thể chấm dứt hợp đồng."
    );
}

// Client nhận hoàn 100%
BigDecimal fullRefund = contract.getTotalAmount();
client.setBalance(client.getBalance().add(fullRefund));
// Ghi log: TERMINATION_FULL_REFUND_CLIENT

// Owner: Không nhận gì từ hợp đồng
// Owner đã mất: totalTeamGross (đã chuyển cho Team)
// Thuế: 0 (Owner không nhận thu nhập từ hợp đồng)
```

#### Bước 3: Ghi nhận thuế
```java
TaxRecord taxRecord = TaxRecord.builder()
    .contractId(contract.getId())
    .terminationType(TerminationType.BEFORE_DAY_20)
    .terminatedBy(TerminatedBy.OWNER)
    .paymentType(PaymentType.FULL)
    .originalPitTax(BigDecimal.ZERO)
    .originalVatTax(BigDecimal.ZERO)
    .originalTax(BigDecimal.ZERO)
    .actualTax(totalTeamTax) // 7% của Team (Owner đã trả thay)
    .actualVatTax(BigDecimal.ZERO)
    .actualTax(totalTeamTax)
    .ownerActualReceive(BigDecimal.ZERO) // Owner không nhận từ hợp đồng
    .teamCompensation(totalTeamGross) // Gross team compensation
    .taxPaidByOwner(totalTeamTax) // Owner trả PIT thay Team
    .ownerCompensationPaymentId(payment.getId()) // Link to payment
    .status(TaxStatus.COMPLETED)
    .build();
```

**Giải thích:** 
- Owner chuyển GROSS amount TỪ TÚI qua PayOS
- Hệ thống nhận tiền → Cộng NET vào balance Team (đã trừ 7%)
- Phần thuế 7% do Owner chịu và nộp thay

#### Tóm tắt:
- ✅ Team Members: Nhận đền bù **sau khi khấu trừ 7%** vào balance
- ❌ Owner: Không nhận gì từ hợp đồng, phải **chuyển tiền TỪ TÚI** (gross) cho Team
- ✅ Client: Nhận 100% tiền hợp đồng vào balance
- 📊 Thuế: 7% × Tiền đền bù Team (Owner trả thay)
- 💰 Owner tổng mất: Gross Team Compensation (từ túi riêng, không phải balance hệ thống)
- ⏳ Luồng: Owner chuyển tiền → Webhook confirm → Team nhận balance → Chấm dứt HĐ → Client nhận hoàn

---

### C. Trường hợp A3: MILESTONE - CLIENT chấm dứt

#### Bước 1: Xác định scope
```java
// Lấy các milestone đang IN_PROGRESS
List<Milestone> inProgressMilestones = 
    milestoneRepository.findByContractIdAndStatus(
        contractId, MilestoneStatus.IN_PROGRESS
    );

// Tính tổng tiền
BigDecimal totalAmount = inProgressMilestones.stream()
    .map(Milestone::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

#### Bước 2: Tính toán
```
Số tiền gốc = Tổng amount của milestones IN_PROGRESS
Tiền đền bù Team = SUM(MilestoneMoneySplit.amount 
                   WHERE status = APPROVED 
                   AND milestone IN inProgressMilestones)
Tiền đền bù Owner = Số tiền gốc × compensationPercentage
Tiền Owner thực nhận = Tiền đền bù Owner - Tiền đền bù Team
Thuế Owner = Tiền Owner thực nhận × 7%
```

#### Bước 3: Phân phối vào Balance
```java
// 1. Team Members (khấu trừ thuế 7%)
for (MilestoneMoneySplit split : approvedSplitsInProgress) {
    BigDecimal grossAmount = split.getAmount();
    BigDecimal tax = grossAmount.multiply(new BigDecimal("0.07"));
    BigDecimal netAmount = grossAmount.subtract(tax);
    
    member.setBalance(member.getBalance().add(netAmount));
    // Tạo TaxPayoutRecord
}

// 2. Owner (khấu trừ thuế 7%)
BigDecimal ownerNet = tienOwnerThucNhan.subtract(thueOwner);
owner.setBalance(owner.getBalance().add(ownerNet));

// 3. Client
BigDecimal clientRefund = soTienGoc - tienDenBuOwner;
client.setBalance(client.getBalance().add(clientRefund));
```

#### Bước 4: Xử lý các milestone khác
```
- PENDING: Không động chạm, không hoàn tiền (chưa làm)
- COMPLETED/PAID: Không động chạm (đã thanh toán rồi)
- IN_PROGRESS: Đã xử lý ở trên
```

#### Tóm tắt:
- ✅ Team Members: Nhận phần chia **sau khi trừ thuế 7%** vào balance
- ✅ Owner: Nhận (Đền bù - Phần Team) **sau khi trừ thuế 7%** vào balance
- ✅ Client: Nhận hoàn phần còn lại của milestones IN_PROGRESS vào balance
- 📊 Thuế: 7% × (Tiền đền bù Team + Tiền Owner thực nhận)

---

### D. Trường hợp A4: MILESTONE - OWNER chấm dứt

#### Điều kiện tiên quyết:
```
✅ Owner phải đền bù Team trước
✅ Chỉ xử lý các milestone IN_PROGRESS
```

#### Bước 1: Xác định scope
```java
List<Milestone> inProgressMilestones = 
    milestoneRepository.findByContractIdAndStatus(
        contractId, MilestoneStatus.IN_PROGRESS
    );
```

#### Bước 2: Owner đền bù Team

**Lưu ý:** Owner phải **chuyển tiền từ túi** (qua PayOS), KHÔNG lấy từ balance.

```java
// Tính tổng đền bù Team từ milestones IN_PROGRESS (gross)
BigDecimal totalTeamGross = 
    SUM(MilestoneMoneySplit.amount 
        WHERE status = APPROVED 
        AND milestone IN inProgressMilestones);

// Tính thuế Team (7%)
BigDecimal totalTeamTax = totalTeamGross.multiply(new BigDecimal("0.07"));

// Owner phải chuyển tiền TỪ TÚI qua PayOS
OwnerCompensationPayment payment = createOwnerCompensationPayment(
    contract.getId(),
    owner.getId(),
    totalTeamGross
);

// Tạo PayOS payment order
PaymentOrder paymentOrder = createPayOSPaymentForOwnerCompensation(
    owner, totalTeamGross, payment.getId(),
    "Đền bù Team milestones IN_PROGRESS - Contract #" + contract.getId()
);

// Gửi link thanh toán cho Owner
sendPaymentLinkToOwner(owner, paymentOrder.getPaymentUrl());

// === Chờ webhook PayOS confirm ===
// Khi Owner chuyển tiền xong:
// 1. Webhook xử lý
// 2. Cộng NET vào balance Team (đã trừ 7%)
// 3. Cập nhật payment.status = COMPLETED
// 4. Trigger tiếp bước chấm dứt hợp đồng
```

**Webhook xử lý (tương tự A2):**
- Owner chuyển tiền → PayOS confirm → Cộng NET vào balance Team
- Sau đó mới tiếp tục bước 3: Hoàn tiền Client
```

#### Bước 3: Hoàn tiền Client
```java
// Client nhận 100% số tiền các milestone IN_PROGRESS
BigDecimal totalMilestoneAmount = inProgressMilestones.stream()
    .map(Milestone::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

client.setBalance(client.getBalance().add(totalMilestoneAmount));
```

#### Tóm tắt:
- ✅ Team Members: Nhận đền bù **sau khi khấu trừ 7%** vào balance
- ❌ Owner: Không nhận gì, phải **chuyển tiền TỪ TÚI** (gross) qua PayOS cho Team
- ✅ Client: Nhận 100% số tiền milestones IN_PROGRESS vào balance
- 📊 Thuế: 7% × Tiền đền bù Team (Owner trả thay)
- ⏳ Luồng: Owner chuyển tiền → Webhook → Team nhận → Chấm dứt HĐ → Client nhận hoàn

---

## IV. LUỒNG CHẤM DỨT SAU NGÀY 20

> **Đặc điểm:** Đã kê khai thuế → Thanh toán 2 lần (Lần 1: trừ thuế gốc, Lần 2: hoàn thuế)

---

### A. Trường hợp B1: FULL - CLIENT chấm dứt

#### Bước 1: Tính toán
```
Số tiền gốc = Contract.totalAmount
Thuế gốc đã kê khai = Contract.pitTax + Contract.vatTax
Tiền đền bù Team (gross) = SUM(MilestoneMoneySplit.amount WHERE status = APPROVED)
Tiền đền bù Owner (gross) = Số tiền gốc × compensationPercentage
Tiền Owner thực nhận = Tiền đền bù Owner - Tiền đền bù Team

// Tính thuế thực tế (7% trên số tiền thực nhận)
Thuế Team thực tế = Tiền đền bù Team × 7%
Thuế Owner thực tế = Tiền Owner thực nhận × 7%
Thuế thực tế (tổng) = Thuế Team + Thuế Owner

// Thuế hoàn lại
Thuế hoàn lại = Thuế gốc - Thuế thực tế (tổng)
```

#### Bước 2: Thanh toán lần 1 (ngay khi chấm dứt)
```java
// 1. Team Members (khấu trừ thuế 7%)
BigDecimal totalTeamTax = BigDecimal.ZERO;
for (MilestoneMoneySplit split : approvedSplits) {
    BigDecimal grossAmount = split.getAmount();
    BigDecimal tax = grossAmount.multiply(new BigDecimal("0.07"));
    BigDecimal netAmount = grossAmount.subtract(tax);
    
    member.setBalance(member.getBalance().add(netAmount));
    totalTeamTax = totalTeamTax.add(tax);
    // Ghi log: TERMINATION_TEAM_ROUND_1 (đã trừ thuế)
}

// 2. Owner (BỊ TRỪ THUẾ GỐC của cả Owner lẫn Team)
// Lưu ý: Thuế gốc bao gồm cả thuế của Team và Owner
BigDecimal ownerRound1 = tienDenBuOwner - thueGoc - tienDenBuTeam;
owner.setBalance(owner.getBalance().add(ownerRound1));
// Ghi log: TERMINATION_OWNER_ROUND_1

// 3. Client
BigDecimal clientRefund = soTienGoc - tienDenBuOwner;
client.setBalance(client.getBalance().add(clientRefund));
// Ghi log: TERMINATION_CLIENT_REFUND
```

**Lưu ý:** Owner bị trừ **TOÀN BỘ thuế gốc** vì đã kê khai với chi cục thuế

#### Bước 3: Thanh toán lần 2 (tháng sau - sau khi quyết toán thuế)
```java
// Tính thuế hoàn lại
BigDecimal thueHoanLai = thueGoc - thueThucTe;

// Owner nhận hoàn
owner.setBalance(owner.getBalance().add(thueHoanLai));
// Ghi log: TAX_REFUND_OWNER_ROUND_2
```

#### Bước 4: Ghi nhận thuế
```java
TaxRecord taxRecord = TaxRecord.builder()
    .contractId(contract.getId())
    .terminationType(TerminationType.AFTER_DAY_20)
    .terminatedBy(TerminatedBy.CLIENT)
    .paymentType(PaymentType.FULL)
    .originalTax(thueGoc)
    .actualTax(thueThucTe)
    .refundedTax(thueHoanLai)
    .ownerReceiveRound1(ownerRound1)
    .ownerReceiveRound2(thueHoanLai)
    .refundScheduledDate(nextMonth20th) // Ngày 20 tháng sau
    .status(TaxStatus.WAITING_REFUND)
    .build();
```

#### Giải thích:
- Đã kê khai với chi cục thuế → Phải nộp `Thuế gốc`
- Thực tế chỉ phải nộp `7% × Tiền thực nhận`
- Chi cục thuế hoàn lại phần chênh lệch → Chuyển cho Owner

#### Tóm tắt:
- ✅ Team: Nhận **sau khi trừ thuế 7%** vào balance (lần 1)
- ✅ Owner: 
  - Lần 1: Nhận (Đền bù - Thuế gốc - Phần Team) vào balance
  - Lần 2: Nhận phần thuế hoàn lại vào balance
- ✅ Client: Nhận hoàn vào balance (lần 1)
- 📊 Thuế: 
  - Thuế gốc bao gồm cả Team và Owner (7% tổng)
  - Thuế thực tế = 7% × (Team gross + Owner thực nhận)
  - Thuế hoàn = Thuế gốc - Thuế thực tế
  - Owner nhận hoàn thuế ở lần 2

---

### B. Trường hợp B2: FULL - OWNER chấm dứt

#### Điều kiện tiên quyết:
```
✅ Owner phải đền bù Team trước
```

#### Bước 1: Owner đền bù Team

**Lưu ý:** Owner phải **chuyển tiền từ túi** qua PayOS.

```java
BigDecimal totalTeamGross = 
    SUM(MilestoneMoneySplit.amount WHERE status = APPROVED);

BigDecimal totalTeamTax = totalTeamGross.multiply(new BigDecimal("0.07"));

// Owner chuyển tiền TỪ TÚI qua PayOS
OwnerCompensationPayment payment = createOwnerCompensationPayment(
    contract.getId(), owner.getId(), totalTeamGross
);

PaymentOrder paymentOrder = createPayOSPaymentForOwnerCompensation(
    owner, totalTeamGross, payment.getId(),
    "Đền bù Team - Contract #" + contract.getId()
);

// Gửi link thanh toán
sendPaymentLinkToOwner(owner, paymentOrder.getPaymentUrl());

// === Chờ webhook ===
// Khi Owner chuyển tiền xong:
// - Webhook cộng NET vào balance Team (đã trừ 7%)
// - Cập nhật payment.status = COMPLETED
// - Trigger tiếp bước 2
```

#### Bước 2: Thanh toán lần 1 (sau khi Team đã được đền bù)
```java
BigDecimal thueGoc = contract.getPitTax().add(contract.getVatTax());
BigDecimal clientRound1 = contract.getTotalAmount().subtract(thueGoc);

client.setBalance(client.getBalance().add(clientRound1));
// Ghi log: TERMINATION_CLIENT_ROUND_1

// Owner nhận: 0
```

#### Bước 3: Thanh toán lần 2 (tháng sau)
```java
// Thuế hoàn lại = Thuế gốc (vì Owner không nhận tiền từ hợp đồng)
BigDecimal thueHoanLai = thueGoc;

client.setBalance(client.getBalance().add(thueHoanLai));
// Ghi log: TAX_REFUND_CLIENT_ROUND_2
```

#### Tổng Client nhận:
```
Lần 1: totalAmount - Thuế gốc
Lần 2: Thuế gốc
-------------------------------
Tổng:  100% totalAmount
```

#### Bước 4: Ghi nhận thuế
```java
TaxRecord taxRecord = TaxRecord.builder()
    .contractId(contract.getId())
    .terminationType(TerminationType.AFTER_DAY_20)
    .terminatedBy(TerminatedBy.OWNER)
    .paymentType(PaymentType.FULL)
    .originalTax(thueGoc)
    .actualTax(BigDecimal.ZERO) // Owner không nhận gì
    .refundedTax(thueGoc) // Hoàn 100%
    .teamCompensation(totalTeamCompensation)
    .refundScheduledDate(nextMonth20th)
    .status(TaxStatus.WAITING_REFUND)
    .build();
```

#### Tóm tắt:
- ✅ Team: Nhận đền bù **sau khi khấu trừ 7%** vào balance
- ❌ Owner: Không nhận gì, phải **chuyển tiền TỪ TÚI** (gross) qua PayOS
- ✅ Client:
  - Lần 1: Nhận (Tổng tiền - Thuế gốc) vào balance
  - Lần 2: Nhận thuế hoàn lại vào balance
  - **Tổng: 100% tiền hợp đồng**
- 📊 Thuế: 
  - Thuế gốc đã kê khai (PIT + VAT)
  - Thuế thực tế = 7% × Team gross (Owner không nhận gì)
  - Thuế hoàn = Thuế gốc - Thuế thực tế
  - Client nhận hoàn ở lần 2
- ⏳ Luồng: Owner chuyển tiền → Webhook → Team nhận → Chấm dứt HĐ → Client nhận (2 lần)

---

### C. Trường hợp B3: MILESTONE - CLIENT chấm dứt

#### Bước 1: Xác định scope
```java
List<Milestone> inProgressMilestones = 
    milestoneRepository.findByContractIdAndStatus(
        contractId, MilestoneStatus.IN_PROGRESS
    );

BigDecimal soTienGoc = inProgressMilestones.stream()
    .map(Milestone::getAmount)
    .reduce(BigDecimal.ZERO, BigDecimal::add);

BigDecimal thueGoc = inProgressMilestones.stream()
    .map(m -> m.getPitTax().add(m.getVatTax()))
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

#### Bước 2: Tính toán
```
Tiền đền bù Team = SUM(MilestoneMoneySplit.amount 
                   WHERE status = APPROVED 
                   AND milestone IN inProgressMilestones)
Tiền đền bù Owner = Số tiền gốc × compensationPercentage
Tiền Owner thực nhận = Tiền đền bù Owner - Tiền đền bù Team
Thuế thực tế = Tiền Owner thực nhận × 7%
Thuế hoàn lại = Thuế gốc - Thuế thực tế
```

#### Bước 3: Thanh toán lần 1
```java
// 1. Team (khấu trừ thuế 7%)
for (MilestoneMoneySplit split : approvedSplitsInProgress) {
    BigDecimal grossAmount = split.getAmount();
    BigDecimal tax = grossAmount.multiply(new BigDecimal("0.07"));
    BigDecimal netAmount = grossAmount.subtract(tax);
    
    member.setBalance(member.getBalance().add(netAmount));
}

// 2. Owner (trừ thuế gốc)
BigDecimal ownerRound1 = tienDenBuOwner - thueGoc - tienDenBuTeam;
owner.setBalance(owner.getBalance().add(ownerRound1));

// 3. Client
BigDecimal clientRefund = soTienGoc - tienDenBuOwner;
client.setBalance(client.getBalance().add(clientRefund));
```

#### Bước 4: Thanh toán lần 2 (tháng sau)
```java
BigDecimal thueHoanLai = thueGoc - thueThucTe;
owner.setBalance(owner.getBalance().add(thueHoanLai));
```

#### Tóm tắt:
- ✅ Team: Nhận phần chia **sau khi trừ thuế 7%** vào balance (lần 1)
- ✅ Owner:
  - Lần 1: Nhận (Đền bù - Thuế gốc - Phần Team) vào balance
  - Lần 2: Nhận thuế hoàn lại vào balance
- ✅ Client: Nhận hoàn vào balance (lần 1)
- 📊 Thuế: 
  - Thuế gốc bao gồm cả Team và Owner (của milestones IN_PROGRESS): 7% tổng
  - Thuế thực tế = 7% × (Team gross + Owner thực nhận)
  - Thuế hoàn = Thuế gốc - Thuế thực tế

---

### D. Trường hợp B4: MILESTONE - OWNER chấm dứt

#### Bước 1: Xác định scope
```java
List<Milestone> inProgressMilestones = 
    milestoneRepository.findByContractIdAndStatus(
        contractId, MilestoneStatus.IN_PROGRESS
    );
```

#### Bước 2: Owner đền bù Team

**Lưu ý:** Owner chuyển tiền TỪ TÚI qua PayOS.

```java
BigDecimal totalTeamGross = 
    SUM(MilestoneMoneySplit.amount 
        WHERE status = APPROVED 
        AND milestone IN inProgressMilestones);

BigDecimal totalTeamTax = totalTeamGross.multiply(new BigDecimal("0.07"));

// Owner chuyển tiền TỪ TÚI qua PayOS
OwnerCompensationPayment payment = createOwnerCompensationPayment(
    contract.getId(), owner.getId(), totalTeamGross
);

PaymentOrder paymentOrder = createPayOSPaymentForOwnerCompensation(
    owner, totalTeamGross, payment.getId(),
    "Đền bù Team milestones IN_PROGRESS - Contract #" + contract.getId()
);

// Gửi link thanh toán
sendPaymentLinkToOwner(owner, paymentOrder.getPaymentUrl());

// === Chờ webhook ===
// Owner chuyển tiền → Webhook → Cộng NET vào balance Team
```

#### Bước 3: Thanh toán lần 1
```java
BigDecimal soTienGoc = SUM(milestone.amount WHERE IN_PROGRESS);
BigDecimal thueGoc = SUM(milestone.pitTax + vatTax WHERE IN_PROGRESS);

BigDecimal clientRound1 = soTienGoc.subtract(thueGoc);
client.setBalance(client.getBalance().add(clientRound1));

// Owner: 0
```

#### Bước 4: Thanh toán lần 2 (tháng sau)
```java
// Thuế hoàn lại = Thuế gốc (vì Owner không nhận gì)
BigDecimal thueHoanLai = thueGoc;
client.setBalance(client.getBalance().add(thueHoanLai));
```

#### Tổng Client nhận:
```
Lần 1: Số tiền milestones IN_PROGRESS - Thuế gốc
Lần 2: Thuế gốc
----------------------------------------------
Tổng:  100% số tiền milestones IN_PROGRESS
```

#### Tóm tắt:
- ✅ Team: Nhận đền bù **sau khi khấu trừ 7%** vào balance
- ❌ Owner: Không nhận gì, phải **chuyển tiền TỪ TÚI** (gross) qua PayOS
- ✅ Client:
  - Lần 1: Nhận (Tổng milestones - Thuế gốc) vào balance
  - Lần 2: Nhận thuế hoàn lại vào balance
  - **Tổng: 100% số tiền milestones IN_PROGRESS**
- 📊 Thuế:
  - Thuế gốc đã kê khai (PIT + VAT của milestones IN_PROGRESS)
  - Thuế thực tế = 7% × Team gross
  - Thuế hoàn = Thuế gốc - Thuế thực tế
  - Client nhận hoàn ở lần 2
- ⏳ Luồng: Owner chuyển tiền → Webhook → Team nhận → Chấm dứt HĐ → Client nhận (2 lần)

---

## V. BẢNG TỔNG HỢP

### Bảng 1: Phân phối tiền theo trường hợp

| Thời điểm | Loại HĐ | Người chấm dứt | Team Members | Owner | Client | Số lần TT |
|-----------|---------|----------------|--------------|-------|--------|-----------|
| **Trước 20** | FULL | CLIENT | TeamSplit × 93% (sau 7%) | (ĐB - TeamSplit) × 93% | Total - ĐB | 1 |
| **Trước 20** | FULL | OWNER | TeamSplit × 93% (Owner trả gross từ túi) | 0 (mất TeamSplit gross) | 100% | 1 |
| **Trước 20** | MILESTONE | CLIENT | TeamSplit(MS) × 93% | (ĐB - TeamSplit) × 93% | MS - ĐB | 1 |
| **Trước 20** | MILESTONE | OWNER | TeamSplit(MS) × 93% (Owner trả gross từ túi) | 0 (mất TeamSplit gross) | 100% MS | 1 |
| **Sau 20** | FULL | CLIENT | TeamSplit × 93%<br>(lần 1) | L1: ĐB - TGốc - TeamSplit<br>L2: THoàn | Total - ĐB | 2 |
| **Sau 20** | FULL | OWNER | TeamSplit × 93%<br>(Owner trả gross từ túi) | 0 (mất TeamSplit gross) | L1: Total - TGốc<br>L2: TGốc | 2 |
| **Sau 20** | MILESTONE | CLIENT | TeamSplit(MS) × 93%<br>(lần 1) | L1: ĐB - TGốc - TeamSplit<br>L2: THoàn | MS - ĐB | 2 |
| **Sau 20** | MILESTONE | OWNER | TeamSplit(MS) × 93%<br>(Owner trả gross từ túi) | 0 (mất TeamSplit gross) | L1: MS - TGốc<br>L2: TGốc | 2 |

**Chú thích:**
- `ĐB` = Đền bù (compensationPercentage × Số tiền) - **GROSS**
- `TeamSplit` = Tổng MilestoneMoneySplit (APPROVED) - **GROSS**
- `TeamSplit × 93%` = Team nhận **AFTER TAX** (đã khấu trừ 7%)
- `TGốc` = Thuế gốc đã kê khai = **7%** - bao gồm cả Team và Owner
- `THoàn` = Thuế hoàn lại = TGốc - Thuế thực tế (7% × Tổng tiền thực nhận)
- `MS` = Tổng amount các milestone IN_PROGRESS
- `L1` = Lần 1, `L2` = Lần 2
- `Balance` = Tất cả đều vào balance của người dùng
- **Quan trọng:** 
  - Team và Owner đều bị khấu trừ **7%** tại nguồn
  - Thuế 7% áp dụng đồng nhất cho mọi trường hợp
  - Owner chấm dứt: Phải chuyển tiền **từ túi** qua PayOS (không từ balance)

### Bảng 2: Xử lý thuế theo trường hợp

| Thời điểm | Loại HĐ | Người chấm dứt | Thuế đã kê khai | Thuế thực tế | Thuế hoàn lại | Ai nhận hoàn |
|-----------|---------|----------------|-----------------|--------------|---------------|--------------|
| **Trước 20** | FULL | CLIENT | 0 | 7% × ĐB | 0 | - |
| **Trước 20** | FULL | OWNER | 0 | 7% × TeamSplit | 0 | - |
| **Trước 20** | MILESTONE | CLIENT | 0 | 7% × ĐB | 0 | - |
| **Trước 20** | MILESTONE | OWNER | 0 | 7% × TeamSplit | 0 | - |
| **Sau 20** | FULL | CLIENT | **7%** | 7% × ĐB | TGốc - TThực | Owner (balance) |
| **Sau 20** | FULL | OWNER | **7%** | 7% × TeamSplit | TGốc - TThực | Client (balance) |
| **Sau 20** | MILESTONE | CLIENT | **Σ7%** MS | 7% × ĐB | TGốc - TThực | Owner (balance) |
| **Sau 20** | MILESTONE | OWNER | **Σ7%** MS | 7% × TeamSplit | TGốc - TThực | Client (balance) |

---

## VI. LƯU Ý KHI TRIỂN KHAI

### 1. Kiểm tra điều kiện trước khi chấm dứt

```java
// Checklist validation
✓ Contract.status phải ở trạng thái hợp lệ (PAID/COMPLETED)
✓ Xác định người chấm dứt (CLIENT hay OWNER)
✓ Kiểm tra thời điểm (trước/sau ngày 20)
✓ Với MILESTONE: Xác định chính xác các milestone IN_PROGRESS
✓ Với OWNER chấm dứt: Kiểm tra Owner có đủ balance để đền bù Team
✓ Kiểm tra tất cả MilestoneMoneySplit đều có status rõ ràng
```

### 2. Thứ tự xử lý giao dịch (QUAN TRỌNG)

```java
@Transactional
public void terminateContract(...) {
    // 1. Validation
    validateTermination();
    
    // 2. Tính toán tất cả số tiền
    TerminationCalculation calc = calculateAllAmounts();
    
    // 3. Kiểm tra balance (nếu Owner chấm dứt)
    if (terminatedBy == OWNER) {
        validateOwnerBalance(calc.getTeamCompensation());
    }
    
    // 4. Xử lý Team Members trước (ưu tiên cao nhất)
    processTeamCompensation(calc);
    
    // 5. Xử lý Owner
    processOwnerCompensation(calc);
    
    // 6. Xử lý Client
    processClientRefund(calc);
    
    // 7. Ghi nhận thuế
    createTaxRecord(calc);
    
    // 8. Lưu transaction logs
    saveTransactionLogs(calc);
    
    // 9. Cập nhật trạng thái hợp đồng
    contract.setStatus(ContractStatus.TERMINATED);
    contract.setTerminatedAt(LocalDateTime.now());
    contract.setTerminatedBy(terminatedBy);
    
    // 10. Schedule thanh toán lần 2 (nếu sau ngày 20)
    if (isAfterDay20) {
        scheduleSecondPayment(calc);
    }
    
    // 11. Gửi notifications
    sendNotifications(calc);
}
```

### 3. Cấu trúc Entity và Table cần thiết

#### TaxRecord Entity
```java
@Entity
@Table(name = "tax_records")
public class TaxRecord {
    @Id
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "contract_id")
    private Contract contract;
    
    @Enumerated(EnumType.STRING)
    private TerminationType terminationType; // BEFORE_DAY_20, AFTER_DAY_20
    
    @Enumerated(EnumType.STRING)
    private TerminatedBy terminatedBy; // CLIENT, OWNER
    
    @Enumerated(EnumType.STRING)
    private PaymentType paymentType; // FULL, MILESTONE
    
    private LocalDate terminationDate;
    
    // Số tiền liên quan
    // === THUẾ GỐC (đã kê khai với chi cục thuế) ===
    private BigDecimal originalPitTax; // PIT đã kê khai
    private BigDecimal originalVatTax; // VAT đã kê khai
    private BigDecimal originalTax; // Tổng thuế gốc = PIT + VAT
    
    // === THUẾ THỰC TẾ (tính trên số tiền thực nhận) ===
    private BigDecimal actualTax; // Thuế thực tế (7%)
    private BigDecimal actualVatTax; // VAT thực tế (thường = 0 khi chấm dứt)
    private BigDecimal actualTax; // Tổng thuế thực tế = PIT + VAT
    
    // === THUẾ HOÀN LẠI ===
    private BigDecimal refundedTax; // Thuế được hoàn = Gốc - Thực tế
    
    // === CHI TIẾT ===
    private BigDecimal ownerActualReceive; // Số tiền Owner thực nhận (gross)
    private BigDecimal teamCompensation; // Tổng đền bù Team (gross)
    
    // Thanh toán 2 lần (nếu sau ngày 20)
    private BigDecimal ownerReceiveRound1;
    private BigDecimal ownerReceiveRound2;
    private LocalDate refundScheduledDate; // Ngày dự kiến hoàn thuế
    private LocalDate refundCompletedDate; // Ngày hoàn thuế thực tế
    
    @Enumerated(EnumType.STRING)
    private TaxStatus status; // COMPLETED, WAITING_REFUND, REFUNDED
    
    // Metadata
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### BalanceTransaction Entity
```java
@Entity
@Table(name = "balance_transactions")
public class BalanceTransaction {
    @Id
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "contract_id")
    private Contract contract;
    
    @Enumerated(EnumType.STRING)
    private TransactionType type; 
    // TERMINATION_TEAM_COMPENSATION
    // TERMINATION_OWNER_COMPENSATION
    // TERMINATION_CLIENT_REFUND
    // TAX_REFUND
    // OWNER_COMPENSATE_TEAM
    
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    
    @Enumerated(EnumType.STRING)
    private TransactionStatus status; // PENDING, COMPLETED, FAILED
    
    private String description;
    private String referenceId; // TaxRecord.id nếu có
    
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
```

#### OwnerCompensationPayment Entity
```java
@Entity
@Table(name = "owner_compensation_payments")
public class OwnerCompensationPayment {
    @Id
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "contract_id")
    private Contract contract;
    
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;
    
    // Số tiền Owner phải trả (gross)
    @Column(name = "total_amount", precision = 15, scale = 2)
    private BigDecimal totalAmount;
    
    // Thông tin PayOS
    @Column(name = "payment_order_id")
    private String paymentOrderId; // ID từ PayOS
    
    @Column(name = "payment_order_code")
    private String paymentOrderCode; // Order code từ PayOS
    
    @Column(name = "payment_url", columnDefinition = "TEXT")
    private String paymentUrl; // Link thanh toán cho Owner
    
    // Trạng thái
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;
    // PENDING    - Đang chờ Owner chuyển tiền
    // PROCESSING - Đang xử lý
    // COMPLETED  - Owner đã chuyển tiền, Team đã nhận
    // FAILED     - Thất bại
    // EXPIRED    - Hết hạn (Owner không chuyển tiền)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "expired_at")
    private LocalDateTime expiredAt; // Hết hạn sau 24h
    
    // Ghi chú
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;
}
```

**Lưu ý về xử lý phạt Owner nếu không đền bù:**
```
TODO: Làm sau
- Nếu Owner không chuyển tiền sau 24h → payment.status = EXPIRED
- Hệ thống gửi cảnh báo cho Owner
- Có thể khóa tài khoản Owner hoặc áp dụng biện pháp khác
- Logic cụ thể sẽ triển khai sau
```

#### ContractTermination Entity
```java
@Entity
@Table(name = "contract_terminations")
public class ContractTermination {
    @Id
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "contract_id")
    private Contract contract;
    
    @Enumerated(EnumType.STRING)
    private TerminatedBy terminatedBy;
    
    @Enumerated(EnumType.STRING)
    private TerminationType terminationType;
    
    private LocalDateTime terminationDate;
    
    // Tổng quan tài chính
    private BigDecimal totalContractAmount;
    private BigDecimal totalTeamCompensation;
    private BigDecimal totalOwnerCompensation;
    private BigDecimal totalClientRefund;
    private BigDecimal totalTaxDeducted;
    
    // Milestones liên quan (nếu MILESTONE payment)
    @OneToMany(mappedBy = "termination")
    private List<TerminatedMilestone> terminatedMilestones;
    
    // Tham chiếu đến TaxRecord
    @OneToOne(mappedBy = "termination")
    private TaxRecord taxRecord;
    
    @Enumerated(EnumType.STRING)
    private TerminationStatus status; 
    // PROCESSING, COMPLETED, PARTIAL_COMPLETED
    
    private String notes;
    private String reason; // Lý do chấm dứt
}
```

### 4. Xử lý giao dịch Atomic

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public TerminationResult terminateContract(...) {
    try {
        // Lock contract để tránh concurrent termination
        Contract contract = contractRepository
            .findByIdWithLock(contractId)
            .orElseThrow();
        
        // Validate status
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new ContractAlreadyTerminatedException();
        }
        
        // Thực hiện các bước xử lý...
        // Nếu có lỗi ở bất kỳ bước nào → rollback toàn bộ
        
        // Commit nếu tất cả thành công
        return TerminationResult.success();
        
    } catch (Exception e) {
        // Log error
        log.error("Failed to terminate contract", e);
        // Transaction sẽ tự động rollback
        throw e;
    }
}
```

### 5. Xử lý Balance Thread-Safe

```java
@Transactional
public void updateUserBalance(User user, BigDecimal amount, 
                               TransactionType type) {
    // Lock user row
    User lockedUser = userRepository.findByIdWithLock(user.getId());
    
    BigDecimal oldBalance = lockedUser.getBalance();
    BigDecimal newBalance = oldBalance.add(amount);
    
    // Validate không âm (trừ khi là rút tiền)
    if (newBalance.compareTo(BigDecimal.ZERO) < 0 
        && type != TransactionType.WITHDRAWAL) {
        throw new InsufficientBalanceException();
    }
    
    // Update balance
    lockedUser.setBalance(newBalance);
    userRepository.save(lockedUser);
    
    // Ghi log transaction
    BalanceTransaction transaction = BalanceTransaction.builder()
        .user(lockedUser)
        .type(type)
        .amount(amount)
        .balanceBefore(oldBalance)
        .balanceAfter(newBalance)
        .status(TransactionStatus.COMPLETED)
        .createdAt(LocalDateTime.now())
        .build();
    balanceTransactionRepository.save(transaction);
}
```

### 6. Schedule thanh toán lần 2 (Sau ngày 20)

```java
// Sử dụng Spring Scheduler hoặc Quartz
@Scheduled(cron = "0 0 10 20 * ?") // 10:00 sáng ngày 20 hàng tháng
public void processMonthlyTaxRefunds() {
    LocalDate today = LocalDate.now();
    
    // Lấy các TaxRecord cần hoàn thuế
    List<TaxRecord> pendingRefunds = taxRecordRepository
        .findByStatusAndRefundScheduledDate(
            TaxStatus.WAITING_REFUND, 
            today
        );
    
    for (TaxRecord record : pendingRefunds) {
        try {
            processSecondPayment(record);
        } catch (Exception e) {
            log.error("Failed to process tax refund for record: " 
                + record.getId(), e);
            // Tiếp tục với record tiếp theo
        }
    }
}

@Transactional
private void processSecondPayment(TaxRecord record) {
    Contract contract = record.getContract();
    User owner = contract.getProject().getCreator();
    User client = contract.getProject().getClient();
    
    BigDecimal refundAmount = record.getRefundedTax();
    
    if (record.getTerminatedBy() == TerminatedBy.CLIENT) {
        // Owner nhận thuế hoàn
        updateUserBalance(owner, refundAmount, 
            TransactionType.TAX_REFUND);
    } else {
        // Client nhận thuế hoàn
        updateUserBalance(client, refundAmount, 
            TransactionType.TAX_REFUND);
    }
    
    // Cập nhật trạng thái
    record.setStatus(TaxStatus.REFUNDED);
    record.setRefundCompletedDate(LocalDate.now());
    taxRecordRepository.save(record);
    
    // Gửi notification
    sendTaxRefundNotification(record, refundAmount);
}
```

### 7. Xử lý rút tiền (Withdrawal)

**Lưu ý quan trọng:** Với chế độ khấu trừ tại nguồn, balance của user đã là số tiền **sau thuế**. Khi rút tiền, không trừ thuế nữa.

```java
@Transactional
public WithdrawalResult processWithdrawal(Long userId, 
                                         BigDecimal amount) {
    User user = userRepository.findByIdWithLock(userId);
    
    // Validate balance
    if (user.getBalance().compareTo(amount) < 0) {
        throw new InsufficientBalanceException();
    }
    
    // KHÔNG TRỪ THUẾ NỮA vì đã khấu trừ tại nguồn
    // Balance đã là số tiền sau thuế
    BigDecimal netAmount = amount; // Rút bao nhiêu nhận bấy nhiêu
    
    // Trừ balance
    user.setBalance(user.getBalance().subtract(amount));
    userRepository.save(user);
    
    // Ghi log
    BalanceTransaction transaction = BalanceTransaction.builder()
        .user(user)
        .type(TransactionType.WITHDRAWAL)
        .amount(amount.negate()) // Số âm
        .balanceBefore(user.getBalance().add(amount))
        .balanceAfter(user.getBalance())
        .description("Withdrawal - No tax (already withheld at source)")
        .build();
    balanceTransactionRepository.save(transaction);
    
    // Tạo lệnh chuyển tiền qua PayOS
    // Chuyển FULL amount (không trừ thuế)
    PaymentOrder order = createPayOSWithdrawal(user, netAmount);
    
    return WithdrawalResult.builder()
        .grossAmount(amount)
        .taxAmount(BigDecimal.ZERO) // Thuế đã trừ lúc nhận thu nhập
        .netAmount(netAmount) // = grossAmount
        .paymentOrderId(order.getId())
        .note("Thuế đã được khấu trừ tại nguồn khi nhận thu nhập")
        .build();
}
```

**Giải thích:**
- ✅ User nhận thu nhập → Đã bị trừ 7% → Balance = NET
- ✅ User rút tiền → Rút đúng số tiền trong balance → Không trừ thêm
- ✅ Đơn giản hơn cho user: "Số dư bạn thấy là số bạn được rút"

### 8. Notification System

```java
public void sendTerminationNotifications(TerminationResult result) {
    Contract contract = result.getContract();
    
    // 1. Thông báo cho Team Members
    for (TeamCompensation tc : result.getTeamCompensations()) {
        emailService.send(EmailTemplate.TERMINATION_TEAM_COMPENSATION,
            tc.getMember(),
            Map.of(
                "contractTitle", contract.getProject().getTitle(),
                "amount", tc.getAmount(),
                "newBalance", tc.getNewBalance(),
                "reason", result.getReason()
            )
        );
    }
    
    // 2. Thông báo cho Owner
    if (result.getOwnerCompensation().compareTo(BigDecimal.ZERO) > 0) {
        emailService.send(EmailTemplate.TERMINATION_OWNER_COMPENSATION,
            contract.getProject().getCreator(),
            Map.of(
                "contractTitle", contract.getProject().getTitle(),
                "compensationAmount", result.getOwnerCompensation(),
                "taxDeducted", result.getOwnerTax(),
                "netAmount", result.getOwnerNetReceive(),
                "newBalance", result.getOwnerNewBalance(),
                "hasSecondPayment", result.isAfterDay20(),
                "secondPaymentDate", result.getSecondPaymentDate()
            )
        );
    }
    
    // 3. Thông báo cho Client
    emailService.send(EmailTemplate.TERMINATION_CLIENT_REFUND,
        contract.getProject().getClient(),
        Map.of(
            "contractTitle", contract.getProject().getTitle(),
            "refundAmount", result.getClientRefund(),
            "newBalance", result.getClientNewBalance(),
            "hasSecondPayment", result.isAfterDay20(),
            "secondPaymentDate", result.getSecondPaymentDate()
        )
    );
}
```

### 9. Logging chi tiết

```java
@Slf4j
public class ContractTerminationService {
    
    private void logTerminationProcess(TerminationRequest request, 
                                      TerminationResult result) {
        log.info("=== CONTRACT TERMINATION SUMMARY ===");
        log.info("Contract ID: {}", request.getContractId());
        log.info("Terminated By: {}", request.getTerminatedBy());
        log.info("Termination Type: {}", result.getTerminationType());
        log.info("Payment Type: {}", result.getPaymentType());
        log.info("Termination Date: {}", result.getTerminationDate());
        log.info("");
        
        log.info("--- FINANCIAL SUMMARY ---");
        log.info("Total Contract Amount: {}", result.getTotalAmount());
        log.info("Team Compensation: {}", result.getTotalTeamCompensation());
        log.info("Owner Compensation: {}", result.getOwnerCompensation());
        log.info("Client Refund: {}", result.getClientRefund());
        log.info("Tax Deducted: {}", result.getTotalTax());
        log.info("");
        
        if (result.isAfterDay20()) {
            log.info("--- SECOND PAYMENT INFO ---");
            log.info("Scheduled Date: {}", result.getSecondPaymentDate());
            log.info("Refund Amount: {}", result.getTaxRefund());
            log.info("Recipient: {}", result.getTaxRefundRecipient());
        }
        
        log.info("=== END SUMMARY ===");
    }
}
```

### 10. API Endpoints

```java
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/termination")
public class ContractTerminationController {
    
    // 1. Tạo yêu cầu chấm dứt
    @PostMapping
    public ResponseEntity<TerminationResponse> createTermination(
        @PathVariable Long contractId,
        @RequestBody TerminationRequest request,
        Authentication auth
    ) {
        TerminationResult result = terminationService
            .terminateContract(contractId, request, auth);
        return ResponseEntity.ok(TerminationResponse.from(result));
    }
    
    // 2. Xem chi tiết chấm dứt
    @GetMapping
    public ResponseEntity<TerminationDetailResponse> getTerminationDetail(
        @PathVariable Long contractId,
        Authentication auth
    ) {
        ContractTermination termination = terminationService
            .getTerminationDetail(contractId, auth);
        return ResponseEntity.ok(TerminationDetailResponse.from(termination));
    }
    
    // 3. Tính toán trước (preview) - không thực hiện
    @PostMapping("/preview")
    public ResponseEntity<TerminationPreviewResponse> previewTermination(
        @PathVariable Long contractId,
        @RequestBody TerminationRequest request,
        Authentication auth
    ) {
        TerminationCalculation calc = terminationService
            .calculateTermination(contractId, request, auth);
        return ResponseEntity.ok(TerminationPreviewResponse.from(calc));
    }
    
    // 4. Kiểm tra điều kiện có thể chấm dứt
    @GetMapping("/eligibility")
    public ResponseEntity<TerminationEligibilityResponse> checkEligibility(
        @PathVariable Long contractId,
        Authentication auth
    ) {
        TerminationEligibility eligibility = terminationService
            .checkTerminationEligibility(contractId, auth);
        return ResponseEntity.ok(TerminationEligibilityResponse.from(eligibility));
    }
}
```

---

## VII. SƠ ĐỒ LUỒNG

### Sơ đồ tổng quan

```
START: Yêu cầu chấm dứt hợp đồng
│
├─> [1] Validation
│   ├─> Kiểm tra quyền (CLIENT hay OWNER?)
│   ├─> Kiểm tra trạng thái hợp đồng
│   ├─> Kiểm tra thời điểm (Trước/Sau ngày 20?)
│   └─> Kiểm tra loại hợp đồng (FULL/MILESTONE?)
│
├─> [2] Xác định scope
│   ├─> [FULL] Toàn bộ hợp đồng
│   └─> [MILESTONE] Xác định các milestone IN_PROGRESS
│
├─> [3] Kiểm tra điều kiện đặc biệt
│   └─> [Nếu OWNER chấm dứt]
│       ├─> Kiểm tra Owner có đủ balance đền bù Team?
│       ├─> [Không đủ] → Yêu cầu Owner nạp tiền → Dừng
│       └─> [Đủ] → Tiếp tục
│
├─> [4] Tính toán số tiền
│   ├─> Tính tiền đền bù Team (MilestoneMoneySplit APPROVED)
│   ├─> Tính tiền đền bù Owner (compensationPercentage)
│   ├─> Tính tiền hoàn Client
│   └─> Tính thuế
│       ├─> [Trước 20] 7% trên tiền thực nhận
│       └─> [Sau 20] Thuế gốc (7%) + Thuế hoàn lại
│
├─> [5] Xử lý giao dịch (Transaction)
│   │
│   ├─> [5.1] Xử lý Team Members (Ưu tiên 1)
│   │   ├─> [Owner chấm dứt] Owner trả từ balance
│   │   │   ├─> Trừ balance Owner
│   │   │   └─> Cộng balance Team Members
│   │   └─> [Client chấm dứt] Từ tiền hợp đồng
│   │       └─> Cộng balance Team Members
│   │
│   ├─> [5.2] Xử lý Owner (Ưu tiên 2)
│   │   ├─> [Client chấm dứt]
│   │   │   ├─> [Trước 20] Cộng (ĐB - 7%thuế - TeamSplit) vào balance
│   │   │   └─> [Sau 20] 
│   │   │       ├─> Lần 1: Cộng (ĐB - ThuếGốc - TeamSplit) vào balance
│   │   │       └─> Schedule lần 2: Cộng ThuếHoàn vào balance
│   │   └─> [Owner chấm dứt] Balance = 0
│   │
│   └─> [5.3] Xử lý Client (Ưu tiên 3)
│       ├─> [Client chấm dứt] Cộng (Total - ĐB) vào balance
│       └─> [Owner chấm dứt]
│           ├─> [Trước 20] Cộng 100% vào balance
│           └─> [Sau 20]
│               ├─> Lần 1: Cộng (Total - ThuếGốc) vào balance
│               └─> Schedule lần 2: Cộng ThuếGốc vào balance
│
├─> [6] Ghi nhận dữ liệu
│   ├─> Tạo TaxRecord
│   ├─> Tạo ContractTermination
│   ├─> Tạo BalanceTransaction cho mỗi người
│   └─> Cập nhật Contract.status = TERMINATED
│
├─> [7] Schedule thanh toán lần 2 (nếu sau ngày 20)
│   └─> Tạo job chạy vào ngày 20 tháng sau
│
├─> [8] Gửi notifications
│   ├─> Email cho Team Members
│   ├─> Email cho Owner
│   └─> Email cho Client
│
└─> END: Hoàn tất chấm dứt hợp đồng
```

### Sơ đồ chi tiết: Owner chấm dứt (có đền bù Team)

```
[OWNER chấm dứt hợp đồng]
│
├─> Bước 1: Kiểm tra balance Owner
│   ├─> Tính tổng đền bù Team
│   ├─> Owner.balance >= TổngĐềnBùTeam?
│   │   ├─> [Không] → Báo lỗi "Insufficient balance"
│   │   └─> [Có] → Tiếp tục
│
├─> Bước 2: Owner đền bù Team (Transaction 1)
│   ├─> Lock Owner record
│   ├─> Lock tất cả Team Member records
│   ├─> FOR EACH Team Member:
│   │   ├─> Trừ balance Owner
│   │   ├─> Cộng balance Team Member
│   │   ├─> Tạo BalanceTransaction (OWNER_COMPENSATE_TEAM)
│   │   └─> Gửi notification cho Member
│   └─> Commit Transaction 1
│
├─> Bước 3: Hoàn tiền Client (Transaction 2)
│   ├─> Lock Client record
│   ├─> [Trước 20]
│   │   ├─> Cộng 100% vào Client.balance
│   │   └─> Tạo BalanceTransaction (TERMINATION_FULL_REFUND)
│   ├─> [Sau 20]
│   │   ├─> Lần 1: Cộng (Total - ThuếGốc) vào Client.balance
│   │   ├─> Tạo BalanceTransaction (TERMINATION_REFUND_ROUND_1)
│   │   └─> Schedule lần 2: Cộng ThuếGốc (ngày 20 tháng sau)
│   └─> Commit Transaction 2
│
├─> Bước 4: Ghi nhận thuế
│   ├─> Tạo TaxRecord (thuế = 0 hoặc hoàn 100%)
│   └─> [Sau 20] Tạo schedule hoàn thuế
│
├─> Bước 5: Cập nhật Contract
│   ├─> Contract.status = TERMINATED
│   ├─> Contract.terminatedBy = OWNER
│   └─> Contract.terminatedAt = now()
│
└─> Bước 6: Notifications
    ├─> Email Team: "Đã nhận đền bù"
    ├─> Email Owner: "Đã chấm dứt, đền bù Team thành công"
    └─> Email Client: "Hợp đồng đã chấm dứt, tiền hoàn đã vào balance"
```

### Sơ đồ: Thanh toán lần 2 (Sau ngày 20)

```
[Scheduler chạy vào 10:00 ngày 20 hàng tháng]
│
├─> Query TaxRecords
│   └─> WHERE status = WAITING_REFUND
│       AND refundScheduledDate = TODAY
│
├─> FOR EACH TaxRecord:
│   │
│   ├─> Lấy thông tin
│   │   ├─> Contract
│   │   ├─> Owner
│   │   ├─> Client
│   │   └─> Số tiền hoàn (refundedTax)
│   │
│   ├─> Xác định người nhận
│   │   ├─> [terminatedBy = CLIENT] → Owner nhận
│   │   └─> [terminatedBy = OWNER] → Client nhận
│   │
│   ├─> Cộng vào balance (Transaction)
│   │   ├─> Lock user record
│   │   ├─> Cộng refundedTax vào balance
│   │   ├─> Tạo BalanceTransaction (TAX_REFUND)
│   │   └─> Commit
│   │
│   ├─> Cập nhật TaxRecord
│   │   ├─> status = REFUNDED
│   │   └─> refundCompletedDate = TODAY
│   │
│   └─> Gửi notification
│       └─> Email: "Thuế đã được hoàn, balance cập nhật"
│
└─> Ghi log summary
    └─> "Processed X tax refunds, total amount: Y"
```

### Sơ đồ: User rút tiền

```
[User tạo yêu cầu rút tiền]
│
├─> Validation
│   ├─> User.balance >= withdrawal amount?
│   ├─> Minimum withdrawal amount?
│   └─> Bank account verified?
│
├─> KHÔNG TRỪ THUẾ
│   └─> Balance đã là số tiền sau thuế (khấu trừ tại nguồn)
│
├─> Xử lý balance (Transaction)
│   ├─> Lock User record
│   ├─> Trừ balance: balance = balance - amount
│   ├─> Tạo BalanceTransaction (WITHDRAWAL)
│   └─> Commit
│
├─> Tạo lệnh chuyển tiền PayOS
│   ├─> Recipient: User's bank account
│   ├─> Amount: FULL amount (KHÔNG trừ thuế)
│   └─> Reference: WithdrawalId
│
├─> Notification
│   └─> Email: "Lệnh rút tiền đã tạo, số tiền X đ
│       (Không trừ thêm thuế - đã khấu trừ tại nguồn)"
│
└─> [Webhook từ PayOS]
    ├─> SUCCESS → Cập nhật status = COMPLETED
    └─> FAILED → Hoàn lại balance, cập nhật status = FAILED
```

**Lợi ích của khấu trừ tại nguồn:**
- ✅ User biết chính xác số tiền mình có (không bất ngờ khi rút)
- ✅ Không cần tính thuế phức tạp khi rút tiền
- ✅ Đơn giản cho hệ thống
- ✅ Tuân thủ quy định khấu trừ thuế TNCN

---

## VIII. CÔNG THỨC TÍNH TOÁN CHI TIẾT

### Trước ngày 20

#### CLIENT chấm dứt:
```java
// Input
BigDecimal totalAmount = contract.getTotalAmount();
BigDecimal compensationPercent = contract.getCompensationPercentage();
BigDecimal teamSplitTotal = calculateTeamSplit();

// Calculate
BigDecimal ownerCompensation = totalAmount.multiply(compensationPercent)
    .divide(new BigDecimal("100"));
BigDecimal ownerActualReceive = ownerCompensation.subtract(teamSplitTotal);
BigDecimal ownerTax = ownerActualReceive.multiply(new BigDecimal("0.07"));
BigDecimal ownerNet = ownerActualReceive.subtract(ownerTax);
BigDecimal clientRefund = totalAmount.subtract(ownerCompensation);

// Output
owner.balance += ownerNet;
client.balance += clientRefund;
team[i].balance += teamSplit[i];
```

#### OWNER chấm dứt:
```java
// Input
BigDecimal totalAmount = contract.getTotalAmount();
BigDecimal teamSplitTotal = calculateTeamSplit();

// Calculate & Validate
if (owner.getBalance().compareTo(teamSplitTotal) < 0) {
    throw new InsufficientBalanceException();
}

// Output
owner.balance -= teamSplitTotal;  // Owner trả Team
team[i].balance += teamSplit[i];
client.balance += totalAmount;     // Client nhận 100%
```

### Sau ngày 20

#### CLIENT chấm dứt:
```java
// Input
BigDecimal totalAmount = contract.getTotalAmount();
BigDecimal taxOriginal = contract.getPitTax().add(contract.getVatTax());
BigDecimal compensationPercent = contract.getCompensationPercentage();
BigDecimal teamSplitTotal = calculateTeamSplit();

// Calculate
BigDecimal ownerCompensation = totalAmount.multiply(compensationPercent)
    .divide(new BigDecimal("100"));
BigDecimal ownerActualReceive = ownerCompensation.subtract(teamSplitTotal);
BigDecimal taxActual = ownerActualReceive.multiply(new BigDecimal("0.07"));
BigDecimal taxRefund = taxOriginal.subtract(taxActual);

// Round 1
BigDecimal ownerRound1 = ownerCompensation
    .subtract(taxOriginal)
    .subtract(teamSplitTotal);
BigDecimal clientRefund = totalAmount.subtract(ownerCompensation);

owner.balance += ownerRound1;
client.balance += clientRefund;
team[i].balance += teamSplit[i];

// Round 2 (scheduled)
owner.balance += taxRefund;  // Ngày 20 tháng sau
```

#### OWNER chấm dứt:
```java
// Input
BigDecimal totalAmount = contract.getTotalAmount();
BigDecimal taxOriginal = contract.getPitTax().add(contract.getVatTax());
BigDecimal teamSplitTotal = calculateTeamSplit();

// Validate
if (owner.getBalance().compareTo(teamSplitTotal) < 0) {
    throw new InsufficientBalanceException();
}

// Owner compensate Team first
owner.balance -= teamSplitTotal;
team[i].balance += teamSplit[i];

// Round 1
BigDecimal clientRound1 = totalAmount.subtract(taxOriginal);
client.balance += clientRound1;

// Round 2 (scheduled)
BigDecimal taxRefund = taxOriginal;  // 100% vì Owner không nhận gì
client.balance += taxRefund;  // Ngày 20 tháng sau
```

---

## IX. CHECKLIST TESTING

### Test Cases phải cover

#### 1. FULL - CLIENT chấm dứt - Trước 20
```
✓ Tính toán đúng số tiền Team, Owner, Client
✓ Thuế 7% được tính đúng
✓ Balance cập nhật đúng cho cả 3 bên
✓ Transaction logs đầy đủ
✓ Notifications gửi đúng
✓ Contract status = TERMINATED
```

#### 2. FULL - OWNER chấm dứt - Trước 20
```
✓ Validate Owner có đủ balance
✓ Owner trả đủ cho Team
✓ Client nhận 100%
✓ Thuế = 0
✓ Rollback nếu Owner không đủ balance
```

#### 3. MILESTONE - CLIENT chấm dứt - Trước 20
```
✓ Chỉ xử lý milestones IN_PROGRESS
✓ PENDING và COMPLETED không bị ảnh hưởng
✓ Team chỉ nhận từ milestones IN_PROGRESS
✓ Tính toán đúng
```

#### 4. MILESTONE - OWNER chấm dứt - Trước 20
```
✓ Validate Owner balance
✓ Chỉ xử lý milestones IN_PROGRESS
✓ Client nhận 100% milestones IN_PROGRESS
```

#### 5. Sau ngày 20 - Tất cả trường hợp
```
✓ Lần 1: Trừ đúng thuế gốc
✓ Schedule lần 2 được tạo
✓ Lần 2: Thuế hoàn được tính đúng
✓ Người nhận đúng (Owner hoặc Client)
```

#### 6. Edge Cases
```
✓ Contract đã TERMINATED → Báo lỗi
✓ Owner không đủ balance → Báo lỗi, rollback
✓ Không có Team splits → Xử lý bình thường
✓ CompensationPercentage = 0 → Client nhận 100%
✓ CompensationPercentage = 100 → Client nhận 0
✓ Concurrent termination → Lock đúng
```

#### 7. Performance & Scalability
```
✓ Xử lý nhiều Team Members (>100)
✓ Transaction không bị timeout
✓ Database locks không gây deadlock
```

---

## X. MIGRATION PLAN

### Phase 1: Tạo tables mới
```sql
-- 1. Tax Records
CREATE TABLE tax_records (
    id BIGINT PRIMARY KEY,
    contract_id BIGINT REFERENCES contracts(id),
    termination_type VARCHAR(20),
    terminated_by VARCHAR(10),
    payment_type VARCHAR(20),
    termination_date DATE,
    original_tax DECIMAL(15,2),
    actual_tax DECIMAL(15,2),
    refunded_tax DECIMAL(15,2),
    owner_actual_receive DECIMAL(15,2),
    team_compensation DECIMAL(15,2),
    owner_receive_round_1 DECIMAL(15,2),
    owner_receive_round_2 DECIMAL(15,2),
    refund_scheduled_date DATE,
    refund_completed_date DATE,
    status VARCHAR(20),
    notes TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 2. Balance Transactions
CREATE TABLE balance_transactions (
    id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    contract_id BIGINT REFERENCES contracts(id),
    type VARCHAR(50),
    amount DECIMAL(15,2),
    balance_before DECIMAL(15,2),
    balance_after DECIMAL(15,2),
    status VARCHAR(20),
    description TEXT,
    reference_id VARCHAR(100),
    created_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- 3. Contract Terminations
CREATE TABLE contract_terminations (
    id BIGINT PRIMARY KEY,
    contract_id BIGINT REFERENCES contracts(id) UNIQUE,
    terminated_by VARCHAR(10),
    termination_type VARCHAR(20),
    termination_date TIMESTAMP,
    total_contract_amount DECIMAL(15,2),
    total_team_compensation DECIMAL(15,2),
    total_owner_compensation DECIMAL(15,2),
    total_client_refund DECIMAL(15,2),
    total_tax_deducted DECIMAL(15,2),
    status VARCHAR(20),
    notes TEXT,
    reason TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 4. User Tax Records (for withdrawals)
CREATE TABLE user_tax_records (
    id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    amount DECIMAL(15,2),
    tax_amount DECIMAL(15,2),
    net_amount DECIMAL(15,2),
    tax_type VARCHAR(50),
    tax_rate DECIMAL(5,2),
    withdrawal_date DATE,
    created_at TIMESTAMP
);

-- Indexes
CREATE INDEX idx_tax_records_contract ON tax_records(contract_id);
CREATE INDEX idx_tax_records_status_date ON tax_records(status, refund_scheduled_date);
CREATE INDEX idx_balance_tx_user ON balance_transactions(user_id);
CREATE INDEX idx_balance_tx_contract ON balance_transactions(contract_id);
CREATE INDEX idx_terminations_contract ON contract_terminations(contract_id);
CREATE INDEX idx_user_tax_user_date ON user_tax_records(user_id, withdrawal_date);
```

### Phase 2: Thêm cột mới vào Contract
```sql
ALTER TABLE contracts ADD COLUMN terminated_at TIMESTAMP;
ALTER TABLE contracts ADD COLUMN terminated_by VARCHAR(10);
ALTER TABLE contracts ADD COLUMN termination_reason TEXT;
```

### Phase 3: Thêm status mới
```java
// ContractStatus enum
public enum ContractStatus {
    // ... existing statuses
    TERMINATED
}
```

### Phase 4: Deploy code
1. Deploy entities, repositories
2. Deploy services
3. Deploy controllers
4. Deploy scheduler jobs

### Phase 5: Testing
1. Unit tests
2. Integration tests
3. End-to-end tests
4. UAT (User Acceptance Testing)

---

## XI. FAQ & TROUBLESHOOTING

### Q1: Nếu Owner không đủ balance để đền bù Team khi chấm dứt thì sao?
**A:** Hệ thống sẽ báo lỗi `InsufficientBalanceException` và yêu cầu Owner nạp thêm tiền. Transaction sẽ không được thực hiện.

### Q2: Team Member có thể từ chối nhận đền bù không?
**A:** Không. Khi hợp đồng bị chấm dứt, tiền sẽ tự động vào balance. Team Member có thể chọn không rút tiền ra nếu muốn.

### Q3: Nếu lần thanh toán thứ 2 (hoàn thuế) bị lỗi thì sao?
**A:** 
- TaxRecord vẫn giữ status = `WAITING_REFUND`
- Scheduler sẽ retry vào tháng sau
- Admin có thể trigger manual refund
- Log chi tiết để debug

### Q4: Có thể chấm dứt một phần hợp đồng không (chỉ 1 milestone)?
**A:** Không. Chấm dứt hợp đồng là chấm dứt toàn bộ. Với MILESTONE payment, chỉ xử lý các milestone IN_PROGRESS, các milestone PENDING sẽ không bị ảnh hưởng nhưng cũng không thể thực hiện thêm.

### Q5: Nếu Client và Owner cùng yêu cầu chấm dứt cùng lúc?
**A:** Database lock sẽ đảm bảo chỉ một request được xử lý. Request sau sẽ thấy contract đã TERMINATED và báo lỗi.

### Q6: Thuế 7% có áp dụng cho Team Members không?
**A:** Có. Team Members bị khấu trừ 7% tại nguồn khi nhận đền bù vào balance. Khi rút tiền ra không trừ thêm thuế nữa.

### Q7: Làm sao biết được thuế đã kê khai với chi cục thuế chưa?
**A:** Dựa vào ngày chấm dứt:
- Trước ngày 20 → Chưa kê khai
- Từ ngày 20 trở đi → Đã kê khai

### Q8: Có thể hoàn tác (undo) chấm dứt hợp đồng không?
**A:** Không. Chấm dứt là permanent. Cần cân nhắc kỹ trước khi thực hiện.

### Q9: Balance có thể âm không?
**A:** Không. Hệ thống validate balance >= 0 trước mọi giao dịch trừ tiền.

### Q10: Nếu PayOS webhook không về (khi rút tiền)?
**A:** 
- Có cronjob kiểm tra các withdrawal pending quá lâu
- Admin có thể query trực tiếp PayOS API
- Có thể retry webhook manually

---

## XII. PHỤ LỤC

### A. Enums cần thiết

```java
public enum TerminationType {
    BEFORE_DAY_20,  // Trước ngày 20
    AFTER_DAY_20    // Sau ngày 20
}

public enum TerminatedBy {
    CLIENT,
    OWNER
}

public enum TaxStatus {
    COMPLETED,       // Hoàn tất (trước 20 hoặc lần 1 sau 20)
    WAITING_REFUND,  // Chờ hoàn thuế (lần 2 sau 20)
    REFUNDED        // Đã hoàn thuế
}

public enum TransactionType {
    // Termination related
    TERMINATION_TEAM_COMPENSATION,
    TERMINATION_OWNER_COMPENSATION,
    TERMINATION_CLIENT_REFUND,
    OWNER_COMPENSATE_TEAM,
    TAX_REFUND,
    
    // Withdrawal related
    WITHDRAWAL,
    
    // Other
    PAYMENT,
    REFUND
}

public enum TerminationStatus {
    PROCESSING,         // Đang xử lý
    COMPLETED,          // Hoàn tất
    PARTIAL_COMPLETED,  // Hoàn tất một phần (chờ lần 2)
    FAILED             // Thất bại
}
```

### B. DTOs Sample

```java
@Data
public class TerminationRequest {
    private TerminatedBy terminatedBy;
    private String reason;
}

@Data
public class TerminationResponse {
    private Long terminationId;
    private ContractStatus newStatus;
    private TerminationType terminationType;
    private BigDecimal teamCompensation;
    private BigDecimal ownerCompensation;
    private BigDecimal clientRefund;
    private BigDecimal taxDeducted;
    private Boolean hasSecondPayment;
    private LocalDate secondPaymentDate;
    private String message;
}

@Data
public class TerminationPreviewResponse {
    private BigDecimal totalAmount;
    private BigDecimal teamWillReceive;
    private BigDecimal ownerWillReceive;
    private BigDecimal clientWillReceive;
    private BigDecimal taxDeducted;
    private Boolean requiresOwnerBalance;
    private BigDecimal requiredOwnerBalance;
    private Boolean hasTwoPayments;
    private Map<String, BigDecimal> breakdown;
}
```

---

## XIII. QUẢN LÝ THUẾ VÀ KÊ KHAI

> **Mục đích:** Hệ thống cần thu thập và lưu trữ đầy đủ thông tin để kê khai và nộp thuế thay cho người dùng theo quy định pháp luật Việt Nam.

### 🎯 ĐIỂM QUAN TRỌNG

**Từ 01/07/2021, tại Việt Nam:**
```
✅ Số CCCD 12 số CHÍNH LÀ mã số thuế cá nhân
✅ Không cần đăng ký mã số thuế (MST) riêng
✅ Chỉ cần xác thực CCCD là ĐỦ để khai báo thuế
✅ Hệ thống sử dụng CCCD làm định danh duy nhất
```

**Yêu cầu với người dùng:**
- ✅ Xác thực CCCD qua eKYC (bắt buộc)
- ✅ Thông tin trên CCCD phải khớp với tài khoản ngân hàng
- ❌ KHÔNG cần cung cấp MST riêng

---

### A. THÔNG TIN ĐỊNH DANH NGƯỜI DÙNG

#### 1. Thông tin Căn cước công dân (CCCD)

**⚠️ CẬP NHẬT:** Thông tin CCCD đã được lưu trực tiếp trong `User` entity (không còn sử dụng `UserIdentityVerification`)

Hệ thống xác thực và lưu trữ thông tin CCCD trong User entity để khai báo thuế hợp pháp:

```java
@Entity
@Table(name = "users")
public class User extends AbstractEntity<Long> implements UserDetails {
    // ... existing fields ...
    
    // === THÔNG TIN CCCD ===
    @Column(name = "cccd_number", unique = true)
    private String cccdNumber; // Số CCCD 12 số
    
    @Column(name = "cccd_full_name")
    private String cccdFullName; // Họ và tên (theo CCCD)
    
    @Column(name = "cccd_birth_day")
    private String cccdBirthDay; // Ngày sinh
    
    @Column(name = "cccd_gender")
    private String cccdGender; // Giới tính
    
    @Column(name = "cccd_origin_location")
    private String cccdOriginLocation; // Quê quán
    
    @Column(name = "cccd_recent_location")
    private String cccdRecentLocation; // Nơi thường trú
    
    @Column(name = "cccd_issue_date")
    private String cccdIssueDate; // Ngày cấp
    
    @Column(name = "cccd_issue_place")
    private String cccdIssuePlace; // Nơi cấp
    
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false; // Trạng thái xác thực
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt; // Thời gian xác thực
    
    @Column(name = "cccd_front_image_url", length = 1000)
    private String cccdFrontImageUrl;
    
    @Column(name = "cccd_back_image_url", length = 1000)
    private String cccdBackImageUrl;
    
    // === THÔNG TIN THUẾ ===
    // Lưu ý: Từ 01/07/2021, số CCCD 12 số CHÍNH LÀ mã số thuế cá nhân
    // Không cần đăng ký MST riêng nữa
    @Column(name = "tax_code", length = 13)
    private String taxCode; // Mã số thuế = CCCD (hoặc MST cũ nếu đã có trước đó)
    
    @Column(name = "tax_department")
    private String taxDepartment; // Chi cục thuế quản lý (theo nơi thường trú)
    
    @Column(name = "bank_name")
    private String bankName; // Tên ngân hàng
    
    @Column(name = "bank_branch")
    private String bankBranch; // Chi nhánh
    
    @Column(name = "bank_account_holder")
    private String bankAccountHolder; // Chủ tài khoản (phải trùng CCCD)
    
    // === XÁC THỰC ===
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus; 
    // PENDING, VERIFIED, REJECTED, EXPIRED
    
    @Column(name = "verification_method")
    private String verificationMethod; // eKYC, Manual, etc.
    
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;
    
    @Column(name = "verified_by")
    private String verifiedBy; // Admin ID hoặc System
    
    // === LƯU TRỮ ẢNH ===
    @Column(name = "cccd_front_image_url")
    private String cccdFrontImageUrl; // Ảnh mặt trước CCCD
    
    @Column(name = "cccd_back_image_url")
    private String cccdBackImageUrl; // Ảnh mặt sau CCCD
    
    @Column(name = "selfie_image_url")
    private String selfieImageUrl; // Ảnh chân dung
    
    // === METADATA ===
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 2. Validation Rules

```java
// Quy tắc xác thực CCCD
- CCCD phải có đúng 12 số
- Ngày sinh hợp lệ (từ 18 tuổi trở lên)
- CCCD chưa hết hạn
- Tên trên CCCD phải khớp với tên tài khoản ngân hàng
- Một CCCD chỉ được đăng ký một tài khoản
- Ảnh CCCD phải rõ ràng, đọc được thông tin
- Ảnh selfie phải khớp với ảnh trên CCCD (face matching)
```

#### 3. CCCD = Mã số thuế cá nhân

**Theo quy định từ 01/07/2021:**
```
✓ Số CCCD 12 số CHÍNH LÀ mã số thuế cá nhân
✓ Không cần đăng ký MST riêng
✓ Cơ quan thuế sử dụng số CCCD để tra cứu và quản lý
✓ Khi khai báo thuế: sử dụng số CCCD thay cho MST
✓ Nếu có MST cũ (trước 2021): vẫn có thể dùng song song
```

**Logic trong hệ thống:**
```java
// Khi xác thực CCCD qua eKYC (trong User entity)
if (user.getTaxCode() == null || user.getTaxCode().isEmpty()) {
    // Tự động set taxCode = CCCD
    user.setTaxCode(user.getCccdNumber());
}

// Khi khai báo thuế, ưu tiên dùng taxCode, fallback về CCCD
String taxIdentifier = user.getTaxCode() != null 
    ? user.getTaxCode() 
    : user.getCccdNumber();
```

---

### B. THỐNG KÊ THEO GIAO DỊCH (Transaction Level)

#### 1. Ghi nhận từng lần giải ngân (Payout Record)

Mỗi lần user nhận tiền (vào balance hoặc rút tiền) phải được ghi nhận đầy đủ:

```java
@Entity
@Table(name = "tax_payout_records")
public class TaxPayoutRecord {
    @Id
    private Long id;
    
    // === NGƯỜI NHẬN ===
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "user_cccd")
    private String userCccd; // Denormalize để báo cáo nhanh
    
    @Column(name = "user_tax_code")
    private String userTaxCode;
    
    @Column(name = "user_full_name")
    private String userFullName;
    
    // === NGUỒN TIỀN ===
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_source", nullable = false)
    private PayoutSource payoutSource;
    // MILESTONE_PAYMENT           - Thanh toán milestone
    // TERMINATION_COMPENSATION    - Đền bù khi chấm dứt
    // TAX_REFUND                  - Hoàn thuế
    // OTHER_INCOME                - Thu nhập khác
    
    @ManyToOne
    @JoinColumn(name = "contract_id")
    private Contract contract; // Contract liên quan (nếu có)
    
    @ManyToOne
    @JoinColumn(name = "milestone_id")
    private Milestone milestone; // Milestone liên quan (nếu có)
    
    @Column(name = "termination_id")
    private Long terminationId; // ContractTermination ID (nếu có)
    
    // === SỐ TIỀN ===
    @Column(name = "gross_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal grossAmount; // Số tiền trước thuế
    
    @Column(name = "tax_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal taxAmount; // Thuế đã khấu trừ (7%)
    
    @Column(name = "net_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal netAmount; // Số tiền sau thuế (thực nhận)
    
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate; // Thuế suất (0.07 = 7%)
    
    // === LOẠI THUẾ ===
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type")
    private TaxType taxType;
    // PERSONAL_INCOME_TAX  - Thuế TNCN
    // VAT                  - Thuế VAT
    // PIT                  - Thuế PIT
    
    @Column(name = "tax_category")
    private String taxCategory; // Loại thu nhập: "Tiền công, tiền lương"
    
    // === THỜI GIAN ===
    @Column(name = "payout_date", nullable = false)
    private LocalDate payoutDate; // Ngày giải ngân
    
    @Column(name = "tax_period_month")
    private Integer taxPeriodMonth; // Tháng kỳ thuế (1-12)
    
    @Column(name = "tax_period_year")
    private Integer taxPeriodYear; // Năm kỳ thuế
    
    @Column(name = "tax_period_quarter")
    private Integer taxPeriodQuarter; // Quý (1-4)
    
    // === CHI TIẾT ===
    @Enumerated(EnumType.STRING)
    @Column(name = "payout_method")
    private PayoutMethod payoutMethod;
    // TO_BALANCE   - Cộng vào balance
    // TO_BANK      - Rút về ngân hàng
    
    @Column(name = "withdrawal_id")
    private Long withdrawalId; // ID của withdrawal request (nếu rút tiền)
    
    @Column(name = "balance_transaction_id")
    private Long balanceTransactionId; // ID của BalanceTransaction
    
    @Column(name = "reference_code")
    private String referenceCode; // Mã tham chiếu giao dịch
    
    // === TRẠNG THÁI ===
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PayoutStatus status;
    // PENDING, COMPLETED, FAILED, REVERSED
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    // === KÊ KHAI THUẾ ===
    @Column(name = "is_tax_declared")
    private Boolean isTaxDeclared = false; // Đã kê khai chưa?
    
    @Column(name = "tax_declaration_date")
    private LocalDate taxDeclarationDate; // Ngày kê khai
    
    @Column(name = "tax_declaration_id")
    private Long taxDeclarationId; // ID của tờ khai thuế
    
    @Column(name = "tax_paid")
    private Boolean taxPaid = false; // Đã nộp thuế chưa?
    
    @Column(name = "tax_payment_date")
    private LocalDate taxPaymentDate; // Ngày nộp thuế
    
    // === GHI CHÚ ===
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 2. Tự động tạo TaxPayoutRecord

Mỗi khi có giao dịch liên quan đến thu nhập, hệ thống tự động tạo record:

```java
// Khi cộng tiền vào balance (từ milestone, termination, etc.)
private void createPayoutRecord(
    User user, 
    BigDecimal amount, 
    PayoutSource source,
    Contract contract,
    Milestone milestone
) {
    // Tính thuế 7%
    BigDecimal taxRate = new BigDecimal("0.02");
    BigDecimal taxAmount = amount.multiply(taxRate);
    BigDecimal netAmount = amount.subtract(taxAmount);
    
    // Kiểm tra user đã xác thực CCCD chưa
    if (!Boolean.TRUE.equals(user.getIsVerified()) || user.getCccdNumber() == null) {
        throw new AppException("User must verify CCCD before tax calculation");
    }
    
    // Xác định mã số thuế (CCCD chính là MST từ 2021)
    String taxCode = identity.getTaxCode() != null 
        ? identity.getTaxCode() 
        : identity.getCccdNumber();
    
    // Xác định kỳ thuế
    LocalDate now = LocalDate.now();
    int month = now.getMonthValue();
    int year = now.getYear();
    int quarter = (month - 1) / 3 + 1;
    
    // Tạo record
    TaxPayoutRecord record = TaxPayoutRecord.builder()
        .user(user)
        .userCccd(identity.getCccdNumber())
        .userTaxCode(taxCode) // Ưu tiên taxCode, fallback về CCCD
        .userFullName(identity.getFullName())
        .payoutSource(source)
        .contract(contract)
        .milestone(milestone)
        .grossAmount(amount)
        .taxAmount(taxAmount)
        .netAmount(netAmount)
        .taxRate(taxRate)
        .taxType(TaxType.PERSONAL_INCOME_TAX)
        .taxCategory("Tiền công, tiền lương")
        .payoutDate(now)
        .taxPeriodMonth(month)
        .taxPeriodYear(year)
        .taxPeriodQuarter(quarter)
        .payoutMethod(PayoutMethod.TO_BALANCE)
        .status(PayoutStatus.COMPLETED)
        .completedAt(LocalDateTime.now())
        .isTaxDeclared(false)
        .taxPaid(false)
        .build();
    
    taxPayoutRecordRepository.save(record);
}
```

---

### C. THỐNG KÊ TỔNG HỢP (Aggregation Level)

#### 1. Báo cáo thuế theo User (User Tax Summary)

Tổng hợp thu nhập và thuế của từng user theo kỳ:

```java
@Entity
@Table(name = "user_tax_summaries")
public class UserTaxSummary {
    @Id
    private Long id;
    
    // === NGƯỜI NỘP THUẾ ===
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "user_cccd")
    private String userCccd;
    
    @Column(name = "user_tax_code")
    private String userTaxCode;
    
    @Column(name = "user_full_name")
    private String userFullName;
    
    // === KỲ THUẾ ===
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_period_type")
    private TaxPeriodType taxPeriodType; 
    // MONTHLY, QUARTERLY, YEARLY
    
    @Column(name = "tax_period_year")
    private Integer taxPeriodYear;
    
    @Column(name = "tax_period_month")
    private Integer taxPeriodMonth; // Nếu monthly
    
    @Column(name = "tax_period_quarter")
    private Integer taxPeriodQuarter; // Nếu quarterly
    
    @Column(name = "period_start_date")
    private LocalDate periodStartDate;
    
    @Column(name = "period_end_date")
    private LocalDate periodEndDate;
    
    // === TỔNG HỢP THU NHẬP ===
    @Column(name = "total_gross_income", precision = 15, scale = 2)
    private BigDecimal totalGrossIncome; // Tổng thu nhập trước thuế
    
    @Column(name = "total_taxable_income", precision = 15, scale = 2)
    private BigDecimal totalTaxableIncome; // Thu nhập chịu thuế
    
    @Column(name = "total_non_taxable_income", precision = 15, scale = 2)
    private BigDecimal totalNonTaxableIncome; // Thu nhập không chịu thuế
    
    // === PHÂN LOẠI THU NHẬP ===
    @Column(name = "income_from_milestone", precision = 15, scale = 2)
    private BigDecimal incomeFromMilestone; // Từ milestone payment
    
    @Column(name = "income_from_termination", precision = 15, scale = 2)
    private BigDecimal incomeFromTermination; // Từ đền bù chấm dứt
    
    @Column(name = "income_from_refund", precision = 15, scale = 2)
    private BigDecimal incomeFromRefund; // Từ hoàn thuế
    
    @Column(name = "income_from_other", precision = 15, scale = 2)
    private BigDecimal incomeFromOther; // Thu nhập khác
    
    // === THUẾ ===
    @Column(name = "total_tax_withheld", precision = 15, scale = 2)
    private BigDecimal totalTaxWithheld; // Tổng thuế đã khấu trừ
    
    @Column(name = "total_tax_paid", precision = 15, scale = 2)
    private BigDecimal totalTaxPaid; // Tổng thuế đã nộp
    
    @Column(name = "total_tax_refunded", precision = 15, scale = 2)
    private BigDecimal totalTaxRefunded; // Tổng thuế được hoàn
    
    @Column(name = "total_tax_due", precision = 15, scale = 2)
    private BigDecimal totalTaxDue; // Thuế còn phải nộp
    
    @Column(name = "effective_tax_rate", precision = 5, scale = 4)
    private BigDecimal effectiveTaxRate; // Thuế suất thực tế
    
    // === SỐ LƯỢNG GIAO DỊCH ===
    @Column(name = "total_payout_count")
    private Integer totalPayoutCount; // Tổng số lần giải ngân
    
    @Column(name = "total_contract_count")
    private Integer totalContractCount; // Số hợp đồng liên quan
    
    @Column(name = "total_withdrawal_count")
    private Integer totalWithdrawalCount; // Số lần rút tiền
    
    // === TRẠNG THÁI ===
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TaxSummaryStatus status;
    // DRAFT, FINALIZED, DECLARED, PAID
    
    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;
    
    @Column(name = "declared_at")
    private LocalDateTime declaredAt;
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    // === GHI CHÚ ===
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 2. Tự động tính toán summary

```java
@Service
public class TaxSummaryService {
    
    /**
     * Tính toán summary cho user trong tháng/quý/năm
     */
    @Transactional
    public UserTaxSummary calculateUserTaxSummary(
        Long userId, 
        TaxPeriodType periodType,
        int year,
        Integer monthOrQuarter
    ) {
        User user = userRepository.findById(userId).orElseThrow();
        
        // Kiểm tra user đã xác thực CCCD chưa
        if (!Boolean.TRUE.equals(user.getIsVerified()) || user.getCccdNumber() == null) {
            throw new AppException("User must verify CCCD before tax summary");
        }
        
        // Xác định khoảng thời gian
        LocalDate startDate, endDate;
        if (periodType == TaxPeriodType.MONTHLY) {
            startDate = LocalDate.of(year, monthOrQuarter, 1);
            endDate = startDate.plusMonths(1).minusDays(1);
        } else if (periodType == TaxPeriodType.QUARTERLY) {
            int startMonth = (monthOrQuarter - 1) * 3 + 1;
            startDate = LocalDate.of(year, startMonth, 1);
            endDate = startDate.plusMonths(3).minusDays(1);
        } else { // YEARLY
            startDate = LocalDate.of(year, 1, 1);
            endDate = LocalDate.of(year, 12, 31);
        }
        
        // Lấy tất cả payout records trong kỳ
        List<TaxPayoutRecord> records = taxPayoutRecordRepository
            .findByUserIdAndPayoutDateBetween(userId, startDate, endDate);
        
        // Tính tổng
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal incomeFromMilestone = BigDecimal.ZERO;
        BigDecimal incomeFromTermination = BigDecimal.ZERO;
        BigDecimal incomeFromRefund = BigDecimal.ZERO;
        BigDecimal incomeFromOther = BigDecimal.ZERO;
        
        Set<Long> contractIds = new HashSet<>();
        int withdrawalCount = 0;
        
        for (TaxPayoutRecord record : records) {
            totalGross = totalGross.add(record.getGrossAmount());
            totalTax = totalTax.add(record.getTaxAmount());
            
            switch (record.getPayoutSource()) {
                case MILESTONE_PAYMENT:
                    incomeFromMilestone = incomeFromMilestone
                        .add(record.getGrossAmount());
                    break;
                case TERMINATION_COMPENSATION:
                    incomeFromTermination = incomeFromTermination
                        .add(record.getGrossAmount());
                    break;
                case TAX_REFUND:
                    incomeFromRefund = incomeFromRefund
                        .add(record.getGrossAmount());
                    break;
                default:
                    incomeFromOther = incomeFromOther
                        .add(record.getGrossAmount());
            }
            
            if (record.getContract() != null) {
                contractIds.add(record.getContract().getId());
            }
            
            if (record.getWithdrawalId() != null) {
                withdrawalCount++;
            }
        }
        
        // Tính thuế suất thực tế
        BigDecimal effectiveRate = BigDecimal.ZERO;
        if (totalGross.compareTo(BigDecimal.ZERO) > 0) {
            effectiveRate = totalTax.divide(totalGross, 4, RoundingMode.HALF_UP);
        }
        
        // Tạo hoặc cập nhật summary
        UserTaxSummary summary = UserTaxSummary.builder()
            .user(user)
            .userCccd(identity.getCccdNumber())
            .userTaxCode(identity.getTaxCode())
            .userFullName(identity.getFullName())
            .taxPeriodType(periodType)
            .taxPeriodYear(year)
            .taxPeriodMonth(periodType == TaxPeriodType.MONTHLY ? monthOrQuarter : null)
            .taxPeriodQuarter(periodType == TaxPeriodType.QUARTERLY ? monthOrQuarter : null)
            .periodStartDate(startDate)
            .periodEndDate(endDate)
            .totalGrossIncome(totalGross)
            .totalTaxableIncome(totalGross)
            .totalNonTaxableIncome(BigDecimal.ZERO)
            .incomeFromMilestone(incomeFromMilestone)
            .incomeFromTermination(incomeFromTermination)
            .incomeFromRefund(incomeFromRefund)
            .incomeFromOther(incomeFromOther)
            .totalTaxWithheld(totalTax)
            .totalTaxPaid(totalTax)
            .totalTaxRefunded(BigDecimal.ZERO)
            .totalTaxDue(BigDecimal.ZERO)
            .effectiveTaxRate(effectiveRate)
            .totalPayoutCount(records.size())
            .totalContractCount(contractIds.size())
            .totalWithdrawalCount(withdrawalCount)
            .status(TaxSummaryStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .build();
        
        return userTaxSummaryRepository.save(summary);
    }
}
```

---

### D. TỜ KHAI THUẾ 05-KK-TNCN (Việt Nam)

#### 1. Entity cho Tờ khai thuế

```java
@Entity
@Table(name = "tax_declarations")
public class TaxDeclaration {
    @Id
    private Long id;
    
    // === THÔNG TIN CHUNG ===
    @Column(name = "declaration_code", unique = true)
    private String declarationCode; // Mã tờ khai (auto-generate)
    
    @Enumerated(EnumType.STRING)
    @Column(name = "declaration_type")
    private TaxDeclarationType declarationType;
    // FORM_05_KK_TNCN          - Tờ khai quyết toán thuế TNCN
    // FORM_02_TNCN             - Tờ khai thuế TNCN hàng tháng/quý
    
    @Column(name = "tax_form_version")
    private String taxFormVersion; // Phiên bản mẫu biểu
    
    // === KỲ THUẾ ===
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_period_type")
    private TaxPeriodType taxPeriodType;
    
    @Column(name = "tax_period_year")
    private Integer taxPeriodYear;
    
    @Column(name = "tax_period_month")
    private Integer taxPeriodMonth;
    
    @Column(name = "tax_period_quarter")
    private Integer taxPeriodQuarter;
    
    // === ĐƠN VỊ NỘP THUẾ (HỆ THỐNG) ===
    @Column(name = "company_name")
    private String companyName; // Tên công ty (Producer Workbench)
    
    @Column(name = "company_tax_code")
    private String companyTaxCode; // MST công ty
    
    @Column(name = "company_address")
    private String companyAddress;
    
    @Column(name = "company_phone")
    private String companyPhone;
    
    @Column(name = "company_email")
    private String companyEmail;
    
    @Column(name = "legal_representative")
    private String legalRepresentative; // Người đại diện pháp luật
    
    // === CHI CỤC THUẾ ===
    @Column(name = "tax_department")
    private String taxDepartment; // Tên chi cục thuế
    
    @Column(name = "tax_department_code")
    private String taxDepartmentCode; // Mã chi cục thuế
    
    // === NỘI DUNG TỜ KHAI ===
    
    // Phần A: Tổng hợp thu nhập và thuế
    @Column(name = "total_employee_count")
    private Integer totalEmployeeCount; // Tổng số người có thu nhập
    
    @Column(name = "total_taxable_income", precision = 15, scale = 2)
    private BigDecimal totalTaxableIncome; // Tổng thu nhập chịu thuế
    
    @Column(name = "total_tax_withheld", precision = 15, scale = 2)
    private BigDecimal totalTaxWithheld; // Tổng thuế đã khấu trừ
    
    @Column(name = "total_tax_paid", precision = 15, scale = 2)
    private BigDecimal totalTaxPaid; // Tổng thuế đã nộp
    
    @Column(name = "total_tax_due", precision = 15, scale = 2)
    private BigDecimal totalTaxDue; // Thuế còn phải nộp
    
    @Column(name = "total_tax_refund", precision = 15, scale = 2)
    private BigDecimal totalTaxRefund; // Thuế được hoàn
    
    // Phần B: Danh sách người có thu nhập
    @OneToMany(mappedBy = "taxDeclaration", cascade = CascadeType.ALL)
    private List<TaxDeclarationDetail> details; // Chi tiết từng người
    
    // === FILE ĐÍNH KÈM ===
    @Column(name = "xml_file_url")
    private String xmlFileUrl; // File XML (theo chuẩn của GDT)
    
    @Column(name = "pdf_file_url")
    private String pdfFileUrl; // File PDF để xem
    
    @Column(name = "excel_file_url")
    private String excelFileUrl; // File Excel backup
    
    // === TRẠNG THÁI ===
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TaxDeclarationStatus status;
    // DRAFT           - Nháp
    // FINALIZED       - Đã hoàn thiện
    // SUBMITTED       - Đã nộp
    // ACCEPTED        - Đã được chấp nhận
    // REJECTED        - Bị từ chối
    // AMENDED         - Đã bổ sung
    
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;
    
    @Column(name = "submitted_by")
    private String submittedBy; // Admin ID
    
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    
    @Column(name = "acceptance_code")
    private String acceptanceCode; // Mã tiếp nhận từ cơ quan thuế
    
    // === GHI CHÚ ===
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### 2. Chi tiết từng người trong tờ khai

```java
@Entity
@Table(name = "tax_declaration_details")
public class TaxDeclarationDetail {
    @Id
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "tax_declaration_id")
    private TaxDeclaration taxDeclaration;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Column(name = "user_tax_summary_id")
    private Long userTaxSummaryId; // Link to UserTaxSummary
    
    // === THÔNG TIN CÁ NHÂN ===
    @Column(name = "sequence_number")
    private Integer sequenceNumber; // STT trong tờ khai
    
    @Column(name = "full_name")
    private String fullName;
    
    @Column(name = "cccd_number")
    private String cccdNumber;
    
    @Column(name = "tax_code")
    private String taxCode;
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "address")
    private String address;
    
    // === THU NHẬP VÀ THUẾ ===
    @Column(name = "taxable_income", precision = 15, scale = 2)
    private BigDecimal taxableIncome; // Thu nhập chịu thuế
    
    @Column(name = "tax_withheld", precision = 15, scale = 2)
    private BigDecimal taxWithheld; // Thuế đã khấu trừ
    
    @Column(name = "tax_paid", precision = 15, scale = 2)
    private BigDecimal taxPaid; // Thuế đã nộp
    
    @Column(name = "tax_due", precision = 15, scale = 2)
    private BigDecimal taxDue; // Thuế còn phải nộp
    
    @Column(name = "tax_refund", precision = 15, scale = 2)
    private BigDecimal taxRefund; // Thuế được hoàn
    
    // === GHI CHÚ ===
    @Column(name = "notes")
    private String notes;
    
    private LocalDateTime createdAt;
}
```

#### 3. Tự động tạo tờ khai thuế

```java
@Service
public class TaxDeclarationService {
    
    /**
     * Tạo tờ khai thuế cho tất cả users trong kỳ
     */
    @Transactional
    public TaxDeclaration createTaxDeclaration(
        TaxPeriodType periodType,
        int year,
        Integer monthOrQuarter
    ) {
        // 1. Tạo hoặc cập nhật summary cho tất cả users
        List<User> activeUsers = userRepository.findAllActive();
        List<UserTaxSummary> summaries = new ArrayList<>();
        
        for (User user : activeUsers) {
            try {
                UserTaxSummary summary = taxSummaryService
                    .calculateUserTaxSummary(user.getId(), periodType, year, monthOrQuarter);
                summaries.add(summary);
            } catch (Exception e) {
                log.error("Failed to calculate tax summary for user: " + user.getId(), e);
            }
        }
        
        // 2. Tính tổng hợp
        BigDecimal totalIncome = summaries.stream()
            .map(UserTaxSummary::getTotalTaxableIncome)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalTaxWithheld = summaries.stream()
            .map(UserTaxSummary::getTotalTaxWithheld)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // 3. Tạo tờ khai
        TaxDeclaration declaration = TaxDeclaration.builder()
            .declarationCode(generateDeclarationCode(periodType, year, monthOrQuarter))
            .declarationType(TaxDeclarationType.FORM_05_KK_TNCN)
            .taxFormVersion("2023")
            .taxPeriodType(periodType)
            .taxPeriodYear(year)
            .taxPeriodMonth(periodType == TaxPeriodType.MONTHLY ? monthOrQuarter : null)
            .taxPeriodQuarter(periodType == TaxPeriodType.QUARTERLY ? monthOrQuarter : null)
            // Thông tin công ty
            .companyName("Producer Workbench JSC")
            .companyTaxCode("0123456789")
            .companyAddress("...")
            .companyPhone("...")
            .companyEmail("tax@producerworkbench.com")
            .legalRepresentative("Nguyễn Văn A")
            // Chi cục thuế
            .taxDepartment("Chi cục Thuế Quận 1")
            .taxDepartmentCode("101")
            // Tổng hợp
            .totalEmployeeCount(summaries.size())
            .totalTaxableIncome(totalIncome)
            .totalTaxWithheld(totalTaxWithheld)
            .totalTaxPaid(totalTaxWithheld)
            .totalTaxDue(BigDecimal.ZERO)
            .totalTaxRefund(BigDecimal.ZERO)
            // Trạng thái
            .status(TaxDeclarationStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .build();
        
        taxDeclarationRepository.save(declaration);
        
        // 4. Tạo chi tiết cho từng user
        int seqNum = 1;
        for (UserTaxSummary summary : summaries) {
            TaxDeclarationDetail detail = TaxDeclarationDetail.builder()
                .taxDeclaration(declaration)
                .user(summary.getUser())
                .userTaxSummaryId(summary.getId())
                .sequenceNumber(seqNum++)
                .fullName(summary.getUserFullName())
                .cccdNumber(summary.getUserCccd())
                .taxCode(summary.getUserTaxCode())
                .taxableIncome(summary.getTotalTaxableIncome())
                .taxWithheld(summary.getTotalTaxWithheld())
                .taxPaid(summary.getTotalTaxPaid())
                .taxDue(summary.getTotalTaxDue())
                .taxRefund(summary.getTotalTaxRefunded())
                .createdAt(LocalDateTime.now())
                .build();
            
            taxDeclarationDetailRepository.save(detail);
        }
        
        // 5. Xuất file XML, PDF
        exportTaxDeclarationFiles(declaration);
        
        return declaration;
    }
    
    /**
     * Xuất file XML theo chuẩn của Tổng cục Thuế
     */
    private void exportTaxDeclarationFiles(TaxDeclaration declaration) {
        // Tạo file XML theo schema của GDT
        String xmlContent = generateXMLContent(declaration);
        String xmlUrl = fileStorageService.save("tax-declarations", 
            declaration.getDeclarationCode() + ".xml", xmlContent);
        declaration.setXmlFileUrl(xmlUrl);
        
        // Tạo file PDF để xem
        byte[] pdfContent = generatePDFContent(declaration);
        String pdfUrl = fileStorageService.save("tax-declarations", 
            declaration.getDeclarationCode() + ".pdf", pdfContent);
        declaration.setPdfFileUrl(pdfUrl);
        
        // Tạo file Excel backup
        byte[] excelContent = generateExcelContent(declaration);
        String excelUrl = fileStorageService.save("tax-declarations", 
            declaration.getDeclarationCode() + ".xlsx", excelContent);
        declaration.setExcelFileUrl(excelUrl);
        
        taxDeclarationRepository.save(declaration);
    }
}
```

---

### E. API BÁO CÁO THUẾ

#### 1. API cho Admin (quản lý thuế)

```java
@RestController
@RequestMapping("/api/v1/admin/tax")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTaxController {
    
    // === QUẢN LÝ TỜ KHAI ===
    
    /**
     * Tạo tờ khai thuế cho kỳ
     */
    @PostMapping("/declarations")
    public ResponseEntity<TaxDeclarationResponse> createTaxDeclaration(
        @RequestBody TaxDeclarationRequest request
    ) {
        TaxDeclaration declaration = taxDeclarationService.createTaxDeclaration(
            request.getPeriodType(),
            request.getYear(),
            request.getMonthOrQuarter()
        );
        return ResponseEntity.ok(TaxDeclarationResponse.from(declaration));
    }
    
    /**
     * Danh sách tờ khai
     */
    @GetMapping("/declarations")
    public ResponseEntity<Page<TaxDeclarationResponse>> listTaxDeclarations(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) TaxDeclarationStatus status,
        Pageable pageable
    ) {
        Page<TaxDeclaration> declarations = taxDeclarationService
            .listDeclarations(year, status, pageable);
        return ResponseEntity.ok(declarations.map(TaxDeclarationResponse::from));
    }
    
    /**
     * Chi tiết tờ khai
     */
    @GetMapping("/declarations/{id}")
    public ResponseEntity<TaxDeclarationDetailResponse> getTaxDeclaration(
        @PathVariable Long id
    ) {
        TaxDeclaration declaration = taxDeclarationService.getById(id);
        return ResponseEntity.ok(TaxDeclarationDetailResponse.from(declaration));
    }
    
    /**
     * Nộp tờ khai
     */
    @PostMapping("/declarations/{id}/submit")
    public ResponseEntity<TaxDeclarationResponse> submitTaxDeclaration(
        @PathVariable Long id
    ) {
        TaxDeclaration declaration = taxDeclarationService.submit(id);
        return ResponseEntity.ok(TaxDeclarationResponse.from(declaration));
    }
    
    /**
     * Download file tờ khai
     */
    @GetMapping("/declarations/{id}/download")
    public ResponseEntity<Resource> downloadTaxDeclaration(
        @PathVariable Long id,
        @RequestParam(defaultValue = "xml") String format
    ) {
        byte[] content = taxDeclarationService.exportFile(id, format);
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=tax-declaration." + format)
            .body(new ByteArrayResource(content));
    }
    
    // === THỐNG KÊ TỔNG QUAN ===
    
    /**
     * Dashboard thống kê thuế
     */
    @GetMapping("/dashboard")
    public ResponseEntity<TaxDashboardResponse> getTaxDashboard(
        @RequestParam Integer year
    ) {
        TaxDashboardData data = taxStatisticsService.getDashboard(year);
        return ResponseEntity.ok(TaxDashboardResponse.from(data));
    }
    
    /**
     * Báo cáo theo tháng
     */
    @GetMapping("/reports/monthly")
    public ResponseEntity<List<MonthlyTaxReport>> getMonthlyReport(
        @RequestParam Integer year
    ) {
        List<MonthlyTaxReport> report = taxStatisticsService.getMonthlyReport(year);
        return ResponseEntity.ok(report);
    }
    
    /**
     * Báo cáo theo người dùng
     */
    @GetMapping("/reports/users")
    public ResponseEntity<Page<UserTaxReportResponse>> getUserTaxReport(
        @RequestParam Integer year,
        @RequestParam(required = false) Integer month,
        Pageable pageable
    ) {
        Page<UserTaxSummary> summaries = taxStatisticsService
            .getUserTaxReport(year, month, pageable);
        return ResponseEntity.ok(summaries.map(UserTaxReportResponse::from));
    }
    
    // === QUẢN LÝ PAYOUT RECORDS ===
    
    /**
     * Danh sách payout records
     */
    @GetMapping("/payouts")
    public ResponseEntity<Page<TaxPayoutRecordResponse>> listPayoutRecords(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) LocalDate fromDate,
        @RequestParam(required = false) LocalDate toDate,
        @RequestParam(required = false) Boolean isDeclared,
        Pageable pageable
    ) {
        Page<TaxPayoutRecord> records = taxPayoutRecordService
            .listRecords(userId, fromDate, toDate, isDeclared, pageable);
        return ResponseEntity.ok(records.map(TaxPayoutRecordResponse::from));
    }
    
    /**
     * Đánh dấu đã kê khai
     */
    @PostMapping("/payouts/mark-declared")
    public ResponseEntity<Void> markPayoutsAsDeclared(
        @RequestBody MarkDeclaredRequest request
    ) {
        taxPayoutRecordService.markAsDeclared(
            request.getPayoutIds(),
            request.getDeclarationId()
        );
        return ResponseEntity.ok().build();
    }
}
```

#### 2. API cho User (xem thông tin thuế của mình)

```java
@RestController
@RequestMapping("/api/v1/users/me/tax")
public class UserTaxController {
    
    /**
     * Thông tin thuế tổng quan
     */
    @GetMapping("/summary")
    public ResponseEntity<UserTaxSummaryResponse> getTaxSummary(
        @RequestParam Integer year,
        @RequestParam(required = false) Integer month,
        Authentication auth
    ) {
        Long userId = getUserIdFromAuth(auth);
        UserTaxSummary summary = taxSummaryService.getUserSummary(
            userId, year, month
        );
        return ResponseEntity.ok(UserTaxSummaryResponse.from(summary));
    }
    
    /**
     * Lịch sử thu nhập và thuế
     */
    @GetMapping("/history")
    public ResponseEntity<Page<TaxPayoutRecordResponse>> getTaxHistory(
        @RequestParam(required = false) Integer year,
        @RequestParam(required = false) PayoutSource source,
        Pageable pageable,
        Authentication auth
    ) {
        Long userId = getUserIdFromAuth(auth);
        Page<TaxPayoutRecord> records = taxPayoutRecordService
            .getUserHistory(userId, year, source, pageable);
        return ResponseEntity.ok(records.map(TaxPayoutRecordResponse::from));
    }
    
    /**
     * Báo cáo thuế theo năm
     */
    @GetMapping("/annual-report")
    public ResponseEntity<AnnualTaxReportResponse> getAnnualReport(
        @RequestParam Integer year,
        Authentication auth
    ) {
        Long userId = getUserIdFromAuth(auth);
        AnnualTaxReport report = taxStatisticsService
            .getUserAnnualReport(userId, year);
        return ResponseEntity.ok(AnnualTaxReportResponse.from(report));
    }
    
    /**
     * Download chứng từ khấu trừ thuế
     */
    @GetMapping("/withholding-certificate")
    public ResponseEntity<Resource> downloadWithholdingCertificate(
        @RequestParam Integer year,
        Authentication auth
    ) {
        Long userId = getUserIdFromAuth(auth);
        byte[] pdfContent = taxDocumentService
            .generateWithholdingCertificate(userId, year);
        
        return ResponseEntity.ok()
            .header("Content-Disposition", 
                "attachment; filename=withholding-certificate-" + year + ".pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(new ByteArrayResource(pdfContent));
    }
}
```

---

### F. CRONJOB TỰ ĐỘNG

#### 1. Tự động tạo summary hàng tháng

```java
@Component
public class TaxScheduledTasks {
    
    /**
     * Chạy vào 00:00 ngày 1 hàng tháng
     * Tạo summary tháng trước cho tất cả users
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void generateMonthlySummaries() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int lastMonth = now.minusMonths(1).getMonthValue();
        
        log.info("Starting monthly tax summary generation for {}/{}", lastMonth, year);
        
        List<User> activeUsers = userRepository.findAllActive();
        int successCount = 0;
        int failCount = 0;
        
        for (User user : activeUsers) {
            try {
                taxSummaryService.calculateUserTaxSummary(
                    user.getId(),
                    TaxPeriodType.MONTHLY,
                    year,
                    lastMonth
                );
                successCount++;
            } catch (Exception e) {
                log.error("Failed to generate summary for user: " + user.getId(), e);
                failCount++;
            }
        }
        
        log.info("Monthly summary generation completed. Success: {}, Failed: {}",
            successCount, failCount);
    }
    
    /**
     * Chạy vào 00:00 ngày 1 của tháng đầu quý
     * Tạo summary quý trước cho tất cả users
     */
    @Scheduled(cron = "0 0 0 1 1,4,7,10 ?")
    public void generateQuarterlySummaries() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int currentMonth = now.getMonthValue();
        int lastQuarter = ((currentMonth - 1) / 3); // 0,1,2,3
        if (lastQuarter == 0) {
            lastQuarter = 4;
            year--;
        }
        
        log.info("Starting quarterly tax summary generation for Q{}/{}", 
            lastQuarter, year);
        
        List<User> activeUsers = userRepository.findAllActive();
        
        for (User user : activeUsers) {
            try {
                taxSummaryService.calculateUserTaxSummary(
                    user.getId(),
                    TaxPeriodType.QUARTERLY,
                    year,
                    lastQuarter
                );
            } catch (Exception e) {
                log.error("Failed to generate quarterly summary for user: " 
                    + user.getId(), e);
            }
        }
        
        log.info("Quarterly summary generation completed");
    }
    
    /**
     * Chạy vào 00:00 ngày 1/1 hàng năm
     * Tạo summary năm trước cho tất cả users
     */
    @Scheduled(cron = "0 0 0 1 1 ?")
    public void generateAnnualSummaries() {
        int lastYear = LocalDate.now().getYear() - 1;
        
        log.info("Starting annual tax summary generation for {}", lastYear);
        
        List<User> activeUsers = userRepository.findAllActive();
        
        for (User user : activeUsers) {
            try {
                taxSummaryService.calculateUserTaxSummary(
                    user.getId(),
                    TaxPeriodType.YEARLY,
                    lastYear,
                    null
                );
            } catch (Exception e) {
                log.error("Failed to generate annual summary for user: " 
                    + user.getId(), e);
            }
        }
        
        log.info("Annual summary generation completed");
    }
    
    /**
     * Reminder: Nhắc admin nộp tờ khai
     * Chạy vào ngày 15 hàng tháng
     */
    @Scheduled(cron = "0 0 9 15 * ?")
    public void sendTaxDeclarationReminder() {
        LocalDate now = LocalDate.now();
        int month = now.minusMonths(1).getMonthValue();
        int year = now.getYear();
        
        // Kiểm tra xem đã tạo tờ khai cho tháng trước chưa
        TaxDeclaration declaration = taxDeclarationRepository
            .findByPeriodTypeAndYearAndMonth(
                TaxPeriodType.MONTHLY, year, month
            ).orElse(null);
        
        if (declaration == null || declaration.getStatus() == TaxDeclarationStatus.DRAFT) {
            // Gửi email nhắc admin
            emailService.sendToAdmins(
                "Nhắc nhở: Cần nộp tờ khai thuế tháng " + month + "/" + year,
                "Vui lòng kiểm tra và nộp tờ khai thuế TNCN cho tháng " + month
            );
            
            log.warn("Tax declaration reminder sent for {}/{}", month, year);
        }
    }
}
```

---

### G. LƯU Ý QUAN TRỌNG

#### 1. CCCD là đủ để khai báo thuế

```
✓ Từ 01/07/2021: Số CCCD 12 số = Mã số thuế cá nhân
✓ Không cần yêu cầu user đăng ký MST riêng
✓ Chỉ cần xác thực CCCD là có thể khai báo thuế
✓ Hệ thống tự động sử dụng CCCD làm mã định danh thuế
✓ Nếu user có MST cũ (trước 2021): lưu vào taxCode, nhưng không bắt buộc
```

#### 2. Bảo mật thông tin

```
✓ Thông tin CCCD là dữ liệu cá nhân nhạy cảm → Mã hóa khi lưu trữ
✓ Chỉ admin và chính user được xem thông tin thuế của user đó
✓ Audit log tất cả truy cập vào dữ liệu thuế
✓ Backup định kỳ database thuế
✓ Không log thông tin CCCD vào file log plaintext
✓ Tuân thủ Luật Bảo vệ dữ liệu cá nhân (PDPA Vietnam)
```

#### 3. Tuân thủ pháp luật

```
✓ Nộp tờ khai đúng hạn (trước ngày 20 hàng tháng)
✓ Lưu trữ hồ sơ thuế tối thiểu 10 năm
✓ Cung cấp chứng từ khấu trừ cho người lao động
✓ Báo cáo đầy đủ, chính xác với cơ quan thuế
✓ Sử dụng số CCCD làm mã định danh thuế (từ 2021)
✓ Xác thực CCCD trước khi khấu trừ và khai báo thuế
✓ Cập nhật theo thay đổi luật thuế
```

#### 4. Quy trình kiểm tra

```
✓ Review tờ khai trước khi nộp
✓ So sánh với báo cáo kế toán
✓ Kiểm tra tổng số tiền khớp với giao dịch ngân hàng
✓ Xác nhận danh sách người có thu nhập
✓ Kiểm tra tất cả users đều có CCCD hợp lệ
✓ Verify số CCCD 12 số đúng định dạng
✓ Lưu bản chứng từ gốc
```

#### 5. Xử lý sai sót

```
✓ Nếu phát hiện sai sót sau khi nộp → Nộp tờ khai bổ sung
✓ Nếu user khiếu nại về thuế → Kiểm tra lại TaxPayoutRecord
✓ Nếu thiếu thông tin CCCD → Block user rút tiền cho đến khi cập nhật
✓ Nếu CCCD không hợp lệ → Yêu cầu xác thực lại
✓ Log mọi thay đổi để audit trail
```

---

### H. CHECKLIST TRIỂN KHAI

#### Phase 1: Thu thập thông tin người dùng (eKYC)
```
✅ Lưu thông tin CCCD trong User entity (đã có sẵn)
✅ Implement eKYC verification qua VNPT (đã có sẵn)
✅ API upload CCCD và xác thực (đã có sẵn)
☐ Thêm taxCode và taxDepartment vào User entity
☐ Tự động set taxCode = cccdNumber
☐ Lưu trữ an toàn ảnh CCCD (mã hóa)
☐ Kiểm tra CCCD không trùng lặp trong hệ thống
☐ Verify tên trên CCCD khớp với tài khoản ngân hàng
```

#### Phase 2: Ghi nhận giao dịch
```
☐ Tạo TaxPayoutRecord entity
☐ Auto-create record khi có payout
☐ Link với Contract, Milestone, Termination
☐ Tính thuế 7% cho mỗi record
☐ API query payout history
```

#### Phase 3: Tổng hợp báo cáo
```
☐ Tạo UserTaxSummary entity
☐ Service tính summary theo kỳ
☐ Cronjob tự động tạo summary
☐ API xem summary cho user
☐ API xem summary cho admin
```

#### Phase 4: Tờ khai thuế
```
☐ Tạo TaxDeclaration entity
☐ Service tạo tờ khai từ summaries
☐ Xuất file XML theo chuẩn GDT
☐ Xuất file PDF để xem
☐ API nộp tờ khai
```

#### Phase 5: Integration & Testing
```
☐ Test với data thật
☐ So sánh với tính toán thủ công
☐ UAT với kế toán
☐ Training cho admin
☐ Documentation đầy đủ
```

---

## XIV. CHANGELOG

### Version 1.6 (10/12/2025 - Update 6)
- ✅ **ĐƠN GIẢN HÓA THUẾ:** Tất cả đều 7%
- ✅ **Loại bỏ phân biệt PIT và VAT** - không cần phân biệt nữa
- ✅ **Contract gốc:** 7%
- ✅ **Khi chấm dứt:** 7%
- ✅ **Khi đền bù:** 7%
- ✅ Team và Owner nhận: **93%** (thay vì 98%)
- ✅ Cập nhật tất cả công thức: 0.02 → 0.07, 98% → 93%
- ✅ Đơn giản hóa TaxConfiguration: chỉ một taxRate = 7%
- ✅ Đơn giản hóa TaxRecord: không cần phân biệt pitTax và vatTax
- ✅ Sửa tất cả lỗi text còn sót từ logic cũ
- 📊 **Lợi ích:** Logic đơn giản hơn nhiều, dễ hiểu, dễ tính toán, dễ bảo trì

### Version 1.5 (10/12/2025 - Update 5)
- ✅ **QUAN TRỌNG:** Cập nhật tỷ lệ thuế chính xác
- ✅ **PIT (Thuế TNCN):** 2% (thay vì 10%)
- ✅ **VAT (Thuế GTGT):** 5% (thay vì 10% hoặc 0%)
- ✅ Tổng thuế trong contract: PIT 2% + VAT 5% = **7%**
- ✅ Khấu trừ khi chấm dứt: Chỉ **PIT 2%** (VAT không khấu trừ lại)
- ✅ Team và Owner nhận: **98%** (đã trừ PIT 2%)
- ✅ Cập nhật tất cả công thức tính toán: 10% → 2%
- ✅ Cập nhật tất cả ví dụ: 90% → 98%
- ✅ Cập nhật bảng tổng hợp: TeamSplit × 93%, Owner × 93%
- ✅ Cập nhật TaxConfiguration: pitRate = 0.02, vatRate = 0.05

### Version 1.4 (10/12/2025 - Update 4)
- ✅ **QUAN TRỌNG:** Sửa logic Owner đền bù Team
- ✅ Owner phải **chuyển tiền TỪ TÚI** (qua PayOS), KHÔNG lấy từ balance hệ thống
- ✅ Tạo Entity mới: `OwnerCompensationPayment` để tracking việc Owner trả tiền
- ✅ Luồng mới:
  1. Owner tạo yêu cầu chấm dứt
  2. Hệ thống tính tiền đền bù và tạo PayOS payment order
  3. Owner chuyển tiền qua PayOS vào tài khoản hệ thống
  4. Webhook PayOS confirm → Cộng NET vào balance Team
  5. Sau đó mới chấm dứt hợp đồng và hoàn tiền Client
- ✅ Cập nhật tất cả 4 trường hợp Owner chấm dứt (A2, A4, B2, B4)
- ✅ Thêm TODO: Xử lý phạt Owner nếu không đền bù (làm sau)
- 📊 Lợi ích: Owner không thể lợi dụng balance hệ thống để đền bù

### Version 1.3 (10/12/2025 - Update 3)
- ✅ **QUAN TRỌNG:** Làm rõ PIT và VAT
- ✅ **PIT (Thuế TNCN):** 10% - Áp dụng cho thu nhập từ lao động
- ✅ **VAT (Thuế GTGT):** 0-10% - Áp dụng cho giá trị dịch vụ (có thể miễn)
- ✅ Khấu trừ tại nguồn = **PIT 10%** (VAT không áp dụng khi chấm dứt)
- ✅ Cập nhật TaxRecord entity: Thêm originalPitTax, originalVatTax, actualPitTax, actualVatTax
- ✅ Cập nhật tất cả ví dụ: 7% → 10% PIT
- ✅ Cập nhật bảng tổng hợp: Team × 90%, Owner × 90%
- ✅ Thêm phần giải thích chi tiết về PIT vs VAT
- ✅ Thêm TaxConfiguration để config thuế suất theo loại dịch vụ
- 📊 Trong contract gốc: Thuế = PIT + VAT
- 📊 Khi chấm dứt: Chỉ khấu trừ PIT (VAT đã tính trong contract)

### Version 1.2 (10/12/2025 - Update 2)
- ✅ **QUAN TRỌNG:** Team Members cũng chịu thuế (khấu trừ tại nguồn)
- ✅ Cập nhật tất cả 8 trường hợp (A1-A4, B1-B4) để tính thuế cho Team
- ✅ Team nhận NET amount (đã khấu trừ PIT) vào balance
- ✅ Owner chấm dứt: Phải trả GROSS (bao gồm thuế) cho Team
- ✅ Cập nhật công thức tính thuế: Thuế thực tế = PIT × (Team + Owner)
- ✅ Cập nhật logic rút tiền: KHÔNG trừ thuế (đã khấu trừ tại nguồn)
- ✅ Cập nhật sơ đồ luồng rút tiền
- 📊 Lợi ích: User thấy balance là số thực tế nhận được

### Version 1.1 (10/12/2025 - Update 1)
- ✅ Bổ sung phần XIII: QUẢN LÝ THUẾ VÀ KÊ KHAI
- ✅ Thông tin định danh CCCD/CMND
- ✅ Số CCCD = Mã số thuế (từ 01/07/2021)
- ✅ Thống kê theo giao dịch (TaxPayoutRecord)
- ✅ Thống kê tổng hợp (UserTaxSummary)
- ✅ Tờ khai thuế 05-KK-TNCN (TaxDeclaration)
- ✅ API báo cáo thuế cho Admin và User
- ✅ Cronjob tự động tạo báo cáo thuế
- ✅ Checklist triển khai hệ thống thuế

### Version 1.0 (10/12/2025)
- ✅ Initial version
- ✅ Định nghĩa 8 trường hợp chính (FULL/MILESTONE × CLIENT/OWNER × Trước/Sau 20)
- ✅ Xây dựng công thức tính toán
- ✅ Thiết kế database schema
- ✅ Xây dựng luồng xử lý
- ✅ Thêm balance mechanism cho tất cả user
- ✅ Thêm withdrawal flow với thuế 7%

### Planned Updates
- [ ] Version 1.2: Thêm partial termination (chấm dứt một phần)
- [ ] Version 1.3: Thêm installment refund (hoàn tiền theo đợt)
- [ ] Version 1.4: Tích hợp API nộp tờ khai điện tử với Tổng cục Thuế
- [ ] Version 1.5: Tích hợp hệ thống kế toán tổng hợp

---

**END OF DOCUMENT**

> Tài liệu này là tài liệu sống (living document), sẽ được cập nhật khi có thay đổi logic nghiệp vụ hoặc yêu cầu mới.

