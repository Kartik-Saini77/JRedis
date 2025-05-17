package com.jredis.components.services;

import com.jredis.components.infra.RedisConfig;
import com.jredis.components.repository.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
public class CommandHandler {

    private final RespSerializer respSerializer;
    private final RedisConfig redisConfig;
    private final Store store;

    public CommandHandler(RespSerializer respSerializer, RedisConfig redisConfig, Store store) {
        this.respSerializer = respSerializer;
        this.redisConfig = redisConfig;
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
            log.error("Error setting value for key {}: {}", command[1], e.getMessage());
            return "$-1\r\n";
        }
    }

    public String get(String[] command) {
        try {
            String key = command[1];

            return store.get(key);
        } catch (Exception e) {
            log.error("Error getting value for key {}: {}", command[1], e.getMessage());
            return "$-1\r\n";
        }
    }

    public String info(String[] command) {
        int replication = Arrays.stream(command).map(String::toLowerCase).toList().indexOf("replication");
        if (replication > -1) {
            return respSerializer.serializeArray(new String[]{
                    "role:"+redisConfig.getRole().toString(),
                    "master_host:"+redisConfig.getMasterHost(),
                    "master_port:"+redisConfig.getMasterPort(),
                    "master_replid:"+redisConfig.getMasterReplId(),
                    "master_repl_offset:"+redisConfig.getMasterReplOffset()
            });
        }
        return "$-1\r\n";
    }
}
