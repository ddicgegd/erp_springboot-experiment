package com.ddicg.erp.modules.fineract.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.Base64;

@Configuration
public class FineractClientConfig {

    @Bean
    public RestClient fineractRestClient(FineractProperties properties) throws Exception {
        // Create auth header
        String authString = properties.getUsername() + ":" + properties.getPassword();
        String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes());
        String authHeader = "Basic " + encodedAuth;

        java.net.http.HttpClient.Builder httpClientBuilder = java.net.http.HttpClient.newBuilder();

        if (properties.isSslBypass()) {
            // Bypass SSL for local testing (matches MCP server behavior)
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            httpClientBuilder.sslContext(sslContext);
            
            // Bypass hostname verification for Java 11 HttpClient
            System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
        }
        
        java.net.http.HttpClient httpClient = httpClientBuilder.build();
                
        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory = 
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .defaultHeader("Fineract-Platform-TenantId", properties.getTenantId())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }
}
