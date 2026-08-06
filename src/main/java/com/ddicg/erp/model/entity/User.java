package com.ddicg.erp.model.entity;

import com.ddicg.erp.model.base.BaseEntity;
import com.ddicg.erp.model.embedded.AuthCode;
import com.ddicg.erp.model.enums.ActiveStatus;
import com.ddicg.erp.model.enums.Gender;
import com.ddicg.erp.model.enums.RoleType;
import com.ddicg.erp.model.enums.UserRank;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class User extends BaseEntity<Long> implements UserDetails {

    /**
     * Họ và tên
     * 
     * @en Full name
     */
    @Column(name = "full_name")
    @Pattern(regexp = "^[\\p{L}\\s]+$", message = "Tên chỉ được chứa chữ cái và khoảng trắng!")
    String fullName;

    /**
     * Tên đăng nhập
     */
    @Column(name = "username", unique = true)
    String name;

    /**
     * Mật khẩu
     * 
     * @en Password
     */
    @NotNull(message = "Mật khẩu không được để trống")
    @Column(nullable = false)
    String password;

    /**
     * Số điện thoại
     * 
     * @en Phone number
     */
    @Column(name = "phone_number")
    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại chỉ được 10 số!")
    String phoneNumber;

    /**
     * Địa chỉ email
     * 
     * @en Email address
     */
    @NotNull(message = "Email không được để trống")
    @Email(message = "Email phải hợp lệ!")
    @Column(nullable = false)
    String email;

    /**
     * Ngày sinh
     * 
     * @en Date of birth
     */
    @Column(name = "date_of_birth")
    Date dateOfBirth;

    /**
     * Ảnh đại diện
     * 
     * @en Avatar URL
     */
    @Column(name = "avatar_url")
    String avatarUrl;

    /**
     * ID Client tương ứng trên hệ thống Fineract
     */
    @Column(name = "fineract_client_id")
    String fineractClientId;

    /**
     * Vai trò
     * 
     * @en Roles
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "roles")
    @Builder.Default
    Set<RoleType> roles = new HashSet<>();

    /*
     * ============================ 🧩 Embedded Fields ============================
     */

    /**
     * Mã xác thực
     * 
     * @en Auth code
     */
    @Embedded
    @Builder.Default
    AuthCode authCode = new AuthCode();

    /* ============================ 🗂️ Enum ============================ */

    /**
     * Trạng thái
     * 
     * @en Status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    ActiveStatus status;

    /**
     * Giới tính
     * 
     * @en Gender
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    Gender gender;

    /**
     * Hang thanh vien
     * 
     * @en User rank / loyalty tier
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_rank", length = 20)
    @Builder.Default
    UserRank rank = UserRank.MEMBER;

    /* ============================ 🔐 UserDetails ============================ */

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (roles == null || roles.isEmpty())
            return Collections.emptyList();
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != ActiveStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == ActiveStatus.ACTIVE;
    }
}