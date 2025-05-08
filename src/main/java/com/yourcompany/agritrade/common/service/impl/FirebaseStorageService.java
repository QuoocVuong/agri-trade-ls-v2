package com.yourcompany.agritrade.common.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*; // Import các class từ google-cloud-storage
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import com.yourcompany.agritrade.common.exception.StorageException;
import com.yourcompany.agritrade.common.exception.StorageFileNotFoundException;
import com.yourcompany.agritrade.common.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource; // Dùng ByteArrayResource để trả về
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream; // Import FileInputStream
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path; // Vẫn dùng Path cho loadAll nếu cần (ít dùng với cloud)
import java.util.UUID;
import java.util.concurrent.TimeUnit; // Import TimeUnit cho signed URL
import java.util.stream.Stream; // Import Stream

@Service("firebaseStorageService") // Đặt tên cụ thể cho bean
@Primary
@Slf4j
public class FirebaseStorageService implements FileStorageService {

    @Value("${firebase.storage.bucket-name}")
    private String bucketName;

    @Value("${firebase.storage.service-account-key-path}")
    private String serviceAccountKeyPath;

    // Bỏ @Value cho publicBaseUrl nếu bạn quyết định luôn dùng Signed URL
    // (Tùy chọn) Base URL nếu bucket public
//    @Value("${firebase.storage.public-base-url:#{null}}") // Mặc định là null
//    private String publicBaseUrl;


    private Storage storage; // Client của Google Cloud Storage
    // private Bucket bucket; // Không cần bucket ở đây nữa nếu dùng StorageClient.getInstance().bucket() trực tiếp

