package com.yourcompany.agritrade.common.service;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
  void init(); // Khởi tạo nơi lưu trữ

  String store(MultipartFile file, String subFolderPrefix);

  Stream<Path> loadAll(String subFolderPrefix); // Load danh sách file

  Path load(String blobPath);

  Resource loadAsResource(String blobPath);

  void delete(String blobPath);

  String getFileUrl(String blobPath);

  void deleteAll(String subFolderPrefix); // Xóa toàn bộ thư mục con
}
