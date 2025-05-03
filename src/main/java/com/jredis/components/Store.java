package com.jredis.components;

import com.jredis.models.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Store {
    private final RespSerializer respSerializer;
    Logger logger = LoggerFactory.getLogger(getClass());
    public ConcurrentHashMap<String, Values> map;

    public Store(RespSerializer respSerializer) {
        this.respSerializer = respSerializer;
        this.map = new ConcurrentHashMap<>();
    }

    public Set<String> getKeys() {
        return map.keySet();
    }

    public String set(String key, String value) {
        try {
            map.put(key, new Values(value, LocalDateTime.now(), LocalDateTime.MAX));
            return "+OK\r\n";
        } catch (Exception e) {
            logger.error("Error setting value for key {}: {}", key, e.getMessage());
            return "$-1\r\n";
        }
    }

    public String set(String key, String value, int expiryMilliseconds) {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiryTime = now.plusNanos(expiryMilliseconds * 1_000_000L);
            map.put(key, new Values(value, now, expiryTime));

            return "+OK\r\n";
        } catch (Exception e) {
            logger.error("Error setting value for key {}: {}", key, e.getMessage());
            return "$-1\r\n";
        }
    }

    public String get(String key) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Values value = map.get(key);

            if (value != null) {
                if (value.expiresAt.isBefore(now)) {
                    map.remove(key);
                    return "$-1\r\n";
                }
                return respSerializer.serializeBulkString(value.value);
            } else {
                logger.info("Key not found : {}", key);
                return "$-1\r\n";
            }
        } catch (Exception e) {
            logger.error("Error getting value for key {}: {}", key, e.getMessage());
            return "$-1\r\n";
        }
    }
}
