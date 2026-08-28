package com.ddicg.erp.core.security;

import com.ddicg.erp.core.common.model.embedded.SkuInfo;
import com.ddicg.erp.core.common.model.embedded.SpecificationGroup;
import com.ddicg.erp.core.common.model.embedded.Specification;
import com.ddicg.erp.core.common.model.enums.ActiveStatus;
import com.ddicg.erp.core.common.model.enums.StockStatus;
import com.ddicg.erp.modules.merchandise.model.Category;
import com.ddicg.erp.modules.merchandise.model.Product;
import com.ddicg.erp.modules.merchandise.model.Attributes;
import com.ddicg.erp.modules.merchandise.repository.CategoryRepository;
import com.ddicg.erp.modules.merchandise.repository.ProductRepository;
import com.ddicg.erp.modules.merchandise.repository.AttributesRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ProductSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final AttributesRepository attributesRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (productRepository.count() > 0) {
            log.debug("Products already exist in the database. Skipping ProductSeeder.");
            return;
        }

        log.debug("Checking for scraped_products.json to seed CellphoneS data...");

        ClassPathResource resource = new ClassPathResource("scraped_products.json");
        if (!resource.exists()) {
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Map<String, Object>> productsData = objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            
            log.debug("Found {} products in scraped_products.json. Seeding database...", productsData.size());

            for (Map<String, Object> productData : productsData) {
                String catName = (String) productData.get("category_name");
                String catSku = (String) productData.get("category_sku");
                
                Category category = categoryRepository.findCategoryBySkuInfo_Sku(catSku)
                        .orElseGet(() -> {
                            Category newCat = Category.builder()
                                    .name(catName)
                                    .skuInfo(new SkuInfo(catSku))
                                    .createdBy("SYSTEM")
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            log.info("Seeded new Category: {} ({})", catName, catSku);
                            return categoryRepository.save(newCat);
                        });
                        
                String prdName = (String) productData.get("product_name");
                String cleanPrdName = prdName.split("\\|")[0].strip();
                // Create a unique SKU for product
                String prdSku = "prd-" + catSku.substring(catSku.length() - 2) + "-" + UUID.randomUUID().toString().substring(0, 8);
                
                Product product = Product.builder()
                        .name(cleanPrdName)
                        .category(category)
                        .skuInfo(new SkuInfo(prdSku))
                        .status(ActiveStatus.ACTIVE)
                        .createdBy("SYSTEM")
                        .createdAt(LocalDateTime.now())
                        .build();
                        
                product = productRepository.save(product);
                log.info("Seeded new Product: {} ({})", cleanPrdName, prdSku);
                
                // Parse specifications
                List<Map<String, String>> specsData = (List<Map<String, String>>) productData.get("specifications");
                List<SpecificationGroup> specGroups = new ArrayList<>();
                if (specsData != null && !specsData.isEmpty()) {
                    List<Object> specList = new ArrayList<>();
                    for (Map<String, String> spec : specsData) {
                        specList.add(new Specification(spec.get("key"), spec.get("data")));
                    }
                    specGroups.add(new SpecificationGroup("Thông số kỹ thuật", specList));
                }
                
                // Parse variants (which will become Attributes in the DB)
                List<Map<String, Object>> variantsData = (List<Map<String, Object>>) productData.get("variants");
                if (variantsData != null) {
                    for (Map<String, Object> varData : variantsData) {
                        String varName = (String) varData.get("name");
                        Double salePrice = ((Number) varData.get("sale_price")).doubleValue();
                        Double price = ((Number) varData.get("price")).doubleValue();
                        
                        String attrSku = "attr-" + prdSku.substring(prdSku.length() - 3) + "-" + UUID.randomUUID().toString().substring(0, 8);
                        
                        Attributes attributes = Attributes.builder()
                                .product(product)
                                .sku(new SkuInfo(attrSku))
                                .name(varName)
                                .price(price)
                                .salePrice(salePrice)
                                .specifications(specGroups)
                                .statusProduct(StockStatus.AVAILABLE)
                                .createdBy("SYSTEM")
                                .createdAt(LocalDateTime.now())
                                .build();
                                
                        attributesRepository.save(attributes);
                        log.info("  Seeded variant/attribute: {} (Price: {}, SalePrice: {})", varName, price, salePrice);
                    }
                }
            }
            log.info("Successfully seeded all CellphoneS products and attributes!");
        } catch (Exception e) {
            log.error("Failed to seed product data", e);
        }
    }
}
