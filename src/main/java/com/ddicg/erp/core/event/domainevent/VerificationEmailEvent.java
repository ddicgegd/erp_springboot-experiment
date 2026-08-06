package com.ddicg.erp.core.event.domainevent;

import com.ddicg.erp.core.common.model.enums.ActiveStatus;

public record VerificationEmailEvent(
        String email,
        String username,
        String emailVerificationToken,
        ActiveStatus purpose
) {
    public static VerificationEmailEventBuilder builder() {
        return new VerificationEmailEventBuilder();
    }

    public static class VerificationEmailEventBuilder {
        private String email;
        private String username;
        private String emailVerificationToken;
        private ActiveStatus purpose;

        public VerificationEmailEventBuilder email(String email) { this.email = email; return this; }
        public VerificationEmailEventBuilder username(String username) { this.username = username; return this; }
        public VerificationEmailEventBuilder emailVerificationToken(String emailVerificationToken) { this.emailVerificationToken = emailVerificationToken; return this; }
        public VerificationEmailEventBuilder purpose(ActiveStatus purpose) { this.purpose = purpose; return this; }

        public VerificationEmailEvent build() {
            return new VerificationEmailEvent(email, username, emailVerificationToken, purpose);
        }
    }
}
