package org.codeart.test;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = org.codeart.App.class)
public class RedisTest {

    @Autowired
    private RedisClient redisClient;

    @After
    public void after() {
        redisClient.shutdown();
    }

    @Test
    public void testGet() {
        StatefulRedisConnection<String, String> connection = redisClient.connect();
        RedisCommands<String, String> commands = connection.sync();
        commands.set("key1", "HelloWorld");
        String value = commands.get("key1");
        System.out.println(value);
        connection.close();
    }
}
