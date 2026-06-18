package com.bbd.sales.adapter.out.event;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공유 브로커(kafka.inwoohub.com:9092)에 <b>sales가 실제로 의존하는 토픽만</b> 멱등 생성하고
 * 존재를 검증하는 통합 테스트.
 *
 * <p><b>왜 바뀌었나</b>: 예전엔 sales.order.* 상태 이벤트 6종(updated/canceled/fulfilling/
 * backordered/rejected/received)을 생성했지만, 그 발행 경로는 커밋 af5674d("안 쓰는 이벤트 발행 제거")로
 * 모두 삭제됐다(SalesOrderEventPublisher 에는 이제 publishSubmitted 만 남음). 그 6개는 sales가
 * 더 이상 발행하지 않는 <b>죽은 토픽</b>이므로 공유 브로커에 만들 이유가 없다.
 *
 * <p><b>현재 sales가 실제로 필요로 하는 토픽(발행+구독) = 3개</b>:
 * <ul>
 *   <li>{@code sales.order.submitted} — 발행(submit→publishSubmitted) + 자가 구독(HqNotificationListener). sales 소유.</li>
 *   <li>{@code sales.purchase-requested} — 발행(approve 부족분→procurement 계약, Line.sourcingType 힌트 포함). sales 소유.</li>
 *   <li>{@code inventory.stock-replenished} — 구독(StockReplenishedListener, 백오더 트리거). 발행 주체는 inventory(여기선
 *       컨슈머 어태치 지점 확보용으로 멱등 생성만; 운영 발행은 inventory).</li>
 * </ul>
 *
 * <p><b>일부러 제외</b>:
 * <ul>
 *   <li>{@code sales.order.{requested,updated,canceled,fulfilling,backordered,rejected,received}} — 발행 제거됨(죽음).</li>
 *   <li>{@code sales.stock-out-requested} — 동기 REST 예약/출고(issue)로 대체됨(이벤트 아님, inventory_stock_reservation_handoff §3.4).</li>
 *   <li>{@code procurement.stock-in-requested} — sales 직접 구독 폐기, inventory.stock-replenished 로 변경(inventory-stock-replenished 핸드오프).</li>
 * </ul>
 *
 * <p>왜 AdminClient로 명시 생성까지 하나: 단순 publish는 브로커의 {@code auto.create.topics.enable}가
 * 켜져 있어야만 토픽을 만든다. 꺼져 있으면 발행이 실패할 뿐 토픽은 안 생긴다. 그래서
 * (1) AdminClient.createTopics()로 필요한 3종을 명시 생성(이미 있으면 멱등 통과),
 * (2) 스키마가 SalesOrderEventMessage인 sales.order.submitted 에만 실제 발행 스모크,
 * (3) listTopics()로 3종 존재를 확인(없으면 조용히 넘기지 않고 '실패')한다.
 *
 * <p>스프링 컨텍스트를 안 띄우므로 current user 리졸버·웹 계층·RDS와 무관하다.
 *
 * <p>실행(브로커 도달 가능한 환경): {@code ./gradlew test --tests '*SalesOrderEventTopicPublishingTest'}
 * <br>브로커 주소 변경: 환경변수 {@code KAFKA_BOOTSTRAP_SERVERS}. CI 등에서 제외: {@code --exclude-tag integration}.
 */
@Tag("integration")
class SalesOrderEventTopicPublishingTest {

    /** application.yml 기본값과 동일(공유 브로커). 환경변수로 덮어쓸 수 있다. */
    private static final String BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka.inwoohub.com:9092");

    /** sales가 발행하는 내부 알림 토픽(자가 구독). 운영 코드 규칙 "sales.order." + eventType. */
    private static final String TOPIC_SUBMITTED = "sales.order.submitted";
    /** sales가 발행하는 procurement 계약 토픽(부족분 구매/생산 요청). */
    private static final String TOPIC_PURCHASE_REQUESTED = "sales.purchase-requested";
    /** sales가 구독하는 백오더 보충 통지(발행 주체 inventory). */
    private static final String TOPIC_STOCK_REPLENISHED = "inventory.stock-replenished";

