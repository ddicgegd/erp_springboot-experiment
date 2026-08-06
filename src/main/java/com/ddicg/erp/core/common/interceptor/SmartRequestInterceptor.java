package com.ddicg.erp.core.common.interceptor;

import com.ddicg.erp.core.security.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class SmartRequestInterceptor implements HandlerInterceptor {

    private final SecurityUtil securityUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userId = securityUtil.getCurrentUserId();
        boolean isAdmin = securityUtil.hasRole("ADMIN");
        boolean isStaff = securityUtil.hasRole("STAFF");

        if (userId != null) {
            request.setAttribute("X-User-Id", userId);
            request.setAttribute("X-Is-Admin", isAdmin);
            request.setAttribute("X-Is-Staff", isStaff);
        }
        return true;
    }
}
