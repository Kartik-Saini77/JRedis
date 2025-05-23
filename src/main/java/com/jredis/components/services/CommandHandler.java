package com.jredis.components.services;

import com.jredis.components.infra.Client;
import com.jredis.components.infra.ConnectionPool;
import com.jredis.components.infra.RedisConfig;
import com.jredis.components.infra.Slave;
import com.jredis.components.repository.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;

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
            case "listening-port" :
                connectionPool.removeClient(client);
                Slave s = new Slave(client);
                connectionPool.addSlave(s);
                return "+OK\r\n";
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
                return "+OK\r\n";
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

            return new ResponseDto("+FULLRESYNC " + replId + " " + replOffset + "\r\n", rdb);
        }
        return new ResponseDto("+Options are not supported yet\r\n");
    }
}
