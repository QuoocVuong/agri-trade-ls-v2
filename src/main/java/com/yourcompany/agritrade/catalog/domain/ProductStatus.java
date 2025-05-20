package com.yourcompany.agritrade.catalog.domain;

public enum ProductStatus {
  DRAFT, // Bản nháp, chỉ farmer thấy
  PENDING_APPROVAL, // Chờ Admin duyệt
  PUBLISHED, // Đã duyệt, hiển thị công khai
  UNPUBLISHED, // Farmer tạm ẩn
  REJECTED // Bị Admin từ chối
}
