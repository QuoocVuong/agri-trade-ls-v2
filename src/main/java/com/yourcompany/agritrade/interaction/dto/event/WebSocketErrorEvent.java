package com.yourcompany.agritrade.interaction.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketErrorEvent {
  private String error;
  private String message;

}
