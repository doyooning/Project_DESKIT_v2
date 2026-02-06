package com.deskit.deskit.ai.suggest.service;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.enums.MBTI;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.ai.suggest.service.UserPreferenceTagMapper.UserPreferenceTags;
import com.deskit.deskit.product.dto.ProductResponse;
import com.deskit.deskit.product.repository.ProductTagRepository;
import com.deskit.deskit.product.repository.ProductTagRepository.ProductTagRow;
import com.deskit.deskit.product.service.ProductService;
import com.deskit.deskit.tag.entity.TagCategory.TagCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DeskteriorRecommendationService {

  private static final int DEFAULT_LIMIT = 4;

  private final MemberRepository memberRepository;
  private final ProductTagRepository productTagRepository;
  private final ProductService productService;
  private final UserPreferenceTagMapper preferenceTagMapper;

  public DeskteriorRecommendationService(MemberRepository memberRepository,
                                         ProductTagRepository productTagRepository,
                                         ProductService productService,
                                         UserPreferenceTagMapper preferenceTagMapper) {
    this.memberRepository = memberRepository;
    this.productTagRepository = productTagRepository;
    this.productService = productService;
    this.preferenceTagMapper = preferenceTagMapper;
  }

  public List<ProductResponse> recommendForLoginId(String loginId) {
    return recommendForLoginId(loginId, DEFAULT_LIMIT);
  }

  public List<ProductResponse> recommendForLoginId(String loginId, int limit) {
    if (loginId == null || loginId.isBlank()) {
      return Collections.emptyList();
    }

    Member member = memberRepository.findByLoginId(loginId);
    if (member == null) {
      return Collections.emptyList();
    }

    MBTI mbti = member.getMbti();
    JobCategory jobCategory = member.getJobCategory();
    if (mbti == null || mbti == MBTI.NONE || jobCategory == null || jobCategory == JobCategory.NONE) {
      return Collections.emptyList();
    }

    UserPreferenceTags preferenceTags = preferenceTagMapper.map(mbti, jobCategory);
    if (preferenceTags.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> tagNames = preferenceTags.allTagNames();
    if (tagNames.isEmpty()) {
      return Collections.emptyList();
    }

    List<ProductTagRow> rows = productTagRepository.findActiveTagsByTagNames(tagNames);
    if (rows.isEmpty()) {
      return Collections.emptyList();
    }

    Map<Long, Integer> scores = new HashMap<>();
    for (ProductTagRow row : rows) {
      if (row == null || row.getProductId() == null || row.getTagCode() == null) {
        continue;
      }
      if (!preferenceTags.contains(row.getTagCode(), row.getTagName())) {
        continue;
      }
      int weight = weightFor(row.getTagCode());
      scores.merge(row.getProductId(), weight, Integer::sum);
    }

    if (scores.isEmpty()) {
      return Collections.emptyList();
    }

    List<Long> rankedIds = scores.entrySet().stream()
        .sorted(Comparator.<Map.Entry<Long, Integer>>comparingInt(Map.Entry::getValue).reversed()
            .thenComparing(Map.Entry::getKey))
        .limit(limit)
        .map(Map.Entry::getKey)
        .toList();

    if (rankedIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<ProductResponse> responses = productService.getProductsByIds(rankedIds);
    Map<Long, ProductResponse> byId = new HashMap<>();
    for (ProductResponse response : responses) {
      if (response == null) {
        continue;
      }
      byId.put(response.getProductId(), response);
    }

    List<ProductResponse> ordered = new ArrayList<>();
    for (Long id : rankedIds) {
      ProductResponse response = byId.get(id);
      if (response != null) {
        ordered.add(response);
      }
    }
    return ordered;
  }

  private int weightFor(TagCode code) {
    if (code == null) {
      return 0;
    }
    return switch (code) {
      case MOOD -> 3;
      case SITUATION -> 2;
      case SPACE, TONE -> 1;
    };
  }
}
