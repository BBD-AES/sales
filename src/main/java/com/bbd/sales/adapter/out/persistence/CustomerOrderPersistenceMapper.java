package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.CustomerOrder;
import com.bbd.sales.domain.CustomerOrderLine;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CustomerOrderPersistenceMapper {

    // INSERT: 새 엔티티ㅣ 만들고 불변 필드(co_number, dealer/customer)까지 채움
    public CustomerOrderJpaEntity toNewEntity(CustomerOrder co) {
        CustomerOrderJpaEntity e = new CustomerOrderJpaEntity(
                co.coNumber(),
                co.dealerWarehouseCode(),
                co.dealerName(),
                co.customerName(),
                co.customerContact()
        );
        applyTo(e, co);
        return e;
    }
    // UPDATE: 관리 중인 기존 엔티티에 가변 필드만 반영(version 유지 -> 낙관적 락).
    public void applyTo(CustomerOrderJpaEntity e, CustomerOrder co) {
        e.setStatus(co.status());
        e.setNote(co.note());
        e.setRequestedBy(co.requestedBy());
        e.setConfirmedBy(co.confirmedBy());
        e.setCanceledBy(co.canceledBy());
        e.setClosedBy(co.closedBy());
        e.setRequestedAt(co.requestedAt());
        e.setConfirmedAt(co.confirmedAt());
        e.setCanceledAt(co.canceledAt());
        e.setClosedAt(co.closedAt());

        List<CustomerOrderLineJpaEntity> lineEntities = co.lines().stream()
                .map(l -> new CustomerOrderLineJpaEntity(l.lineNo(), l.sku(), l.nameSnapshot(), l.unitPriceSnapshot(), l.quantity()))
                .toList();
        e.replaceLines(lineEntities);
    }

    public CustomerOrder toDomain(CustomerOrderJpaEntity e) {
        List<CustomerOrderLine> lines = e.getLines().stream()
                .sorted(Comparator.comparingInt(CustomerOrderLineJpaEntity::getLineNo))
                .map(l -> new CustomerOrderLine(l.getLineNo(), l.getSku(), l.getNameSnapshot(), l.getUnitPriceSnapshot(), l.getQuantity()))
                .toList();

        return CustomerOrder.reconstitute(
                e.getCoNumber(), e.getDealerWarehouseCode(), e.getDealerName(), e.getCustomerName(), e.getCustomerContact(),
                e.getStatus(), e.getNote(), lines, e.getRequestedBy(), e.getConfirmedBy(), e.getCanceledBy(), e.getClosedBy(),
                e.getRequestedAt(), e.getConfirmedAt(), e.getCanceledAt(), e.getClosedAt()
        );
    }
}
