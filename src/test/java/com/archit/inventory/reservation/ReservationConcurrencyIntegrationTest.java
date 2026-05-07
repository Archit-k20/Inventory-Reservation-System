package com.archit.inventory.reservation;

import com.archit.inventory.product.CreateProductRequest;
import com.archit.inventory.product.ProductResponse;
import com.archit.inventory.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationConcurrencyIntegrationTest extends AbstractIntegrationTest {
    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void concurrentReservationsDoNotOversell() throws Exception {
        int initialStock = 20;
        int users = 100;
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), initialStock);
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(users);
        try {
            List<Callable<HttpStatus>> tasks = new ArrayList<>();
            for (int i = 0; i < users; i++) {
                int userNumber = i;
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            url("/reservations"),
                            new ReserveInventoryRequest(product.id(), "user-" + userNumber, 1),
                            String.class
                    );
                    return HttpStatus.valueOf(response.getStatusCode().value());
                });
            }

            var futures = tasks.stream().map(executor::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<HttpStatus> statuses = new ArrayList<>();
            for (var future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }

            long successfulReservations = count(statuses, HttpStatus.CREATED);
            long rejectedReservations = count(statuses, HttpStatus.CONFLICT);
            List<HttpStatus> unexpectedStatuses = statuses.stream()
                    .filter(status -> status != HttpStatus.CREATED && status != HttpStatus.CONFLICT)
                    .collect(Collectors.toList());

            ProductResponse finalProduct = restTemplate.getForObject(url("/products/" + product.id()), ProductResponse.class);
            assertThat(unexpectedStatuses).isEmpty();
            assertThat(successfulReservations).isEqualTo(initialStock);
            assertThat(rejectedReservations).isEqualTo(users - initialStock);
            assertThat(finalProduct).isNotNull();
            assertThat(finalProduct.totalStock()).isEqualTo(initialStock);
            assertThat(finalProduct.availableStock()).isZero();
            assertThat(finalProduct.reservedStock()).isEqualTo(initialStock);
            assertThat(finalProduct.soldStock()).isZero();
            assertThat(finalProduct.totalStock()).isEqualTo(finalProduct.availableStock() + finalProduct.reservedStock() + finalProduct.soldStock());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentRetriesWithSameIdempotencyKeyReserveOnlyOnce() throws Exception {
        int initialStock = 20;
        int retries = 25;
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), initialStock);
        String idempotencyKey = "reserve-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(retries);
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(retries);
        try {
            List<Callable<Attempt>> tasks = new ArrayList<>();
            for (int i = 0; i < retries; i++) {
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Idempotency-Key", idempotencyKey);
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            url("/reservations"),
                            new HttpEntity<>(new ReserveInventoryRequest(product.id(), "user-1", 1), headers),
                            String.class
                    );
                    return new Attempt(HttpStatus.valueOf(response.getStatusCode().value()), response.getBody());
                });
            }

            var futures = tasks.stream().map(executor::submit).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Attempt> attempts = new ArrayList<>();
            for (var future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }

            assertThat(attempts)
                    .extracting(Attempt::status)
                    .containsOnly(HttpStatus.CREATED);

            Set<UUID> reservationIds = attempts.stream()
                    .map(Attempt::body)
                    .map(this::reservationIdFrom)
                    .collect(Collectors.toSet());
            assertThat(reservationIds).hasSize(1);

            ProductResponse finalProduct = restTemplate.getForObject(url("/products/" + product.id()), ProductResponse.class);
            assertThat(finalProduct).isNotNull();
            assertThat(finalProduct.totalStock()).isEqualTo(initialStock);
            assertThat(finalProduct.availableStock()).isEqualTo(initialStock - 1);
            assertThat(finalProduct.reservedStock()).isEqualTo(1);
            assertThat(finalProduct.soldStock()).isZero();
            assertThat(finalProduct.totalStock()).isEqualTo(finalProduct.availableStock() + finalProduct.reservedStock() + finalProduct.soldStock());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private UUID reservationIdFrom(String body) {
        try {
            return objectMapper.readValue(body, ReservationResponse.class).reservationId();
        } catch (Exception exception) {
            throw new AssertionError("Unable to parse reservation response: " + body, exception);
        }
    }

    private long count(List<HttpStatus> statuses, HttpStatus expectedStatus) {
        return statuses.stream()
                .filter(status -> status == expectedStatus)
                .count();
    }

    private record Attempt(HttpStatus status, String body) {
    }

    private ProductResponse createProduct(String sku, long stock) {
        ResponseEntity<ProductResponse> response = restTemplate.postForEntity(
                url("/products"),
                new CreateProductRequest(sku, "Widget", stock),
                ProductResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
