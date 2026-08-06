package com.ddicg.erp.component;

import com.ddicg.erp.model.entity.User;
import com.ddicg.erp.model.enums.ActiveStatus;
import com.ddicg.erp.model.enums.RoleType;
import com.ddicg.erp.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSeeder implements CommandLineRunner {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return;
        }

        log.info("Kiểm tra không thấy có account nào tồn tại. Yêu cầu tạo mới account 'ADMIN'.");
        final var account = new  User();

        account.setFullName("Ngô Ngọc Định");
        account.setName("ADMIN");
        account.setEmail("ADMIN@gmail.com");
        account.setPassword(passwordEncoder.encode("admin"));
        account.setStatus(ActiveStatus.ACTIVE);
        account.setRoles(
                (Set.of(RoleType.ADMIN, RoleType.USER, RoleType.SUPER_ADMIN)));

        this.userRepository.save(account);
    }
}
