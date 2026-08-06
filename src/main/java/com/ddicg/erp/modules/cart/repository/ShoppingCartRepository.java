package com.ddicg.erp.modules.cart.repository;

import com.ddicg.erp.modules.cart.model.ShoppingCart;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.core.common.model.enums.UserRank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface ShoppingCartRepository extends JpaRepository<ShoppingCart, Long> {

    Optional<ShoppingCart> findByUser(User user);

    /**
     * Tim gio hang het han theo rank va thoi gian.
     * Dung cho Scheduled Job doc dep gio hang cu.
     *
     * @en Find expired carts by user rank and cutoff time.
     */
    @Query("SELECT sc FROM ShoppingCart sc WHERE sc.user.rank = :rank AND sc.lastActivityAt < :cutoff")
    List<ShoppingCart> findExpiredCartsByRank(
            @Param("rank") UserRank rank,
            @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Xoa gio hang het han theo rank va thoi gian.
     *
     * @en Delete expired carts by user rank and cutoff time.
     */
    @Modifying
    @Query("DELETE FROM ShoppingCart sc WHERE sc.user.rank = :rank AND sc.lastActivityAt < :cutoff")
    int deleteExpiredCartsByRank(
            @Param("rank") UserRank rank,
            @Param("cutoff") LocalDateTime cutoff
    );
}
