package com.ddicg.erp.modules.cart.model;

import com.ddicg.erp.core.common.model.enums.UserRank;
import com.ddicg.erp.modules.iam.model.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
public class CartContext {

    private static final Set<UserRank> VIP_RANKS = Set.of(
            UserRank.SILVER,
            UserRank.GOLD,
            UserRank.PLATINUM,
            UserRank.DIAMOND
    );

    String key;
    String ownerName;
    long ttlDays;
    boolean isGuest;
    User user;

    public boolean isVip() {
        if (isGuest || user == null) {
            return false;
        }
        UserRank userRank = user.getUserRank();
        if (userRank != null && VIP_RANKS.contains(userRank)) {
            return true;
        }
        String rankStr = user.getRank();
        if (rankStr != null) {
            try {
                UserRank parsed = UserRank.valueOf(rankStr.toUpperCase());
                return VIP_RANKS.contains(parsed);
            } catch (IllegalArgumentException ignored) {}
        }
        return false;
    }
}
