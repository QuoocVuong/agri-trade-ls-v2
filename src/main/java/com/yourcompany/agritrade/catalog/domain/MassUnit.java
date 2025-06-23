package com.yourcompany.agritrade.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum MassUnit {
  KG("kg", new BigDecimal("1.0")),
  YEN("yến", new BigDecimal("10.0")), // 1 yến = 10 kg
  TA("tạ", new BigDecimal("100.0")), // 1 tạ = 100 kg
  TAN("tấn", new BigDecimal("1000.0")); // 1 tấn = 1000 kg
  // Thêm các đơn vị khác nếu cần: GAM("g", new BigDecimal("0.001"))

  private final String displayName;
  private final BigDecimal kgFactor; // Hệ số để quy đổi về KG

  MassUnit(String displayName, BigDecimal kgFactor) {
    this.displayName = displayName;
    this.kgFactor = kgFactor;
  }

  public String getDisplayName() {
    return displayName;
  }

  public BigDecimal getKgFactor() {
    return kgFactor;
  }

  /**
   * Quy đổi một số lượng từ đơn vị hiện tại về Kilogram.
   *
   * @param quantity Số lượng theo đơn vị hiện tại.
   * @return Số lượng tương đương tính bằng Kilogram.
   */
  public BigDecimal convertToKg(BigDecimal quantity) {
    if (quantity == null) {
      return BigDecimal.ZERO;
    }
    return quantity
        .multiply(this.kgFactor)
        .setScale(3, RoundingMode.HALF_UP); // Giữ 3 chữ số thập phân cho kg
  }

  /**
   * Quy đổi một số lượng từ Kilogram về đơn vị hiện tại.
   *
   * @param kgQuantity Số lượng tính bằng Kilogram.
   * @return Số lượng tương đương theo đơn vị hiện tại.
   */
  public BigDecimal convertFromKg(BigDecimal kgQuantity) {
    if (kgQuantity == null) {
      return BigDecimal.ZERO;
    }
    if (this.kgFactor.compareTo(BigDecimal.ZERO) == 0) {
      throw new ArithmeticException(
          "Cannot convert from KG: kgFactor is zero for unit " + this.name());
    }
    // Chia với độ chính xác cao hơn, sau đó làm tròn nếu cần
    return kgQuantity.divide(this.kgFactor, 5, RoundingMode.HALF_UP);
  }

  /**
   * Lấy MassUnit enum từ một chuỗi tên đơn vị (không phân biệt hoa thường).
   *
   * @param unitString Chuỗi tên đơn vị (ví dụ: "kg", "Tấn", "YEN")
   * @return MassUnit tương ứng, hoặc null nếu không tìm thấy.
   */
  public static MassUnit fromString(String unitString) {
    if (unitString == null || unitString.trim().isEmpty()) {
      return null;
    }
    String normalizedUnit = unitString.trim().toLowerCase();
    for (MassUnit unit : values()) {
      if (unit.name().equalsIgnoreCase(normalizedUnit)
          || unit.getDisplayName().equalsIgnoreCase(normalizedUnit)) {
        return unit;
      }
    }
    // Thử một số biến thể phổ biến
    if ("kilogram".equalsIgnoreCase(normalizedUnit)) return KG;
    // Thêm các alias khác nếu cần
    return null; // Hoặc ném IllegalArgumentException
  }
}
