package com.ddicg.erp.core.security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.RoleType;
import com.ddicg.erp.modules.iam.repository.UserRepository;
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
    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

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