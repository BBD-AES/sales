package com.bbd.sales.adapter.out.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 헥사고날에서 위치: 인프라(2차 persistence)
 * 헥사고날에서 OutboxEvent와 함께 "보내야 할 이벤트"를 DB에 내구성 있게 적재
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100BySentFalseOrderByIdAsc();
}
