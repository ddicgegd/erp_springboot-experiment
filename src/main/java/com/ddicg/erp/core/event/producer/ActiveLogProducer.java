package com.ddicg.erp.core.event.producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.ddicg.erp.core.common.constants.KafkaTopics;
import com.ddicg.erp.modules.merchandise.dto.ActiveLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveLogProducer {
    private static final Logger log = LoggerFactory.getLogger(ActiveLogProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendLog(ActiveLogDto message) {
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(KafkaTopics.ACTIVE_LOG_TOPIC, message);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Đã gửi thành công message='{}' đến topic='{}' với offset={}",
                            message, KafkaTopics.ACTIVE_LOG_TOPIC, result.getRecordMetadata().offset());
                } else {
                    log.error("Lỗi khi gửi message='{}' đến topic='{}': {}",
                            message, KafkaTopics.ACTIVE_LOG_TOPIC, ex.getMessage());
                }
            });
        } catch (Exception e) {
            log.error("Đã xảy ra lỗi không mong muốn khi gửi ActiveLog: ", e);
        }
    }
}