package com.yourcompany.agritradels.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "storage") // Prefix trong application.yaml
@Getter
@Setter
public class StorageProperties {
    /**
     * Folder location for storing files
     */
    private String location = "upload-dir"; // Thư mục lưu trữ mặc định
    private String baseUrl = "/api/files/download"; // Đường dẫn base URL để truy cập file
}