package com.yourcompany.agritradels.interaction.controller;

import com.yourcompany.agritradels.common.dto.ApiResponse;
import com.yourcompany.agritradels.interaction.dto.response.ChatMessageResponse;
import com.yourcompany.agritradels.interaction.dto.response.ChatRoomResponse;
import com.yourcompany.agritradels.interaction.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort; // Import Sort
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập cho mọi API chat
public class ChatController {

    private final ChatService chatService;

    // Lấy danh sách các phòng chat của user hiện tại
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> getMyChatRooms(Authentication authentication) {
        List<ChatRoomResponse> rooms = chatService.getMyChatRooms(authentication);
        return ResponseEntity.ok(ApiResponse.success(rooms));
    }

    // Lấy hoặc tạo phòng chat với người dùng khác
    @PostMapping("/rooms/user/{recipientId}")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> getOrCreateChatRoom(
            Authentication authentication,
            @PathVariable Long recipientId) {
        ChatRoomResponse room = chatService.getOrCreateChatRoom(authentication, recipientId);
        // Trả về 200 OK vì có thể là lấy hoặc tạo
        return ResponseEntity.ok(ApiResponse.success(room));
    }

    // Lấy lịch sử tin nhắn của một phòng chat (phân trang)
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getChatMessages(
            Authentication authentication,
            @PathVariable Long roomId,
            @PageableDefault(size = 30, sort = "sentAt", direction = Sort.Direction.DESC) Pageable pageable) { // Sắp xếp theo thời gian giảm dần
        Page<ChatMessageResponse> messages = chatService.getChatMessages(authentication, roomId, pageable);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    // Đánh dấu tin nhắn trong phòng là đã đọc
    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Void>> markMessagesAsRead(
            Authentication authentication,
            @PathVariable Long roomId) {
        chatService.markMessagesAsRead(authentication, roomId);
        return ResponseEntity.ok(ApiResponse.success("Messages marked as read"));
    }

    // Lấy tổng số tin nhắn chưa đọc
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Integer>> getTotalUnreadMessages(Authentication authentication) {
        int count = chatService.getTotalUnreadMessages(authentication);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    // API gửi tin nhắn (tùy chọn, thường gửi qua WebSocket tốt hơn)
    // Nếu dùng API này, nó sẽ gọi ChatService.sendMessage và service sẽ tự push qua WebSocket
    /*
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendMessageViaApi(
            Authentication authentication,
            @Valid @RequestBody ChatMessageRequest request) {
        ChatMessageResponse sentMessage = chatService.sendMessage(authentication, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(sentMessage, "Message sent"));
    }
    */
}