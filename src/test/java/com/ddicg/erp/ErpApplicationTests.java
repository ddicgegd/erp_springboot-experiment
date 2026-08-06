package com.ddicg.erp;

import com.ddicg.erp.modules.iam.service.EmailService;
import com.ddicg.erp.core.common.service.MinioService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ErpApplicationTests {

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MinioService minioService;

    @Test
    void contextLoads() {
    }
}
