package com.jredis.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class CommandHandler {

    private final RespSerializer respSerializer;
    private final Store store;
    Logger logger = LoggerFactory.getLogger(getClass());

    public CommandHandler(RespSerializer respSerializer, Store store) {
        this.respSerializer = respSerializer;
        this.store = store;
    }

    public String ping(String[] command) {
        return "+PONG\r\n";
    }

    public String echo(String[] command) {
        return respSerializer.serializeBulkString(command[1]);
    }

    public String set(String[] command) {
        try {
            String key = command[1];
            String value = command[2];

            int pxFlag = Arrays.stream(command).map(String::toLowerCase).toList().indexOf("px");

            if(pxFlag > -1) {
                int delta = Integer.parseInt(command[pxFlag + 1]);
                return store.set(key, value, delta);
            }

            return store.set(key, value);
        } catch (Exception e) {
            logger.error("Error setting value for key {}: {}", command[1], e.getMessage());
            return "$-1\r\n";
        }
    }

    public String get(String[] command) {
        try {
            String key = command[1];

            return store.get(key);
        } catch (Exception e) {
            logger.error("Error getting value for key {}: {}", command[1], e.getMessage());
            return "$-1\r\n";
        }
    }
}
