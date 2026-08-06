package com.ddicg.erp.modules.iam.service;

import com.ddicg.erp.core.common.model.embedded.DeviceInfo;
import com.ddicg.erp.modules.iam.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Pattern;

@Component("featureUserHelper")
@RequiredArgsConstructor
public class Helper {

    private final JwtService jwtService;

    public String createShortToken(UserDetails userDetails, long expirationTimeMillis) {
        return jwtService.generateToken(userDetails, expirationTimeMillis);
    }

    public boolean isEmailFormat(String input) {
        return input != null && Pattern.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", input);
    }

    public String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        int atIndex = email.indexOf("@");
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 5) {
            return localPart + domain;
        }

        String firstTwo = localPart.substring(0, 2);
        String lastThree = localPart.substring(localPart.length() - 3);
        int starCount = localPart.length() - 5;
        String stars = "*".repeat(starCount);

        return firstTwo + stars + lastThree + domain;
    }

    public boolean areDeviceInfoMatching(DeviceInfo d1, DeviceInfo d2) {
        if (d1 == null || d2 == null) return false;
        String d1Type = (d1.getDeviceType() != null) ? d1.getDeviceType().trim().toLowerCase() : null;
        String d2Type = (d2.getDeviceType() != null) ? d2.getDeviceType().trim().toLowerCase() : null;

        String d1Os = (d1.getOsName() != null) ? d1.getOsName().trim().toLowerCase() : null;
        String d2Os = (d2.getOsName() != null) ? d2.getOsName().trim().toLowerCase() : null;

        boolean typeMatch = Objects.equals(d1Type, d2Type);
        boolean osMatch = Objects.equals(d1Os, d2Os);

        return typeMatch && osMatch;
    }

}
