package com.jredis.Components;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

@Component
public class TcpServer {
    @Value("${tcp.server.port}")
    int port;

    public void startServer() {
        ServerSocket serverSocket;
        Socket clientSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);

            while (true) {
                clientSocket = serverSocket.accept();
                Socket finalClientSocket = clientSocket;
                CompletableFuture.runAsync(() -> {
                    handleClient(finalClientSocket);
                });
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }

    public static void handleClient(Socket clientSocket) {
        try {
            OutputStream outputStream = clientSocket.getOutputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            while (true) {
                String line = br.readLine();
                if (line == null || line.isEmpty()) {
                    break;
                } else if (line.equalsIgnoreCase("PING")) {
                    outputStream.write("PONG\r\n".getBytes());
                } else if (line.equalsIgnoreCase("ECHO")) {
                    String response = br.readLine() + "\r\n" + br.readLine() + "\r\n";
                    outputStream.write(response.getBytes());
                }
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }
}
