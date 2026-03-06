# DESKIT: 데스크테리어 전문 라이브커머스 플랫폼
![](https://velog.velcdn.com/images/doyooning/post/7bfae199-972c-44cc-add3-6a81e857afef/image.png)

### 접속 링크
[DESKIT](https://dynii.deskit.o-r.kr)

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [서버 구조도 및 기능 모식도](#서버-구조도-및-기능-모식도)
- [주요 개선/보완 사항](#주요-개선보완-사항)
- [트러블슈팅](#트러블슈팅)

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
- NCP 서버 배포 환경에서 AWS 서버 배포 환경으로 변경하였습니다.
- 아키텍처 마이그레이션 맵핑:
  - **NCP 서버(Compute)** → **EC2 인스턴스**
  - **NCP Object Storage** → **S3**
  - **DB 서버** → **RDS(MySQL)** 또는 EC2 자체 운영(최종 RDS 선택)
  - **Redis** → **ElastiCache** 또는 EC2(최종 EC2 선택)
  - **OpenVidu 미디어 서버** → **EC2 단독 인스턴스**


### AI/RAG 기능 개선
- RAG 임베딩 청크 전략을 청킹 크기 500단어로 재설정하고, 답변 시 참고 인덱스 로깅을 추가해 추적 가능성을 높였습니다.
- 유사도 Threshold를 0.75에서 0.7로 조정해 응답 정확도와 관련도 균형을 개선했습니다.


### 라이브 방송 예약/운영 로직 강화
- 방송 예약 생성 경쟁 상황에서 Redis 락 기반 제어를 강화하고, 슬롯 정합성 검증을 보강했습니다.
- 예약 가능일/방송 날짜 선택 UX와 서버 권한 허용 경로를 함께 조정해 예약 실패 케이스를 줄였습니다.

---

## 트러블슈팅




---



    
