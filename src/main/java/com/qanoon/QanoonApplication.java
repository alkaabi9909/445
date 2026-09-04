package com.qanoon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * نقطة تشغيل نظام "قانون" – النظام المتكامل لإدارة مكاتب المحاماة.
 */
@SpringBootApplication
@EnableScheduling
public class QanoonApplication {
    public static void main(String[] args) {
        SpringApplication.run(QanoonApplication.class, args);
    }
}
