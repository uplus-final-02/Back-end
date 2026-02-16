package org.backend.userapi.video.dto;

import lombok.Data;

@Data
public class VideoResponseDto {
  private Integer code;
  private String message;
  private VideoPlayDto data;
}