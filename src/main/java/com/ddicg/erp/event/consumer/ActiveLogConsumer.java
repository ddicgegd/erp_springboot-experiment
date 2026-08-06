//package com.ddicg.erp.event.consumer;
//
//import com.ddicg.erp.common.constants.KafkaTopics;
//import com.ddicg.erp.service.dto.ActiveLogDto;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.kafka.annotation.KafkaListener;
//import org.springframework.stereotype.Component;
//
//@Slf4j
//@Component
//public class ActiveLogConsumer {
//
//    // Bên trong file MultiTypeConsumer.java của bạn
//
//    @KafkaListener(
//            topics = KafkaTopics.ACTIVE_LOG_TOPIC,
//            groupId = "log-group",
//            containerFactory = "kafkaListenerContainerFactory"
//    )
//    public void consume(ActiveLogDto message) { // Tham số đầu vào là đối tượng message
//        // THAY THẾ DÒNG LOG CŨ CỦA BẠN BẰNG CÁC DÒNG SAU:
//        log.info("✅ Consumer đã nhận được một ActiveLog:");
//        log.info("   -> Người thực hiện: {}", message.getPerformedBy());
//        log.info("   -> Mô tả: {}", message.getDescription());
//        log.info("   -> Thời gian: {}", message.getCreatedAt());
//        log.info("   -> Trạng thái: {}", message.getStatus());
//    }
//}