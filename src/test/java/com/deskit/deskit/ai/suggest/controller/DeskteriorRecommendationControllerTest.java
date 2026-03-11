package com.deskit.deskit.ai.suggest.controller;

import com.deskit.deskit.account.dto.UserDTO;
import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.ai.suggest.service.DeskteriorRecommendationService;
import com.deskit.deskit.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeskteriorRecommendationControllerTest {

    private final DeskteriorRecommendationService recommendationService = mock(DeskteriorRecommendationService.class);
    private final DeskteriorRecommendationController controller =
            new DeskteriorRecommendationController(recommendationService);

    @Test
    void recommendThrowsUnauthorizedWhenAuthenticationMissing() {
        assertThatThrownBy(() -> controller.recommend(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void recommendThrowsUnauthorizedWhenPrincipalInvalid() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("principal", "n/a");

        assertThatThrownBy(() -> controller.recommend(authentication))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void recommendReturnsRecommendationForCustomOAuthUser() {
        CustomOAuth2User user = new CustomOAuth2User(UserDTO.builder()
                .username("member1")
                .role("ROLE_MEMBER")
                .build());
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, "n/a", "ROLE_MEMBER");
        List<ProductResponse> expected = List.of(product(1L), product(2L));
        when(recommendationService.recommendForLoginId("member1")).thenReturn(expected);

        List<ProductResponse> actual = controller.recommend(authentication);

        assertThat(actual).isEqualTo(expected);
        verify(recommendationService).recommendForLoginId("member1");
    }

    private ProductResponse product(Long id) {
        return new ProductResponse(id, 1L, "p" + id, null, null, 1000, 500, null, 1, 0, null, null);
    }
}
