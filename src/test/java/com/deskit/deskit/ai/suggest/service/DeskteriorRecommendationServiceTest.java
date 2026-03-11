package com.deskit.deskit.ai.suggest.service;

import com.deskit.deskit.account.entity.Member;
import com.deskit.deskit.account.enums.JobCategory;
import com.deskit.deskit.account.enums.MBTI;
import com.deskit.deskit.account.repository.MemberRepository;
import com.deskit.deskit.ai.suggest.service.UserPreferenceTagMapper.UserPreferenceTags;
import com.deskit.deskit.product.dto.ProductResponse;
import com.deskit.deskit.product.entity.Product;
import com.deskit.deskit.product.repository.ProductTagRepository;
import com.deskit.deskit.product.repository.ProductTagRepository.ProductTagRow;
import com.deskit.deskit.product.service.ProductService;
import com.deskit.deskit.tag.entity.TagCategory.TagCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeskteriorRecommendationServiceTest {

    private MemberRepository memberRepository;
    private ProductTagRepository productTagRepository;
    private ProductService productService;
    private UserPreferenceTagMapper preferenceTagMapper;
    private DeskteriorRecommendationService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        productTagRepository = mock(ProductTagRepository.class);
        productService = mock(ProductService.class);
        preferenceTagMapper = mock(UserPreferenceTagMapper.class);
        service = new DeskteriorRecommendationService(memberRepository, productTagRepository, productService, preferenceTagMapper);
    }

    @Test
    void recommendReturnsEmptyWhenLoginIdInvalidOrMemberMissing() {
        assertThat(service.recommendForLoginId(null, 4)).isEmpty();
        assertThat(service.recommendForLoginId(" ", 4)).isEmpty();

        when(memberRepository.findByLoginId("user")).thenReturn(null);
        assertThat(service.recommendForLoginId("user", 4)).isEmpty();
    }

    @Test
    void recommendReturnsEmptyWhenProfileIncomplete() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.NONE, JobCategory.CREATIVE_TYPE));
        assertThat(service.recommendForLoginId("user", 4)).isEmpty();

        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.NONE));
        assertThat(service.recommendForLoginId("user", 4)).isEmpty();

        when(memberRepository.findByLoginId("user")).thenReturn(member(null, JobCategory.CREATIVE_TYPE));
        assertThat(service.recommendForLoginId("user", 4)).isEmpty();

        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, null));
        assertThat(service.recommendForLoginId("user", 4)).isEmpty();
    }

    @Test
    void recommendReturnsEmptyWhenPreferenceTagsEmpty() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(new UserPreferenceTags());

        assertThat(service.recommendForLoginId("user", 4)).isEmpty();
    }

    @Test
    void recommendFallsBackWhenPreferenceTagNamesEmpty() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        UserPreferenceTags tags = mock(UserPreferenceTags.class);
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(tags);
        when(tags.isEmpty()).thenReturn(false);
        when(tags.allTagNames()).thenReturn(Collections.emptyList());
        when(productService.getProducts()).thenReturn(List.of(product(2L), product(1L)));

        List<ProductResponse> result = service.recommendForLoginId("user", 2);

        assertThat(result).hasSize(2);
    }

    @Test
    void recommendReturnsEmptyWhenNoCandidates() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        UserPreferenceTags tags = mock(UserPreferenceTags.class);
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(tags);
        when(tags.isEmpty()).thenReturn(false);
        when(tags.allTagNames()).thenReturn(List.of("focus"));
        when(productService.getProducts()).thenReturn(List.of());

        assertThat(service.recommendForLoginId("user", 4)).isEmpty();
    }

    @Test
    void recommendFallsBackWhenNoTagRowsOrNoScores() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        UserPreferenceTags tags = mock(UserPreferenceTags.class);
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(tags);
        when(tags.isEmpty()).thenReturn(false);
        when(tags.allTagNames()).thenReturn(List.of("focus"));
        when(productService.getProducts()).thenReturn(List.of(product(1L), product(2L)));
        when(productTagRepository.findActiveTagsByProductIds(List.of(1L, 2L))).thenReturn(List.of());

        assertThat(service.recommendForLoginId("user", 2)).hasSize(2);

        when(productTagRepository.findActiveTagsByProductIds(List.of(1L, 2L)))
                .thenReturn(java.util.Arrays.asList(null, row(1L, null, "x"), row(null, TagCode.MOOD, "focus"), row(2L, TagCode.MOOD, "focus")));
        when(tags.matches(TagCode.MOOD, "focus")).thenReturn(false);

        assertThat(service.recommendForLoginId("user", 2)).hasSize(2);
    }

    @Test
    void recommendReturnsRankedProductsWhenScoresExist() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        UserPreferenceTags tags = mock(UserPreferenceTags.class);
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(tags);
        when(tags.isEmpty()).thenReturn(false);
        when(tags.allTagNames()).thenReturn(List.of("focus"));
        when(productService.getProducts()).thenReturn(List.of(product(1L), product(2L), product(3L)));
        when(productTagRepository.findActiveTagsByProductIds(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        row(1L, TagCode.MOOD, "focus"),
                        row(2L, TagCode.SITUATION, "focus"),
                        row(3L, TagCode.TONE, "focus"),
                        row(3L, TagCode.SPACE, "focus")
                ));
        when(tags.matches(TagCode.MOOD, "focus")).thenReturn(true);
        when(tags.matches(TagCode.SITUATION, "focus")).thenReturn(true);
        when(tags.matches(TagCode.TONE, "focus")).thenReturn(true);
        when(tags.matches(TagCode.SPACE, "focus")).thenReturn(true);
        when(productService.getProductsByIds(org.mockito.ArgumentMatchers.argThat(ids ->
                ids != null && ids.containsAll(List.of(1L, 2L, 3L)) && ids.size() == 3
        ))).thenReturn(List.of(product(1L), product(2L), product(3L)));

        List<ProductResponse> result = service.recommendForLoginId("user", 3);

        assertThat(result).extracting(ProductResponse::getProductId).containsExactlyInAnyOrder(1L, 2L, 3L);
        verify(productService).getProductsByIds(org.mockito.ArgumentMatchers.argThat(ids ->
                ids.containsAll(List.of(1L, 2L, 3L)) && ids.size() == 3
        ));
    }

    @Test
    void recommendFallsBackWhenRankedIdsEmptyOrOrderedEmpty() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        UserPreferenceTags tags = mock(UserPreferenceTags.class);
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(tags);
        when(tags.isEmpty()).thenReturn(false);
        when(tags.allTagNames()).thenReturn(List.of("focus"));
        when(productService.getProducts()).thenReturn(List.of(product(1L), product(2L)));
        when(productTagRepository.findActiveTagsByProductIds(List.of(1L, 2L)))
                .thenReturn(List.of(row(1L, TagCode.MOOD, "focus")));
        when(tags.matches(TagCode.MOOD, "focus")).thenReturn(true);

        List<ProductResponse> rankedEmptyFallback = service.recommendForLoginId("user", 0);
        assertThat(rankedEmptyFallback).isNotEmpty();

        when(productService.getProductsByIds(List.of(1L))).thenReturn(List.of());
        List<ProductResponse> orderedEmptyFallback = service.recommendForLoginId("user", 1);
        assertThat(orderedEmptyFallback).isNotEmpty();
    }

    @Test
    void recommendUsesDefaultLimitOverload() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        UserPreferenceTags tags = mock(UserPreferenceTags.class);
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(tags);
        when(tags.isEmpty()).thenReturn(false);
        when(tags.allTagNames()).thenReturn(Collections.emptyList());
        when(productService.getProducts()).thenReturn(List.of(product(1L), product(2L), product(3L), product(4L), product(5L)));

        List<ProductResponse> result = service.recommendForLoginId("user");

        assertThat(result).hasSize(4);
    }

    @Test
    void recommendSkipsNullResponsesAndFallsBackWhenOrderedBecomesEmpty() {
        when(memberRepository.findByLoginId("user")).thenReturn(member(MBTI.INTJ, JobCategory.CREATIVE_TYPE));
        UserPreferenceTags tags = mock(UserPreferenceTags.class);
        when(preferenceTagMapper.map(MBTI.INTJ, JobCategory.CREATIVE_TYPE)).thenReturn(tags);
        when(tags.isEmpty()).thenReturn(false);
        when(tags.allTagNames()).thenReturn(List.of("focus"));
        when(productService.getProducts()).thenReturn(List.of(product(1L), product(2L)));
        when(productTagRepository.findActiveTagsByProductIds(List.of(1L, 2L)))
                .thenReturn(List.of(row(1L, TagCode.MOOD, "focus")));
        when(tags.matches(TagCode.MOOD, "focus")).thenReturn(true);
        when(productService.getProductsByIds(List.of(1L))).thenReturn(java.util.Arrays.asList(null, product(2L)));

        List<ProductResponse> result = service.recommendForLoginId("user", 1);

        assertThat(result).isNotEmpty();
    }

    @Test
    void weightForReturnsZeroForNullTagCode() throws Exception {
        Method method = DeskteriorRecommendationService.class.getDeclaredMethod("weightFor", TagCode.class);
        method.setAccessible(true);

        Object result = method.invoke(service, new Object[]{null});

        assertThat(result).isEqualTo(0);
    }

    @Test
    void fallbackProductsReturnsEmptyWhenSourceProductsEmpty() throws Exception {
        when(productService.getProducts()).thenReturn(List.of());
        Method method = DeskteriorRecommendationService.class.getDeclaredMethod("fallbackProducts", int.class, int.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ProductResponse> result = (List<ProductResponse>) method.invoke(service, 3, 123);

        assertThat(result).isEmpty();
    }

    @Test
    void tieBreakerReturnsMaxValueForNullProductId() throws Exception {
        Method method = DeskteriorRecommendationService.class.getDeclaredMethod("tieBreaker", Long.class, int.class);
        method.setAccessible(true);

        Object result = method.invoke(service, null, 99);

        assertThat(result).isEqualTo(Integer.MAX_VALUE);
    }

    private Member member(MBTI mbti, JobCategory jobCategory) {
        return Member.builder()
                .loginId("user")
                .mbti(mbti)
                .jobCategory(jobCategory)
                .build();
    }

    private ProductResponse product(Long id) {
        return new ProductResponse(id, 1L, "p" + id, null, null, 1000, 500, Product.Status.ON_SALE, 10, 1, null, null);
    }

    private ProductTagRow row(Long productId, TagCode code, String tagName) {
        return new ProductTagRow() {
            @Override
            public Long getProductId() {
                return productId;
            }

            @Override
            public Long getTagId() {
                return 1L;
            }

            @Override
            public TagCode getTagCode() {
                return code;
            }

            @Override
            public String getTagName() {
                return tagName;
            }
        };
    }
}
