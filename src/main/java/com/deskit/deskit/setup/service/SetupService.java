package com.deskit.deskit.setup.service;

import com.deskit.deskit.livehost.service.AwsS3Service;
import com.deskit.deskit.setup.dto.SetupResponse;
import com.deskit.deskit.setup.dto.SetupResponse.SetupTags;
import com.deskit.deskit.setup.entity.Setup;
import com.deskit.deskit.setup.repository.SetupRepository;
import com.deskit.deskit.setup.repository.SetupTagRepository;
import com.deskit.deskit.setup.repository.SetupTagRepository.SetupTagRow;
import com.deskit.deskit.tag.entity.TagCategory.TagCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service // Setup 관련 조회/조합 로직을 담당하는 스프링 서비스 빈
public class SetupService {

  private final SetupRepository setupRepository; // Setup 조회용 JPA Repository
  private final SetupTagRepository setupTagRepository; // Setup-Tag 매핑 조회용 JPA Repository
  private final AwsS3Service awsS3Service;

  // 생성자 주입: final 필드 + 테스트 용이
  public SetupService(SetupRepository setupRepository,
                      SetupTagRepository setupTagRepository,
                      AwsS3Service awsS3Service) {
    this.setupRepository = setupRepository;
    this.setupTagRepository = setupTagRepository;
    this.awsS3Service = awsS3Service;
  }

  // 셋업 목록 조회:
  // - deleted_at IS NULL인 셋업만 조회
  // - 셋업ID 리스트로 태그를 한 번에 batch 조회해서 N+1 방지
  // - 프론트가 원하는 tags(카테고리별) + tagsFlat(합친 리스트)로 조립
  public List<SetupResponse> getSetups() {
    List<Setup> setups = setupRepository.findAllByDeletedAtIsNullOrderByIdAsc();
    if (setups.isEmpty()) {
      return Collections.emptyList();
    }

    List<Long> setupIds = setups.stream()
            .map(Setup::getId)
            .collect(Collectors.toList());

    // (setup_id, tagCode, tagName) 형태의 projection row들
    List<SetupTagRow> rows = setupTagRepository.findActiveTagsBySetupIds(setupIds);

    // setupId -> (tags, tagsFlat) 번들로 변환
    Map<Long, TagsBundle> tagsBySetupId = buildTagsBySetupId(rows);

    // 엔티티 + 태그 번들 => DTO 응답 조립
    return setups.stream()
            .map(setup -> {
              TagsBundle bundle = tagsBySetupId.get(setup.getId());
              SetupTags tags = bundle == null ? SetupTags.empty() : bundle.getTags();
              List<String> tagsFlat = bundle == null ? Collections.emptyList() : bundle.getTagsFlat();
              String resolvedImageUrl = resolveSetupImageUrl(setup.getSetupImageUrl());
              return SetupResponse.from(setup, tags, tagsFlat, resolvedImageUrl);
            })
            .collect(Collectors.toList());
  }

  // 셋업 단건 조회:
  // - deleted_at IS NULL 조건 포함
  // - 없으면 Optional.empty()
  public Optional<SetupResponse> getSetup(Long id) {
    Optional<Setup> setup = setupRepository.findByIdAndDeletedAtIsNull(id);
    if (setup.isEmpty()) {
      return Optional.empty();
    }

    // 단건이지만 동일 로직 재사용: IN(List.of(id))로 태그 조회
    List<SetupTagRow> rows = setupTagRepository.findActiveTagsBySetupIds(List.of(id));
    Map<Long, TagsBundle> tagsBySetupId = buildTagsBySetupId(rows);

    TagsBundle bundle = tagsBySetupId.get(id);
    SetupTags tags = bundle == null ? SetupTags.empty() : bundle.getTags();
    List<String> tagsFlat = bundle == null ? Collections.emptyList() : bundle.getTagsFlat();

    List<Long> productIdsRaw = setupRepository.findProductIdsBySetupId(id);
    List<Long> productIds = productIdsRaw == null
            ? Collections.emptyList()
            : new ArrayList<>(new LinkedHashSet<>(productIdsRaw));
    String resolvedImageUrl = resolveSetupImageUrl(setup.get().getSetupImageUrl());
    return Optional.of(SetupResponse.from(setup.get(), tags, tagsFlat, productIds, resolvedImageUrl));
  }

