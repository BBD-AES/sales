package com.bbd.sales.global.error.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    TEST_ERROR(HttpStatus.NOT_FOUND, "T001", "찾을 수 없습니다.")
;
    private final HttpStatus status;
    private final String code;
    private final String message;
}
