package com.yourcompany.agritradels.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor; // Import đúng

import java.util.concurrent.Executor;

@Configuration
@EnableAsync // Bật tính năng xử lý bất đồng bộ của Spring
public class AsyncConfig {

    // Cấu hình một thread pool để thực thi các tác vụ @Async
    @Bean(name = "taskExecutor") // Đặt tên cho bean executor
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Số luồng cơ bản
        executor.setMaxPoolSize(10); // Số luồng tối đa
        executor.setQueueCapacity(25); // Hàng đợi tác vụ
        executor.setThreadNamePrefix("Async-"); // Tiền tố tên luồng
        executor.initialize();
        return executor;
    }
}