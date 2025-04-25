package com.yourcompany.agritrade.interaction.dto.event;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageReadEvent {
    private Long roomId;
    private Long readerId; // ID của người đã đọc
}