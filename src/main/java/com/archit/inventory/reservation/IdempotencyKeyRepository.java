package com.archit.inventory.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    @Modifying
    @Query(
            value = """
                    insert into idempotency_keys (idempotency_key, request_hash, created_at, updated_at)
                    values (:key, :requestHash, now(), now())
                    on conflict (idempotency_key) do nothing
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(String key, String requestHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from IdempotencyKey i where i.key = :key")
    Optional<IdempotencyKey> findByKeyForUpdate(String key);
}
