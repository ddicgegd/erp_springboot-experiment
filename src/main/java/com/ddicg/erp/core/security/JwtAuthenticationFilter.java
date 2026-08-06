package com.ddicg.erp.core.security;

import com.ddicg.erp.modules.iam.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String jwt;
        final String userName;

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(BEARER_PREFIX.length());

        // Với logout: dù token hết hạn vẫn cho đi qua để service có thể revoke token
        boolean isLogoutRequest = request.getRequestURI().equals("/api/auth/logout");

        try {
            userName = jwtService.extractUsername(jwt);
            if (userName != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userName);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.debug("Người dùng '{}' đã xác thực thành công qua JWT.", userName);
                } else if (!isLogoutRequest) {
                    logger.warn("JWT không hợp lệ đối với người dùng: {}. Yêu cầu bị chặn.", userName);
                    handleJwtException(response, HttpServletResponse.SC_UNAUTHORIZED, "Mã thông báo không hợp lệ.");
                    return;
                }
            }
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            if (isLogoutRequest) {
                // Token hết hạn nhưng vẫn cho logout đi qua để revoke
                logger.info("Token hết hạn nhưng cho phép logout tiếp tục.");
                filterChain.doFilter(request, response);
            } else {
                logger.warn("JWT authentication error: {}", mapJwtExceptionToMessage(e));
                handleJwtException(response, HttpServletResponse.SC_UNAUTHORIZED, mapJwtExceptionToMessage(e));
            }
        } catch (UnsupportedJwtException | MalformedJwtException | SignatureException
                | IllegalArgumentException e) {

            int status = (e instanceof IllegalArgumentException)
                    ? HttpServletResponse.SC_BAD_REQUEST
                    : HttpServletResponse.SC_UNAUTHORIZED;

            String message = mapJwtExceptionToMessage(e);
            logger.warn("JWT authentication error: {}", message);
            handleJwtException(response, status, message);
        }
    }

    private void handleJwtException(HttpServletResponse response, int status, String message) throws IOException {
        if (!response.isCommitted()) {
            SecurityContextHolder.clearContext();
            response.setStatus(status);
            response.setContentType("application/json;charset=UTF-8");
            String jsonErrorResponse = String.format(
                    "{\"timestamp\": %d, \"status\": %d, \"error\": \"%s\", \"message\": \"%s\"}",
                    System.currentTimeMillis(), status,
                    HttpStatus.valueOf(status).getReasonPhrase().replace("\"", "\\\""), message.replace("\"", "\\\""));
            response.getWriter().write(jsonErrorResponse);
            response.getWriter().flush();
        } else {
            logger.warn("Phản hồi đã được cam kết. Không thể gửi lỗi JWT: {}", message);
        }
    }

    private String mapJwtExceptionToMessage(Exception e) {
        return switch (e) {
            case ExpiredJwtException ignored -> "Token đã hết hạn";
            case UnsupportedJwtException ignored -> "Token không được hỗ trợ";
            case MalformedJwtException ignored -> "Token không đúng định dạng";
            case SignatureException ignored -> "Chữ ký JWT không hợp lệ";
            case IllegalArgumentException ignored -> "Token không hợp lệ hoặc rỗng";
            default -> "Lỗi xác thực JWT: " + e.getMessage();
        };
    }
}