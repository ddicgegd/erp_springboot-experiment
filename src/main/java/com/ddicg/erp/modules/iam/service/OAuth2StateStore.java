package com.ddicg.erp.modules.iam.service;

public interface OAuth2StateStore {

    /**
     * Saves the given state to Redis with a TTL of 10 minutes.
     * @param state The state to save.
     */
    void saveState(String state);

    /**
     * Validates and consumes the OAuth2 state.
     * (Khung phương thức - Sẽ được hoàn thiện ở Phase 3 bằng lệnh Atomic get-and-delete)
     *
     * @param cookieState The state value retrieved from the cookie.
     * @param queryState The state value retrieved from the query parameter.
     * @return true if valid and successfully consumed, false otherwise.
     */
    boolean validateAndConsume(String cookieState, String queryState);
}
