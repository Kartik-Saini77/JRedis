package com.jredis.components;

import com.jredis.models.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class TcpServer {

    @Value("${tcp.server.port}")
    int port;
    Logger logger = LoggerFactory.getLogger(getClass());

    private final RespSerializer respSerializer;
    private final CommandHandler commandHandler;

    public TcpServer(RespSerializer respSerializer, CommandHandler commandHandler) {
        this.respSerializer = respSerializer;
        this.commandHandler = commandHandler;
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                logger.info("New client connected : {}", clientSocket.getInetAddress().getHostAddress());
                Client client = new Client(clientSocket, clientSocket.getInputStream(), clientSocket.getOutputStream());
                CompletableFuture.runAsync(() -> handleClient(client));
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
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
            logger.info("Client disconnected : {}", client.id);
        } catch (IOException e) {
            logger.error("Error handling client {}: {}", client.id, e.getMessage());
        } finally {
            try {
                client.socket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket: {}", e.getMessage());
            }
        }
    }

    private void handleCommand(String[] command, Client client) {
        String response = switch (command[0]) {
            case "PING" -> commandHandler.ping(command);
            case "ECHO" -> commandHandler.echo(command);
            case "SET" -> commandHandler.set(command);
            case "GET" -> commandHandler.get(command);
            default -> "-ERR unknown command\r\n";
        };
        if (response != null) {
            try {
                client.outputStream.write(response.getBytes());
            } catch (IOException e) {
                logger.error("Error sending response to client {}: {}", client.id, e.getMessage());
            }
        }
    }
}
