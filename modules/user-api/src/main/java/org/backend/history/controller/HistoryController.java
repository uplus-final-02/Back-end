package org.backend.history.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.backend.history.dto.SavePointRequest;
import org.backend.history.dto.SavePointResponse;
import org.backend.history.service.HistoryService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/histories")
public class HistoryController implements HistoryApi {

  private final HistoryService historyService;

  @Override
  @PostMapping("/savepoint/{videoId}") // URL 매핑은 구현체에 두는 것이 관례 (Spring MVC 인식용)
  public SavePointResponse savePoint(
      @AuthenticationPrincipal Long userId, // 실제 주입은 여기서
      @PathVariable Long videoId,           // 바인딩 어노테이션도 여기서
      @RequestBody @Valid SavePointRequest request) {

    return historyService.savePoint(userId, videoId, request);
  }
}