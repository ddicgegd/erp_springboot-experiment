package com.ddicg.erp.modules.iam.repository;

import com.ddicg.erp.modules.iam.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    Optional<Address> findBySku(String sku);

    Optional<Address> findBySkuAndUserId(String sku, Long userId);

    boolean existsBySku(String sku);

    List<Address> findByUserId(Long userId);

    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
}
