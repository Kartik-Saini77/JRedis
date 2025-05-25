package com.jredis.components.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Slave {
    public Client connection;
    public List<String> capabilities;

    public Slave(Client client) {
        this.connection = client;
        this.capabilities = new ArrayList<String>();
    }

    public void send(byte[] bytes) throws IOException {
        if(bytes != null) {
            this.connection.outputStream.write(bytes);
        }
    }
}
