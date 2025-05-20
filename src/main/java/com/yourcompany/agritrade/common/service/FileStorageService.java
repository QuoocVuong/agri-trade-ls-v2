package com.yourcompany.agritrade.common.service;

import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
  void init(); // Khởi tạo nơi lưu trữ (nếu cần)

  /**
   * Lưu file vào storage.
   *
   * @param file File cần lưu.
   * @param subFolderPrefix Prefix thư mục con (ví dụ: "product_images", "avatars").
   * @return blobPath (key/đường dẫn đầy đủ của file trên storage, ví dụ:
   *     "product_images/uuid.jpg").
   */
  String store(MultipartFile file, String subFolderPrefix);

  Stream<Path> loadAll(String subFolderPrefix); // Load danh sách file

  Path load(String blobPath); // Có thể trả về null hoặc ném lỗi UnsupportedOperationException

  //    Resource loadAsResource(String filename, String subFolder); // Load file dưới dạng Resource
  //    void delete(String filename, String subFolder); // Xóa file
  //    String getFileUrl(String filename, String subFolder); // Lấy URL truy cập file

  // ****** SỬA CHỮ KÝ CÁC HÀM NÀY ******

  Resource loadAsResource(String blobPath); // Chỉ cần blobPath

  /**
   * Xóa file khỏi storage.
   *
   * @param blobPath Đường dẫn đầy đủ/key của file trên storage.
   */
  void delete(String blobPath); // Chỉ cần blobPath

  /**
   * Lấy URL truy cập file (thường là Signed URL).
   *
   * @param blobPath Đường dẫn đầy đủ/key của file trên storage.
   * @return URL để truy cập file.
   */
  String getFileUrl(String blobPath); // Chỉ cần blobPath

  // ************************************

  void deleteAll(String subFolderPrefix); // Xóa toàn bộ thư mục con
}
