package com.deskit.deskit.home.controller;

import com.deskit.deskit.home.dto.HomePopularSetupResponse;
import com.deskit.deskit.home.service.HomePopularSetupService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomePopularSetupController {

  private static final int DEFAULT_LIMIT = 6;
  private static final int MAX_LIMIT = 50;

  private final HomePopularSetupService homePopularSetupService;

  public HomePopularSetupController(HomePopularSetupService homePopularSetupService) {
    this.homePopularSetupService = homePopularSetupService;
  }

  @GetMapping("/popular-setups")
  public List<HomePopularSetupResponse> getPopularSetups(
      @RequestParam(value = "limit", required = false) String limit) {
    int resolvedLimit = resolveLimit(limit);
    return homePopularSetupService.getPopularSetups(resolvedLimit);
  }

  private int resolveLimit(String limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }

    String trimmed = limit.trim();
    if (trimmed.isEmpty()) {
      return DEFAULT_LIMIT;
    }

    try {
      int parsed = Integer.parseInt(trimmed);
      if (parsed <= 0) {
        return DEFAULT_LIMIT;
      }
      return Math.min(parsed, MAX_LIMIT);
    } catch (NumberFormatException ex) {
      return DEFAULT_LIMIT;
    }
  }
}
