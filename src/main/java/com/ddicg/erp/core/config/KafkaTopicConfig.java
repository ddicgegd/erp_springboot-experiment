package com.ddicg.erp.core.config;

import com.ddicg.erp.core.common.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderTopic() {
        return new NewTopic(KafkaTopics.ORDER_TOPIC, 2, (short) 1);
    }
}
