package com.ddicg.erp.modules.payment.repository;

import com.ddicg.erp.modules.payment.model.CustomerPaymentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPaymentProfileRepository extends JpaRepository<CustomerPaymentProfile, Long> {
    Optional<CustomerPaymentProfile> findByProfileCode(String profileCode);
    List<CustomerPaymentProfile> findByUserId(Long userId);
    Optional<CustomerPaymentProfile> findByUserIdAndStatus(Long userId, String status);
}
