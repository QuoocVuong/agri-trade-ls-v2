package com.yourcompany.agritrade.common.service;

import com.yourcompany.agritrade.ordering.dto.response.OrderSummaryResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelExportService {

  public ByteArrayInputStream ordersToExcel(List<OrderSummaryResponse> orders) throws IOException {
    String[] COLUMNS = {
      "Mã ĐH", "Ngày đặt", "Người mua", "Người bán", "Tổng tiền", "Trạng thái ĐH", "Trạng thái TT"
    };

    try (Workbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("DonHang");

      // Header
      Row headerRow = sheet.createRow(0);
      for (int col = 0; col < COLUMNS.length; col++) {
        Cell cell = headerRow.createCell(col);
        cell.setCellValue(COLUMNS[col]);
      }

      // Data
      int rowIdx = 1;
      for (OrderSummaryResponse order : orders) {
        Row row = sheet.createRow(rowIdx++);
        row.createCell(0).setCellValue(order.getOrderCode());
        row.createCell(1)
            .setCellValue(
                order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        row.createCell(2).setCellValue(order.getBuyerName());
        row.createCell(3).setCellValue(order.getFarmerName());
        row.createCell(4).setCellValue(order.getTotalAmount().doubleValue());
        row.createCell(5)
            .setCellValue(order.getStatus() != null ? order.getStatus().name() : "N/A");
        row.createCell(6)
            .setCellValue(
                order.getPaymentStatus() != null ? order.getPaymentStatus().name() : "N/A");
      }

      workbook.write(out);
      return new ByteArrayInputStream(out.toByteArray());
    }
  }
}
