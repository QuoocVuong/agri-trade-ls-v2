package com.yourcompany.agritrade.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessProfileRequiredException extends RuntimeException {
    public BusinessProfileRequiredException(String message) {

        super(message);
    }
}
