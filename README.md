# DESKIT: 데스크테리어 전문 라이브커머스 플랫폼
![](https://velog.velcdn.com/images/doyooning/post/7bfae199-972c-44cc-add3-6a81e857afef/image.png)

### 접속 링크
[DESKIT](https://dynii.deskit.o-r.kr)
> ⚠️ 현재 개발 환경으로 배포된 상태로, 인증 번호 전송 및 사업자등록번호 검증은 적용되어 있지 않습니다.

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [서버 구조도 및 기능 모식도](#서버-구조도-및-기능-모식도)
- [주요 개선/보완 사항](#주요-개선보완-사항)
- [트러블슈팅](#트러블슈팅)
- [향후 진행 계획](#향후-진행-계획)

---

## 프로젝트 개요

- 목표: 신세계I&C 팀 프로젝트 'DESKIT'를 개선 및 보완, 성능 개선
- 기간: 26.02 ~ 26.03
- 주요 기능:
  [DESKIT_주요기능](https://drive.google.com/file/d/1oxaB-RDMtOUjrHZP19cEsVKlyyFntf3W/view?usp=sharing)

> [DESKIT 프로젝트 리포지토리](https://github.com/doyooning/Project_DESKIT)

---

## 서버 구조도 및 기능 모식도
### 1. 서버 구조도
![](https://velog.velcdn.com/images/doyooning/post/75643ad2-5e3b-4b6b-a17f-915374fc16b8/image.jpg)

### 2. 챗봇 기능 모식도
![](https://velog.velcdn.com/images/doyooning/post/c79b1b38-101d-4b09-97c2-4df82f21c23d/image.jpg)

### 3. 라이브 방송 기능 모식도
![](https://velog.velcdn.com/images/doyooning/post/0a13aef5-3289-4de3-abf2-a40e0f0e996a/image.jpg)

---

## 주요 개선/보완 사항
### 플랫폼/환경 마이그레이션
- NCP 서버 배포 환경에서 AWS 서버 배포 환경으로 변경
- 아키텍처 마이그레이션 맵핑:
  - **NCP 서버(Compute)** → **EC2 인스턴스**
  - **NCP Object Storage** → **S3**
  - **DB 서버** → **RDS(MySQL)** 또는 EC2 자체 운영(최종 RDS 선택)
  - **Redis** → **ElastiCache** 또는 EC2(최종 EC2 선택)
  - **OpenVidu 미디어 서버** → **EC2 단독 인스턴스**


### AI/RAG 기능 개선
- RAG 임베딩 청크 전략을 청킹 크기 500단어로 재설정하고, 답변 시 참고 인덱스 로깅을 추가해 추적 가능성 향상
  - 기존 방식: PDF 등 문서 파일을 통째로 임베딩하여 답변이 과도하게 길어지거나 불필요한 정보들이 포함됨
  - 청킹 전략: 필요한 답변이 포함되어 있을 가능성이 높은 청크를 반환하여 답변 정확도가 개선됨 </br>


- 유사도 Threshold를 0.75에서 0.7로 조정해 응답 정확도와 관련도 균형 개선
  ![](https://velog.velcdn.com/images/doyooning/post/47fd6fa5-6cb0-40c8-ab2e-b142aa9e2721/image.jpg)
  - 동일한 질문에 대해 답변 가능성이 달라짐(유사도 Threshold 0.75(위), 0.7(아래))


### 라이브 방송 예약 로직 강화
- 방송 예약 생성 경쟁 상황에서 Redis 락 기반 제어 강화, 슬롯 정합성 검증 보강
  - [트러블슈팅](#트러블슈팅)에서 설명

### 단위 테스트 커버리지 100% 유지
- JaCoCo를 활용한 패키지(기능) 단위 테스트
- 구현한 기능들(account, admin, ai, common, directchat 패키지)에 대한 단위 테스트 커버리지를 100%로 유지
  ![](https://velog.velcdn.com/images/doyooning/post/e54e8a19-fe70-4e6e-bfc3-652205135b88/image.jpg)

---

## 트러블슈팅
### 1. 방송 예약 생성 경쟁 동시성 테스트
**문제 상황**
- K6를 활용한 동시성 테스트 중, 방송 예약 생성 경쟁에서 기대값과 다른 결과값이 표시됨
> 테스트 목적: 동일 시간 방송 예약은 최대 3명의 판매자까지 예약 가능 </br>
> 예시) 동일 시간 10명의 판매자가 방송 예약 시도시, 3명까지만 예약 성공

테스트 결과:
![](https://velog.velcdn.com/images/doyooning/post/46369fcc-18cf-446f-89c5-1cff8678a2da/image.jpg)
- Attempt 1: 예약 성공(reserve_success) 2건, 예약 실패(reserve_too_many_requests) 8건으로 기대값과 다름

![](https://velog.velcdn.com/images/doyooning/post/67a21ac1-cfab-4dc8-b7a9-4c74d68c88f6/image.jpg)
- Attempt 2: 예약 성공(reserve_success) 5건, 예약 실패(reserve_too_many_requests) 5건으로 기대값과 다름

문제 원인:
- 락을 선점하는 과정에서 실패하는 요청들이 발생
- 경합이 이루어지는 방송 날짜에 3명이 예약되고, 자리가 꽉 차서 나머지 요청들이 실패하는 것이 아닌 락 타이밍에 따라 결과값이 다르게 나오게 됨
- 결과적으로 동시에 10명이 시도하면 정책상 가능한 최대치인 3명까지 예약이 보장되지 않고, 락 처리 시간에 따라 결과가 달라지게 됨

해결 방안:
1. 경쟁 상황 처리 결과 검증 추가
```java
private void ensureSlotCapacityForReservation(LocalDateTime scheduledAt) {
    long slotCount = broadcastRepository.countByTimeSlot(scheduledAt, scheduledAt.plusMinutes(30));
    if (slotCount >= 3) {
        throw new BusinessException(ErrorCode.BROADCAST_SLOT_FULL);
    }
}
private void ensureSlotCapacityAfterReservation(LocalDateTime scheduledAt) {
    long slotCount = broadcastRepository.countByTimeSlot(scheduledAt, scheduledAt.plusMinutes(30));
    if (slotCount > 3) {
        throw new BusinessException(ErrorCode.BROADCAST_SLOT_FULL);
    }
}
```
- 저장 전/후로 이미 3명이 찼는지, 결과가 3명이 넘지 않았는지를 점검하여 정합성 유지

2. Redisson 활용하여 안정적인 제어
```java
private String buildDbSlotLockKey(LocalDateTime scheduledAt) {
    return "db-lock:broadcast-slot:" + scheduledAt;
}
private boolean acquireDbSlotLock(String lockKey, int timeoutSeconds) {
    Integer result = dsl.resultQuery("SELECT GET_LOCK(?, ?)", lockKey, timeoutSeconds)
            .fetchOne(0, Integer.class);
    return result != null && result == 1;
}
private void releaseDbSlotLock(String lockKey) {
    try {
        dsl.resultQuery("SELECT RELEASE_LOCK(?)", lockKey).fetch();
    } catch (Exception e) {
        log.warn("DB slot lock release failed: key={}, message={}", lockKey, e.getMessage());
    }
}
```
- DB 락을 적용, Redisson을 활용하여 락 점유 및 해제를 안정적으로 처리
- 락 키 만료, 락 해제 오류 등 까다로운 부분이나 실수할 수 있는 민감한 부분을 Redisson으로 리스크 최소화

최종 상태:
![](https://velog.velcdn.com/images/doyooning/post/52b84765-3037-4000-8d71-a9a0665240ea/image.jpg)
- 테스트를 반복 실행해도 항상 같은 결과값(예약 성공 3건, 실패 7건) 반환

---
## 향후 진행 계획
### 1. 성능 모니터링 도입
- 현재 K6를 사용하여 테스트 진행중, 실시간 성능 및 병목 지점 판단을 위해 모니터링 도구 도입 예정

### 2. RAG 기반 챗봇의 기능 및 사용자 경험 개선
- RAG에 사용될 문서 임베딩 및 벡터 검색 관련하여 확장성을 고려한 최적화 필요 예상
- AI 응답의 구조화가 더 필요, Tool Calling 사용하여 개선 예정
- 챗봇의 UX 측면 개선 예정


    
