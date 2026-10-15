package com.modeldock;

public class ApiException extends RuntimeException {
  final int status;
  final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  static ApiException missing() {
    return new ApiException(404, "NOT_FOUND", "요청한 항목을 찾을 수 없습니다.");
  }
}
