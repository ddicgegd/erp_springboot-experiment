package com.ddicg.erp.modules.iam.model;

import com.ddicg.erp.core.common.model.base.IdentityOnly;
import com.ddicg.erp.core.common.model.embedded.AuditInfo;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "addresses", indexes = {
        @Index(name = "idx_address_user", columnList = "user_id"),
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Address extends IdentityOnly<Long> {

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

    @ManyToOne(targetEntity = User.class)
    @JoinColumn(name = "user_id")
    User user;

    @Embedded
    @Builder.Default
    AuditInfo auditInfo = new AuditInfo();

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public AuditInfo getAuditInfo() { return auditInfo; }
    public void setAuditInfo(AuditInfo auditInfo) { this.auditInfo = auditInfo; }

    public void setStreet(String s) {}
    public void setWard(String w) {}
    public void setDistrict(String d) {}
    public void setCity(String c) {}
    public void setCountry(String c) {}

    public static AddressBuilder builder() { return new AddressBuilder(); }
    public static class AddressBuilder {
        private String recipientName;
        private String phoneNumber;
        private String address;
        private User user;
        AddressBuilder() {}
        public AddressBuilder recipientName(String recipientName) { this.recipientName = recipientName; return this; }
        public AddressBuilder phoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; return this; }
        public AddressBuilder address(String address) { this.address = address; return this; }
        public AddressBuilder user(User user) { this.user = user; return this; }
        public Address build() {
            Address a = new Address();
            a.setRecipientName(recipientName);
            a.setPhoneNumber(phoneNumber);
            a.setAddress(address);
            a.setUser(user);
            return a;
        }
    }
}
