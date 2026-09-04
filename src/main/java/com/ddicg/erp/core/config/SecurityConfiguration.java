package com.ddicg.erp.core.config;

import com.ddicg.erp.core.common.model.enums.RoleType;
import com.ddicg.erp.core.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 1. Tài liệu API & Swagger UI
    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    // 2. Nhóm xác thực & tài khoản công khai
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh-token",
            "/api/auth/verify**",
            "/api/auth/test-response",
            "/api/auth/logout",
            "/api/auth/recover-account/**",
            "/api/auth/validate-reset-token**",
            "/api/auth/reset-password**",
            "/api/auth/change-username"
    };

    // 3. Nhóm nghiệp vụ E-Commerce & Core Banking công khai
    private static final String[] PUBLIC_BUSINESS_ENDPOINTS = {
            "/api/merchandise/search-Product",
            "/api/merchandise/search-Category",
            "/api/merchandise/search-Attributes",
            "/api/images/**",
            "/api/payment/result**",
            "/api/delivery/**",
            "/api/addresses/resolve",
            "/api/shipping/**",
            "/api/vouchers/active",
            "/api/vouchers/check/**",
            "/api/cart/**",
            "/api/v1/erp/loan-products/**"
    };

    @Bean
    public SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/mcp/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Công khai (Permit All)
                        .requestMatchers(SWAGGER_WHITELIST).permitAll()
                        .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_BUSINESS_ENDPOINTS).permitAll()

                        // 2. Quản trị viên & Ban quản lý (Admin / Management)
                        .requestMatchers("/api/auth/search").hasRole(RoleType.ADMIN.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/erp/loans/*/approve",
                                                          "/api/v1/erp/loans/*/disburse",
                                                          "/api/v1/erp/loans/*/reject").hasAnyRole(RoleType.ADMIN.name(), RoleType.MANAGEMENT.name())
                        .requestMatchers("/api/v1/erp/journalentries/**").hasAnyRole(RoleType.ADMIN.name(), RoleType.MANAGEMENT.name())
                        .requestMatchers("/api/v1/erp/clients/**").hasAnyRole(RoleType.ADMIN.name(), RoleType.MANAGEMENT.name())

                        // 3. Người dùng đã xác thực (Authenticated)
                        .requestMatchers("/api/address/**", "/api/orders/**").authenticated()
                        .requestMatchers("/api/auth/get-user/**").hasRole(RoleType.USER.name())
                        .requestMatchers(HttpMethod.POST, "/api/v1/erp/loans").authenticated()
                        .requestMatchers("/api/v1/erp/loans/*/repayments", "/api/v1/erp/loans/*/withdraw").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/erp/loans", "/api/v1/erp/loans/**").authenticated()

                        // 4. Mọi request còn lại đều yêu cầu đăng nhập
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOriginPatterns(List.of(
                "http://localhost:3881",
                "http://localhost:3000"
        ));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", corsConfiguration);
        source.registerCorsConfiguration("/mcp/**", corsConfiguration);

        CorsFilter corsFilter = new CorsFilter(source);
        corsFilter.setCorsProcessor(new PrivateNetworkCorsProcessor());
        return corsFilter;
    }

    /**
     * Bộ xử lý CORS hỗ trợ cờ Access-Control-Allow-Private-Network cho môi trường local/nội bộ.
     */
    private static class PrivateNetworkCorsProcessor extends DefaultCorsProcessor {
        @Override
        protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response,
                                         CorsConfiguration config, boolean preFlightRequest) throws IOException {
            boolean result = super.handleInternal(request, response, config, preFlightRequest);
            if (result && preFlightRequest && request.getHeaders().containsKey("Access-Control-Request-Private-Network")) {
                if (response instanceof ServletServerHttpResponse servletResponse) {
                    servletResponse.getServletResponse().setHeader("Access-Control-Allow-Private-Network", "true");
                }
            }
            return result;
        }
    }
}
