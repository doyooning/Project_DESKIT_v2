package com.deskit.deskit.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync // 비동기 기능을 활성화 -> @Async 붙이면 됨
public class AsyncConfig { // 비동기 쓰레드풀 설정
    @Bean(name = "chatSaveExecutor")
    public Executor chatSaveExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);      // 기본 유지 쓰레드
        executor.setMaxPoolSize(20);      // 최대 쓰레드
        executor.setQueueCapacity(1000);  // 대기 큐
        executor.setThreadNamePrefix("AsyncChat-");
        executor.initialize();
        return executor;
    }
}