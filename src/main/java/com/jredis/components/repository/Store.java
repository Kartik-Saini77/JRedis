package com.jredis.components.repository;

import com.jredis.components.services.RespSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class Store {
    private final RespSerializer respSerializer;
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
            log.error("Error setting value for key {}: {}", key, e.getMessage());
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
            log.error("Error setting value for key {}: {}", key, e.getMessage());
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
                log.info("Key not found : {}", key);
                return "$-1\r\n";
            }
        } catch (Exception e) {
            log.error("Error getting value for key {}: {}", key, e.getMessage());
            return "$-1\r\n";
        }
    }
}
