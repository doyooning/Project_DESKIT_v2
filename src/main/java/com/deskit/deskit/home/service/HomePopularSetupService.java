package com.deskit.deskit.home.service;

import com.deskit.deskit.home.dto.HomePopularSetupResponse;
import com.deskit.deskit.setup.repository.SetupRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HomePopularSetupService {

  private final SetupRepository setupRepository;

  public HomePopularSetupService(SetupRepository setupRepository) {
    this.setupRepository = setupRepository;
  }

  public List<HomePopularSetupResponse> getPopularSetups(int limit) {
    return setupRepository.findPopularSetups(limit).stream()
        .map(HomePopularSetupResponse::from)
        .toList();
  }
}
