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
    private static int port = 6379;
    private static Role role = Role.master;
    private static String masterHost = "localhost";
    private static int masterPort = 6379;

    public static void main(String[] args) {

        for(int i=0; i<args.length; i++) {
            switch(args[i]) {
                case "--port" :
                    try {
                        port = Integer.parseInt(args[i+1]);
                        if (role.equals(Role.master))
                            masterPort = port;
                    } catch (NumberFormatException e) {
                        log.error("Invalid port number provided. Using default port 6380.");
                    }
                    break;
                case "--replicaof" :
                    role = Role.slave;
                    masterHost = args[i+1].split(" ")[0];
                    try {
                        masterPort = Integer.parseInt(args[i+1].split(" ")[1]);
                    } catch (NumberFormatException e) {
                        log.error("Invalid port number provided for master. Using default port 6379.");
                    }
                    break;
            }
        }

        SpringApplication.run(Main.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(ApplicationContext context) {
        return args -> {
            TcpServer tcpServer = context.getBean(TcpServer.class);
            RedisConfig redisConfig = context.getBean(RedisConfig.class);

            redisConfig.setPort(port);
            redisConfig.setRole(role);
            redisConfig.setMasterHost(masterHost);
            redisConfig.setMasterPort(masterPort);

            tcpServer.startServer(port);
        };
    }
}