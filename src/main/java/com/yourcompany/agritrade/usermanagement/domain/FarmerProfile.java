package com.yourcompany.agritrade.usermanagement.domain;

import com.yourcompany.agritrade.common.model.VerificationStatus; // Tạo Enum này
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "farmer_profiles")
@Getter
@Setter
@NoArgsConstructor
public class FarmerProfile {
    @Id
    private Long userId; // Trùng với user_id, là khóa chính và khóa ngoại

    @OneToOne(fetch = FetchType.LAZY) // Liên kết ngược lại User
    @MapsId // Sử dụng userId làm khóa ngoại và khóa chính
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String farmName;

    @Lob // Cho các trường text dài
    private String description;

    private String addressDetail;

    @Column(nullable = false, length = 10)
    private String provinceCode;

    @Column(length = 10)
    private String districtCode;

    @Column(length = 10)
    private String wardCode;

    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    private LocalDateTime verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY) // Admin duyệt
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    @Column(nullable = false)
    private boolean canSupplyB2b = false;

    @Lob
    private String b2bCertifications;

    private BigDecimal minB2bOrderValue;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}