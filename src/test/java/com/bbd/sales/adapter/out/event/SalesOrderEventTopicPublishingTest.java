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
 * 사진(공유 브로커 kafka.inwoohub.com:9092)에 아직 없는 sales.order.* 이벤트 6종을
 * 실제 브로커에 "생성 + 발행"해서 누락 토픽을 만드는 통합 테스트.
 *
 * <p>왜 AdminClient로 명시 생성까지 하나: 단순 publish는 브로커의 {@code auto.create.topics.enable}가
 * 켜져 있어야만 토픽을 만든다. 꺼져 있으면 발행이 실패할 뿐 토픽은 안 생긴다. 그래서
 * <ol>
 *   <li>AdminClient.createTopics()로 6종을 명시 생성(이미 있으면 멱등 통과),</li>
 *   <li>OutboxPoller.flush()와 동일한 호출로 각 토픽에 실제 이벤트 발행,</li>
 *   <li>listTopics()로 실제 존재를 확인(없으면 조용히 넘기지 않고 '실패')</li>
 * </ol>
 * 한다.
 *
 * <p>스프링 컨텍스트를 안 띄우므로 current user 리졸버(주석 처리됨)·웹 계층·RDS와 무관하다.
 * 토픽 규칙은 운영 코드와 동일한 {@code "sales.order." + eventType}.
 *
 * <p>사진에 이미 있는 토픽: requested, submitted (+ purchase-requested). 아래 6종이 "없어서 생겨야" 하는 상태다.
 *
 * <p>실행(브로커 도달 가능한 환경): {@code ./gradlew test --tests '*SalesOrderEventTopicPublishingTest'}
 * <br>브로커 주소 변경: 환경변수 {@code KAFKA_BOOTSTRAP_SERVERS}. CI 등에서 제외: {@code --exclude-tag integration}.
 */
@Tag("integration")
class SalesOrderEventTopicPublishingTest {

    /** application.yml 기본값과 동일(공유 브로커). 환경변수로 덮어쓸 수 있다. */
    private static final String BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "kafka.inwoohub.com:9092");

    /** 사진에 없는 = 새로 생겨야 하는 sales.order 상태 이벤트들. */
    private static final List<String> MISSING_EVENT_TYPES =
            List.of("updated", "canceled", "fulfilling", "backordered", "rejected", "received");

    private static final List<String> MISSING_TOPICS =
            MISSING_EVENT_TYPES.stream().map(t -> "sales.order." + t).toList();

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
    @DisplayName("사진에 없는 sales.order.* 6종을 브로커에 생성+발행 → 실제 토픽 생성 확인")
    void createAndPublishMissingSalesOrderTopics() throws Exception {
        // 1) 명시 생성 — auto-create 꺼져 있어도 확실히 생성. 이미 있으면 토픽별로 멱등 통과.
        var createResult = admin.createTopics(
                MISSING_TOPICS.stream()
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

        // 2) 각 토픽으로 실제 이벤트 발행 — OutboxPoller.flush()와 동일(key=soNumber, get()로 발행 확인).
        for (String eventType : MISSING_EVENT_TYPES) {
            String topic = "sales.order." + eventType;
            var message = new SalesOrderEventMessage(
                    UUID.randomUUID().toString(), eventType, SO_NUMBER, Instant.now().toString());
            String payload = objectMapper.writeValueAsString(message); // Jackson3 unchecked
            var metadata = kafkaTemplate.send(topic, SO_NUMBER, payload)
                    .get(15, TimeUnit.SECONDS)
                    .getRecordMetadata();
            assertThat(metadata.topic()).isEqualTo(topic);
            System.out.println("[topic-bootstrap] 발행: " + topic
                    + " partition=" + metadata.partition() + " offset=" + metadata.offset());
        }

        // 3) 브로커에 실제로 존재하는지 확인 — 없으면 조용히 넘어가지 않고 실패한다.
        Set<String> existing = admin.listTopics().names().get(15, TimeUnit.SECONDS);
        assertThat(existing).containsAll(MISSING_TOPICS);
        System.out.println("[topic-bootstrap] 존재 확인 완료: " + MISSING_TOPICS);
    }
}
