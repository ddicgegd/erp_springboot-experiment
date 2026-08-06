package com.ddicg.erp.service.dto;

import com.ddicg.erp.model.embedded.SkuInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for {@link SkuInfo}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuInfoDto implements Serializable {
    @JsonProperty("sku")
    private String sku;
}