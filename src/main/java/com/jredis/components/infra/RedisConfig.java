package com.jredis.components.infra;

import lombok.*;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Component
public class RedisConfig {
    private Role role;
    private int port;
    private String masterHost;
    private int masterPort;
    private final String masterReplId = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().substring(0, 8);
    private long masterReplOffset = 0L;
}
