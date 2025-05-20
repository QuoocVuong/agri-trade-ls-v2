package com.yourcompany.agritrade.catalog.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_pricing_tiers")
@Getter
@Setter
@NoArgsConstructor
public class ProductPricingTier {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Column(nullable = false)
  private Integer minQuantity;

  @Column(nullable = false, precision = 15, scale = 2)
  private BigDecimal pricePerUnit;
}
