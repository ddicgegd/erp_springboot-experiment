package com.ddicg.erp.modules.merchandise.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.core.common.model.embedded.AuditInfo;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.cart.model.CartItem;
import com.ddicg.erp.modules.cart.model.ShoppingCart;
import com.ddicg.erp.modules.iam.model.User;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.cart.dto.ShoppingCartDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component("featureMerchandiseHelper")
@Slf4j
@RequiredArgsConstructor
public class Helper {


    private static final String ALPHANUMERIC_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // ─── General helpers ───

    UUID convertStringToUUID(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("ID không được để trống.");
        }
        String text = id.trim().replace("[", "").replace("]", "").replace("\"", "");
        if (text.length() == 36 && text.chars().filter(c -> c == '-').count() == 4) {
            try { return UUID.fromString(text); } catch (IllegalArgumentException e) {}
        }
        if (text.length() == 32 && !text.contains("-")) {
            return buildUUIDFromHex(text);
        }
        String raw = text.replace("-", "");
        if (raw.length() != 32) {
            throw new IllegalArgumentException(String.format(
                    "Định dạng ID sai. Mong đợi 32 ký tự hex hoặc chuẩn UUID 36 ký tự, nhận được: %d ký tự.", text.length()));
        }
        return buildUUIDFromHex(raw);
    }

    private UUID buildUUIDFromHex(String hex) {
        if (hex.length() != 32) throw new IllegalArgumentException("Chuỗi hex phải có đúng 32 ký tự.");
        if (!hex.matches("[0-9a-fA-F]+")) throw new IllegalArgumentException("ID chứa ký tự không hợp lệ.");
        String formatted = String.format("%s-%s-%s-%s-%s",
                hex.substring(0, 8), hex.substring(8, 12), hex.substring(12, 16),
                hex.substring(16, 20), hex.substring(20, 32));
        return UUID.fromString(formatted);
    }

    public String generateKey() {
        StringBuilder sb = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHANUMERIC_CHARACTERS.charAt(
                    ThreadLocalRandom.current().nextInt(ALPHANUMERIC_CHARACTERS.length())));
        }
        return sb.toString();
    }

    List<String> filterBlank(List<String> list) {
        if (list == null) return List.of();
        return list.stream().filter(s -> s != null && !s.isBlank()).toList();
    }
}