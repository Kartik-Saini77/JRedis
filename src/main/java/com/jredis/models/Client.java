package com.jredis.models;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.UUID;

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
}
