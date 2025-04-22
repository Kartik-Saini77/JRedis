package com.jredis.Components;

import org.springframework.stereotype.Component;

@Component
public class RespSerializer {
    public void serialize() {
        System.out.println("Serializing RESP");
    }
}
