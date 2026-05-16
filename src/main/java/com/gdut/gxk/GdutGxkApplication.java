package com.gdut.gxk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.gdut.gxk.mapper")
@EnableScheduling
public class GdutGxkApplication {

    public static void main(String[] args) {
        SpringApplication.run(GdutGxkApplication.class, args);
    }

}
