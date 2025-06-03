package com.yourcompany.agritrade.common.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.agritrade.common.dto.response.FileUploadResponse;
import com.yourcompany.agritrade.common.exception.StorageFileNotFoundException;
import com.yourcompany.agritrade.common.service.FileStorageService;
import com.yourcompany.agritrade.config.TestSecurityConfig; // Đảm bảo import đúng
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.ServletContext; // Thêm import này
import java.nio.charset.StandardCharsets;
import java.util.Arrays; // Thêm import này
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import(TestSecurityConfig.class) // Sử dụng TestSecurityConfig
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean // Mock ServletContext để kiểm soát getMimeType
    private ServletContext servletContext;


    private MockMultipartFile sampleFilePng;
    private MockMultipartFile sampleFileJpg;


    @BeforeEach
    void setUp() {
        sampleFilePng = new MockMultipartFile(
                "file", // Tên tham số cho single file upload
                "test-image.png",
                MediaType.IMAGE_PNG_VALUE,
                "nội dung ảnh thử nghiệm png".getBytes(StandardCharsets.UTF_8)
        );
        sampleFileJpg = new MockMultipartFile(
                "files", // Tên tham số cho multiple file upload
                "image2.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "nội dung ảnh thử nghiệm jpg".getBytes(StandardCharsets.UTF_8)
        );
    }

    @Nested
    @DisplayName("Kiểm tra Tải lên Tệp")
    @WithMockUser // Người dùng đã xác thực mặc định để tải lên
    class FileUploadTests {
        @Test
        @DisplayName("POST /api/files/upload - Tải lên Một Tệp - Thành công")
        void uploadFile_success() throws Exception {
            String blobPath = "images/generated-uuid.png";
            String fileUrl = "http://localhost/api/files/download/" + blobPath;
            // Không cần tạo expectedResponse ở đây vì chúng ta kiểm tra từng field

            when(fileStorageService.store(any(MultipartFile.class), eq("images"))).thenReturn(blobPath);
            when(fileStorageService.getFileUrl(blobPath)).thenReturn(fileUrl);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/files/upload")
                            .file(sampleFilePng) // Sử dụng sampleFilePng
                            .param("type", "images"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("File uploaded successfully"))) // Giữ tiếng Anh nếu API trả về tiếng Anh
                    .andExpect(jsonPath("$.data.fileName", is(blobPath)))
                    .andExpect(jsonPath("$.data.fileDownloadUri", is(fileUrl)));
        }

        @Test
        @DisplayName("POST /api/files/uploadMultiple - Tải lên Nhiều Tệp - Thành công")
        void uploadMultipleFiles_success() throws Exception {
            // Đảm bảo tên tham số của các MockMultipartFile là "files"
            MockMultipartFile file1ForMulti = new MockMultipartFile(
                    "files", // Tên tham số cho mảng MultipartFile[] files
                    "image1.png",
                    MediaType.IMAGE_PNG_VALUE,
                    "image1_content".getBytes(StandardCharsets.UTF_8)
            );
            MockMultipartFile file2ForMulti = new MockMultipartFile(
                    "files", // Tên tham số phải giống nhau cho tất cả các file trong mảng
                    "image2.jpg",
                    MediaType.IMAGE_JPEG_VALUE,
                    "image2_content_jpg".getBytes(StandardCharsets.UTF_8)
            );

            String blobPath1 = "other/uuid1.png";
            String blobPath2 = "other/uuid2.jpg";
            String url1 = "http://localhost/api/files/download/" + blobPath1;
            String url2 = "http://localhost/api/files/download/" + blobPath2;

            // Mock cho từng file
            when(fileStorageService.store(eq(file1ForMulti), eq("other"))).thenReturn(blobPath1);
            when(fileStorageService.getFileUrl(blobPath1)).thenReturn(url1);
            when(fileStorageService.store(eq(file2ForMulti), eq("other"))).thenReturn(blobPath2);
            when(fileStorageService.getFileUrl(blobPath2)).thenReturn(url2);

            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/files/uploadMultiple")
                            .file(file1ForMulti) // File đầu tiên
                            .file(file2ForMulti)  // File thứ hai
                            .param("type", "other"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.data.length()", is(2))) // Mong đợi 2 file
                    .andExpect(jsonPath("$.data[0].fileName", is(blobPath1)))
                    .andExpect(jsonPath("$.data[1].fileName", is(blobPath2)));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Tải xuống Tệp")
    class FileDownloadTests {
        @Test
        @DisplayName("GET /api/files/download/** - Tải xuống Tệp PNG - Thành công")
        void downloadFile_png_success() throws Exception {
            String blobPath = "images/test-image.png";
            byte[] contentBytes = "nội dung ảnh thử nghiệm png".getBytes(StandardCharsets.UTF_8);
            Resource resource = spy(new ByteArrayResource(contentBytes)); // SPY resource

            // Mock getFilename() để logic fallback trong controller hoạt động
            when(resource.getFilename()).thenReturn("test-image.png");
            // Mock getFile() để ném IOException, buộc controller dùng logic fallback
            // Hoặc không cần mock getFile() nếu bạn chấp nhận getMimeType trả về null
            // when(resource.getFile()).thenThrow(new IOException("Simulated: Not a file system resource"));

            when(fileStorageService.loadAsResource(blobPath)).thenReturn(resource);
            // Không cần mock servletContext.getMimeType nữa nếu logic fallback hoạt động

            mockMvc.perform(get("/api/files/download/" + blobPath))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"test-image.png\""))
                    .andExpect(content().contentType(MediaType.IMAGE_PNG)) // Mong đợi IMAGE_PNG
                    .andExpect(content().bytes(contentBytes));
        }

        @Test
        @DisplayName("GET /api/files/download/** - Tải xuống Tệp - Không tìm thấy (404)")
        void downloadFile_notFound_shouldReturn404() throws Exception {
            String blobPath = "images/not-found.png";
            when(fileStorageService.loadAsResource(blobPath)).thenThrow(new StorageFileNotFoundException("File not found"));

            // GlobalExceptionHandler sẽ bắt StorageFileNotFoundException và trả về 404
            // nếu được cấu hình đúng.
            mockMvc.perform(get("/api/files/download/" + blobPath))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("File not found"))); // Kiểm tra message từ GlobalExceptionHandler
        }
    }

    @Nested
    @DisplayName("Kiểm tra Xóa Tệp")
    @WithMockUser(roles = "ADMIN")
    class FileDeletionTests {
        @Test
        @DisplayName("DELETE /api/files/** - Xóa Tệp - Thành công")
        void deleteFile_success() throws Exception {
            String blobPath = "images/to-delete.png";
            doNothing().when(fileStorageService).delete(blobPath);

            mockMvc.perform(delete("/api/files/" + blobPath))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)))
                    .andExpect(jsonPath("$.message", is("File deleted successfully")));

            verify(fileStorageService).delete(blobPath);
        }

        @Test
        @DisplayName("DELETE /api/files/** - Xóa Tệp - Không tìm thấy (404)")
        void deleteFile_notFound_shouldReturn404() throws Exception {
            String blobPath = "images/not-found-to-delete.png";
            doThrow(new StorageFileNotFoundException("File not found for deletion")).when(fileStorageService).delete(blobPath);

            mockMvc.perform(delete("/api/files/" + blobPath))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success", is(false)))
                    .andExpect(jsonPath("$.message", is("File not found for deletion")));
        }
    }

    @Nested
    @DisplayName("Kiểm tra Quyền Xóa Tệp")
    class FileDeletionPermissionTests {
        @Test
        @WithMockUser // Không có vai trò/quyền mặc định
        @DisplayName("DELETE /api/files/** - Xóa Tệp - Bị cấm (403) đối với người dùng không có quyền")
        void deleteFile_forbiddenForUnprivilegedUser() throws Exception {
            String blobPath = "images/anyfile.png";
            // Không cần mock fileStorageService.delete vì không được gọi

            mockMvc.perform(delete("/api/files/" + blobPath))
                    .andExpect(status().isForbidden()); // Mong đợi 403 từ Spring Security
        }

        @Test
        @WithMockUser(authorities = "MANAGE_OWN_FILES")
        @DisplayName("DELETE /api/files/** - Xóa Tệp - Được phép đối với người dùng có quyền MANAGE_OWN_FILES")
        void deleteFile_allowedForUserWithAuthority() throws Exception {
            String blobPath = "images/userfile.png";
            doNothing().when(fileStorageService).delete(blobPath);

            mockMvc.perform(delete("/api/files/" + blobPath))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success", is(true)));
        }
    }
}