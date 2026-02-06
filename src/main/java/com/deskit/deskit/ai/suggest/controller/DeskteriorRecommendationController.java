package com.deskit.deskit.ai.suggest.controller;

import com.deskit.deskit.account.oauth.CustomOAuth2User;
import com.deskit.deskit.ai.suggest.service.DeskteriorRecommendationService;
import com.deskit.deskit.product.dto.ProductResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class DeskteriorRecommendationController {

  private final DeskteriorRecommendationService recommendationService;

  @GetMapping("/deskterior")
  public List<ProductResponse> recommend(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof CustomOAuth2User user)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return recommendationService.recommendForLoginId(user.getUsername());
  }
}
