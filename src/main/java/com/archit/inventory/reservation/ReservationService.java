package com.archit.inventory.reservation;

import com.archit.inventory.common.ConflictException;
import com.archit.inventory.common.NotFoundException;
import com.archit.inventory.event.InventoryEventPublisher;
import com.archit.inventory.event.InventoryOutOfStockEvent;
import com.archit.inventory.event.ReservationConfirmedEvent;
import com.archit.inventory.event.ReservationCreatedEvent;
import com.archit.inventory.order.CustomerOrder;
import com.archit.inventory.order.OrderRepository;
import com.archit.inventory.product.Product;
import com.archit.inventory.product.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.logstash.logback.argument.StructuredArguments.kv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ReservationService {
    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private final MeterRegistry meterRegistry;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OrderRepository orderRepository;
    private final InventoryEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration reservationTtl;
    private final int expiryBatchSize;

    public ReservationService(
            ProductRepository productRepository,
            ReservationRepository reservationRepository,
            IdempotencyKeyRepository idempotencyKeyRepository,
            OrderRepository orderRepository,
            InventoryEventPublisher eventPublisher,
            Clock clock,
            MeterRegistry meterRegistry,
            @Value("${inventory.reservations.ttl:PT15M}") Duration reservationTtl,
            @Value("${inventory.reservations.expiry-batch-size:100}") int expiryBatchSize
    ) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.reservationTtl = reservationTtl;
        this.expiryBatchSize = expiryBatchSize;
    }

    @Transactional
    public ReservationResponse reserve(ReserveInventoryRequest request) {
        return createReservation(request);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ReservationResponse> getReservations(org.springframework.data.domain.Pageable pageable) {
        return reservationRepository.findAll(pageable).map(ReservationResponse::from);
    }

    @Transactional
    public ReservationResponse reserve(ReserveInventoryRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return createReservation(request);
        }

        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() > 255) {
            throw new ConflictException("Idempotency key is too long");
        }

        String requestHash = hash(request);
        idempotencyKeyRepository.insertIfAbsent(normalizedKey, requestHash);
        
        IdempotencyKey record;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            record = idempotencyKeyRepository.findByKeyForUpdate(normalizedKey)
                    .orElseThrow(() -> new ConflictException("Unable to lock idempotency key"));
        } finally {
            sample.stop(meterRegistry.timer("reservations.lock.wait", "type", "idempotency"));
        }

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Idempotency key was already used with a different request");
        }
        if (record.getReservationId() != null) {
            return reservationRepository.findByReservationId(record.getReservationId())
                    .map(ReservationResponse::from)
                    .orElseThrow(() -> new NotFoundException("Reservation not found"));
        }

        Reservation reservation = createReservationEntity(request);
        record.attachReservation(reservation.getReservationId());
        return ReservationResponse.from(reservation);
    }

    private ReservationResponse createReservation(ReserveInventoryRequest request) {
        return ReservationResponse.from(createReservationEntity(request));
    }

    private Reservation createReservationEntity(ReserveInventoryRequest request) {
        Product product;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            product = productRepository.findByIdForUpdate(request.productId())
                    .orElseThrow(() -> new NotFoundException("Product not found"));
        } finally {
            sample.stop(meterRegistry.timer("reservations.lock.wait", "type", "product_create"));
        }

        try {
            product.reserve(request.quantity());
        } catch (IllegalStateException exception) {
            meterRegistry.counter("reservations.conflicts").increment();
            log.warn("Reservation conflict", kv("productId", request.productId()), kv("userId", request.userId()));
            throw new ConflictException("Insufficient available stock");
        }

        Reservation reservation = new Reservation(
                UUID.randomUUID(),
                product,
                request.userId(),
                request.quantity(),
                Instant.now(clock).plus(reservationTtl)
        );
        Reservation saved = reservationRepository.save(reservation);
        
        meterRegistry.counter("reservations.created").increment();
        log.info("Reservation created", kv("reservationId", saved.getReservationId()), kv("productId", request.productId()), kv("quantity", request.quantity()));

        eventPublisher.publishReservationCreated(new ReservationCreatedEvent(
                saved.getReservationId(), product.getId(), request.userId(), request.quantity(), Instant.now(clock)));

        if (product.getAvailableStock() == 0) {
            eventPublisher.publishOutOfStock(new InventoryOutOfStockEvent(
                    product.getId(), product.getSku(), Instant.now(clock)));
        }
        
        return saved;
    }

    @Transactional(noRollbackFor = ConflictException.class)
    public ConfirmReservationResponse confirm(UUID reservationId) {
        Reservation reservation = lockedReservation(reservationId);
        Product product = lockedProduct(reservation);
        Instant now = Instant.now(clock);

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Reservation is not active");
        }
        if (reservation.isExpired(now)) {
            product.release(reservation.getQuantity());
            reservation.markExpired();
            throw new ConflictException("Reservation has expired");
        }

        product.confirm(reservation.getQuantity());
        reservation.markConfirmed();
        CustomerOrder order = orderRepository.save(new CustomerOrder(UUID.randomUUID(), reservation));
        
        meterRegistry.counter("reservations.confirmed").increment();
        log.info("Reservation confirmed", kv("reservationId", reservationId), kv("orderId", order.getOrderId()));

        eventPublisher.publishReservationConfirmed(new ReservationConfirmedEvent(
                reservation.getReservationId(), order.getOrderId(), reservation.getProduct().getId(), Instant.now(clock)));
        
        return new ConfirmReservationResponse(reservation.getReservationId(), order.getOrderId(), reservation.getStatus());
    }

    @Transactional
    public ReservationResponse release(UUID reservationId) {
        Reservation reservation = lockedReservation(reservationId);
        Product product = lockedProduct(reservation);

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Reservation is not active");
        }

        product.release(reservation.getQuantity());
        reservation.markReleased();
        
        meterRegistry.counter("reservations.released").increment();
        log.info("Reservation released", kv("reservationId", reservationId));
        
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationResponse releaseAfterOrderFailed(UUID reservationId) {
        Reservation reservation = lockedReservation(reservationId);
        Product product = lockedProduct(reservation);

        if (reservation.getStatus() == ReservationStatus.ACTIVE) {
            product.release(reservation.getQuantity());
            reservation.markReleased();

            meterRegistry.counter("reservations.released", "source", "order_failed_event").increment();
            log.info("Reservation released after order failure", kv("reservationId", reservationId));
            return ReservationResponse.from(reservation);
        }

        log.info("Order failure event did not require reservation release",
                kv("reservationId", reservationId),
                kv("status", reservation.getStatus()));
        return ReservationResponse.from(reservation);
    }

    @Transactional
    public int expireReservations() {
        Instant now = Instant.now(clock);
        var expiredReservations = reservationRepository.findExpiredActiveForUpdate(
                now,
                PageRequest.of(0, expiryBatchSize)
        );
        for (Reservation reservation : expiredReservations) {
            Product product = lockedProduct(reservation);
            product.release(reservation.getQuantity());
            reservation.markExpired();
            
            meterRegistry.counter("reservations.expired").increment();
            log.info("Reservation expired", kv("reservationId", reservation.getReservationId()));
        }
        return expiredReservations.size();
    }

    private Reservation lockedReservation(UUID reservationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return reservationRepository.findByReservationIdForUpdate(reservationId)
                    .orElseThrow(() -> new NotFoundException("Reservation not found"));
        } finally {
            sample.stop(meterRegistry.timer("reservations.lock.wait", "type", "reservation"));
        }
    }

    private Product lockedProduct(Reservation reservation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return productRepository.findByIdForUpdate(reservation.getProduct().getId())
                    .orElseThrow(() -> new NotFoundException("Product not found"));
        } finally {
            sample.stop(meterRegistry.timer("reservations.lock.wait", "type", "product"));
        }
    }

    private String hash(ReserveInventoryRequest request) {
        String canonicalRequest = request.productId() + "|" + request.userId() + "|" + request.quantity();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

}
