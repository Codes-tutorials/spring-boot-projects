package org.codeart.kafka.producer.config;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;

import java.util.List;
import java.util.Map;

/**
 * Custom Kafka partitioner that partitions by service name.
 * Ensures logs from the same service go to the same partition.
 * This enables ordered processing per service while allowing parallelism across
 * services.
 */
public class ServiceNamePartitioner implements Partitioner {

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        List<PartitionInfo> partitions = cluster.partitionsForTopic(topic);
        int numPartitions = partitions.size();

        if (key == null) {
            // Round-robin for null keys
            return (int) (Math.random() * numPartitions);
        }

        // Use service name (from key) to determine partition
        // Key format expected: "serviceName" or "serviceName:instanceId"
        String serviceName = extractServiceName(key.toString());

        // Murmur2 hash (consistent with Kafka's default)
        int hash = murmur2(serviceName.getBytes());
        return Math.abs(hash % numPartitions);
    }

    private String extractServiceName(String key) {
        // Extract service name (part before colon if present)
        int colonIndex = key.indexOf(':');
        return colonIndex > 0 ? key.substring(0, colonIndex) : key;
    }

    /**
     * Murmur2 hash (same as Kafka's default partitioner)
     */
    private int murmur2(byte[] data) {
        int length = data.length;
        int seed = 0x9747b28c;
        int m = 0x5bd1e995;
        int r = 24;

        int h = seed ^ length;
        int length4 = length / 4;

        for (int i = 0; i < length4; i++) {
            int i4 = i * 4;
            int k = (data[i4] & 0xff) + ((data[i4 + 1] & 0xff) << 8)
                    + ((data[i4 + 2] & 0xff) << 16) + ((data[i4 + 3] & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        switch (length % 4) {
            case 3:
                h ^= (data[(length & ~3) + 2] & 0xff) << 16;
            case 2:
                h ^= (data[(length & ~3) + 1] & 0xff) << 8;
            case 1:
                h ^= data[length & ~3] & 0xff;
                h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    @Override
    public void close() {
        // No resources to close
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration needed
    }
}
