package com.jredis.components.infra;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Getter
@Component
public class ConnectionPool {
    private Set<Client> clients;
    private Set<Slave> slaves;
    public int slavesThatAreCaughtUp = 0;
    public int bytesSentToSlaves = 0;

    public ConnectionPool() {
        this.clients = new HashSet<>();
        this.slaves = new HashSet<>();
    }

    public void slaveAck(int ackResponse) {
        if (this.bytesSentToSlaves == ackResponse)
            slavesThatAreCaughtUp++;
    }

    public void addClient(Client client) {
        if (client != null)
            clients.add(client);
    }

    public void addSlave(Slave slave) {
        if (slave != null)
            slaves.add(slave);
    }

    public boolean removeClient(Client client) {
        return clients.remove(client);
    }

    public boolean removeSlave(Slave slave) {
        return slaves.remove(slave);
    }

    public boolean removeSlave(Client client) {
        for (Slave slave : slaves) {
            if (slave.connection.equals(client)) {
                return removeSlave(slave);
            }
        }
        return false;
    }
}
