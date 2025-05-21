package com.jredis.components.server;

import com.jredis.components.infra.Client;
import com.jredis.components.infra.ConnectionPool;
import com.jredis.components.infra.RedisConfig;
import com.jredis.components.services.CommandHandler;
import com.jredis.components.services.RespSerializer;
import com.jredis.components.services.ResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class SlaveTcpServer {

    private final RespSerializer respSerializer;
    private final CommandHandler commandHandler;
    private final RedisConfig redisConfig;
    private final ConnectionPool connectionPool;

    public SlaveTcpServer(RespSerializer respSerializer, CommandHandler commandHandler, RedisConfig redisConfig, ConnectionPool connectionPool) {
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

            CompletableFuture<Void> slaveConnectionFuture = CompletableFuture.runAsync(this::initiateSlaveConnection);
            slaveConnectionFuture.thenRun(() -> log.info("Replication completed"));

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

    private void initiateSlaveConnection() {
        try(Socket master = new Socket(redisConfig.getMasterHost(), redisConfig.getMasterPort())) {
            InputStream inputStream = master.getInputStream();
            OutputStream outputStream = master.getOutputStream();
            byte[] inputBuffer = new byte[1024];

            // Part 1 of the handshake
            byte[] data = "*1\r\n$4\r\nPING\r\n".getBytes();
            outputStream.write(data);
            int bytesRead = inputStream.read(inputBuffer);
            String response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            log.info(response);

            // Part 2 of the handshake
            String replconf = "*3\r\n$8\r\nREPLCONF\r\n$4\r\nLISTENING\r\n$" + ("" + redisConfig.getPort()).length() + "\r\n" + redisConfig.getPort() + "\r\n";
            data = replconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer);
            response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            log.info(response);

            replconf = "*3\r\n$8\r\nREPLCONF\r\n$4\r\ncapa\r\n$6\r\npsync2\r\n";
            data = replconf.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer);
            response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            log.info(response);

            // Part 3 of the handshake
            String psync = "*3\r\n$5\r\nPSYNC\r\n$1\r\n?\r\n$2\r\n-1\r\n";
            data = psync.getBytes();
            outputStream.write(data);
            bytesRead = inputStream.read(inputBuffer);
            response = new String(inputBuffer, 0, bytesRead, StandardCharsets.UTF_8);
            log.info(response);

//            handlePsyncResponse(response, inputStream, outputStream);

        } catch (Exception e) {
            log.error("Error connecting to master: {}", e.getMessage());
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
        byte[] data = null;
        String response = switch (command[0]) {
            case "PING" -> commandHandler.ping(command);
            case "ECHO" -> commandHandler.echo(command);
            case "SET" -> "-READONLY You can't write against a replica.\r\n";
            case "GET" -> commandHandler.get(command);
            case "INFO" -> commandHandler.info(command);
            case "REPLCONF" -> commandHandler.replconf(command, client);
            case "PSYNC" -> {
                ResponseDto resDto = commandHandler.psync(command);
                data = resDto.getData();
                yield resDto.getResponse();
            }
            default -> "-ERR unknown command\r\n";
        };
        client.send(response, data);
    }
}
