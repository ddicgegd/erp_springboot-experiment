package com.ddicg.erp.core.event.domainevent;

import com.ddicg.erp.modules.iam.model.User;

public record AccountRecoveryEvent(
        User user,
        String email,
        String username,
        String token
) {
    public static AccountRecoveryEventBuilder builder() {
        return new AccountRecoveryEventBuilder();
    }

    public static class AccountRecoveryEventBuilder {
        private User user;
        private String email;
        private String username;
        private String token;

        public AccountRecoveryEventBuilder user(User user) { this.user = user; return this; }
        public AccountRecoveryEventBuilder email(String email) { this.email = email; return this; }
        public AccountRecoveryEventBuilder username(String username) { this.username = username; return this; }
        public AccountRecoveryEventBuilder token(String token) { this.token = token; return this; }

        public AccountRecoveryEvent build() {
            return new AccountRecoveryEvent(user, email, username, token);
        }
    }
}
