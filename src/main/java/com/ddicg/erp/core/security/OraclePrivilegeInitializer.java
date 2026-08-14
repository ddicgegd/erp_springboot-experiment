package com.ddicg.erp.core.security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Tự động cấp quyền Oracle cho user Spring_app khi ứng dụng khởi động.
 * Kết nối với user admin (system) để chạy GRANT CREATE TABLE/VIEW/SEQUENCE.
 * Chạy trước Hibernate ddl-auto:update nhờ @Lazy(false).
 */
@Component
@Lazy(false)
@Slf4j
public class OraclePrivilegeInitializer {


    @Value("${app.datasource.admin.url:}")
    private String adminUrl;

    @Value("${app.datasource.admin.username:}")
    private String adminUsername;

    @Value("${app.datasource.admin.password:}")
    private String adminPassword;

    @Value("${spring.datasource.username:Spring_app}")
    private String targetUser;

    @PostConstruct
    public void init() {
        if (adminUrl.isBlank() || adminUsername.isBlank()) {
            log.info("Admin datasource not configured. Skip automatic privilege grant.");
            log.info("To enable, add app.datasource.admin.url, .username, .password to application.yml");
            return;
        }

        log.info("Connecting as '{}' to grant privileges to user '{}'...", adminUsername, targetUser);

        try (Connection conn = DriverManager.getConnection(adminUrl, adminUsername, adminPassword);
             Statement stmt = conn.createStatement()) {

            stmt.execute("GRANT CREATE TABLE TO " + targetUser);
            log.info("Granted CREATE TABLE to {}", targetUser);

            stmt.execute("GRANT CREATE VIEW TO " + targetUser);
            log.info("Granted CREATE VIEW to {}", targetUser);

            stmt.execute("GRANT CREATE SEQUENCE TO " + targetUser);
            log.info("Granted CREATE SEQUENCE to {}", targetUser);

            log.info("All privileges granted successfully to user '{}'.", targetUser);

        } catch (Exception e) {
            log.warn("Cannot grant privileges: {}. May already have rights, or check admin credentials in application.yml.",
                    e.getMessage());
        }
    }
}