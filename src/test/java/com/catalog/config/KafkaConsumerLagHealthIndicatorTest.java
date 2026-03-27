package com.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class KafkaConsumerLagHealthIndicatorTest {

    @Test
    void health_up_whenLagBelowThreshold() throws Exception {
        var adminClient = mock(AdminClient.class);
        var indicator = createIndicator(adminClient);

        var tp = new TopicPartition("catalog.applications", 0);
        mockCommittedOffsets(adminClient, Map.of(tp, new OffsetAndMetadata(90)));
        mockEndOffsets(adminClient, Map.of(tp, 100L));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalLag", 10L);
        assertThat(health.getDetails()).containsEntry("catalog.applications-0", 10L);
        assertThat(health.getDetails()).containsEntry("consumerGroup", "catalog-service");
    }

    @Test
    void health_down_whenLagExceedsThreshold() throws Exception {
        var adminClient = mock(AdminClient.class);
        var indicator = createIndicator(adminClient);

        var tp = new TopicPartition("catalog.releases", 0);
        mockCommittedOffsets(adminClient, Map.of(tp, new OffsetAndMetadata(0)));
        mockEndOffsets(adminClient, Map.of(tp, 5000L));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("totalLag", 5000L);
    }

    @Test
    void health_up_whenNoCommittedOffsets() throws Exception {
        var adminClient = mock(AdminClient.class);
        var indicator = createIndicator(adminClient);

        mockCommittedOffsets(adminClient, Map.of());

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalLag", 0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void health_unknown_whenAdminClientFails() throws Exception {
        var adminClient = mock(AdminClient.class);
        var indicator = createIndicator(adminClient);

        var failingFuture = mock(KafkaFuture.class);
        when(failingFuture.get(any(Long.class), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Broker unavailable"));

        var offsetsResult = mock(ListConsumerGroupOffsetsResult.class);
        when(offsetsResult.partitionsToOffsetAndMetadata()).thenReturn(failingFuture);
        when(adminClient.listConsumerGroupOffsets("catalog-service")).thenReturn(offsetsResult);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void health_multiplePartitions_aggregatesLag() throws Exception {
        var adminClient = mock(AdminClient.class);
        var indicator = createIndicator(adminClient);

        var tp1 = new TopicPartition("catalog.applications", 0);
        var tp2 = new TopicPartition("catalog.releases", 0);
        mockCommittedOffsets(adminClient, Map.of(
                tp1, new OffsetAndMetadata(50),
                tp2, new OffsetAndMetadata(100)));
        mockEndOffsets(adminClient, Map.of(tp1, 60L, tp2, 120L));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalLag", 30L);
        assertThat(health.getDetails()).containsEntry("catalog.applications-0", 10L);
        assertThat(health.getDetails()).containsEntry("catalog.releases-0", 20L);
    }

    @Test
    void destroy_closesAdminClient() throws Exception {
        var adminClient = mock(AdminClient.class);
        var indicator = createIndicator(adminClient);

        indicator.destroy();

        verify(adminClient).close(any(java.time.Duration.class));
    }

    private KafkaConsumerLagHealthIndicator createIndicator(AdminClient adminClient) {
        try {
            var field = KafkaConsumerLagHealthIndicator.class.getDeclaredField("adminClient");
            field.setAccessible(true);
            var indicator = allocateInstance();
            field.set(indicator, adminClient);
            return indicator;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private KafkaConsumerLagHealthIndicator allocateInstance() throws Exception {
        var unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        return (KafkaConsumerLagHealthIndicator) ((sun.misc.Unsafe) unsafe.get(null))
                .allocateInstance(KafkaConsumerLagHealthIndicator.class);
    }

    @SuppressWarnings("unchecked")
    private void mockCommittedOffsets(AdminClient adminClient,
                                     Map<TopicPartition, OffsetAndMetadata> offsets) throws Exception {
        var result = mock(ListConsumerGroupOffsetsResult.class);
        when(adminClient.listConsumerGroupOffsets("catalog-service")).thenReturn(result);
        when(result.partitionsToOffsetAndMetadata())
                .thenReturn(KafkaFuture.completedFuture(offsets));
    }

    @SuppressWarnings("unchecked")
    private void mockEndOffsets(AdminClient adminClient,
                                Map<TopicPartition, Long> endOffsets) throws Exception {
        var result = mock(ListOffsetsResult.class);
        when(adminClient.listOffsets(any())).thenReturn(result);

        Map<TopicPartition, ListOffsetsResultInfo> infoMap = new java.util.HashMap<>();
        for (var entry : endOffsets.entrySet()) {
            var info = mock(ListOffsetsResultInfo.class);
            when(info.offset()).thenReturn(entry.getValue());
            infoMap.put(entry.getKey(), info);
        }
        when(result.all()).thenReturn(KafkaFuture.completedFuture(infoMap));
    }

    @SuppressWarnings("unchecked")
    private <T> KafkaFuture<T> mockFailingFuture(Exception ex) throws Exception {
        var future = mock(KafkaFuture.class);
        when(future.get(any(Long.class), any(TimeUnit.class))).thenThrow(ex);
        return future;
    }
}
