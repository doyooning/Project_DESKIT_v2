package com.deskit.deskit.ai.suggest.service;

import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.enums.MBTI;
import com.deskit.deskit.tag.entity.TagCategory.TagCode;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class UserPreferenceTagMapper {

  public UserPreferenceTags map(MBTI mbti, JobCategory jobCategory) {
    UserPreferenceTags tags = new UserPreferenceTags();

    if (mbti != null && mbti != MBTI.NONE) {
      applyMbtiTraits(mbti.name(), tags);
    }

    if (jobCategory != null && jobCategory != JobCategory.NONE) {
      applyJobTraits(jobCategory, tags);
    }

    return tags;
  }

  private void applyMbtiTraits(String mbti, UserPreferenceTags tags) {
    if (mbti == null || mbti.length() != 4) {
      return;
    }

    char energy = mbti.charAt(0);
    char info = mbti.charAt(1);
    char decision = mbti.charAt(2);
    char lifestyle = mbti.charAt(3);

    if (energy == 'I') {
      tags.add(TagCode.MOOD, "차분한", "집중");
      tags.add(TagCode.SPACE, "서재");
    } else if (energy == 'E') {
      tags.add(TagCode.MOOD, "활기", "컬러풀");
      tags.add(TagCode.SITUATION, "취미");
    }

    if (info == 'N') {
      tags.add(TagCode.MOOD, "감성", "창의");
      tags.add(TagCode.TONE, "파스텔");
    } else if (info == 'S') {
      tags.add(TagCode.MOOD, "실용", "깔끔한");
      tags.add(TagCode.TONE, "모던");
    }

    if (decision == 'T') {
      tags.add(TagCode.MOOD, "집중");
      tags.add(TagCode.TONE, "메탈");
    } else if (decision == 'F') {
      tags.add(TagCode.MOOD, "따뜻한");
      tags.add(TagCode.TONE, "우드");
    }

    if (lifestyle == 'J') {
      tags.add(TagCode.MOOD, "깔끔한");
      tags.add(TagCode.TONE, "미니멀");
    } else if (lifestyle == 'P') {
      tags.add(TagCode.MOOD, "자유로운");
      tags.add(TagCode.TONE, "컬러풀");
    }
  }

  private void applyJobTraits(JobCategory jobCategory, UserPreferenceTags tags) {
    switch (jobCategory) {
      case CREATIVE_TYPE -> {
        tags.add(TagCode.MOOD, "감성", "컬러풀");
        tags.add(TagCode.TONE, "우드", "파스텔");
        tags.add(TagCode.SITUATION, "취미", "영상편집");
        tags.add(TagCode.SPACE, "서재");
      }
      case FLEXIBLE_TYPE -> {
        tags.add(TagCode.MOOD, "따뜻한");
        tags.add(TagCode.TONE, "우드");
        tags.add(TagCode.SITUATION, "재택근무");
        tags.add(TagCode.SPACE, "홈카페");
      }
      case EDU_RES_TYPE -> {
        tags.add(TagCode.MOOD, "집중", "차분한");
        tags.add(TagCode.TONE, "미니멀");
        tags.add(TagCode.SITUATION, "공부", "연구");
        tags.add(TagCode.SPACE, "서재");
      }
      case MED_PRO_TYPE -> {
        tags.add(TagCode.MOOD, "깔끔한", "차분한");
        tags.add(TagCode.TONE, "화이트");
        tags.add(TagCode.SITUATION, "업무");
        tags.add(TagCode.SPACE, "오피스");
      }
      case ADMIN_PLAN_TYPE -> {
        tags.add(TagCode.MOOD, "깔끔한", "집중");
        tags.add(TagCode.TONE, "모던");
        tags.add(TagCode.SITUATION, "오피스", "재택근무");
        tags.add(TagCode.SPACE, "오피스");
      }
      default -> {
        // NO-OP
      }
    }
  }

  public static class UserPreferenceTags {
    private final Map<TagCode, LinkedHashSet<String>> byCode = new EnumMap<>(TagCode.class);

    public void add(TagCode code, String... values) {
      if (code == null || values == null || values.length == 0) {
        return;
      }
      LinkedHashSet<String> bucket = byCode.computeIfAbsent(code, ignored -> new LinkedHashSet<>());
      for (String value : values) {
        if (value != null && !value.isBlank()) {
          bucket.add(value.trim());
        }
      }
    }

    public boolean contains(TagCode code, String value) {
      if (code == null || value == null || value.isBlank()) {
        return false;
      }
      Set<String> bucket = byCode.get(code);
      return bucket != null && bucket.contains(value);
    }

    public List<String> allTagNames() {
      LinkedHashSet<String> all = new LinkedHashSet<>();
      for (LinkedHashSet<String> values : byCode.values()) {
        all.addAll(values);
      }
      return List.copyOf(all);
    }

    public boolean isEmpty() {
      return byCode.values().stream().allMatch(Set::isEmpty);
    }
  }
}
