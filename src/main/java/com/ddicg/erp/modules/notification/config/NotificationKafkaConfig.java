package com.ddicg.erp.modules.notification.config;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình topic Kafka và cơ chế Dead Letter Queue cho Notification Service.
 */
@Configuration
public class NotificationKafkaConfig {

    @Bean
    public NewTopic notificationEmailTopic() {
        return new NewTopic(KafkaTopics.NOTIFICATION_EMAIL_TOPIC, 2, (short) 1);
    }

    @Bean
    public NewTopic notificationEmailDltTopic() {
        return new NewTopic(KafkaTopics.NOTIFICATION_EMAIL_DLT, 2, (short) 1);
    }
}
