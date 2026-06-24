package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.SalesOrder;
import com.bbd.sales.domain.SalesOrderLine;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** 도메인 <-> JPA 엔티티 변환(경계 번역기). 복원은 SalesOrder.reconstitute 로 무검증 재구성. */
@Component
public class SalesOrderPersistenceMapper {

    public SalesOrderJpaEntity toNewEntity(SalesOrder so) {
        SalesOrderJpaEntity entity = new SalesOrderJpaEntity(
                so.soNumber(), so.toWarehouseCode(), so.toWarehouseName()
        );
        applyTo(entity, so);
        return entity;
    }

    public void applyTo(SalesOrderJpaEntity entity, SalesOrder so) {
        entity.setStatus(so.status());
        entity.setPriority(so.priority());
        entity.setNote(so.note());
        entity.setCustomerOrderNumber(so.customerOrderNumber());
        entity.setRequestedBy(so.requestedBy());
        entity.setApprovedBy(so.approvedBy());
        entity.setRejectedBy(so.rejectedBy());
        entity.setReceivedBy(so.receivedBy());
        entity.setCanceledBy(so.canceledBy());
        entity.setRejectedReason(so.rejectedReason());
        entity.setRequestedAt(so.requestedAt());
        entity.setApprovedAt(so.approvedAt());
        entity.setRejectedAt(so.rejectedAt());
        entity.setReceivedAt(so.receivedAt());
        entity.setCanceledAt(so.canceledAt());

        List<SalesOrderLineJpaEntity> lineEntities = so.lines().stream()
                .map(l -> {
                    SalesOrderLineJpaEntity le = new SalesOrderLineJpaEntity(
                            l.lineNo(), l.sku(), l.nameSnapshot(), l.unitPriceSnapshot(), l.sourcingType(), l.quantity());
                    le.setReservedQuantity(l.reservedQuantity());
                    le.setFulfillmentSource(l.fulfillmentSource());
                    return le;
                })
                .toList();
        entity.replaceLines(lineEntities);
    }

    public SalesOrder toDomain(SalesOrderJpaEntity e) {
        List<SalesOrderLine> lines = e.getLines().stream()
                .sorted(Comparator.comparingInt(SalesOrderLineJpaEntity::getLineNo))
                .map(l -> {
                    SalesOrderLine line = new SalesOrderLine(
                            l.getLineNo(), l.getSku(), l.getNameSnapshot(), l.getUnitPriceSnapshot(),
                            l.getSourcingType(), l.getQuantity());
                    line.restore(l.getReservedQuantity(), l.getFulfillmentSource());  // 저장 상태 복원(파생X)
                    return line;
                })
                .toList();

        return SalesOrder.reconstitute(
                e.getSoNumber(),
                e.getToWarehouseCode(), e.getToWarehouseName(),
                e.getStatus(), e.getPriority(), e.getNote(), e.getCustomerOrderNumber(), lines,
                e.getRequestedBy(), e.getApprovedBy(), e.getRejectedBy(),
                e.getReceivedBy(), e.getCanceledBy(), e.getRejectedReason(),
                e.getRequestedAt(), e.getApprovedAt(), e.getRejectedAt(),
                e.getReceivedAt(), e.getCanceledAt());
    }
}
