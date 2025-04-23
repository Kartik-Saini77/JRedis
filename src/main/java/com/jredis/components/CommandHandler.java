package com.jredis.components;

import org.springframework.stereotype.Component;

@Component
public class CommandHandler {

    public String ping(String[] command) {
        return "+PONG\r\n";
    }

    public String echo(String[] command) {
        String respHeader = "$" + command[1].length() + "\r\n";
        return respHeader + command[1] + "\r\n";
    }

    public String set(String[] command) {
        return null;
    }

    public String get(String[] command) {
        return null;
    }
}
