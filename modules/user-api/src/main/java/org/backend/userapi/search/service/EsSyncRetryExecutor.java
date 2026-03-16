package org.backend.userapi.search.service;

import org.backend.userapi.search.entity.EsSyncFailure;
import org.backend.userapi.search.repository.EsSyncFailureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EsSyncRetryExecutor {

    private final EsSyncFailureRepository esSyncFailureRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retrySingle(EsSyncFailure failure,
                               ContentIndexingService indexingService) {
        // REQUIRES_NEW로 새 트랜잭션 열면 기존 영속성 컨텍스트에서 detached
        // → findById로 다시 조회해서 managed 상태로 만들어야 dirty checking 동작
        EsSyncFailure managed = esSyncFailureRepository.findById(failure.getId())
                .orElse(null);
        if (managed == null) return false;

        try {
            indexingService.indexContent(managed.getContentId());
            managed.markResolved();
            return true;
        } catch (Exception e) {
            managed.incrementRetry(e.getMessage());
            return false;
        }
    }
}