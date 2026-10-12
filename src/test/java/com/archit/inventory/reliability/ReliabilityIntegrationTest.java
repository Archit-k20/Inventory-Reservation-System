package com.archit.inventory.reliability;

import com.archit.inventory.event.*;
import com.archit.inventory.product.*;
import com.archit.inventory.reservation.*;
import com.archit.inventory.support.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeout;

@TestPropertySource(properties = "inventory.outbox.enabled=false")
class ReliabilityIntegrationTest extends AbstractIntegrationTest {
    @Autowired ProductService products;
    @Autowired ReservationService reservations;
    @Autowired InventoryEventPublisher publisher;
    @Autowired OutboxDelivery delivery;
    @Autowired FailedKafkaRecordStore failures;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CacheManager caches;

    @BeforeEach
    void isolateOutbox() {
        jdbc.update("DELETE FROM event_outbox");
    }

    @Test
    void rolledBackReservationCannotLeakAnEvent() {
        var product = createProduct(10);
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            reservations.reserve(new ReserveInventoryRequest(product.id(), "rollback-user", 2));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_outbox", Long.class)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(products.get(product.id()).availableStock()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE product_id = ?", Long.class, product.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM event_outbox", Long.class)).isZero();
        assertThat(delivery.publishNext()).isFalse();
    }

    @Test
    void redisOutageCannotFailWritesOrResurrectOldStock() {
        var product = createProduct(10);
        assertThat(products.get(product.id()).availableStock()).isEqualTo(10);
        var cache = caches.getCache("product-snapshots-v2");
        assertThat(cache).isNotNull();
        assertThat(cache.get(product.id() + ":0", ProductResponse.class).availableStock()).isEqualTo(10);
        var docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            assertTimeout(Duration.ofSeconds(5), () -> {
                var reservation = reservations.reserve(new ReserveInventoryRequest(product.id(), "redis-down", 2));
                assertThat(reservations.confirm(reservation.reservationId()).status()).isEqualTo(ReservationStatus.CONFIRMED);
                var release = reservations.reserve(new ReserveInventoryRequest(product.id(), "redis-down-release", 3));
                assertThat(reservations.release(release.reservationId()).status()).isEqualTo(ReservationStatus.RELEASED);
                assertThat(createProduct(3).totalStock()).isEqualTo(3);
            });
            assertThat(products.get(product.id()).soldStock()).isEqualTo(2);
            assertThat(products.get(product.id()).availableStock()).isEqualTo(8);
        } finally {
            docker.unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        // The old cached value still exists. The committed version must select a different key.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(cache.get(product.id() + ":0", ProductResponse.class).availableStock()).isEqualTo(10));
        var current = products.get(product.id());
        assertThat(current.availableStock()).isEqualTo(8);
        assertThat(current.reservedStock()).isZero();
        assertThat(current.soldStock()).isEqualTo(2);
    }

    @Test
    void brokerOutageQueuesWritesAndDeliveryResumesWithoutDuplicateStockRelease() {
        var product = createProduct(10);
        var docker = DockerClientFactory.instance().client();
        UUID reservationId;
        docker.pauseContainerCmd(KAFKA.getContainerId()).exec();
        try {
            var reservation = assertTimeout(Duration.ofSeconds(3), () ->
                    reservations.reserve(new ReserveInventoryRequest(product.id(), "kafka-down", 2)));
            reservationId = reservation.reservationId();
            publisher.publishOrderFailed(new OrderFailedEvent(reservationId, "payment_failed"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE published_at IS NULL", Long.class)).isEqualTo(2);
            assertThat(delivery.publishNext()).isFalse();
            assertThat(jdbc.queryForObject("SELECT sum(attempts) FROM event_outbox", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE published_at IS NOT NULL", Long.class)).isZero();
        } finally {
            docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            while (delivery.publishNext()) { }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE published_at IS NULL", Long.class)).isZero();
            assertThat(products.get(product.id()).availableStock()).isEqualTo(10);
        });
        publisher.publishOrderFailed(new OrderFailedEvent(reservationId, "duplicate_payment_failure"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            while (delivery.publishNext()) { }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE published_at IS NULL", Long.class)).isZero();
        });
        assertThat(reservations.releaseAfterOrderFailed(reservationId).status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(products.get(product.id()).availableStock()).isEqualTo(10);
        assertThat(products.get(product.id()).reservedStock()).isZero();
    }

    @Test
    void exhaustedConsumerFailuresArePreservedForRecovery() {
        UUID unknownReservation = UUID.randomUUID();
        publisher.publishOrderFailed(new OrderFailedEvent(unknownReservation, "recovery-test"));
        assertThat(delivery.publishNext()).isTrue();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM failed_kafka_records WHERE payload LIKE ?",
                        Long.class, "%" + unknownReservation + "%")).isEqualTo(1));
    }

    @Test
    void preservingTheSameFailedOffsetIsIdempotent() {
        var record = new ConsumerRecord<>("test.failures", 0, 42, "key",
                new OrderFailedEvent(UUID.randomUUID(), "payment_failed"));
        failures.save(record, new IllegalStateException("test failure"));
        failures.save(record, new IllegalStateException("redelivery"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM failed_kafka_records WHERE topic = 'test.failures'", Long.class)).isEqualTo(1);
    }

    private ProductResponse createProduct(long stock) {
        return products.create(new CreateProductRequest("RELIABILITY-" + UUID.randomUUID(), "Test stock", stock));
    }
}
