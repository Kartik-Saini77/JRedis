package com.jredis;

import com.jredis.components.infra.RedisConfig;
import com.jredis.components.infra.Role;
import com.jredis.components.server.TcpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@Slf4j
@SpringBootApplication
public class Main {
    private static int port = 8080;

    public static void main(String[] args) {
        for(int i=0; i<args.length; i++) {
            if(args[i].equals("--port")) {
                try {
                    port = Integer.parseInt(args[i+1]);
                } catch (NumberFormatException e) {
                    log.error("Invalid port number provided. Using default port 8080.");
                }
            }
        }

        SpringApplication.run(Main.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(ApplicationContext context) {
        return args -> {
            TcpServer tcpServer = context.getBean(TcpServer.class);
            RedisConfig redisConfig = context.getBean(RedisConfig.class);

            redisConfig.setRole(Role.master);
            redisConfig.setPort(port);

            tcpServer.startServer(port);
        };
    }
}