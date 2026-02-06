package com.deskit.deskit.setup.controller;

import com.deskit.deskit.setup.dto.SetupResponse;
import com.deskit.deskit.setup.service.SetupService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Setup(셋업) 조회 전용 REST API 컨트롤러
 * - /api/setups: 셋업 목록 조회
 * - /api/setups/{id}: 셋업 단건 조회
 *
 * 포인트:
 * - 컨트롤러는 HTTP 요청/응답만 담당하고, 조회/태그 집계 로직은 SetupService에 위임한다.
 * - 서비스에서 deletedAt is null(소프트 삭제 제외) 필터 + 태그를 배치 조회해서 N+1을 피한다.
 * - 보안(Spring Security) 설정에 따라 이 엔드포인트도 인증이 필요하면 302로 /login 리다이렉트될 수 있다.
 */
@RestController
@RequestMapping("/api/setups")
public class SetupController {

  // 셋업 조회(셋업 + 태그 집계)를 담당하는 서비스
  private final SetupService setupService;

  // 생성자 주입
  public SetupController(SetupService setupService) {
    this.setupService = setupService;
  }

  /**
   * 셋업 목록 조회
   * - Service에서 tags/tagsFlat까지 포함한 DTO 리스트로 변환해서 반환
   */
  @GetMapping
  public List<SetupResponse> getSetups() {
    return setupService.getSetups();
  }

  /**
   * 셋업 단건 조회
   * - 존재하면 200 OK + SetupResponse
   * - 없으면 404 Not Found
   */
  @GetMapping("/{id}")
  public ResponseEntity<SetupResponse> getSetup(@PathVariable("id") Long id) {
    return setupService.getSetup(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
  }
}