package com.ddicg.erp.model.embedded;

import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ThreadLocalRandom;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class SkuInfo {
    String sku;

    public SkuInfo createSku(@NonNull String name) {
        return new SkuInfo(this.sku = name.toLowerCase() + (ThreadLocalRandom.current().nextInt(1000, 10000) ));
    }
}
