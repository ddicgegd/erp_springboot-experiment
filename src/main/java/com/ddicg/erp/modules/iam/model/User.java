package com.ddicg.erp.modules.iam.model;

import com.ddicg.erp.core.common.model.base.BaseEntity;
import com.ddicg.erp.core.common.model.embedded.AuthCode;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.Gender;
import com.ddicg.erp.core.common.model.enums.RoleType;
import com.ddicg.erp.core.common.model.enums.UserRank;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "Users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_phone", columnList = "phone_number"),
        @Index(name = "idx_user_status", columnList = "status")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity<Long> implements UserDetails {

    @Column(name = "full_name")
    String fullName;

    @Column(name = "username", unique = true)
    String name;

    @NotNull(message = "Mật khẩu không được để trống")
    @Column(nullable = false)
    String password;

    @Column(name = "phone_number")
    String phoneNumber;

    @NotNull(message = "Email không được để trống")
    @Email(message = "Email phải hợp lệ!")
    @Column(nullable = false)
    String email;

    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Column(name = "dob_updated_at")
    LocalDateTime dobUpdatedAt;

    @Column(name = "avatar_url")
    String avatarUrl;

    @Column(name = "fineract_client_id")
    String fineractClientId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "roles")
    @Builder.Default
    Set<RoleType> roles = new HashSet<>();

    @Embedded
    @Builder.Default
    AuthCode authCode = new AuthCode();

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    ActiveStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_rank", length = 20)
    @Builder.Default
    UserRank rank = UserRank.MEMBER;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return status != ActiveStatus.LOCKED; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return status == ActiveStatus.ACTIVE; }

    public String getGender() {
        return gender != null ? gender.name() : null;
    }

    public void setGender(String genderStr) {
        if (genderStr != null) {
            try {
                this.gender = Gender.valueOf(genderStr.toUpperCase());
            } catch (Exception ignored) {}
        }
    }

    public String getRank() {
        return rank != null ? rank.name() : null;
    }

    public UserRank getUserRank() {
        return rank;
    }
}
