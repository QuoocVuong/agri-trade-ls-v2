package com.yourcompany.agritrade.common.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {
    private String fileName; // Tên file đã lưu trên server
    private String fileDownloadUri; // URL để truy cập file
    private String fileType; // Content type của file
    private long size; // Kích thước file (bytes)
}