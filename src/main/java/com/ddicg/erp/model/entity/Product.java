package com.ddicg.erp.model.entity;

import com.ddicg.erp.model.base.BaseEntity;
import com.ddicg.erp.model.embedded.MediaItem;
import com.ddicg.erp.model.embedded.SkuInfo;
import java.time.LocalDateTime;
import com.ddicg.erp.model.enums.ActiveStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.stereotype.Indexed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Product", indexes = {
                @Index(name = "idx_product_category", columnList = "category_uuid"),
                @Index(name = "idx_product_status", columnList = "status"),
                @Index(name = "idx_product_sku", columnList = "SKU")
})
@Getter
@Setter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Product extends BaseEntity<Long> {
        @Column(name = "name")
        String name;

        /**
         * Thông tin SKU
         * 
         * @en SKU info
         */
        @Embedded
        @Column(nullable = false)
        @Builder.Default
        SkuInfo skuInfo = new SkuInfo();

        /**
         * Danh sách phương tiện truyền thông
         * 
         * @en Media items list
         */
        @Convert(converter = com.ddicg.erp.config.converter.MediaItemListConverter.class)
        @Column(name = "media_items", columnDefinition = "CLOB")
        @Builder.Default
        List<MediaItem> mediaItems = new ArrayList<>();

        /**
         * Danh mục
         * 
         * @en Category
         */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "category_uuid", nullable = false, foreignKey = @ForeignKey(name = "FK_product_category"))
        @ToString.Exclude
        @OnDelete(action = OnDeleteAction.CASCADE)
        @JsonIgnore
        Category category;

        /**
         * Thuộc tính
         * 
         * @en Attributes
         */
        @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
        @Builder.Default
        @JsonIgnore
        List<Attributes> attributes = new ArrayList<>();

        /**
         * Trạng thái
         * 
         * @en Status
         */
        @Enumerated(EnumType.STRING)
        ActiveStatus status;

        /*
         * ============================ 📊 Analytics Fields ============================
         */

        /**
         * Tổng số lượng đã bán
         * 
         * @en Total sold quantity
         */
        @Column(name = "total_sold_quantity")
        @Builder.Default
        Integer totalSoldQuantity = 0;

        /**
         * Tổng doanh thu
         * 
         * @en Total revenue
         */
        @Column(name = "total_revenue")
        @Builder.Default
        BigDecimal totalRevenue = BigDecimal.ZERO;

        /**
         * Tổng số đơn hàng
         * 
         * @en Total orders
         */
        @Column(name = "total_orders")
        @Builder.Default
        Integer totalOrders = 0;

        /**
         * Số lượt xem
         * 
         * @en View count
         */
        @Column(name = "view_count")
        @Builder.Default
        Integer viewCount = 0;

        /**
         * Đánh giá trung bình
         * 
         * @en Average rating
         */
        @Column(name = "average_rating")
        @Builder.Default
        Double averageRating = 0.0;

        /**
         * Số lượng đánh giá
         * 
         * @en Review count
         */
        @Column(name = "review_count")
        @Builder.Default
        Integer reviewCount = 0;

    /*
     * ============================ 🏷️ Discount Fields ============================
     */

    /**
     * Phần trăm giảm giá sản phẩm
     * @en Product discount percent
     */
    @Column(name = "discount_percent")
    @Builder.Default
    Double discountPercent = 0.0;

    /**
     * Ngày bắt đầu giảm giá
     * @en Discount start date
     */
    @Column(name = "discount_start_date")
    LocalDateTime discountStartDate;

    /**
     * Ngày kết thúc giảm giá
     * @en Discount end date
     */
    @Column(name = "discount_end_date")
    LocalDateTime discountEndDate;
}
