# Hibernate: Found Shared References to a Collection

## 📋 Tổng quan

Tài liệu này mô tả chi tiết lỗi **"Found shared references to a collection"** trong Hibernate/JPA, nguyên nhân gốc rễ và cách khắc phục triệt để.

---

## 🔴 Mô tả lỗi

### Error Message
```
org.springframework.orm.jpa.JpaSystemException: Found shared references to a collection: com.fpt.producerworkbench.entity.Project.liveSessions
```

```
org.springframework.orm.jpa.JpaSystemException: Found shared references to a collection: com.fpt.producerworkbench.entity.Contract.documents
```

### Khi nào xảy ra?
- Khi gọi API `acceptInvitationById`, `cancelInvitation`, `createContract`
- Xảy ra với **tất cả các role** (COLLABORATOR, OBSERVER, CLIENT)
- Xuất hiện trong quá trình Hibernate flush/dirty checking

---

## 🔍 Nguyên nhân gốc rễ

### Pattern code gây lỗi

```java
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {
    
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<LiveSession> liveSessions = new ArrayList<>();  // ← VẤN ĐỀ Ở ĐÂY!
}
```

### Tại sao lỗi?

1. **Field initialization `= new ArrayList<>()`** kết hợp với **Lombok `@NoArgsConstructor`**:
   - Mỗi lần tạo `new Project()` → tạo 1 ArrayList instance mới
   - Khi Hibernate load entity từ DB → cũng gọi no-arg constructor → tạo ArrayList mới

2. **Load entity nhiều lần trong cùng transaction**:
   ```java
   Project project1 = invitation.getProject();     // Load lần 1 → ArrayList instance A
   Project project2 = projectRepository.findById(id);  // Load lần 2 → ArrayList instance B
   ```

3. **Hibernate dirty checking phát hiện**:
   - Cùng 1 entity ID nhưng có 2 ArrayList instances khác nhau
   - Hibernate nghĩ đây là lỗi dữ liệu → throw exception

### Minh họa

```
Transaction bắt đầu
    │
    ├── invitation.getProject()
    │   └── Project instance #1 { liveSessions: ArrayList@A }
    │
    ├── projectRepository.findById(id)
    │   └── Project instance #2 { liveSessions: ArrayList@B }
    │
    ├── Hibernate flush/dirty check
    │   └── Phát hiện: Cùng Project ID nhưng 2 ArrayList khác nhau!
    │
    └── ❌ EXCEPTION: "Found shared references to a collection"
```

---

## ✅ Giải pháp

### Nguyên tắc cốt lõi

> **KHÔNG BAO GIỜ** khởi tạo `@OneToMany` collection tại field level.
> Để Hibernate hoàn toàn quản lý collection lifecycle.

### Code pattern đúng

```java
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {
    
    /**
     * KHÔNG khởi tạo = new ArrayList<>() tại field level.
     * Để Hibernate quản lý collection, tránh lỗi "Found shared references to a collection".
     */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<LiveSession> liveSessions;  // ← KHÔNG khởi tạo

    /**
     * Getter với lazy initialization - tránh NullPointerException
     */
    public List<LiveSession> getLiveSessions() {
        if (liveSessions == null) {
            liveSessions = new ArrayList<>();
        }
        return liveSessions;
    }

    /**
     * Setter mutate in-place để tránh shared reference
     */
    public void setLiveSessions(List<LiveSession> liveSessions) {
        if (this.liveSessions == null) {
            this.liveSessions = new ArrayList<>();
        }
        this.liveSessions.clear();
        if (liveSessions != null) {
            this.liveSessions.addAll(liveSessions);
        }
    }
}
```

---

## 📁 Các Entity đã được fix

### 1. `Project.java`
```java
// TRƯỚC (SAI):
@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
private List<LiveSession> liveSessions = new ArrayList<>();

// SAU (ĐÚNG):
@OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
private List<LiveSession> liveSessions;
// + getter với lazy init
```

### 2. `Contract.java`
```java
// TRƯỚC (SAI):
@OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ContractDocument> documents = new ArrayList<>();

// SAU (ĐÚNG):
@OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ContractDocument> documents;
// + getter với lazy init
```

