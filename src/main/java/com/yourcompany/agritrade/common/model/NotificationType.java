package com.yourcompany.agritrade.common.model;

public enum NotificationType {
  // Order related
  ORDER_PLACED,
  ORDER_STATUS_UPDATE,
  ORDER_CANCELLED,
  // Payment related
  PAYMENT_SUCCESS,
  PAYMENT_FAILURE,
  // Chat related
  NEW_MESSAGE,
  // Follow related
  NEW_FOLLOWER,
  // Product related
  PRODUCT_APPROVED,
  PRODUCT_REJECTED,
  // Review related
  REVIEW_APPROVED,
  REVIEW_REJECTED,
  // User related
  WELCOME,
  PASSWORD_RESET,
  EMAIL_VERIFIED,
  // System/Admin related
  SYSTEM_ANNOUNCEMENT,
  PROMOTION,
  REVIEW_PENDING,
  FARMER_PROFILE_APPROVED,
  FARMER_PROFILE_REJECTED,
  INVOICE_ISSUED,
  INVOICE_PAID,
  INVOICE_OVERDUE,
  INVOICE_DUE_SOON,
  ADMIN_ALERT,
  OTHER
}
