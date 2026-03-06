package org.backend.admin.tag.service;

import common.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.backend.admin.tag.dto.TagResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {
    private final TagRepository tagRepository;

    public List<TagResponse> getTagsByPriorities(List<Long> priorities) {
        return tagRepository.findByIsActiveTrueAndPriorityIn(priorities).stream()
                .map(TagResponse::from)
                .toList();
    }
}