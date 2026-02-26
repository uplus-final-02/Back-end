package org.backend.userapi.tag.service;

import common.entity.Tag;
import common.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {
    private final TagRepository tagRepository;

    public List<Tag> getTagsByPriorities(List<Long> priorities) {
        return tagRepository.findByIsActiveTrueAndPriorityIn(priorities);
    }
}