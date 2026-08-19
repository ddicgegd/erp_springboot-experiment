package com.ddicg.erp.core.config;

import com.ddicg.erp.core.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.CorsFilter;
import java.io.IOException;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        private static final String[] SWAGGER_WHITELIST = {
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
        };

    private static final String[] REQUEST_PERMIT_ALL = {
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/refresh-token",
                "/api/auth/verify**",
                "/api/auth/test-response",
                "/api/auth/logout",
                "/api/auth/recover-account/**",
                "/api/auth/validate-reset-token**",
                "/api/auth/reset-password**",
                "/api/auth/change-username",
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
        };

        private static final String[] FINERACT_PERMIT_ALL = {
                "/api/v1/erp/clients/**",
                "/api/v1/erp/loans/**",
                "/api/v1/erp/loan-products/**",
        };

        @Bean
        public SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
                http
                        .securityMatcher("/mcp/**")
                        .cors(Customizer.withDefaults())
                        .csrf(AbstractHttpConfigurer::disable)
                        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                        .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

                return http.build();
        }

        @Bean
        public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
                http
                        .securityMatcher("/api/**")
                        .cors(Customizer.withDefaults())
                        .csrf(AbstractHttpConfigurer::disable)
                        .authorizeHttpRequests(auth ->
                                auth.requestMatchers(SWAGGER_WHITELIST).permitAll()
                                        .requestMatchers(REQUEST_PERMIT_ALL).permitAll()
                                        .requestMatchers(FINERACT_PERMIT_ALL).permitAll()
                                        
                                        .requestMatchers("/api/auth/search").hasRole("ADMIN")
                                        .requestMatchers("/api/address/**").authenticated()
                                        .requestMatchers("/api/orders/**").authenticated()
                                        .requestMatchers("/api/auth/get-user/**").hasRole("USER")
                                        .anyRequest().authenticated())
                        .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
                corsConfiguration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                corsConfiguration.setAllowedHeaders(List.of("*"));
                corsConfiguration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/api/**", corsConfiguration);
                source.registerCorsConfiguration("/mcp/**", corsConfiguration);

                CorsFilter corsFilter = new CorsFilter(source);
                corsFilter.setCorsProcessor(new DefaultCorsProcessor() {
                        @Override
                        protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response,
                                                         CorsConfiguration config, boolean preFlightRequest) throws IOException {
                                boolean result = super.handleInternal(request, response, config, preFlightRequest);
                                if (result && preFlightRequest && request.getHeaders().containsKey("Access-Control-Request-Private-Network")) {
                                        if (response instanceof org.springframework.http.server.ServletServerHttpResponse) {
                                                ((org.springframework.http.server.ServletServerHttpResponse) response).getServletResponse()
                                                        .setHeader("Access-Control-Allow-Private-Network", "true");
                                        }
                                }
                                return result;
                        }
                });
                return corsFilter;
        }
}
