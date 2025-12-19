package com.fpt.producerworkbench.entity;

import com.fpt.producerworkbench.common.UserRole;
import com.fpt.producerworkbench.common.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends AbstractEntity<Long> implements UserDetails {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "otp")
    String otp;

    @Column(name = "otp_expiry_date")
    LocalDateTime otpExpiryDate;

    @Column(name = "cccd_number", unique = true)
    private String cccdNumber;

    @Column(name = "cccd_full_name")
    private String cccdFullName;

    @Column(name = "cccd_birth_day")
    private String cccdBirthDay;

    @Column(name = "cccd_gender")
    private String cccdGender;

    @Column(name = "cccd_origin_location")
    private String cccdOriginLocation;

    @Column(name = "cccd_recent_location")
    private String cccdRecentLocation;

    @Column(name = "cccd_issue_date")
    private String cccdIssueDate;

    @Column(name = "cccd_issue_place")
    private String cccdIssuePlace;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "cccd_front_image_url", length = 1000)
    private String cccdFrontImageUrl;

    @Column(name = "cccd_back_image_url", length = 1000)
    private String cccdBackImageUrl;

    // === THÔNG TIN THUẾ ===
    // Từ 01/07/2021: Số CCCD 12 số CHÍNH LÀ mã số thuế cá nhân
    @Column(name = "tax_code", length = 13)
    private String taxCode; // Mã số thuế (nếu có MST cũ)

    @Column(name = "tax_department")
    private String taxDepartment; // Chi cục thuế quản lý

    @Transient
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return this.passwordHash;
    }

    @Override
    public String getUsername() {
        return this.firstName + " " + this.lastName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }


}