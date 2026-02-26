package org.backend.userapi.recommendation.service;

import common.entity.Tag;
import common.repository.TagRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 태그 기반 벡터 빌더.
 *
 * ┌──────────────────────────────────────────────────────────────┐
 * │  MAX_VECTOR_DIMS(100)차원 고정 패딩 벡터                       │
 * │  인덱스 i = tag_id 오름차순 정렬 시 i번째 태그 (0-based)        │
 * │  미사용 차원은 0.0f 패딩 → 코사인 유사도에 영향 없음             │
 * │  태그가 100개 미만이어도, 추가되어도 ES 재인덱싱 불필요           │
 * │                                                              │
 * │  priority 기반 가중치 (콘텐츠 벡터 전용)                       │
 * │    1 (메인 태그)    → 1.0f  e.g. 액션, 로맨스, SF             │
 * │    2 (관리자 태그)  → 0.7f  e.g. 메디컬, 법정, 역사            │
 * │    0 (유저 태그)    → 0.5f  e.g. 팝콘각, 킬링타임              │
 * │                                                              │
 * │  유저 쿼리 벡터: 선택한 태그 모두 1.0f (명시적 선호)             │
 * └──────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagVectorService {

    /** ES dense_vector dims 와 반드시 일치해야 함 (content-mapping.json). */
    public static final int MAX_VECTOR_DIMS = 100;

    private final TagRepository tagRepository;

    // tagId → 벡터 인덱스 (0-based)
    private Map<Long, Integer> tagIdToIndex = new HashMap<>();

    // tagId → priority 기반 가중치
    private Map<Long, Float> tagIdToWeight = new HashMap<>();

    private int totalTags = 0;

    @PostConstruct
    public void init() {
        List<Tag> tags = tagRepository.findAllByIsActiveTrueOrderByIdAsc();
        totalTags = tags.size();

        Map<Long, Integer> indexMap = new HashMap<>();
        Map<Long, Float> weightMap = new HashMap<>();

        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            indexMap.put(tag.getId(), i);
            weightMap.put(tag.getId(), priorityToWeight(tag.getPriority()));
        }

        this.tagIdToIndex = indexMap;
        this.tagIdToWeight = weightMap;

        log.info("[TagVectorService] 태그 벡터 초기화 완료 - 총 {}개 태그 로드 (벡터 차원: {})",
                 totalTags, MAX_VECTOR_DIMS);
    }

    /**
     * 콘텐츠 태그 벡터 빌드.
     * priority 기반 가중치 적용 (메인 > 관리자 > 유저).
     * 미사용 차원(태그 수 ~ MAX_VECTOR_DIMS)은 0.0f 패딩.
     *
     * @param tagIds 콘텐츠에 매핑된 태그 ID 목록
     * @return MAX_VECTOR_DIMS(100)차원 float 벡터
     */
    public float[] buildContentVector(List<Long> tagIds) {
        float[] vector = new float[MAX_VECTOR_DIMS];   // 0.0f 패딩
        for (Long tagId : tagIds) {
            Integer index = tagIdToIndex.get(tagId);
            if (index != null && index < MAX_VECTOR_DIMS) {
                vector[index] = tagIdToWeight.getOrDefault(tagId, 1.0f);
            }
        }
        return vector;
    }

    /**
     * 유저 선호 태그 쿼리 벡터 빌드.
     * 유저가 직접 선택한 태그이므로 모두 1.0f 동일 가중치 적용.
     * 미사용 차원(태그 수 ~ MAX_VECTOR_DIMS)은 0.0f 패딩.
     *
     * @param tagIds 유저 선호 태그 ID 목록
     * @return MAX_VECTOR_DIMS(100)차원 float 벡터
     */
    public float[] buildUserVector(List<Long> tagIds) {
        float[] vector = new float[MAX_VECTOR_DIMS];   // 0.0f 패딩
        for (Long tagId : tagIds) {
            Integer index = tagIdToIndex.get(tagId);
            if (index != null && index < MAX_VECTOR_DIMS) {
                vector[index] = 1.0f;
            }
        }
        return vector;
    }

    public int getTotalTags() {
        return totalTags;
    }

    // ── private ──────────────────────────────────────────────────

    private float priorityToWeight(Long priority) {
        if (priority == null) return 0.5f;
        return switch (priority.intValue()) {
            case 1 -> 1.0f;  // 메인 태그 (액션, 로맨스 등)
            case 2 -> 0.7f;  // 관리자 태그 (메디컬, 법정 등)
            case 0 -> 0.5f;  // 유저 생성 태그 (팝콘각, 킬링타임 등)
            default -> 0.5f;
        };
    }
}
