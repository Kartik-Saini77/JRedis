package com.thryve;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        ServerSocket serverSocket;
        Socket clientSocket = null;
        int port = 8080;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);

            clientSocket = serverSocket.accept();

            OutputStream outputStream = clientSocket.getOutputStream();
            outputStream.write("PONG\r\n".getBytes());
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
}