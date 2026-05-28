package com.bbd.sales.global.error;

import com.bbd.sales.global.error.dto.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/error")
public class ErrorTestController {
    @GetMapping
    public void test1() {
        throw new ApiException(ErrorCode.TEST_ERROR);
    }

}
