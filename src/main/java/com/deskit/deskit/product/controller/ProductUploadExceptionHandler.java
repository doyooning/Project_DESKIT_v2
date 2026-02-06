package com.deskit.deskit.product.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = "com.deskit.deskit.product.controller")
public class ProductUploadExceptionHandler {

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<String> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
    return ResponseEntity
      .status(HttpStatus.PAYLOAD_TOO_LARGE)
      .body("파일 크기가 제한을 초과했습니다.");
  }
}
