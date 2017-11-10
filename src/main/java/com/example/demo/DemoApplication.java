package com.example.demo;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.testcontainers.containers.GenericContainer;

import java.util.Properties;

import static java.text.MessageFormat.format;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@SpringBootApplication
public class DemoApplication {

    public static GenericContainer redis =
            new GenericContainer("redis:3.2")
                    .withExposedPorts(6379);

    @Bean
    public RedisClient redisClient(RedisProperties properties) {
        String connectionStr = format("redis://{0}:{1,number,#}/0", properties.getHost(), properties.getPort());
        return RedisClient.create(connectionStr);
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<String, String> statefulRedisConnection(RedisClient redisClient) {
        return redisClient.connect();
    }

    @Bean
    public RedisReactiveCommands<String, String> redisCommands(StatefulRedisConnection<String, String> statefulRedisConnection) {
        return statefulRedisConnection.reactive();
    }

    @Autowired
    private ReactiveRedisTemplate redisTemplate;

    @Autowired
    private RedisReactiveCommands<String, String> redisCommands;

    @Bean
    public RouterFunction<ServerResponse> routerFunction() {

        return route(path("/spring"), request ->
                redisTemplate.opsForValue().set("spring", request.path())
                        .flatMap($_ -> ServerResponse.ok().build()))
                .andRoute(path("/lettuce"), request ->
                        redisCommands.set("lettuce", request.path())
                                .flatMap($_ -> ServerResponse.ok().build()));
    }

    public static void main(String[] args) {
        redis.start();

        SpringApplication app = new SpringApplication(DemoApplication.class);

        Properties properties = new Properties();
        properties.put("spring.redis.host", redis.getContainerIpAddress());
        properties.put("spring.redis.port", redis.getMappedPort(6379));
        app.setDefaultProperties(properties);

        app.run(args);
    }
}
