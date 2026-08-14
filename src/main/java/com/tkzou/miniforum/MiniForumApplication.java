package com.tkzou.miniforum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 微博/论坛系统启动类
 */
@SpringBootApplication
@EnableScheduling
public class MiniForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniForumApplication.class, args);
    }
}
