package com.yourcompany.agritradels.usermanagement.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate; // Hoặc String tùy cách bạn muốn trả về ngày

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesDataPoint<T> { // Generic để dùng cho cả số lượng và doanh thu
    private LocalDate date; // Ngày (hoặc Tuần/Tháng)
    private T value;      // Giá trị (Số lượng đơn hàng hoặc Doanh thu)
}