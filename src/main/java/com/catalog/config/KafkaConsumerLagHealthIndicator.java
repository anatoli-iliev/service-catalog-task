package com.catalog.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Component;

@Component("kafkaConsumerLag")
@ConditionalOnBean(ConsumerFactory.class)
public class KafkaConsumerLagHealthIndicator implements HealthIndicator, DisposableBean {

    private static final long LAG_THRESHOLD = 1000;
    private static final String GROUP_ID = "catalog-service";
    private static final int TIMEOUT_SECONDS = 5;

    private final AdminClient adminClient;

    public KafkaConsumerLagHealthIndicator(ConsumerFactory<String, Object> consumerFactory) {
        this.adminClient = AdminClient.create(consumerFactory.getConfigurationProperties());
    }

    @Override
    public void destroy() {
        adminClient.close(java.time.Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    @Override
    public Health health() {
        try {
            Map<TopicPartition, OffsetAndMetadata> committedOffsets = adminClient
                    .listConsumerGroupOffsets(GROUP_ID)
                    .partitionsToOffsetAndMetadata()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (committedOffsets == null || committedOffsets.isEmpty()) {
                return Health.up().withDetail("consumerGroup", GROUP_ID)
                        .withDetail("totalLag", 0)
                        .build();
            }

            Map<TopicPartition, Long> endOffsets = adminClient.listOffsets(
                    committedOffsets.keySet().stream().collect(
                            Collectors.toMap(tp -> tp, _ -> OffsetSpec.latest())))
                    .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));

            Map<String, Object> details = new HashMap<>();
            details.put("consumerGroup", GROUP_ID);
            long totalLag = 0;

            for (var entry : committedOffsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long committed = entry.getValue().offset();
                long end = endOffsets.getOrDefault(tp, committed);
                long lag = Math.max(0, end - committed);
                totalLag += lag;
                details.put(tp.topic() + "-" + tp.partition(), lag);
            }

            details.put("totalLag", totalLag);

            if (totalLag > LAG_THRESHOLD) {
                return Health.down().withDetails(details).build();
            }
            return Health.up().withDetails(details).build();

        } catch (Exception e) {
            return Health.unknown().withException(e).build();
        }
    }
}
