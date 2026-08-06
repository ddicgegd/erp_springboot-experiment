package com.ddicg.erp.service.KafkaService;

import com.ddicg.erp.event.producer.ActiveLogProducer;
import com.ddicg.erp.service.dto.ActiveLogDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveLogService {

    private final ActiveLogProducer activeLogProducer;

    public void sendMessage(ActiveLogDto activeLogDto) {
        ActiveLogDto dto =  ActiveLogDto.builder()
                .performedBy(activeLogDto.getPerformedBy())
                .createdAt(LocalDateTime.now())
                .status(activeLogDto.getStatus())
                .targetID(Optional.ofNullable(activeLogDto.getTargetID()).orElse(Collections.emptyList()))
                .description(activeLogDto.getDescription())
                .build();
        activeLogProducer.sendLog(dto);
    }
}
