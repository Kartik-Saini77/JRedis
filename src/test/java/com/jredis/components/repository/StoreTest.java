package com.jredis.components.repository;

import com.jredis.components.services.RespSerializer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class StoreTest {
    private final RespSerializer respSerializer = new RespSerializer();
    private final Store store = new Store(respSerializer);

    @BeforeEach
    public void setUp() {
        store.map.clear();
    }

    @Test
    public void testSetAndGet() {
        String key = "testKey";
        String value = "testValue";

        String setResponse = store.set(key, value);
        String getResponse = store.get(key);

        assertEquals("+OK\r\n", setResponse);
        assertEquals(respSerializer.serializeBulkString(value), getResponse);
    }

    @Test
    public void testSetGetAndExpiry() throws InterruptedException {
        String key = "testKey";
        String value = "testValue";
        int expiryMilliseconds = 100;

        String setResponse = store.set(key, value, expiryMilliseconds);
        String getResponse = store.get(key);
        Thread.sleep(expiryMilliseconds);

        String expiredGetResponse = store.get(key);

        assertEquals("+OK\r\n", setResponse);
        assertEquals(respSerializer.serializeBulkString(value), getResponse);
        assertEquals("$-1\r\n", expiredGetResponse);
    }

    @Test
    public void testSetGetAndExpiryReset() throws InterruptedException {
        String key = "testKey";
        String value = "testValue";
        String value2 = "testValue2";
        int expiryMilliseconds = 100;

        String setResponse = store.set(key, value, expiryMilliseconds);
        String getResponse = store.get(key);
        String setResponseReset = store.set(key, value2, expiryMilliseconds*2);
        Thread.sleep(expiryMilliseconds);

        String expiredGetResponse = store.get(key);

        assertEquals("+OK\r\n", setResponse);
        assertEquals(respSerializer.serializeBulkString(value), getResponse);
        assertEquals("+OK\r\n", setResponseReset);
        assertEquals(respSerializer.serializeBulkString(value2), expiredGetResponse);
    }
}