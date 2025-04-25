package com.yourcompany.agritrade.config;

import org.springframework.context.annotation.Configuration;
// Import các bean cần thiết nếu muốn cấu hình thêm
// import org.springframework.context.annotation.Bean;
// import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;

@Configuration
public class MailConfig {
    // Ví dụ cấu hình TemplateResolver nếu cần tùy chỉnh đường dẫn template email
    /*
    @Bean
    public SpringResourceTemplateResolver emailTemplateResolver() {
        SpringResourceTemplateResolver emailTemplateResolver = new SpringResourceTemplateResolver();
        emailTemplateResolver.setPrefix("classpath:/templates/mail/"); // Đường dẫn tới template email
        emailTemplateResolver.setSuffix(".html");
        emailTemplateResolver.setTemplateMode("HTML");
        emailTemplateResolver.setCharacterEncoding("UTF-8");
        emailTemplateResolver.setOrder(1); // Ưu tiên hơn template resolver mặc định (nếu có)
        emailTemplateResolver.setCheckExistence(true);
        return emailTemplateResolver;
    }
    */
}