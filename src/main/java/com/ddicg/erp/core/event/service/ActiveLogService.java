package com.ddicg.erp.core.event.service;

import com.ddicg.erp.core.event.domainevent.ActiveLogDto;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActiveLogService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ActiveLogService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendActiveLogs(String topic, Object key, List<ActiveLogDto> activeLogs) {
        kafkaTemplate.send(topic, String.valueOf(key), activeLogs);
    }
}
