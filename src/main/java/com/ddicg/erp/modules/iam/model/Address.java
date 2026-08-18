package com.ddicg.erp.modules.iam.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.core.common.model.embedded.AuditInfo;
import com.ddicg.erp.core.common.util.UUIDv7Generator;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "addresses", indexes = {
        @Index(name = "idx_address_user", columnList = "user_id"),
        @Index(name = "idx_address_sku", columnList = "sku", unique = true)
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Address extends IdentityOnly<Long> {

    @Column(name = "sku", unique = true, nullable = false, length = 100)
    String sku;

    @Column(name = "shipping_address", length = 500)
    String address;

    @Column(name = "latitude")
    Double latitude;

    @Column(name = "longitude")
    Double longitude;

    @Column(name = "shipping_phone", length = 20)
    String phoneNumber;

    @Column(name = "shipping_recipient_name", length = 200)
    String recipientName;

    @Column(name = "is_default")
    @Builder.Default
    Boolean isDefault = false;

    @ManyToOne(targetEntity = User.class)
    @JoinColumn(name = "user_id")
    User user;

    @Embedded
    @Builder.Default
    AuditInfo auditInfo = new AuditInfo();

    @PrePersist
    public void prePersist() {
        if (sku == null || sku.isBlank()) {
            this.sku = "ADDR-" + UUIDv7Generator.generate().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
    }
}
