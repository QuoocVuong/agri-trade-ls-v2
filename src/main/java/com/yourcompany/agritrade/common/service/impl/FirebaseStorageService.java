package com.yourcompany.agritrade.common.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import com.yourcompany.agritrade.common.exception.StorageException;
import com.yourcompany.agritrade.common.exception.StorageFileNotFoundException;
import com.yourcompany.agritrade.common.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service("firebaseStorageService")
@Primary
@Slf4j
public class FirebaseStorageService implements FileStorageService {

  @Value("${firebase.storage.bucket-name}")
  private String bucketName;

  @Value("${firebase.storage.service-account-key-path}")
  private String serviceAccountKeyPath;

  private Storage storage; // Client của Google Cloud Storage

  @PostConstruct // Khởi tạo Firebase Admin SDK khi service được tạo
  @Override
  public void init() {
    try {
      // Lấy đường dẫn tuyệt đối đến file key từ classpath
      InputStream serviceAccount =
          getClass()
              .getClassLoader()
              .getResourceAsStream(
                  serviceAccountKeyPath.startsWith("classpath:")
                      ? serviceAccountKeyPath.substring(10)
                      : serviceAccountKeyPath);

      if (serviceAccount == null) {
        // Thử tìm từ hệ thống file nếu không thấy trong classpath
        serviceAccount = new FileInputStream(serviceAccountKeyPath);
      }

      FirebaseOptions options =
          FirebaseOptions.builder()
              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
              .setStorageBucket(bucketName)
              .build();

      // Khởi tạo FirebaseApp nếu chưa có
      if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options);
        log.info("FirebaseApp initialized.");
      } else {
        // Lấy app mặc định nếu đã được khởi tạo bởi config khác
        FirebaseApp.getInstance();
        log.info("FirebaseApp already initialized.");
      }

      // Lấy Storage client từ FirebaseApp đã khởi tạo
      this.storage = StorageClient.getInstance().bucket().getStorage();

      log.info("Firebase Storage initialized for bucket: {}", bucketName);

    } catch (IOException e) {
      log.error("Error initializing Firebase Admin SDK", e);
      throw new StorageException("Could not initialize Firebase Storage.", e);
    }
  }

  @Override
  public String store(MultipartFile file, String subFolder) {
    if (file.isEmpty()) {
      throw new StorageException("Failed to store empty file.");
    }

    String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
    String extension = StringUtils.getFilenameExtension(originalFilename);
    String uniqueFilename =
        UUID.randomUUID().toString() + (extension != null ? "." + extension : "");

    // Tạo đường dẫn đầy đủ trên Firebase Storage (bao gồm thư mục con)
    String blobPath = (StringUtils.hasText(subFolder) ? subFolder + "/" : "") + uniqueFilename;

    try {
      BlobId blobId = BlobId.of(bucketName, blobPath);
      BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();

      // Upload file lên Firebase Storage
      storage.create(blobInfo, file.getBytes());

      log.info("Stored file {} to Firebase Storage at path: {}", originalFilename, blobPath);
      // Trả về đường dẫn đầy đủ (key) của file trên Storage
      return blobPath;

    } catch (IOException e) {
      throw new StorageException("Failed to store file " + originalFilename, e);
    } catch (StorageException e) {
      log.error("Error uploading file to Firebase Storage", e);
      throw new StorageException("Could not store file. Please try again!", e);
    }
  }

  @Override
  public Stream<Path> loadAll(String subFolderPrefix) {
    log.warn(
        "loadAll operation is not efficiently supported by FirebaseStorageService. Returning empty stream.");
    return Stream.empty();
  }

  @Override
  public Path load(String blobPath) {
    // Không trả về Path vật lý vì file nằm trên cloud.

    throw new UnsupportedOperationException("load(blobPath) is not supported");
  }

  @Override
  public Resource loadAsResource(String blobPath) { // Nhận vào đường dẫn đầy đủ trên Storage
    try {
      Blob blob = storage.get(BlobId.of(bucketName, blobPath));
      if (blob == null || !blob.exists()) {
        throw new StorageFileNotFoundException("Could not read file: " + blobPath);
      }
      // Đọc nội dung file vào byte array và trả về ByteArrayResource
      return new ByteArrayResource(blob.getContent());
    } catch (StorageException e) {
      log.error("Error reading file from Firebase Storage: {}", blobPath, e);
      throw new StorageFileNotFoundException("Could not read file: " + blobPath, e);
    }
  }

  @Override
  public void delete(String blobPath) { // Nhận vào đường dẫn đầy đủ
    try {
      BlobId blobId = BlobId.of(bucketName, blobPath);
      boolean deleted = storage.delete(blobId);
      if (deleted) {
        log.info("Deleted file from Firebase Storage: {}", blobPath);
      } else {
        log.warn("File not found or could not be deleted from Firebase Storage: {}", blobPath);
      }
    } catch (Exception e) {
      log.error("Error deleting file from Firebase Storage: {}", blobPath, e);
      throw new StorageException("Could not delete file: " + blobPath, e);
    }
  }

  @Override
  public String getFileUrl(String blobPath) { // Nhận blobPath
    // Luôn tạo Signed URL
    try {
      BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobPath)).build();
      // Tạo URL có hiệu lực trong 1 giờ (ví dụ)
      URL signedUrl =
          storage.signUrl(blobInfo, 1, TimeUnit.HOURS, Storage.SignUrlOption.withV4Signature());
      return signedUrl.toString();
    } catch (Exception e) {
      log.error("Error generating signed URL for blob: {}", blobPath, e);
      // Trả về một URL placeholder hoặc ném lỗi tùy theo yêu cầu

      return "error-generating-url/" + blobPath;
    }
  }

  @Override
  public void deleteAll(String subFolderPrefix) {
    log.warn("deleteAll not implemented for Firebase");
  }
}
