-- 데모 시드: 출고요청(수주). seed 상태값을 현재 모델 enum 으로 정렬.
--   매핑: DELIVERED->RECEIVED, SHIPPED/APPROVED->IN_FULFILLMENT, REQUESTED(본사검토대기)->SUBMITTED
--   (+ BACKORDERED/REJECTED 예시 추가)
-- 멱등: 주문은 ON CONFLICT(so_number) DO NOTHING, 라인은 NOT EXISTS 가드.
-- spring.sql.init.mode=always + spring.jpa.defer-datasource-initialization=true 와 함께 기동 시 실행.
-- 주의: 기존에 옛 시드(옛 상태값)가 이미 있으면 DO NOTHING 이라 안 덮음 -> 정렬하려면 두 테이블 TRUNCATE 후 재기동.

INSERT INTO sales_order (version, so_number, to_warehouse_code, to_warehouse_name, status, priority, note, requested_by, approved_by, received_by, requested_at, approved_at, received_at)
VALUES (0, 'SO-2026-0001', 'WH-BR-001', '강남 1지점', 'RECEIVED', 'NORMAL', '정상 도착 완료', 'BR003', 'HQ001', 'BR003', '2026-04-20 09:00:00', '2026-04-21 09:00:00', '2026-04-22 09:00:00')
ON CONFLICT (so_number) DO NOTHING;

INSERT INTO sales_order (version, so_number, to_warehouse_code, to_warehouse_name, status, priority, note, requested_by, approved_by, requested_at, approved_at)
VALUES (0, 'SO-2026-0002', 'WH-BR-001', '강남 1지점', 'IN_FULFILLMENT', 'NORMAL', '출고 진행 중', 'BR003', 'HQ001', '2026-05-15 09:00:00', '2026-05-16 09:00:00')
ON CONFLICT (so_number) DO NOTHING;

INSERT INTO sales_order (version, so_number, to_warehouse_code, to_warehouse_name, status, priority, note, requested_by, requested_at)
VALUES (0, 'SO-2026-0003', 'WH-BR-002', '분당 1지점', 'SUBMITTED', 'URGENT', '본사 검토 대기', 'BR002', '2026-05-22 09:00:00')
ON CONFLICT (so_number) DO NOTHING;

INSERT INTO sales_order (version, so_number, to_warehouse_code, to_warehouse_name, status, priority, note, requested_by, approved_by, requested_at, approved_at)
VALUES (0, 'SO-2026-0004', 'WH-BR-003', '부산 1지점', 'BACKORDERED', 'NORMAL', '릴레이 무재고 -> 구매요청(PR) 대기', 'BR003', 'HQ001', '2026-05-23 09:00:00', '2026-05-24 09:00:00')
ON CONFLICT (so_number) DO NOTHING;

INSERT INTO sales_order (version, so_number, to_warehouse_code, to_warehouse_name, status, priority, note, requested_by, rejected_by, rejected_reason, requested_at, rejected_at)
VALUES (0, 'SO-2026-0005', 'WH-BR-001', '강남 1지점', 'REJECTED', 'NORMAL', '반려 예시', 'BR003', 'HQ001', '예산 한도 초과', '2026-05-24 09:00:00', '2026-05-25 09:00:00')
ON CONFLICT (so_number) DO NOTHING;

INSERT INTO sales_order (version, so_number, to_warehouse_code, to_warehouse_name, status, priority, note, requested_by, requested_at)
VALUES (0, 'SO-2026-0006', 'WH-BR-004', '대구 1지점', 'REQUESTED', 'NORMAL', '지점 작성 중', 'BR004', '2026-05-25 09:00:00')
ON CONFLICT (so_number) DO NOTHING;

-- 라인 (해당 주문에 라인이 없을 때만 삽입 -> 멱등)
INSERT INTO sales_order_line (so_number, line_no, sku, name_snapshot, unit_price_snapshot, quantity, reserved_quantity, fulfillment_source)
SELECT so.so_number, 1, 'OIL-FLT-001', '오일필터', 3200, 50, 50, 'STOCK' FROM sales_order so
WHERE so.so_number = 'SO-2026-0001' AND NOT EXISTS (SELECT 1 FROM sales_order_line l WHERE l.so_number = so.so_number);

INSERT INTO sales_order_line (so_number, line_no, sku, name_snapshot, unit_price_snapshot, quantity, reserved_quantity, fulfillment_source)
SELECT so.so_number, 1, 'BRK-PAD-RR-001', '브레이크 패드 (후륜)', 35000, 15, 15, 'STOCK' FROM sales_order so
WHERE so.so_number = 'SO-2026-0002' AND NOT EXISTS (SELECT 1 FROM sales_order_line l WHERE l.so_number = so.so_number);

INSERT INTO sales_order_line (so_number, line_no, sku, name_snapshot, unit_price_snapshot, quantity, reserved_quantity, fulfillment_source)
SELECT so.so_number, 1, 'BAT-12V-60', '배터리 12V 60Ah', 95000, 10, 0, NULL FROM sales_order so
WHERE so.so_number = 'SO-2026-0003' AND NOT EXISTS (SELECT 1 FROM sales_order_line l WHERE l.so_number = so.so_number);

INSERT INTO sales_order_line (so_number, line_no, sku, name_snapshot, unit_price_snapshot, quantity, reserved_quantity, fulfillment_source)
SELECT so.so_number, 1, 'RLY-12V-30A-01', '릴레이 12V 30A', 8500, 20, 0, 'PURCHASE' FROM sales_order so
WHERE so.so_number = 'SO-2026-0004' AND NOT EXISTS (SELECT 1 FROM sales_order_line l WHERE l.so_number = so.so_number);

INSERT INTO sales_order_line (so_number, line_no, sku, name_snapshot, unit_price_snapshot, quantity, reserved_quantity, fulfillment_source)
SELECT so.so_number, 1, 'TIR-225-45-17', '타이어 225/45 R17', 135000, 5, 0, NULL FROM sales_order so
WHERE so.so_number = 'SO-2026-0005' AND NOT EXISTS (SELECT 1 FROM sales_order_line l WHERE l.so_number = so.so_number);

INSERT INTO sales_order_line (so_number, line_no, sku, name_snapshot, unit_price_snapshot, quantity, reserved_quantity, fulfillment_source)
SELECT so.so_number, 1, 'WSH-FLU-2L', '워셔액 2L', 4500, 30, 0, NULL FROM sales_order so
WHERE so.so_number = 'SO-2026-0006' AND NOT EXISTS (SELECT 1 FROM sales_order_line l WHERE l.so_number = so.so_number);
