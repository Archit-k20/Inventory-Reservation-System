package com.archit.inventory.reservation;

import org.springframework.http.ProblemDetail;
import com.archit.inventory.order.OrderRepository;
import com.archit.inventory.product.CreateProductRequest;
import com.archit.inventory.product.ProductResponse;
import com.archit.inventory.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationApiIntegrationTest extends AbstractIntegrationTest {
    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void fullReserveConfirmAndReleaseFlowPreservesInvariant() {
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), 10);

        ResponseEntity<ReservationResponse> reservationResponse = restTemplate.postForEntity(
                url("/reservations"),
                new ReserveInventoryRequest(product.id(), "user-1", 4),
                ReservationResponse.class
        );
        assertThat(reservationResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID reservationId = reservationResponse.getBody().reservationId();

        ProductResponse afterReserve = getProduct(product.id());
        assertStock(afterReserve, 10, 6, 4, 0);

        ResponseEntity<ConfirmReservationResponse> confirmResponse = restTemplate.postForEntity(
                url("/reservations/" + reservationId + "/confirm"),
                null,
                ConfirmReservationResponse.class
        );
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmResponse.getBody().status()).isEqualTo(ReservationStatus.CONFIRMED);

        ProductResponse afterConfirm = getProduct(product.id());
        assertStock(afterConfirm, 10, 6, 0, 4);

        ProductResponse secondProduct = createProduct("SKU-" + UUID.randomUUID(), 5);
        ReservationResponse secondReservation = restTemplate.postForObject(
                url("/reservations"),
                new ReserveInventoryRequest(secondProduct.id(), "user-2", 2),
                ReservationResponse.class
        );

        ResponseEntity<ReservationResponse> releaseResponse = restTemplate.postForEntity(
                url("/reservations/" + secondReservation.reservationId() + "/release"),
                null,
                ReservationResponse.class
        );
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(releaseResponse.getBody().status()).isEqualTo(ReservationStatus.RELEASED);
        assertStock(getProduct(secondProduct.id()), 5, 5, 0, 0);
    }

    @Test
    void reserveRejectsInsufficientStockAndPreservesProductStock() {
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), 2);

        ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
                url("/reservations"),
                new ReserveInventoryRequest(product.id(), "user-1", 3),
                ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertStock(getProduct(product.id()), 2, 2, 0, 0);
    }

    @Test
    void reserveRejectsUnknownProduct() {
        ResponseEntity<ProblemDetail> response = restTemplate.postForEntity(
                url("/reservations"),
                new ReserveInventoryRequest(UUID.randomUUID(), "user-1", 1),
                ProblemDetail.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reserveRejectsInvalidQuantityAndBlankUserId() {
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), 2);

        ResponseEntity<ProblemDetail> zeroQuantityResponse = restTemplate.postForEntity(
                url("/reservations"),
                new ReserveInventoryRequest(product.id(), "user-1", 0),
                ProblemDetail.class
        );
        assertThat(zeroQuantityResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ProblemDetail> blankUserResponse = restTemplate.postForEntity(
                url("/reservations"),
                new ReserveInventoryRequest(product.id(), " ", 1),
                ProblemDetail.class
        );
        assertThat(blankUserResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertStock(getProduct(product.id()), 2, 2, 0, 0);
    }

    @Test
    void reserveWithSameIdempotencyKeyReturnsExistingReservationWithoutChangingStockAgain() {
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), 5);
        String idempotencyKey = "reserve-" + UUID.randomUUID();

        ResponseEntity<ReservationResponse> firstResponse = reserveWithIdempotencyKey(
                product.id(),
                "user-1",
                2,
                idempotencyKey
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertStock(getProduct(product.id()), 5, 3, 2, 0);

        ResponseEntity<ReservationResponse> retryResponse = reserveWithIdempotencyKey(
                product.id(),
                "user-1",
                2,
                idempotencyKey
        );

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retryResponse.getBody()).isEqualTo(firstResponse.getBody());
        assertStock(getProduct(product.id()), 5, 3, 2, 0);
    }

    @Test
    void reserveRejectsIdempotencyKeyReuseWithDifferentRequest() {
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), 5);
        String idempotencyKey = "reserve-" + UUID.randomUUID();

        ResponseEntity<ReservationResponse> firstResponse = reserveWithIdempotencyKey(
                product.id(),
                "user-1",
                2,
                idempotencyKey
        );
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        ResponseEntity<ProblemDetail> secondResponse = restTemplate.postForEntity(
                url("/reservations"),
                new HttpEntity<>(new ReserveInventoryRequest(product.id(), "user-1", 3), headers),
                ProblemDetail.class
        );

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertStock(getProduct(product.id()), 5, 3, 2, 0);
    }

    @Test
    void confirmRejectsDuplicateConfirmationAndDoesNotCreateDuplicateOrder() {
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), 5);
        ReservationResponse reservation = reserve(product.id(), "user-1", 2);

        ResponseEntity<ConfirmReservationResponse> firstConfirmResponse = restTemplate.postForEntity(
                url("/reservations/" + reservation.reservationId() + "/confirm"),
                null,
                ConfirmReservationResponse.class
        );
        assertThat(firstConfirmResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstConfirmResponse.getBody().status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(orderRepository.count()).isEqualTo(1);

        ResponseEntity<ProblemDetail> duplicateConfirmResponse = restTemplate.postForEntity(
                url("/reservations/" + reservation.reservationId() + "/confirm"),
                null,
                ProblemDetail.class
        );

        assertThat(duplicateConfirmResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertStock(getProduct(product.id()), 5, 3, 0, 2);
    }

    @Test
    void releaseRejectsDuplicateReleaseAndConfirmAfterRelease() {
        long initialOrderCount = orderRepository.count();
        ProductResponse product = createProduct("SKU-" + UUID.randomUUID(), 5);
        ReservationResponse reservation = reserve(product.id(), "user-1", 2);

        ResponseEntity<ReservationResponse> firstReleaseResponse = restTemplate.postForEntity(
                url("/reservations/" + reservation.reservationId() + "/release"),
                null,
                ReservationResponse.class
        );
        assertThat(firstReleaseResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstReleaseResponse.getBody().status()).isEqualTo(ReservationStatus.RELEASED);
        assertStock(getProduct(product.id()), 5, 5, 0, 0);

        ResponseEntity<ProblemDetail> duplicateReleaseResponse = restTemplate.postForEntity(
                url("/reservations/" + reservation.reservationId() + "/release"),
                null,
                ProblemDetail.class
        );
        assertThat(duplicateReleaseResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<ProblemDetail> confirmAfterReleaseResponse = restTemplate.postForEntity(
                url("/reservations/" + reservation.reservationId() + "/confirm"),
                null,
                ProblemDetail.class
        );
        assertThat(confirmAfterReleaseResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(orderRepository.count()).isEqualTo(initialOrderCount);
        assertStock(getProduct(product.id()), 5, 5, 0, 0);
    }

    @Test
    void confirmAndReleaseRejectUnknownReservation() {
        UUID reservationId = UUID.randomUUID();

        ResponseEntity<ProblemDetail> confirmResponse = restTemplate.postForEntity(
                url("/reservations/" + reservationId + "/confirm"),
                null,
                ProblemDetail.class
        );
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ProblemDetail> releaseResponse = restTemplate.postForEntity(
                url("/reservations/" + reservationId + "/release"),
                null,
                ProblemDetail.class
        );
        assertThat(releaseResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

    private ReservationResponse reserve(UUID productId, String userId, long quantity) {
        ResponseEntity<ReservationResponse> response = restTemplate.postForEntity(
                url("/reservations"),
                new ReserveInventoryRequest(productId, userId, quantity),
                ReservationResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<ReservationResponse> reserveWithIdempotencyKey(
            UUID productId,
            String userId,
            long quantity,
            String idempotencyKey
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        return restTemplate.postForEntity(
                url("/reservations"),
                new HttpEntity<>(new ReserveInventoryRequest(productId, userId, quantity), headers),
                ReservationResponse.class
        );
    }

    private ProductResponse getProduct(UUID productId) {
        return restTemplate.getForObject(url("/products/" + productId), ProductResponse.class);
    }

    private void assertStock(ProductResponse product, long total, long available, long reserved, long sold) {
        assertThat(product.totalStock()).isEqualTo(total);
        assertThat(product.availableStock()).isEqualTo(available);
        assertThat(product.reservedStock()).isEqualTo(reserved);
        assertThat(product.soldStock()).isEqualTo(sold);
        assertThat(product.totalStock()).isEqualTo(product.availableStock() + product.reservedStock() + product.soldStock());
    }
}
