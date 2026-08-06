package com.ddicg.erp.config;

import com.ddicg.erp.common.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic activeLogTopic() {
        return new NewTopic(KafkaTopics.ACTIVE_LOG_TOPIC, 2, (short) 1);
    }

    @Bean
    public NewTopic orderTopic() {
        return new NewTopic(KafkaTopics.ORDER_TOPIC, 2, (short) 1);
    }

    @Bean
    public NewTopic paymentResultTopic() {
        return new NewTopic("payment-result", 3, (short) 1);
    }
}
