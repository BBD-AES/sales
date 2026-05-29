package com.bbd.sales.application.command;

/** 생성/수정 라인 입력. 웹 DTO 가 아니라 application 자체 타입(경계 격리). */
public record SalesOrderLineCommand(
        String sku,
        int quantity
) {
}
