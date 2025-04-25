package com.yourcompany.agritrade.usermanagement.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "addresses") // Tên bảng đã tạo trong migration V2
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE addresses SET is_deleted = true WHERE id = ?") // Hỗ trợ soft delete
@Where(clause = "is_deleted = false") // Tự động lọc địa chỉ chưa xóa
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // Liên kết với User sở hữu
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String fullName; // Tên người nhận

    @Column(nullable = false, length = 20)
    private String phoneNumber; // SĐT người nhận

    @Column(nullable = false)
    private String addressDetail; // Số nhà, tên đường...

    @Column(nullable = false, length = 10)
    private String provinceCode;

    @Column(nullable = false, length = 10)
    private String districtCode;

    @Column(nullable = false, length = 10)
    private String wardCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddressType type = AddressType.SHIPPING; // Tạo Enum này

    @Column(nullable = false)
    private boolean isDefault = false; // Là địa chỉ mặc định không?

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean isDeleted = false; // Cờ xóa mềm

    // equals và hashCode nên dựa trên ID nếu có, hoặc các trường nội dung nếu cần so sánh
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(id, address.id); // So sánh theo ID là đủ khi đã có ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Hash theo ID
    }
}