
package com.yourcompany.agritrade.ordering.domain;

import com.yourcompany.agritrade.catalog.domain.Product;
import com.yourcompany.agritrade.usermanagement.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "supply_order_requests")
@Getter
@Setter
@NoArgsConstructor
public class SupplyOrderRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer; // Người mua gửi yêu cầu

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farmer_id", nullable = false)
    private User farmer; // Nông dân nhận yêu cầu

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product; // Sản phẩm được yêu cầu (tham chiếu đến sản phẩm gốc)

    @Column(nullable = false)
    private Integer requestedQuantity;

    @Column(length = 50, nullable = false)
    private String requestedUnit; // Đơn vị người mua yêu cầu (có thể khác với đơn vị sỉ của farmer)

    @Column(precision = 15, scale = 2)
    private BigDecimal proposedPricePerUnit; // Giá người mua đề xuất (tùy chọn)

    @Lob
    private String buyerNotes;

    // Thông tin giao hàng mong muốn của người mua
    private String shippingFullName;
    private String shippingPhoneNumber;
    private String shippingAddressDetail;
    private String shippingProvinceCode;
    private String shippingDistrictCode;
    private String shippingWardCode;
    private LocalDate expectedDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private SupplyOrderRequestStatus status = SupplyOrderRequestStatus.PENDING_FARMER_ACTION;

    @Lob
    private String farmerResponseMessage; // Phản hồi của Farmer nếu từ chối hoặc cần thêm thông tin

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}