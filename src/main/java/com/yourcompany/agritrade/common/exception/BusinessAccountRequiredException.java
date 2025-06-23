package com.yourcompany.agritrade.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class BusinessAccountRequiredException extends RuntimeException {
  public BusinessAccountRequiredException(String message) {

    super(message);
  }
}
