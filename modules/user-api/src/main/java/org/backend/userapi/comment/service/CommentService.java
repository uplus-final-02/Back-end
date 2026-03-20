package org.backend.userapi.comment.service;

import common.enums.CommentStatus;
import interaction.entity.Comment;
import interaction.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.backend.userapi.comment.dto.CommentCreateRequestDto;
import org.backend.userapi.comment.dto.CommentResponseDto;
import org.backend.userapi.comment.dto.CommentUpdateRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import user.entity.User;
import user.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

  private final CommentRepository commentRepository;

  public Page<CommentResponseDto> getComments(Long videoId, Pageable pageable) {

    Page<Comment> commentsPage = commentRepository.findActiveCommentsByVideoId(
        videoId,
        CommentStatus.ACTIVE,
        pageable
    );

    return commentsPage.map(comment -> CommentResponseDto.builder()
        .commentId(comment.getId())
        .body(comment.getBody())
        .createdAt(comment.getCreatedAt())
        .userId(comment.getUser().getId())
        .nickname(comment.getUser().getNickname())
        .profileImageUrl(comment.getUser().getProfileImage())
        .build()
    );
  }
  private final UserRepository userRepository; // 생성자 주입

  @Transactional
  public void createComment(Long videoId, Long userId, CommentCreateRequestDto requestDto) {
    // 유저 확인
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

    Comment comment = Comment.builder()
        .videoId(videoId)
        .user(user)
        .body(requestDto.getBody())
        .build();

    commentRepository.save(comment);
  }

  @Transactional
  public CommentResponseDto updateComment(Long commentId, Long userId, CommentUpdateRequestDto requestDto) {

    // 댓글 조회
    Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));

    // 삭제 확인
    if (comment.getStatus() == CommentStatus.DELETED) {
      throw new IllegalArgumentException("삭제된 댓글은 수정할 수 없습니다.");
    }

    // 권한 체크
    if (!comment.getUser().getId().equals(userId)) {
      throw new IllegalArgumentException("댓글을 수정할 권한이 없습니다.");
    }

    // 내용 수정
    comment.updateBody(requestDto.getBody());

    // 수정 결과 반환
    return CommentResponseDto.builder()
        .commentId(comment.getId())
        .body(comment.getBody())
        .createdAt(comment.getCreatedAt())
        .userId(comment.getUser().getId())
        .nickname(comment.getUser().getNickname())
        .profileImageUrl(comment.getUser().getProfileImage())
        .build();
  }

  @Transactional
  public void deleteComment(Long commentId, Long userId) {

    // 댓글 조회
    Comment comment = commentRepository.findById(commentId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다."));

    // 삭제 확인
    if (comment.getStatus() == CommentStatus.DELETED) {
      throw new IllegalArgumentException("이미 삭제된 댓글입니다.");
    }

    // 권한 체크
    if (!comment.getUser().getId().equals(userId)) {
      throw new IllegalArgumentException("댓글을 삭제할 권한이 없습니다.");
    }

    comment.delete();
  }
}