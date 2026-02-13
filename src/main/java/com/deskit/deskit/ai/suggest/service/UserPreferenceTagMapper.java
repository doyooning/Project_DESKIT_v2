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
      tags.add(TagCode.SPACE, "서재", "study");
      tags.add(TagCode.MOOD, "집중", "차분한", "focus", "calm");
    } else if (energy == 'E') {
      tags.add(TagCode.SPACE, "거실", "living");
      tags.add(TagCode.MOOD, "감성", "활기", "energetic", "sensible");
    }

    if (info == 'N') {
      tags.add(TagCode.TONE, "우드", "파스텔", "wood", "pastel");
      tags.add(TagCode.MOOD, "감성", "창의");
    } else if (info == 'S') {
      tags.add(TagCode.TONE, "모던", "미니멀", "modern", "minimal");
      tags.add(TagCode.MOOD, "깔끔", "실용");
    }

    if (decision == 'T') {
      tags.add(TagCode.MOOD, "집중", "깔끔", "focus");
      tags.add(TagCode.SITUATION, "공부", "업무", "study", "work");
    } else if (decision == 'F') {
      tags.add(TagCode.MOOD, "감성", "따뜻", "relaxed");
      tags.add(TagCode.SITUATION, "취미", "hobby");
    }

    if (lifestyle == 'J') {
      tags.add(TagCode.TONE, "미니멀", "minimal");
      tags.add(TagCode.SITUATION, "재택근무", "업무", "remote", "work");
    } else if (lifestyle == 'P') {
      tags.add(TagCode.SITUATION, "게임", "취미", "gaming", "hobby");
      tags.add(TagCode.SPACE, "침실", "bedroom");
    }
  }

  private void applyJobTraits(JobCategory jobCategory, UserPreferenceTags tags) {
    switch (jobCategory) {
      case CREATIVE_TYPE -> {
        tags.add(TagCode.TONE, "우드", "파스텔", "wood", "pastel");
        tags.add(TagCode.MOOD, "감성", "창의", "sensible", "creative");
        tags.add(TagCode.SITUATION, "취미", "게임", "hobby", "gaming");
        tags.add(TagCode.SPACE, "거실", "서재", "living", "study");
      }
      case FLEXIBLE_TYPE -> {
        tags.add(TagCode.TONE, "우드", "미니멀", "wood", "minimal");
        tags.add(TagCode.MOOD, "따뜻", "차분", "relaxed", "calm");
        tags.add(TagCode.SITUATION, "재택근무", "업무", "remote", "work");
        tags.add(TagCode.SPACE, "침실", "서재", "bedroom", "study");
      }
      case EDU_RES_TYPE -> {
        tags.add(TagCode.TONE, "미니멀", "모던", "minimal", "modern");
        tags.add(TagCode.MOOD, "집중", "차분", "focus", "calm");
        tags.add(TagCode.SITUATION, "공부", "연구", "study", "research");
        tags.add(TagCode.SPACE, "서재", "study");
      }
      case MED_PRO_TYPE -> {
        tags.add(TagCode.TONE, "미니멀", "모던", "minimal", "modern", "화이트", "white");
        tags.add(TagCode.MOOD, "깔끔", "차분", "clean", "calm");
        tags.add(TagCode.SITUATION, "업무", "공부", "work", "study");
        tags.add(TagCode.SPACE, "서재", "오피스", "study", "office");
      }
      case ADMIN_PLAN_TYPE -> {
        tags.add(TagCode.TONE, "모던", "미니멀", "modern", "minimal");
        tags.add(TagCode.MOOD, "집중", "깔끔", "focus", "clean");
        tags.add(TagCode.SITUATION, "업무", "재택근무", "work", "remote");
        tags.add(TagCode.SPACE, "서재", "오피스", "study", "office");
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
          bucket.add(normalize(value));
        }
      }
    }

    public boolean matches(TagCode code, String actualTagName) {
      if (code == null || actualTagName == null || actualTagName.isBlank()) {
        return false;
      }
      Set<String> bucket = byCode.get(code);
      if (bucket == null || bucket.isEmpty()) {
        return false;
      }

      String normalizedActual = normalize(actualTagName);
      for (String keyword : bucket) {
        if (normalizedActual.contains(keyword)) {
          return true;
        }
      }
      return false;
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

    private static String normalize(String value) {
      return value.trim().toLowerCase().replace(" ", "");
    }
  }
}
