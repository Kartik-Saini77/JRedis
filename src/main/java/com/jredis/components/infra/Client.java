package com.jredis.components.infra;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

@Slf4j
public class Client {
    public String id;
    public Socket socket;
    public InputStream inputStream;
    public OutputStream outputStream;

    public Client(Socket socket, InputStream inputStream, OutputStream outputStream) {
        this.id = UUID.randomUUID().toString();
        this.socket = socket;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    public void send(String res, byte[] data) {
        try {
            if (res != null && !res.isEmpty())
                outputStream.write(res.getBytes());
            if (data != null)
                outputStream.write(data);
        } catch (IOException e) {
            log.error("Error sending response to master: {}", e.getMessage());
        }
    }
}
