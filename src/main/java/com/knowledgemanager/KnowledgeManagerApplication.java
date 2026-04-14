package com.knowledgemanager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Knowledge Manager 单体应用启动类
 */
@SpringBootApplication
@MapperScan("com.knowledgemanager.common.mapper")
@EnableAsync
public class KnowledgeManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeManagerApplication.class, args);
        System.out.println("===========================================");
        System.out.println("Knowledge Manager Started Successfully!");
        System.out.println("===========================================");
    }
}
