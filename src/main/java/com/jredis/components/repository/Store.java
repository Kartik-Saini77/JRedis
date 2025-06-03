package com.jredis.components.repository;

import com.jredis.components.infra.Client;
import com.jredis.components.services.RespSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;

@Slf4j
@Component
public class Store {
    private final RespSerializer respSerializer;
    public ConcurrentHashMap<String, Value> map;
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public Store(RespSerializer respSerializer) {
        this.respSerializer = respSerializer;
        this.map = new ConcurrentHashMap<>();
    }

    public Set<String> getKeys() {
        rwLock.readLock().lock();
        try {
            return map.keySet();
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public String set(String key, String value) {
        rwLock.writeLock().lock();
        try {
            map.put(key, new Value(value, LocalDateTime.now(), LocalDateTime.MAX));
            return "+OK\r\n";
        } catch (Exception e) {
            log.error("Error setting value for key {}: {}", key, e.getMessage());
            return "$-1\r\n";
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public String set(String key, String value, int expiryMilliseconds) {
        rwLock.writeLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiryTime = now.plusNanos(expiryMilliseconds * 1_000_000L);
            map.put(key, new Value(value, now, expiryTime));

            return "+OK\r\n";
        } catch (Exception e) {
            log.error("Error setting value for key {}: {}", key, e.getMessage());
            return "$-1\r\n";
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public String get(String key) {
        rwLock.readLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            Value value = map.getOrDefault(key, null);

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
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public Value getValue(String key) {
        rwLock.readLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            Value value = map.getOrDefault(key, null);

            if (value != null && value.expiresAt.isBefore(now)) {
                map.remove(key);
                return null;
            }
            return value;
        } catch (Exception e) {
            log.error("Error getting value for key {}: {}", key, e.getMessage());
            return null;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void executeTransaction(Client client, BiFunction<String[], Map<String, Value>, String> transactionCacheApplier) {
        rwLock.writeLock().lock();
        Map<String, Value> localMap = new HashMap<>();
        List<String> responses = new ArrayList<>();
        try {
            while (!client.commandQueue.isEmpty()) {
                String[] command = client.commandQueue.poll();
                String response = transactionCacheApplier.apply(command, localMap);
                responses.add(response);
            }

            for (String key : map.keySet()) {
                Value value = map.get(key);

                if (value.isDeletedInTransaction) {
                    this.map.remove(key);
                } else {
                    this.map.put(key, value);
                }
            }

            client.transactionResponse.addAll(responses);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
