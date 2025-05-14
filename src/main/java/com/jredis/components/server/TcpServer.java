package com.jredis.components.server;

import com.jredis.components.infra.Client;
import com.jredis.components.services.CommandHandler;
import com.jredis.components.services.RespSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class TcpServer {

    private final RespSerializer respSerializer;
    private final CommandHandler commandHandler;

    public TcpServer(RespSerializer respSerializer, CommandHandler commandHandler) {
        this.respSerializer = respSerializer;
        this.commandHandler = commandHandler;
    }

    public void startServer(int port) {
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
        String response = switch (command[0]) {
            case "PING" -> commandHandler.ping(command);
            case "ECHO" -> commandHandler.echo(command);
            case "SET" -> commandHandler.set(command);
            case "GET" -> commandHandler.get(command);
            case "INFO" -> commandHandler.info(command);
            default -> "-ERR unknown command\r\n";
        };
        if (response != null) {
            try {
                client.outputStream.write(response.getBytes());
            } catch (IOException e) {
                log.error("Error sending response to client {}: {}", client.id, e.getMessage());
            }
        }
    }
}
