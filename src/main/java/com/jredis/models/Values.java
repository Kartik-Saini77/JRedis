package com.jredis.models;

import java.time.LocalDateTime;

public class Values {
    public String value;
    public LocalDateTime createdAt;
    public LocalDateTime expiresAt;

    public Values(String value, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.value = value;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
