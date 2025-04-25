package com.yourcompany.agritrade.common.service.impl;

import com.yourcompany.agritrade.common.exception.StorageException;
import com.yourcompany.agritrade.common.exception.StorageFileNotFoundException;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.config.properties.StorageProperties;
import jakarta.annotation.PostConstruct; // Import PostConstruct
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder; // Import để tạo URL

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class FileSystemStorageService implements FileStorageService {

    private final Path rootLocation;
    private final String baseUrl;

    @Autowired
    public FileSystemStorageService(StorageProperties properties) {
        if(properties.getLocation().trim().isEmpty()){
            throw new StorageException("File upload location can not be Empty.");
        }
        this.rootLocation = Paths.get(properties.getLocation());
        this.baseUrl = properties.getBaseUrl(); // Lưu base URL từ properties
        log.info("File storage location initialized at: {}", this.rootLocation.toAbsolutePath());
        log.info("File base URL set to: {}", this.baseUrl);
    }

    @Override
    @PostConstruct // Chạy hàm này sau khi bean được tạo
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            log.info("Created root storage directory: {}", rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new StorageException("Could not initialize storage location: " + rootLocation.toAbsolutePath(), e);
        }
    }

    @Override
    public String store(MultipartFile file, String subFolder) {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file.");
        }

        // Tạo thư mục con nếu chưa có
        Path subFolderPath = this.rootLocation.resolve(Paths.get(subFolder)).normalize().toAbsolutePath();
        if (!subFolderPath.startsWith(this.rootLocation.toAbsolutePath())) {
            throw new StorageException("Cannot store file outside current directory.");
        }
        try {
            Files.createDirectories(subFolderPath);
        } catch (IOException e) {
            throw new StorageException("Could not create subfolder: " + subFolderPath, e);
        }


        // Tạo tên file duy nhất để tránh trùng lặp
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID().toString() + "." + extension;

        Path destinationFile = subFolderPath.resolve(uniqueFilename).normalize().toAbsolutePath();

        // Kiểm tra lại đường dẫn đích
        if (!destinationFile.getParent().equals(subFolderPath)) {
            throw new StorageException("Cannot store file outside target directory.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored file: {}", destinationFile);
            return uniqueFilename; // Trả về tên file duy nhất đã lưu
        } catch (IOException e) {
            throw new StorageException("Failed to store file " + uniqueFilename, e);
        }
    }

    @Override
    public Stream<Path> loadAll(String subFolder) {
        Path subFolderPath = this.rootLocation.resolve(Paths.get(subFolder)).normalize().toAbsolutePath();
        try {
            return Files.walk(subFolderPath, 1) // Chỉ lấy file trong thư mục con, không đệ quy
                    .filter(path -> !path.equals(subFolderPath))
                    .map(subFolderPath::relativize);
        } catch (IOException e) {
            throw new StorageException("Failed to read stored files in " + subFolder, e);
        }
    }

    @Override
    public Path load(String filename, String subFolder) {
        Path subFolderPath = this.rootLocation.resolve(Paths.get(subFolder)).normalize().toAbsolutePath();
        return subFolderPath.resolve(filename);
    }

    @Override
    public Resource loadAsResource(String filename, String subFolder) {
        try {
            Path file = load(filename, subFolder);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new StorageFileNotFoundException("Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new StorageFileNotFoundException("Could not read file: " + filename, e);
        }
    }

    @Override
    public void delete(String filename, String subFolder) {
        try {
            Path file = load(filename, subFolder);
            Files.deleteIfExists(file);
            log.info("Deleted file: {}", file);
        } catch (IOException e) {
            throw new StorageException("Could not delete file: " + filename, e);
        }
    }

    @Override
    public String getFileUrl(String filename, String subFolder) {
        // Tạo URL dựa trên baseUrl đã cấu hình và đường dẫn tương đối
        // Ví dụ: /api/files/download/images/abc.jpg
        return ServletUriComponentsBuilder.fromCurrentContextPath() // Lấy base URL của ứng dụng
                .path(this.baseUrl + "/") // Thêm base URL từ config
                .path(subFolder + "/")    // Thêm thư mục con
                .path(filename)           // Thêm tên file
                .toUriString();
    }


    @Override
    public void deleteAll(String subFolder) {
        Path subFolderPath = this.rootLocation.resolve(Paths.get(subFolder)).normalize().toAbsolutePath();
        FileSystemUtils.deleteRecursively(subFolderPath.toFile());
        log.info("Deleted all files in subfolder: {}", subFolder);
    }
}