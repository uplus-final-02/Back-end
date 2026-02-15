package org.backend.video.dto;

import lombok.Data;

@Data
public class VideoResponseDto {
  private Integer code;
  private String message;
  private VideoDto data;
}