    /** 현재 sales가 실제로 필요로 하는 토픽만(발행 2 + 구독 1). 이 집합만 생성/검증한다. */
    private static final List<String> REQUIRED_TOPICS =
            List.of(TOPIC_SUBMITTED, TOPIC_PURCHASE_REQUESTED, TOPIC_STOCK_REPLENISHED);

    /** 파티션 키(같은 주문 같은 파티션). 운영 publish와 동일하게 soNumber. */
    private static final String SO_NUMBER = "SO-2026-000042";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private static AdminClient admin;
    private static DefaultKafkaProducerFactory<String, String> producerFactory;
    private static KafkaTemplate<String, String> kafkaTemplate;

    @BeforeAll
    static void setUp() {
        Map<String, Object> adminProps = new HashMap<>();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        adminProps.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 20_000);
        admin = AdminClient.create(adminProps);

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 15_000); // 미도달 시 빨리 실패(조용한 무한대기 방지)
        producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @AfterAll
    static void tearDown() {
        if (producerFactory != null) producerFactory.destroy();
        if (admin != null) admin.close();
    }

    @Test
    @DisplayName("필요한 토픽 3종(submitted·purchase-requested·stock-replenished)만 멱등 생성 → 존재 확인")
    void createRequiredTopicsOnly() throws Exception {
        // 1) 필요한 토픽만 명시 생성 — auto-create 꺼져 있어도 확실히 생성. 이미 있으면 토픽별로 멱등 통과.
        var createResult = admin.createTopics(
                REQUIRED_TOPICS.stream()
                        .map(name -> new NewTopic(name, Optional.empty(), Optional.empty())) // 브로커 기본 파티션/복제수
                        .toList());
        for (var entry : createResult.values().entrySet()) {
            try {
                entry.getValue().get(20, TimeUnit.SECONDS);
                System.out.println("[topic-bootstrap] 생성: " + entry.getKey());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof TopicExistsException) {
                    System.out.println("[topic-bootstrap] 이미 존재(멱등): " + entry.getKey());
                } else {
                    throw e; // 권한/연결 등 실제 실패는 그대로 터뜨린다(조용한 스킵 없음)
                }
            }
        }

        // 2) 발행 스모크는 SalesOrderEventMessage 스키마인 sales.order.submitted 에만.
        //    purchase-requested(PurchaseRequested)/stock-replenished(StockReplenished)는 스키마가 달라
        //    더미 발행하지 않는다(공유 브로커에 잘못된 페이로드 오염 방지). 생성/존재만 보장한다.
        var message = new SalesOrderEventMessage(
                UUID.randomUUID().toString(), "submitted", SO_NUMBER, Instant.now().toString());
        String payload = objectMapper.writeValueAsString(message); // Jackson3 unchecked
        var metadata = kafkaTemplate.send(TOPIC_SUBMITTED, SO_NUMBER, payload)
                .get(15, TimeUnit.SECONDS)
                .getRecordMetadata();
        assertThat(metadata.topic()).isEqualTo(TOPIC_SUBMITTED);
        System.out.println("[topic-bootstrap] 발행: " + TOPIC_SUBMITTED
                + " partition=" + metadata.partition() + " offset=" + metadata.offset());

        // 3) 필요한 3종이 실제로 존재하는지 확인 — 없으면 조용히 넘어가지 않고 실패한다.
        Set<String> existing = admin.listTopics().names().get(15, TimeUnit.SECONDS);
        assertThat(existing).containsAll(REQUIRED_TOPICS);
        System.out.println("[topic-bootstrap] 존재 확인 완료: " + REQUIRED_TOPICS);
    }
}
