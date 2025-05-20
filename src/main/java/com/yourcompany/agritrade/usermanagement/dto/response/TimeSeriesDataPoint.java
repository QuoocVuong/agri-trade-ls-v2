package com.yourcompany.agritrade.usermanagement.dto.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesDataPoint<T> { // Generic để dùng cho cả số lượng và doanh thu
  private LocalDate date; // Ngày (hoặc Tuần/Tháng)
  private T value; // Giá trị (Số lượng đơn hàng hoặc Doanh thu)
}