### 3. `Conversation.java`
```java
// TRƯỚC (SAI):
@OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
@Builder.Default
private List<ParticipantInfo> participants = new ArrayList<>();

@OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
@Builder.Default
private List<ChatMessage> chatMessages = new ArrayList<>();

// SAU (ĐÚNG):
@OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ParticipantInfo> participants;

@OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
private List<ChatMessage> chatMessages;
// + getter với lazy init cho cả 2
```

### 4. `MilestoneBriefGroup.java`
```java
// TRƯỚC (SAI):
@OneToMany(mappedBy = "group", orphanRemoval = true, fetch = FetchType.LAZY)
private List<MilestoneBriefBlock> blocks = new ArrayList<>();

// SAU (ĐÚNG):
@OneToMany(mappedBy = "group", orphanRemoval = true, fetch = FetchType.LAZY)
private List<MilestoneBriefBlock> blocks;
// + getter với lazy init
```

---

## ⚠️ Lưu ý quan trọng

### 1. Về `@Builder.Default` của Lombok
```java
// ❌ KHÔNG NÊN dùng với @OneToMany
@Builder.Default
private List<Child> children = new ArrayList<>();

// ✅ Bỏ @Builder.Default, dùng getter lazy init
private List<Child> children;
```

### 2. Về `@ElementCollection`
Pattern này **KHÔNG ÁP DỤNG** cho `@ElementCollection` với primitive types:
```java
// ✅ OK - không phải entity relation
@ElementCollection
private List<String> attachmentKeys = new ArrayList<>();
```

### 3. Khi nào cần setter đặc biệt?
Khi cần thay thế toàn bộ collection, dùng **mutate in-place**:
```java
public void setChildren(List<Child> children) {
    if (this.children == null) {
        this.children = new ArrayList<>();
    }
    this.children.clear();  // Clear existing
    if (children != null) {
        this.children.addAll(children);  // Add all new
    }
}
```

---

## 🔧 Các giải pháp thay thế (không khuyến nghị)

### Option 1: Dùng `getReferenceById()` thay vì `findById()`
```java
// Không load full entity, chỉ tạo proxy
Project projectRef = projectRepository.getReferenceById(projectId);
```
**Nhược điểm**: Không fix gốc, chỉ workaround cho từng service

### Option 2: Dùng Projection
```java
@Query("SELECT p.id AS id, p.title AS title FROM Project p WHERE p.id = :id")
Optional<ProjectBasicInfo> findBasicInfoById(@Param("id") Long id);
```
**Nhược điểm**: Cần tạo nhiều projection, code phức tạp hơn

### Option 3: `EntityManager.detach()`
```java
Project project = projectRepository.findById(id).orElseThrow();
entityManager.detach(project);  // Tách khỏi persistence context
```
**Nhược điểm**: Dễ quên, không nhất quán

---

## 📊 So sánh: Lỗi này vs Lazy Loading

| Đặc điểm | Shared References Error | LazyInitializationException |
|----------|-------------------------|------------------------------|
| **Nguyên nhân** | Load entity nhiều lần trong transaction | Access collection ngoài transaction |
| **Exception** | `JpaSystemException` | `LazyInitializationException` |
| **Khi xảy ra** | Trong transaction, khi flush | Ngoài transaction |
| **Fix** | Không khởi tạo collection tại field | `@Transactional` hoặc `JOIN FETCH` |

---

## 📚 Tham khảo

- [Hibernate User Guide - Collections](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#collections)
- [Vlad Mihalcea - Best Practices](https://vladmihalcea.com/hibernate-facts-favoring-sets-vs-bags/)
- [Baeldung - JPA Collections](https://www.baeldung.com/hibernate-initialize-proxy-exception)

---

## 📝 Checklist cho Entity mới

Khi tạo entity mới có `@OneToMany`:

- [ ] **KHÔNG** khởi tạo collection tại field level
- [ ] **KHÔNG** dùng `@Builder.Default` cho collection
- [ ] Tạo getter với lazy initialization
- [ ] Tạo setter mutate in-place (nếu cần)
- [ ] Test với scenario load entity nhiều lần

---

*Cập nhật: December 14, 2025*
