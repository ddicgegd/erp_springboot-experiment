package com.ddicg.erp.modules.iam.repository;

import com.ddicg.erp.modules.iam.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    java.util.List<Address> findByUserId(Long userId);
}
