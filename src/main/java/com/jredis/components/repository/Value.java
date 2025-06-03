package com.jredis.components.repository;

import java.time.LocalDateTime;

public class Value {
    public String value;
    public LocalDateTime createdAt;
    public LocalDateTime expiresAt;
    public boolean isDeletedInTransaction;

    public Value(String value, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.value = value;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.isDeletedInTransaction = false;
    }
}
