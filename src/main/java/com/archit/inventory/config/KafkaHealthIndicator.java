package com.archit.inventory.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator, DisposableBean {
    private final AdminClient client;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        var config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
        client = AdminClient.create(config);
    }

    @Override
    public Health health() {
        try {
            var brokers = client.describeCluster().nodes().get(2, TimeUnit.SECONDS);
            return brokers.isEmpty() ? Health.down().build() : Health.up().build();
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return Health.down().withDetail("reason", "Kafka check failed").build();
        }
    }

    @Override
    public void destroy() {
        client.close(Duration.ofSeconds(1));
    }
}
