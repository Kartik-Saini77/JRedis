package com.jredis.components.infra;

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
}
