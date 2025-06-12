package com.yourcompany.agritrade.common.exception;

import lombok.Getter;

@Getter
public class OutOfStockException extends RuntimeException {

  private final Integer availableStock;

  public OutOfStockException(String message, Integer availableStock) {
    super(message);
    this.availableStock = availableStock;
  }
}
