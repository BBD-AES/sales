package com.bbd.sales.adapter.in.web;

import com.bbd.sales.application.port.out.ItemPort;
import com.bbd.sales.application.port.out.ProductSnapshot;
import com.bbd.sales.application.port.out.WarehousePort;
import com.bbd.securitycore.adapter.in.annotation.RequireRole;
import com.bbd.securitycore.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@RequireRole({UserRole.HQ_MANAGER, UserRole.HQ_STAFF, UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF, UserRole.ADMIN})
public class OutboundTestController {

    private final ItemPort itemPort;
    private final WarehousePort warehousePort;

    /**
     * item GET /api/v1/items/{sku} 실호출
     */
    @GetMapping("/item/{sku}")
    public ProductSnapshot item(@PathVariable String sku) {
        return itemPort.resolveProduct(sku);
    }

    /**
     * inventory 창고 GET /api/v1/warehouses/{code} 실호출(실패 시 코드 폴백)
     */

    @GetMapping("/warehouses/{code}")
    public String warehouse(@PathVariable String code) {
        return warehousePort.warehouseName(code);
    }
}
