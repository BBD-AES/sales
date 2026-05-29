package com.bbd.sales.adapter.out.persistence;

import com.bbd.sales.domain.SalesOrder;
import com.bbd.sales.domain.SalesOrderLine;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 도메인 <-> JPA 엔티티 변환. 두 모델을 분리했기에 필요한 '경계 번역기'.
 * 도메인은 SalesOrder.reconstitute(...) 로 무검증 복원한다(이미 유효한 DB 상태이므로).
 */
@Component
public class SalesOrderPersistenceMapper {

    /** 신규 도메인 -> 새 엔티티. */
    public SalesOrderJpaEntity toNewEntity(SalesOrder so) {
        SalesOrderJpaEntity entity = new SalesOrderJpaEntity();
        entity.setSoNumber(so.soNumber());
        applyMutable(entity, so);
        return entity;
    }

    /** 기존 엔티티에 도메인 상태 반영(업데이트 경로). soNumber/version 은 건드리지 않는다. */
    public void applyTo(SalesOrderJpaEntity entity, SalesOrder so) {
        applyMutable(entity, so);
    }

    private void applyMutable(SalesOrderJpaEntity entity, SalesOrder so) {
        entity.setFromWarehouseCode(so.fromWarehouseCode());
        entity.setToWarehouseCode(so.toWarehouseCode());
        entity.setStatus(so.status());
        entity.setPriority(so.priority());
        entity.setNote(so.note());
        entity.setRequestedBy(so.requestedBy());
        entity.setApprovedBy(so.approvedBy());
        entity.setRejectedBy(so.rejectedBy());
        entity.setReceivedBy(so.receivedBy());
        entity.setCanceledBy(so.canceledBy());
        entity.setRejectReason(so.rejectReason());
        entity.setRequestedAt(so.requestedAt());
        entity.setApprovedAt(so.approvedAt());
        entity.setRejectedAt(so.rejectedAt());
        entity.setReceivedAt(so.receivedAt());
        entity.setCanceledAt(so.canceledAt());

        List<SalesOrderLineJpaEntity> lineEntities = so.lines().stream()
                .map(l -> new SalesOrderLineJpaEntity(
                        l.lineNo(), l.sku(), l.nameSnapshot(), l.unitPriceSnapshot(), l.quantity()))
                .toList();
        entity.replaceLines(lineEntities);
    }

    /** 엔티티 -> 도메인. */
    public SalesOrder toDomain(SalesOrderJpaEntity e) {
        List<SalesOrderLine> lines = e.getLines().stream()
                .sorted(Comparator.comparingInt(SalesOrderLineJpaEntity::getLineNo))
                .map(l -> new SalesOrderLine(
                        l.getLineNo(), l.getSku(), l.getNameSnapshot(), l.getUnitPriceSnapshot(), l.getQuantity()))
                .toList();

        return SalesOrder.reconstitute(
                e.getSoNumber(), e.getFromWarehouseCode(), e.getToWarehouseCode(),
                e.getStatus(), e.getPriority(), e.getNote(), lines,
                e.getRequestedBy(), e.getApprovedBy(), e.getRejectedBy(),
                e.getReceivedBy(), e.getCanceledBy(), e.getRejectReason(),
                e.getRequestedAt(), e.getApprovedAt(), e.getRejectedAt(),
                e.getReceivedAt(), e.getCanceledAt()
        );
    }
}
