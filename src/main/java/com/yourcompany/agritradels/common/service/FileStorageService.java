package com.yourcompany.agritradels.common.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface FileStorageService {
    void init(); // Khởi tạo nơi lưu trữ (nếu cần)
    String store(MultipartFile file, String subFolder); // Lưu file, trả về tên file hoặc path tương đối
    Stream<Path> loadAll(String subFolder); // Load danh sách file
    Path load(String filename, String subFolder); // Load đường dẫn file
    Resource loadAsResource(String filename, String subFolder); // Load file dưới dạng Resource
    void delete(String filename, String subFolder); // Xóa file
    String getFileUrl(String filename, String subFolder); // Lấy URL truy cập file
    void deleteAll(String subFolder); // Xóa toàn bộ thư mục con
}