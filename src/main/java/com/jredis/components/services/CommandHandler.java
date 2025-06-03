package com.jredis.components.services;

import com.jredis.components.infra.Client;
import com.jredis.components.infra.ConnectionPool;
import com.jredis.components.infra.RedisConfig;
import com.jredis.components.infra.Slave;
import com.jredis.components.repository.Store;
import com.jredis.components.repository.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

@Slf4j
@Component
public class CommandHandler {

    private static final String emptyRdbFile = "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";  // temp, will be replaced with actual rdb file

    private final RespSerializer respSerializer;
    private final ConnectionPool connectionPool;
    private final RedisConfig redisConfig;
    private final Store store;

    public CommandHandler(RespSerializer respSerializer, ConnectionPool connectionPool, RedisConfig redisConfig, Store store) {
        this.respSerializer = respSerializer;
        this.connectionPool = connectionPool;
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

    public String replconf(String[] command, Client client) {
        switch (command[1]) {
            case "GETACK" :
                String[] replConfAck = new String[]{"REPLCONF", "ACK", redisConfig.getMasterReplOffset()+""};
                return respSerializer.serializeArray(replConfAck);
            case "ACK" :
                int ackResponse = Integer.parseInt(command[2]);
                connectionPool.slaveAck(ackResponse);
                return "";
            case "listening-port" :
                connectionPool.removeClient(client);
                Slave s = new Slave(client);
                connectionPool.addSlave(s);
                break;
            case "capa" :
                Slave slave = null;
                for(Slave ss : connectionPool.getSlaves()) {
                    if (ss.connection.equals(client)) {
                        slave = ss;
                        break;
                    }
                }

                for(int i=0; i<command.length; i++) {
                    if (command[i].equalsIgnoreCase("capa")) {
                        assert slave != null;
                        slave.capabilities.add(command[i+1]);
                    }
                }
                break;
        }
        return "+OK\r\n";
    }

    public ResponseDto psync(String[] command) {
        String replIdMaster = command[1];
        String replOffsetMaster = command[2];

        if (replIdMaster.equals("?") && replOffsetMaster.equals("-1")) {
            String replId = redisConfig.getMasterReplId();
            long replOffset = redisConfig.getMasterReplOffset();

            byte[] rdbFile = Base64.getDecoder().decode(emptyRdbFile);
            byte[] header = ("$" + rdbFile.length + "\r\n").getBytes();
            byte[] rdb = new byte[header.length + rdbFile.length];
            System.arraycopy(header, 0, rdb, 0, header.length);
            System.arraycopy(rdbFile, 0, rdb, header.length, rdbFile.length);

            connectionPool.slavesThatAreCaughtUp++;

            return new ResponseDto("+FULLRESYNC " + replId + " " + replOffset + "\r\n", rdb);
        }
        return new ResponseDto("+Options are not supported yet\r\n");
    }

    public String wait(String[] command, Instant now) {
        String[] getAckArray = new String[]{"REPLCONF", "GETACK", "*"};
        byte[] getAck = respSerializer.serializeArray(getAckArray).getBytes();
        int bufferSize = getAck.length;

        int requiredSlaves = Integer.parseInt(command[1]);
        int timeout = Integer.parseInt(command[2]);

        for(Slave slave : connectionPool.getSlaves()) {
            CompletableFuture.runAsync(() -> {
                try {
                    slave.connection.send(getAck);
                } catch (Exception e) {
                    log.error("Error sending REPLCONF GETACK to slave {}: {}", slave.connection.id, e.getMessage());
                }
            });
        }

        int res = 0;
        while (Duration.between(now, Instant.now()).toMillis() < timeout) {
            if (res >= requiredSlaves)
                break;
            res = connectionPool.slavesThatAreCaughtUp;
        }
        connectionPool.bytesSentToSlaves += bufferSize;
        if (res >= requiredSlaves) {
            return respSerializer.serializeInteger(res);
        } else {
            log.warn("Timeout waiting for slaves to catch up. Required: {}, Got: {}", requiredSlaves, res);
            return respSerializer.serializeInteger(res);
        }
    }

    public String incr(String[] command) {
        String key = command[1];
        try {
            Value value = store.getValue(command[1]);
            if (value == null) {
                store.set(key, "0");
                value = store.getValue(key);
            }

            int val = Integer.parseInt(value.value);
            val++;
            value.value = val+"";

            return respSerializer.serializeInteger(val);
        } catch (Exception e) {
            return "-ERR value is not an integer or out of range\r\n";
        }
    }

    public BiFunction<String[], Map<String, Value>, String> getTransactionCommandCacheApplier() {
        final RespSerializer localSerializer = this.respSerializer;
        final Store localStore = this.store;
        return (String[] command, Map<String, Value> map) -> {
            return switch (command[0]) {
                case "SET" -> handleSetCommandTransactional(command, map, localSerializer, localStore);
                case "GET" -> handleGetCommandTransactional(command, map, localSerializer, localStore);
                case "INCR" -> handleIncrCommandTransactional(command, map, localSerializer, localStore);
                case "DEL" -> handleDelCommandTransactional(command, map, localSerializer, localStore);
                default -> "-ERR unknown command "+command[0]+"\r\n";
            };
        };
    }

    private String handleSetCommandTransactional(String[] command, Map<String, Value> map, RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value newValue = new Value(command[2], LocalDateTime.now(), LocalDateTime.MAX);
        map.put(key, newValue);
        return "+OK\r\n";
    }

    private String handleGetCommandTransactional(String[] command, Map<String, Value> map, RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value valueToUse;
        Value cachedValue = map.get(key);
        if (cachedValue == null) {
            Value storeValue = store.getValue(key);
            if (storeValue == null) {
                return localStore.get(key);
            } else {
                valueToUse = new Value(storeValue.value, storeValue.createdAt, storeValue.expiresAt);
                map.put(key, valueToUse);
            }
        } else {
            valueToUse = cachedValue;
        }
        return localSerializer.serializeBulkString(valueToUse.value);
    }

    private String handleIncrCommandTransactional(String[] command, Map<String, Value> map, RespSerializer localSerializer, Store localStore) {
        try {
            String key = command[1];
            Value valueToUse;
            Value cachedValue = map.get(key);
            if (cachedValue == null) {
                Value storeValue = localStore.getValue(key);
                if (storeValue == null) {
                    valueToUse = new Value("0", LocalDateTime.now(), LocalDateTime.MAX);
                } else {
                    valueToUse = new Value(storeValue.value, storeValue.createdAt, storeValue.expiresAt);
                }
            } else {
                valueToUse = cachedValue;
            }

            int val = Integer.parseInt(valueToUse.value);
            val++;
            valueToUse.value = val+"";
            map.put(key, valueToUse);

            return localSerializer.serializeInteger(val);
        } catch (NumberFormatException e) {
            return "-ERR value is not an integer or out of range\r\n";
        }
    }

    private String handleDelCommandTransactional(String[] command, Map<String, Value> map, RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value valueToUse;
        Value cachedValue = map.get(key);
        if (cachedValue == null) {
            Value storeValue = store.getValue(key);
            if (storeValue == null) {
                return "-ERR key does not exist\r\n";
            } else {
                valueToUse = new Value(storeValue.value, storeValue.createdAt, storeValue.expiresAt);
            }
        } else {
            valueToUse = cachedValue;
        }

        valueToUse.isDeletedInTransaction = true;
        map.put(key, valueToUse);
        return "+OK\r\n";
    }
}