    @PostConstruct // Khởi tạo Firebase Admin SDK khi service được tạo
    @Override
    public void init() {
        try {
            // Lấy đường dẫn tuyệt đối đến file key từ classpath
            InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream(
                    serviceAccountKeyPath.startsWith("classpath:") ?
                            serviceAccountKeyPath.substring(10) : serviceAccountKeyPath
            );

            if (serviceAccount == null) {
                // Thử tìm từ hệ thống file nếu không thấy trong classpath
                serviceAccount = new FileInputStream(serviceAccountKeyPath);
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setStorageBucket(bucketName)
                    .build();

            // Khởi tạo FirebaseApp nếu chưa có (tránh lỗi nếu có config Firebase khác)
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
            // Hoặc StorageClient.getInstance(FirebaseApp.getInstance()).bucket().getStorage();
            //this.bucket = StorageClient.getInstance().bucket();// Lấy bucket cụ thể
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
        String uniqueFilename = UUID.randomUUID().toString() + (extension != null ? "." + extension : "");

        // Tạo đường dẫn đầy đủ trên Firebase Storage (bao gồm thư mục con)
        String blobPath = (StringUtils.hasText(subFolder) ? subFolder + "/" : "") + uniqueFilename;

        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    // Thêm metadata nếu cần (ví dụ: cờ public)
                    // .setMetadata(Map.of("public", "true"))
                    .build();

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
        // Việc list tất cả file trong bucket/subfolder có thể tốn kém và không hiệu quả với cloud storage.
        // Thường thì bạn sẽ lưu thông tin file (URL hoặc key) trong DB và truy vấn từ đó.
        // Trả về Stream rỗng hoặc ném lỗi không hỗ trợ.
        log.warn("loadAll operation is not efficiently supported by FirebaseStorageService. Returning empty stream.");
        return Stream.empty();
        // Hoặc nếu thực sự cần:
        // try {
        //     Page<Blob> blobs = bucket.list(Storage.BlobListOption.prefix(subFolder + "/"), Storage.BlobListOption.currentDirectory());
        //     // Cần chuyển đổi Blob sang Path (có thể không trực tiếp)
        //     return StreamSupport.stream(blobs.iterateAll().spliterator(), false).map(blob -> Paths.get(blob.getName()));
        // } catch (Exception e) {
        //     throw new StorageException("Failed to read stored files in " + subFolder, e);
        // }
    }

    @Override
    public Path load(String blobPath) {
        // Không trả về Path vật lý vì file nằm trên cloud.
        // Phương thức này có thể không còn ý nghĩa.
        throw new UnsupportedOperationException("load(blobPath) is not supported");
    }


    @Override // Đảm bảo @Override khớp với interface mới
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

    // Overload để tương thích interface cũ (nhưng nên đổi interface)
//    @Override
//    public Resource loadAsResource(String filename, String subFolder) {
//        String blobPath = (StringUtils.hasText(subFolder) ? subFolder + "/" : "") + filename;
//        return loadAsResource(blobPath);
//    }


    @Override // Đảm bảo @Override khớp với interface mới
    public void delete(String blobPath) { // Nhận vào đường dẫn đầy đủ
        try {
            BlobId blobId = BlobId.of(bucketName, blobPath);
            boolean deleted = storage.delete(blobId);
            if (deleted) {
                log.info("Deleted file from Firebase Storage: {}", blobPath);
            } else {
                log.warn("File not found or could not be deleted from Firebase Storage: {}", blobPath);
                // Không ném lỗi nếu file không tồn tại để tránh lỗi khi xóa nhiều lần
                // throw new StorageFileNotFoundException("Could not delete file: " + blobPath);
            }
        } catch (Exception e) {
            log.error("Error deleting file from Firebase Storage: {}", blobPath, e);
            throw new StorageException("Could not delete file: " + blobPath, e);
        }
    }

    // Overload để tương thích interface cũ
//    @Override
//    public void delete(String filename, String subFolder) {
//        String blobPath = (StringUtils.hasText(subFolder) ? subFolder + "/" : "") + filename;
//        delete(blobPath);
//    }


//    @Override // Đảm bảo @Override khớp với interface mới
//    public String getFileUrl(String blobPath) { // Nhận vào đường dẫn đầy đủ
//        // Cách 1: Nếu bucket public-read
//        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
//            // Cần encode tên file/đường dẫn để tránh lỗi với ký tự đặc biệt
//            try {
//                String encodedBlobPath = URLEncoder.encode(blobPath, StandardCharsets.UTF_8.toString())
//                        .replace("+", "%20"); // Thay dấu + thành %20
//                return publicBaseUrl + "/" + encodedBlobPath;
//            } catch (Exception e) {
//                log.error("Error encoding blob path for public URL", e);
//                // Fallback về signed URL nếu encode lỗi
//            }
//        }
//
//        // Cách 2: Tạo Signed URL (có thời hạn truy cập) - An toàn hơn
//        try {
//            BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobPath)).build();
//            // Tạo URL có hiệu lực trong 1 giờ (ví dụ)
//            // Cần cấp quyền "Service Account Token Creator" cho service account của bạn trong IAM
//            java.net.URL signedUrl = storage.signUrl(blobInfo, 1, TimeUnit.HOURS, Storage.SignUrlOption.withV4Signature());
//            return signedUrl.toString();
//        } catch (Exception e) {
//            log.error("Error generating signed URL for blob: {}", blobPath, e);
//            throw new StorageException("Could not generate file URL for " + blobPath, e);
//        }
//    }
@Override
public String getFileUrl(String blobPath) { // Nhận blobPath
    // Luôn tạo Signed URL
    try {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobPath)).build();
        // Tạo URL có hiệu lực trong 1 giờ (ví dụ)
        URL signedUrl = storage.signUrl(blobInfo, 1, TimeUnit.HOURS, Storage.SignUrlOption.withV4Signature());
        return signedUrl.toString();
    } catch (Exception e) {
        log.error("Error generating signed URL for blob: {}", blobPath, e);
        // Trả về một URL placeholder hoặc ném lỗi tùy theo yêu cầu
        // Ví dụ, trả về blobPath để client biết có lỗi nhưng vẫn có key
        // Hoặc throw new StorageException("Could not generate file URL for " + blobPath, e);
        return "error-generating-url/" + blobPath; // Hoặc một URL lỗi cố định
    }
}

    // Overload để tương thích interface cũ
//    @Override
//    public String getFileUrl(String filename, String subFolder) {
//        String blobPath = (StringUtils.hasText(subFolder) ? subFolder + "/" : "") + filename;
//        return getFileUrl(blobPath);
//    }


    @Override
    public void deleteAll(String subFolderPrefix) {
        // Xóa tất cả object trong một "thư mục" trên cloud storage phức tạp hơn.
        // Cần list tất cả blobs với prefix và xóa từng cái một.
        // Hoặc dùng các thư viện/công cụ quản lý bucket.
        log.warn("deleteAll not implemented for Firebase");
        // Implement nếu thực sự cần thiết và cẩn thận.
    }
}