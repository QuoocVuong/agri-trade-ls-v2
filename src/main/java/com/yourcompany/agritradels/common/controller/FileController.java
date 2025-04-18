package com.yourcompany.agritradels.common.controller;

import com.yourcompany.agritradels.common.dto.ApiResponse;
import com.yourcompany.agritradels.common.dto.response.FileUploadResponse;
import com.yourcompany.agritradels.common.exception.StorageFileNotFoundException;
import com.yourcompany.agritradels.common.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest; // Import HttpServletRequest
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize; // Import nếu cần bảo vệ
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
// Import ServletUriComponentsBuilder nếu dùng để tạo URL download động
// import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files") // Base path cho API file
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileStorageService fileStorageService;

    // Endpoint để upload một file
    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()") // Ví dụ: Yêu cầu đăng nhập để upload
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "images") String type) { // Phân loại thư mục con

        // Kiểm tra loại file nếu cần (ví dụ chỉ cho phép ảnh)
        // if (!Arrays.asList(MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE).contains(file.getContentType())) {
        //     return ResponseEntity.badRequest().body(ApiResponse.badRequest("Only JPG/PNG images are allowed"));
        // }

        String filename = fileStorageService.store(file, type);

        // Lấy URL để truy cập file từ service
        String fileDownloadUri = fileStorageService.getFileUrl(filename, type);

        FileUploadResponse responseData = new FileUploadResponse(filename, fileDownloadUri, file.getContentType(), file.getSize());
        return ResponseEntity.ok(ApiResponse.success(responseData, "File uploaded successfully"));
    }

    // Endpoint để upload nhiều file (tùy chọn)
    @PostMapping("/uploadMultiple")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> uploadMultipleFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(required = false, defaultValue = "images") String type) {

        List<FileUploadResponse> responses = Arrays.stream(files)
                .map(file -> {
                    String filename = fileStorageService.store(file, type);
                    String fileDownloadUri = fileStorageService.getFileUrl(filename, type);
                    return new FileUploadResponse(filename, fileDownloadUri, file.getContentType(), file.getSize());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses, "Files uploaded successfully"));
    }


    // Endpoint để tải/xem file
    @GetMapping("/download/{type}/{filename:.+}")
    // Có thể không cần @PreAuthorize nếu ảnh/file là public
    public ResponseEntity<Resource> downloadFile(@PathVariable String type, @PathVariable String filename, HttpServletRequest request) {
        Resource resource = fileStorageService.loadAsResource(filename, type);

        // Xác định content type
        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            log.info("Could not determine file type.");
        }
        // Content type mặc định nếu không xác định được
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // Header để trình duyệt hiển thị file thay vì tải xuống (nếu là ảnh/pdf...)
                // .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                // Header để trình duyệt luôn tải xuống
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    // Endpoint để xóa file (tùy chọn, cần bảo mật cẩn thận)
    @DeleteMapping("/{type}/{filename:.+}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('MANAGE_OWN_FILES')") // Ví dụ: Chỉ Admin hoặc người sở hữu được xóa
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable String type, @PathVariable String filename) {
        try {
            fileStorageService.delete(filename, type);
            return ResponseEntity.ok(ApiResponse.success("File deleted successfully"));
        } catch (StorageFileNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.notFound(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.internalError("Could not delete the file"));
        }
    }

}