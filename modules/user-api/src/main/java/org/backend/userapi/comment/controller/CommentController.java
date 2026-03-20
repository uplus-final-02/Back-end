package org.backend.userapi.comment.controller;

import core.security.principal.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.comment.dto.CommentCreateRequestDto;
import org.backend.userapi.comment.dto.CommentResponseDto;
import org.backend.userapi.comment.dto.CommentUpdateRequestDto;
import org.backend.userapi.comment.service.CommentService;
import org.backend.userapi.common.dto.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "댓글 API", description = "에피소드 댓글 조회, 작성, 수정, 삭제")
@RestController
@RequestMapping("/api/videos/{videoId}/comments")
@RequiredArgsConstructor
public class CommentController {

  private final CommentService commentService;

  @Operation(summary = "댓글 목록 조회")
  @GetMapping
  public ApiResponse<Page<CommentResponseDto>> getComments(
      @PathVariable("videoId") Long videoId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size
  ) {
    PageRequest pageRequest = PageRequest.of(page, size);
    Page<CommentResponseDto> response = commentService.getComments(videoId, pageRequest);

    return ApiResponse.success(response);
  }

  @Operation(summary = "댓글 작성")
  @PostMapping
  public ApiResponse<String> createComment(
      @PathVariable("videoId") Long videoId,
      @RequestBody CommentCreateRequestDto requestDto,
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    Long userId = jwtPrincipal.getUserId();
    commentService.createComment(videoId, userId, requestDto);
    return ApiResponse.success("댓글이 등록되었습니다.");
  }

  @Operation(summary = "댓글 수정")
  @PatchMapping("/{commentId}")
  public ApiResponse<CommentResponseDto> updateComment(
      @PathVariable("videoId") Long videoId,
      @PathVariable("commentId") Long commentId,
      @RequestBody CommentUpdateRequestDto requestDto,
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    Long userId = jwtPrincipal.getUserId();

    CommentResponseDto response = commentService.updateComment(commentId, userId, requestDto);
    return ApiResponse.success(response);
  }

  @Operation(summary = "댓글 삭제")
  @DeleteMapping("/{commentId}")
  public ApiResponse<String> deleteComment(
      @PathVariable("videoId") Long videoId,
      @PathVariable("commentId") Long commentId,
      @AuthenticationPrincipal JwtPrincipal jwtPrincipal
  ) {
    Long userId = jwtPrincipal.getUserId();

    commentService.deleteComment(commentId, userId);

    return ApiResponse.success("댓글이 정상적으로 삭제되었습니다.");
  }

}