package com.jredis.components.infra;

import com.jredis.components.services.ResponseDto;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;

@Slf4j
public class Client {
    public String id;
    public Socket socket;
    public InputStream inputStream;
    public OutputStream outputStream;

    public boolean transactionalContext;
    public Queue<String[]> commandQueue;
    public List<String> transactionResponse;

    public Client(Socket socket, InputStream inputStream, OutputStream outputStream) {
        this.id = UUID.randomUUID().toString();
        this.socket = socket;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    public boolean beginTransaction() {
        if (transactionalContext)
            return false;
        transactionalContext = true;
        commandQueue = new LinkedList<>();
        transactionResponse = new ArrayList<>();
        return true;
    }

    public void endTransaction() {
        transactionalContext = false;
        commandQueue = null;
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

    public void send(ResponseDto res) {
        try {
            if (res != null) {
                if (res.getResponse() != null && !res.getResponse().isEmpty())
                    outputStream.write(res.getResponse().getBytes());
                if (res.getData() != null)
                    outputStream.write(res.getData());
            }
        } catch (IOException e) {
            log.error("Error sending response to master: {}", e.getMessage());
        }
    }

    public void send(byte[] data) {
        try {
            if (data != null)
                outputStream.write(data);
        } catch (IOException e) {
            log.error("Error sending response to master: {}", e.getMessage());
        }
    }

    public void send(String data) {
        try {
            if (data != null && !data.isEmpty())
                outputStream.write(data.getBytes());
        } catch (IOException e) {
            log.error("Error sending response to master: {}", e.getMessage());
        }
    }
}
