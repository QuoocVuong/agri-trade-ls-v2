package com.yourcompany.agritradels.usermanagement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "business_profiles", uniqueConstraints = {
        @UniqueConstraint(columnNames = "taxCode")
})
@Getter
@Setter
@NoArgsConstructor
public class BusinessProfile {
    @Id
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String businessName;

    @Column(unique = true, length = 20)
    private String taxCode;

    @Column(length = 100)
    private String industry;

    @Column(length = 20)
    private String businessPhone;

    private String businessAddressDetail;

    @Column(nullable = false, length = 10)
    private String businessProvinceCode;

    @Column(length = 10)
    private String businessDistrictCode;

    @Column(length = 10)
    private String businessWardCode;

    @Column(length = 100)
    private String contactPerson; // Có thể lấy từ user.fullName

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}