  private String resolveSetupImageUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }
    String value = raw.trim();
    if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/")) {
      return value;
    }
    return awsS3Service.buildPublicUrl(value);
  }

  // DB에서 가져온 태그 row들을 setupId별로 그룹핑하고 tags/tagsFlat을 만든다
  // - TagCode별 리스트(space/tone/situation/mood)
  // - 중복 제거 + 입력 순서 유지(LinkedHashSet)
  static Map<Long, TagsBundle> buildTagsBySetupId(List<SetupTagRow> rows) {
    Map<Long, TagAccumulator> accumulators = new java.util.HashMap<>();

    for (SetupTagRow row : rows) {
      // projection row 방어 처리
      if (row == null || row.getSetupId() == null || row.getTagCode() == null) {
        continue;
      }

      // setupId별 누적기 생성/재사용
      TagAccumulator acc = accumulators.computeIfAbsent(row.getSetupId(),
              ignored -> new TagAccumulator());

      acc.add(row.getTagCode(), row.getTagName());
    }

    // 누적기 -> 최종 번들로 변환
    return accumulators.entrySet().stream()
            .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().toBundle()
            ));
  }

  // Setup 1건에 대한 태그 결과 묶음
  // - tags: 카테고리별 리스트
  // - tagsFlat: UI용 단일 리스트(카테고리 순서대로 합침)
  static class TagsBundle {
    private final SetupTags tags;
    private final List<String> tagsFlat;

    TagsBundle(SetupTags tags, List<String> tagsFlat) {
      this.tags = tags;
      this.tagsFlat = tagsFlat;
    }

    SetupTags getTags() {
      return tags;
    }

    List<String> getTagsFlat() {
      return tagsFlat;
    }
  }

  // TagCode별 태그명을 누적하는 내부 헬퍼
  // - EnumMap: enum 키에 최적화
  // - LinkedHashSet: 중복 제거 + 순서 유지
  private static class TagAccumulator {
    private final Map<TagCode, LinkedHashSet<String>> byCode = new EnumMap<>(TagCode.class);

    void add(TagCode code, String tagName) {
      if (tagName == null || tagName.isBlank()) {
        return;
      }
      byCode.computeIfAbsent(code, ignored -> new LinkedHashSet<>()).add(tagName);
    }

    // 누적 데이터를 SetupTags + tagsFlat로 변환
    TagsBundle toBundle() {
      // 프론트 기대 순서 유지
      List<String> space = listFor(TagCode.SPACE);
      List<String> tone = listFor(TagCode.TONE);
      List<String> situation = listFor(TagCode.SITUATION);
      List<String> mood = listFor(TagCode.MOOD);

      SetupTags tags = new SetupTags(space, tone, situation, mood);

      // flat은 카테고리 순서대로 합치되 중복 제거
      LinkedHashSet<String> flat = new LinkedHashSet<>();
      addAll(flat, space);
      addAll(flat, tone);
      addAll(flat, situation);
      addAll(flat, mood);

      return new TagsBundle(tags, new ArrayList<>(flat));
    }

    private List<String> listFor(TagCode code) {
      LinkedHashSet<String> values = byCode.get(code);
      if (values == null || values.isEmpty()) {
        return Collections.emptyList();
      }
      return new ArrayList<>(values);
    }

    private void addAll(LinkedHashSet<String> target, List<String> source) {
      if (source == null || source.isEmpty()) {
        return;
      }
      for (String value : source) {
        if (value != null && !value.isBlank()) {
          target.add(value);
        }
      }
    }
  }
}
