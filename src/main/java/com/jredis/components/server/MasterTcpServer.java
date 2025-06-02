package com.jredis.components.server;

import com.jredis.components.infra.Client;
import com.jredis.components.infra.ConnectionPool;
import com.jredis.components.infra.RedisConfig;
import com.jredis.components.infra.Slave;
import com.jredis.components.services.CommandHandler;
import com.jredis.components.services.RespSerializer;
import com.jredis.components.services.ResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class MasterTcpServer {

    private final RespSerializer respSerializer;
    private final CommandHandler commandHandler;
    private final RedisConfig redisConfig;
    private final ConnectionPool connectionPool;

    public MasterTcpServer(RespSerializer respSerializer, CommandHandler commandHandler, RedisConfig redisConfig, ConnectionPool connectionPool) {
        this.respSerializer = respSerializer;
        this.commandHandler = commandHandler;
        this.redisConfig = redisConfig;
        this.connectionPool = connectionPool;
    }

    public void startServer() {
        int port = redisConfig.getPort();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            log.info("Server started on port {}", port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                log.info("New client connected : {}", clientSocket.getInetAddress().getHostAddress());
                Client client = new Client(clientSocket, clientSocket.getInputStream(), clientSocket.getOutputStream());
                CompletableFuture.runAsync(() -> handleClient(client));
            }
        } catch (IOException e) {
            log.error("Error starting server: {}", e.getMessage());
        }
    }

    public void handleClient(Client client) {
        try {
            connectionPool.addClient(client);
            while (true) {
                byte[] buffer = new byte[1024];
                int bytesRead = client.inputStream.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                if (bytesRead > 0) {
                    List<String[]> commands = respSerializer.deserialize(buffer);
                    for (String[] command : commands) {
                        handleCommand(command, client);
                    }
                }
            }
            connectionPool.removeClient(client);
            connectionPool.removeSlave(client);
            log.info("Client disconnected : {}", client.id);
        } catch (IOException e) {
            log.error("Error handling client {}: {}", client.id, e.getMessage());
        } finally {
            try {
                client.socket.close();
            } catch (IOException e) {
                log.error("Error closing client socket: {}", e.getMessage());
            }
        }
    }

    private void handleCommand(String[] command, Client client) {
        if (!client.transactionalContext) {
            ResponseDto responseDto = caseHandler(command, client);
            client.send(responseDto);
        } else if (!isTransactionalControlCommand(command[0])) {
            client.commandQueue.offer(command);
            client.send("+QUEUED\r\n");
        } else {
            transactionController(command, client);
        }
    }

    private boolean isTransactionalControlCommand(String s) {
        return switch (s) {
            case "EXEC", "DISCARD" -> true;
            default -> false;
        };
    }

    private void transactionController(String[] command, Client client) {
        switch (command[0]) {
            case "EXEC" -> {
                if (client.commandQueue.isEmpty()) {
                    client.send("*0\r\n");
                    client.endTransaction();
                    return;
                }

                Queue<String[]> commands = new LinkedList<>(client.commandQueue);

                //execute the transaction


                client.endTransaction();
                while(!commands.isEmpty()) {
                    String[] commandToPropagate = commands.poll();
                    String respArray = respSerializer.serializeArray(commandToPropagate);
                    byte[] bytes = respArray.getBytes();
                    connectionPool.bytesSentToSlaves += bytes.length;
                    CompletableFuture.runAsync(() -> propagate(commandToPropagate));
                }

                String response = respSerializer.serializeArray(client.transactionResponse);
                client.send(response);
            }
            case "DISCARD" -> {
                client.endTransaction();
                client.send("+OK\r\n");
            }
        }
    }

    public ResponseDto caseHandler(String[] command, Client client) {
        byte[] data = null;
        String response = switch (command[0]) {
            case "PING" -> commandHandler.ping(command);
            case "MULTI" -> {
                if (client.beginTransaction()) {
                    yield "+OK\r\n";
                } else {
                    yield "-ERR MULTI calls can not be nested\r\n";
                }
            }
            case "EXEC" -> "-ERR EXEC without MULTI\r\n";
            case "DISCARD" -> "-ERR DISCARD without MULTI\r\n";
            case "INCR" -> commandHandler.incr(command);
            case "ECHO" -> commandHandler.echo(command);
            case "SET" -> {
                String res = commandHandler.set(command);
                String respArray = respSerializer.serializeArray(command);
                byte[] bytes = respArray.getBytes();
                connectionPool.bytesSentToSlaves += bytes.length;
                CompletableFuture.runAsync(() -> propagate(command));
                yield res;
            }
            case "GET" -> commandHandler.get(command);
            case "INFO" -> commandHandler.info(command);
            case "REPLCONF" -> commandHandler.replconf(command, client);
            case "PSYNC" -> {
                ResponseDto resDto = commandHandler.psync(command);
                data = resDto.getData();
                yield resDto.getResponse();
            }
            case "WAIT" -> {
                if (connectionPool.bytesSentToSlaves == 0) {
                    yield respSerializer.serializeInteger(connectionPool.slavesThatAreCaughtUp);
                }
                String res = commandHandler.wait(command, Instant.now());
                connectionPool.slavesThatAreCaughtUp = 0;
                yield res;
            }
            default -> "-ERR unknown command\r\n";
        };
        return new ResponseDto(response, data);
    }

    private void propagate(String[] command) {
        String commandRespString = respSerializer.serializeArray(command);
        for(Slave slave : connectionPool.getSlaves()) {
            try {
                slave.send(commandRespString.getBytes());
            } catch (IOException e) {
                log.error("Error propagating command to slave: {}", e.getMessage());
            }
        }
    }
}
