package com.ddicg.erp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình RedissonClient thủ công để tránh conflict với spring-boot-starter-data-redis.
 * <p>
 * Đọc properties từ {@code spring.data.redis.*} và tạo RedissonClient
 * dùng cho distributed locking (InventoryService, OrderInventoryService).
 *
 * @en Manual RedissonClient configuration to avoid conflict with spring-boot-starter-data-redis
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.username:}")
    private String redisUsername;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        var config = new Config();

        // Single node config with Redis 6+ ACL support
        var address = "redis://" + redisHost + ":" + redisPort;

        var singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(4)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                // Thời gian chờ kết nối: 5 giây
                .setTimeout(5000)
                .setConnectTimeout(5000);

        // Chỉ set password/username khi có giá trị
        if (redisPassword != null && !redisPassword.isBlank()) {
            singleServerConfig.setPassword(redisPassword);
        }
        if (redisUsername != null && !redisUsername.isBlank()) {
            // Redisson dùng username làm password cho Redis 6+ ACL
            // Một số version Redisson dùng setUsername riêng
            singleServerConfig.setUsername(redisUsername);
        }

        return Redisson.create(config);
    }
}
