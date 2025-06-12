package com.yourcompany.agritrade.common.controller;

import com.yourcompany.agritrade.common.dto.ApiResponse;
import com.yourcompany.agritrade.common.dto.response.FileUploadResponse;
import com.yourcompany.agritrade.common.exception.StorageFileNotFoundException;
import com.yourcompany.agritrade.common.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

@RestController
@RequestMapping("/api/files") // Base path cho API file
@RequiredArgsConstructor
@Slf4j
public class FileController {

  private final FileStorageService fileStorageService;

  // Endpoint để upload một file
  @PostMapping("/upload")
  @PreAuthorize("isAuthenticated()") //  Yêu cầu đăng nhập để upload
  public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false, defaultValue = "images")
          String type) { // Phân loại thư mục con

    String blobPath = fileStorageService.store(file, type);
    String fileDownloadUri = fileStorageService.getFileUrl(blobPath);

    FileUploadResponse responseData =
        new FileUploadResponse(blobPath, fileDownloadUri, file.getContentType(), file.getSize());
    return ResponseEntity.ok(ApiResponse.success(responseData, "File uploaded successfully"));
  }

  // Endpoint để upload nhiều file
  @PostMapping("/uploadMultiple")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<List<FileUploadResponse>>> uploadMultipleFiles(
      @RequestParam("files") MultipartFile[] files,
      @RequestParam(required = false, defaultValue = "images") String type) {

    List<FileUploadResponse> responses =
        Arrays.stream(files)
            .map(
                file -> {
                  String blobPath = fileStorageService.store(file, type);
                  String fileDownloadUri = fileStorageService.getFileUrl(blobPath);
                  return new FileUploadResponse(
                      blobPath, fileDownloadUri, file.getContentType(), file.getSize());
                })
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(responses, "Files uploaded successfully"));
  }

  // Endpoint để tải/xem file

  @GetMapping("/download/**")
  public ResponseEntity<Resource> downloadFile(HttpServletRequest request) {
    String finalBlobPath = extractBlobPath(request, "/download/"); // Truyền prefix
    if (!StringUtils.hasText(finalBlobPath)) {
      log.warn(
          "Could not extract blobPath from request URI for download: {}", request.getRequestURI());
      return ResponseEntity.badRequest().build();
    }
    log.debug("Attempting to download file with blobPath: {}", finalBlobPath);
    Resource resource = fileStorageService.loadAsResource(finalBlobPath);

    String contentType = getContentType(request, resource); // Helper lấy content type
    String filename = StringUtils.getFilename(finalBlobPath);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "inline; filename=\"" + (filename != null ? filename : "download") + "\"")
        .body(resource);
  }

  // Endpoint để xóa file
  @DeleteMapping("/**")
  @PreAuthorize(
      "hasRole('ADMIN') or hasAuthority('MANAGE_OWN_FILES')") // Chỉ Admin hoặc người sở hữu
  // được xóa
  public ResponseEntity<ApiResponse<Void>> deleteFile(HttpServletRequest request) {

    String finalBlobPath = extractBlobPath(request, "");

    if (!StringUtils.hasText(finalBlobPath)) {
      return ResponseEntity.badRequest()
          .body(ApiResponse.badRequest("Invalid file path for deletion"));
    }
    try {
      log.info("Attempting to delete file with blobPath: {}", finalBlobPath);
      fileStorageService.delete(finalBlobPath);
      return ResponseEntity.ok(ApiResponse.success("File deleted successfully"));
    } catch (StorageFileNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.notFound(e.getMessage()));
    } catch (Exception e) {
      log.error("Error deleting file: {}", finalBlobPath, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(ApiResponse.internalError("Could not delete the file"));
    }
  }

  // HÀM HELPER

  private String extractBlobPath(HttpServletRequest request, String endpointPrefix) {
    // Lấy toàn bộ đường dẫn sau context path của ứng dụng
    String path =
        (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    // Lấy pattern mà handler này khớp (ví dụ: /api/files/download/**)
    String bestMatchPattern =
        (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

    if (path == null || bestMatchPattern == null) {

      String requestUri = request.getRequestURI();
      String contextPath = request.getContextPath();
      String servletPath = "/api/files"; // Base path của FileController

      int startIndex = requestUri.indexOf(contextPath + servletPath);
      if (startIndex != -1) {
        String remainingPath =
            requestUri.substring(startIndex + (contextPath + servletPath).length());
        if (remainingPath.startsWith(endpointPrefix)) {
          return remainingPath.substring(endpointPrefix.length());
        }
        // Nếu endpointPrefix là rỗng (cho delete /**)
        if (endpointPrefix.isEmpty() && !remainingPath.isEmpty() && remainingPath.startsWith("/")) {
          return remainingPath.substring(1); // Bỏ dấu / ở đầu
        }
        return remainingPath; // Trả về phần còn lại nếu không có prefix hoặc prefix rỗng
      }
      return null;
    }

    // Sử dụng AntPathMatcher để trích xuất phần wildcard (**)
    AntPathMatcher apm = new AntPathMatcher();
    String extractedPath = apm.extractPathWithinPattern(bestMatchPattern, path);

    if (StringUtils.hasText(endpointPrefix)
        && extractedPath.startsWith(
            endpointPrefix.startsWith("/") ? endpointPrefix.substring(1) : endpointPrefix)) {
      return extractedPath.substring(
          (endpointPrefix.startsWith("/") ? endpointPrefix.substring(1) : endpointPrefix).length());
    }
    // Nếu endpointPrefix là rỗng, và extractedPath bắt đầu bằng /, bỏ nó đi
    if (endpointPrefix.isEmpty() && extractedPath.startsWith("/")) {
      return extractedPath.substring(1);
    }

    return extractedPath;
  }

  // --- Helper Method ---
  private String getContentType(HttpServletRequest request, Resource resource) {
    String contentType = null;
    try {
      contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
    } catch (IOException ex) {
      log.info("Could not determine file type via ServletContext.");

      String filename = resource.getFilename();
      if (filename != null) {
        if (filename.endsWith(".png")) contentType = MediaType.IMAGE_PNG_VALUE;
        else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg"))
          contentType = MediaType.IMAGE_JPEG_VALUE;
        else if (filename.endsWith(".gif")) contentType = MediaType.IMAGE_GIF_VALUE;
      }
    }
    return contentType != null ? contentType : "application/octet-stream";
  }
}
