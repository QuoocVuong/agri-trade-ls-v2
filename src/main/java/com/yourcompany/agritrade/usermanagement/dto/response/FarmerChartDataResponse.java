package com.yourcompany.agritrade.usermanagement.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FarmerChartDataResponse {

  // Label cho trục X (ví dụ: ngày, tháng, tên sản phẩm...)
  // Sử dụng String cho linh hoạt, hoặc LocalDate nếu chắc chắn là ngày
  private String label;

  // Giá trị cho trục Y (ví dụ: số lượng đơn, doanh thu)
  // Sử dụng BigDecimal để biểu diễn cả số lượng (không phần thập phân) và tiền tệ
  private BigDecimal value;

  // Constructor tiện lợi nếu label là LocalDate
  public FarmerChartDataResponse(LocalDate dateLabel, BigDecimal value) {
    this.label = dateLabel.toString(); // Chuyển LocalDate thành String YYYY-MM-DD
    this.value = value;
  }

  // Constructor tiện lợi nếu value là Long (cho số đếm)
  public FarmerChartDataResponse(String label, Long countValue) {
    this.label = label;
    this.value = BigDecimal.valueOf(countValue); // Chuyển Long thành BigDecimal
  }

  // Constructor tiện lợi nếu value là Long và label là LocalDate
  public FarmerChartDataResponse(LocalDate dateLabel, Long countValue) {
    this.label = dateLabel.toString();
    this.value = BigDecimal.valueOf(countValue);
  }
}
