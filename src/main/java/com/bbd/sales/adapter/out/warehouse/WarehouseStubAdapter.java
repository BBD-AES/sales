package com.bbd.sales.adapter.out.warehouse;

import com.bbd.sales.application.port.out.WarehousePort;
import org.springframework.stereotype.Component;

/**
 * 창고 마스터(inventory) 아웃바운드 어댑터(임시 스텁).
 * TODO: inventory 서비스 GET /api/v1/warehouses/{code} REST 호출(.name)로 교체.
 */
@Component
public class WarehouseStubAdapter implements WarehousePort {
    @Override
    public String warehouseName(String warehouseCode) {
        if (warehouseCode == null) return null;
        return warehouseCode + " 창고";
    }
}
