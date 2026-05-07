package com.archit.inventory.reservation;

import com.archit.inventory.common.ConflictException;
import com.archit.inventory.common.NotFoundException;
import com.archit.inventory.order.CustomerOrder;
import com.archit.inventory.order.OrderRepository;
import com.archit.inventory.product.Product;
import com.archit.inventory.product.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.archit.inventory.event.InventoryEventPublisher;

class ReservationServiceTest {
    private final InventoryEventPublisher eventPublisher = mock(InventoryEventPublisher.class);
    private final CacheManager cacheManager = mock(CacheManager.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final IdempotencyKeyRepository idempotencyKeyRepository = mock(IdempotencyKeyRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-05T10:00:00Z"), ZoneOffset.UTC);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final ReservationService reservationService = new ReservationService(
            productRepository,
            reservationRepository,
            idempotencyKeyRepository,
            orderRepository,
            eventPublisher,
            cacheManager,
            clock,
            meterRegistry,
            Duration.ofMinutes(15),
            100
    );

    @Test
    void reserveRejectsInsufficientStockWithoutCreatingReservation() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdForUpdate(productId))
                .thenReturn(Optional.of(new Product(productId, "SKU-1", "Widget", 1)));

        assertThatThrownBy(() -> reservationService.reserve(new ReserveInventoryRequest(productId, "user-1", 2)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Insufficient available stock");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveLocksProductRowBeforeMutatingStock() {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-LOCK", "Widget", 5);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.reserve(new ReserveInventoryRequest(productId, "user-1", 1));

        verify(productRepository).findByIdForUpdate(productId);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void reserveRejectsUnknownProduct() {
        UUID productId = UUID.randomUUID();
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.reserve(new ReserveInventoryRequest(productId, "user-1", 1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product not found");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void reserveMovesAvailableToReserved() {
        UUID productId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-2", "Widget", 5);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.reserve(new ReserveInventoryRequest(productId, "user-1", 3));

        assertThat(response.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(response.quantity()).isEqualTo(3);
        assertThat(product.getAvailableStock()).isEqualTo(2);
        assertThat(product.getReservedStock()).isEqualTo(3);
        assertThat(product.getTotalStock()).isEqualTo(product.getAvailableStock() + product.getReservedStock() + product.getSoldStock());
    }

    @Test
    void confirmMovesReservedToSoldAndCreatesOrder() {
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-CONFIRM", "Widget", 5);
        product.reserve(2);
        Reservation reservation = new Reservation(
                reservationId,
                product,
                "user-1",
                2,
                Instant.parse("2026-05-05T10:15:00Z")
        );

        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfirmReservationResponse response = reservationService.confirm(reservationId);

        assertThat(response.reservationId()).isEqualTo(reservationId);
        assertThat(response.orderId()).isNotNull();
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(product.getAvailableStock()).isEqualTo(3);
        assertThat(product.getReservedStock()).isZero();
        assertThat(product.getSoldStock()).isEqualTo(2);
        verify(orderRepository).save(any(CustomerOrder.class));
    }

    @Test
    void confirmRejectsNonActiveReservationWithoutCreatingOrder() {
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-CONFIRMED", "Widget", 5);
        product.reserve(1);
        Reservation reservation = new Reservation(
                reservationId,
                product,
                "user-1",
                1,
                Instant.parse("2026-05-05T10:15:00Z")
        );
        reservation.markConfirmed();

        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> reservationService.confirm(reservationId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Reservation is not active");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void confirmExpiredReservationRestoresStockAndMarksExpired() {
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-EXPIRED", "Widget", 5);
        product.reserve(2);
        Reservation reservation = new Reservation(
                reservationId,
                product,
                "user-1",
                2,
                Instant.parse("2026-05-05T09:59:59Z")
        );

        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> reservationService.confirm(reservationId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Reservation has expired");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(product.getAvailableStock()).isEqualTo(5);
        assertThat(product.getReservedStock()).isZero();
        assertThat(product.getSoldStock()).isZero();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void releaseMovesReservedBackToAvailable() {
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-RELEASE", "Widget", 5);
        product.reserve(2);
        Reservation reservation = new Reservation(
                reservationId,
                product,
                "user-1",
                2,
                Instant.parse("2026-05-05T10:15:00Z")
        );

        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        ReservationResponse response = reservationService.release(reservationId);

        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(product.getAvailableStock()).isEqualTo(5);
        assertThat(product.getReservedStock()).isZero();
        assertThat(product.getSoldStock()).isZero();
    }

    @Test
    void releaseRejectsNonActiveReservation() {
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-RELEASED", "Widget", 5);
        product.reserve(1);
        Reservation reservation = new Reservation(
                reservationId,
                product,
                "user-1",
                1,
                Instant.parse("2026-05-05T10:15:00Z")
        );
        reservation.markReleased();

        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> reservationService.release(reservationId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Reservation is not active");
    }

    @Test
    void releaseAfterOrderFailedReleasesActiveReservation() {
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-ORDER-FAILED", "Widget", 5);
        product.reserve(2);
        Reservation reservation = new Reservation(
                reservationId,
                product,
                "user-1",
                2,
                Instant.parse("2026-05-05T10:15:00Z")
        );

        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        ReservationResponse response = reservationService.releaseAfterOrderFailed(reservationId);

        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(product.getAvailableStock()).isEqualTo(5);
        assertThat(product.getReservedStock()).isZero();
        assertThat(product.getSoldStock()).isZero();
    }

    @Test
    void releaseAfterOrderFailedIgnoresAlreadyReleasedReservation() {
        UUID productId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        Product product = new Product(productId, "SKU-ORDER-FAILED-DUP", "Widget", 5);
        product.reserve(2);
        Reservation reservation = new Reservation(
                reservationId,
                product,
                "user-1",
                2,
                Instant.parse("2026-05-05T10:15:00Z")
        );
        product.release(2);
        reservation.markReleased();

        when(reservationRepository.findByReservationIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));

        ReservationResponse response = reservationService.releaseAfterOrderFailed(reservationId);

        assertThat(response.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(product.getAvailableStock()).isEqualTo(5);
        assertThat(product.getReservedStock()).isZero();
        assertThat(product.getSoldStock()).isZero();
    }

    @Test
    void expireReservationsRestoresStockAndMarksExpired() {
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        Product firstProduct = new Product(firstProductId, "SKU-EXP-1", "Widget", 5);
        Product secondProduct = new Product(secondProductId, "SKU-EXP-2", "Widget", 8);
        firstProduct.reserve(2);
        secondProduct.reserve(3);
        Reservation firstReservation = new Reservation(
                UUID.randomUUID(),
                firstProduct,
                "user-1",
                2,
                Instant.parse("2026-05-05T09:59:00Z")
        );
        Reservation secondReservation = new Reservation(
                UUID.randomUUID(),
                secondProduct,
                "user-2",
                3,
                Instant.parse("2026-05-05T09:58:00Z")
        );

        when(reservationRepository.findExpiredActiveForUpdate(any(), any()))
                .thenReturn(List.of(firstReservation, secondReservation));
        when(productRepository.findByIdForUpdate(firstProductId)).thenReturn(Optional.of(firstProduct));
        when(productRepository.findByIdForUpdate(secondProductId)).thenReturn(Optional.of(secondProduct));

        int expiredCount = reservationService.expireReservations();

        assertThat(expiredCount).isEqualTo(2);
        assertThat(firstReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(secondReservation.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(firstProduct.getAvailableStock()).isEqualTo(5);
        assertThat(firstProduct.getReservedStock()).isZero();
        assertThat(firstProduct.getSoldStock()).isZero();
        assertThat(secondProduct.getAvailableStock()).isEqualTo(8);
        assertThat(secondProduct.getReservedStock()).isZero();
        assertThat(secondProduct.getSoldStock()).isZero();
        verify(productRepository).findByIdForUpdate(firstProductId);
        verify(productRepository).findByIdForUpdate(secondProductId);
    }

    @Test
    void expireReservationsReturnsZeroWhenNoReservationsAreExpired() {
        when(reservationRepository.findExpiredActiveForUpdate(any(), any())).thenReturn(List.of());

        int expiredCount = reservationService.expireReservations();

        assertThat(expiredCount).isZero();
    }
}
