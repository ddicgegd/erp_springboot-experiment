package com.ddicg.erp.core.security;

import com.ddicg.erp.modules.iam.service.EmailService;
import com.ddicg.erp.core.common.service.MinioService;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.ddicg.erp.modules.merchandise.repository.CategoryRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ProductSeederTest {

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private MinioService minioService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AttributesRepository attributesRepository;

    @Test
    void testDatabaseSeeded() {
        // Assert that seeder ran and database has expected values
        long productCount = productRepository.count();
        long categoryCount = categoryRepository.count();
        long attributesCount = attributesRepository.count();

        System.out.println("Product Count: " + productCount);
        System.out.println("Category Count: " + categoryCount);
        System.out.println("Attributes Count: " + attributesCount);

        assertTrue(productCount >= 5, "Should have at least 5 products seeded");
        assertTrue(categoryCount >= 2, "Should have at least 2 categories seeded");
        assertTrue(attributesCount >= 10, "Should have at least 10 attributes/variants seeded");
    }
}
