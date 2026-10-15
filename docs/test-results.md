# 실행한 검증 결과

검증일: 2026-10-15 · Asia/Seoul

| 검증 | 결과 |
| --- | --- |
| Spring Boot 컴파일·실행 JAR 빌드 | 통과 |
| H2 통합·HTTP 융합 계약 테스트 | 24개 통과, 실패 0. ID 별칭·실패 구분·가상 슬롯 제외 회귀 테스트 포함 |
| 실제 MariaDB 11.4.13 통합 테스트 | 13개 통과, 실패 0 |
| TypeScript 검사 · Next.js typegen 포함 | 통과 |
| 프런트엔드 단위 테스트 | 6개 통과 |
| Next.js 16.4.1 production build | 통과 |
| Chromium E2E · 360/390/768/1280px, 가로 화면, drawer 포커스, 테마, 모바일 가입·중지·승인 | 8개 통과 |
| 데스크톱 전용 메뉴 CSS 수정 후 1280px 재검증 | 1개 통과 |
| Docker Compose 구성 문법 | 통과 |
| 지정 원격 서버 `llm-api.intin.myds.me` HTTPS·통합 키·모델 목록 | 오후 재검증 HTTP 200 |
| 실제 non-streaming fusion | HTTP 200, 서로 다른 참여 2개와 지정 judge 합성 성공. 카탈로그 별칭과 공급자 ID 차이를 실제 재현 |
| 수정한 API·웹 Docker 이미지 빌드 | 통과 |
| 수정 이미지의 실행 중 Compose 반영 | API·웹 교체 및 게이트웨이 재시작 완료. DB 볼륨 유지 |
| 실제 ModelDock 전체 흐름 | 로그인 → 새 테스트 대화 → 예산 예약 → 실제 융합 호출 → assistant 저장 → 정산 → 테스트 대화 정리 통과 |

실제 ModelDock 검증에서는 사용자가 실패한 짧은 한국어 자기소개 질문을 새 테스트 대화에 전송했습니다. 생성 상태는 약 9.25초 후 `COMPLETED`, 오류 코드 없음, assistant 메시지 1개, 비어 있지 않은 답변, 상위 보고 1,037토큰이었고 예약 잔여량은 0으로 정산됐습니다. 기존 사용자의 대화는 조회하지 않았으며 검증용 대화만 삭제했습니다. 대화 본문·통합 키·로그인 비밀번호는 로그나 진단 보고서에 출력하지 않았습니다. 이 검증은 현재의 보수적 예산 정산 정책을 사용하며 공급자 청구 전체량의 확정 검증을 뜻하지 않습니다.

로컬 Docker에서 Testcontainers 보조 컨테이너와 MariaDB 컨테이너 시작 요청이 응답하지 않았습니다. 이번 검증이 만든 미실행 컨테이너만 정리한 뒤, `/private/tmp` 아래에 격리한 MariaDB 11.4.13 프로세스와 `modeldock_test_run` DB를 사용하여 같은 `mariaDbTest` 13개를 실행했습니다. 임시 DB 프로세스는 검증 후 종료했습니다. 기존 DB·컨테이너는 변경하지 않았습니다. CI 기본 경로는 Testcontainers이며, 외부 전용 테스트 DB를 지정하는 대체 경로도 제공합니다.

프런트엔드 E2E는 테스트 전용 API fixture를 사용했습니다. 실제 공급자 호출이나 실제 모바일 기기 검증 결과로 해석하면 안 됩니다. 화면의 예산 예시 숫자는 테스트 fixture에만 있으며 제품 화면은 Backend `/usage` 값을 조회합니다. 이후 별도 실제 공급자 호출로 non-streaming 합성 성공을 확인했고, 기존 검증기가 `nemotron-*`와 `nvidia/nemotron-*`를 문자열 비교하여 정상 응답을 거절하던 버그를 수정했습니다.

## 아직 실행하지 않은 검증

- 실제 상위 공급자의 전체 실패·취소 전파·청구 장부와의 usage 완전성 비교. 정상 non-streaming 합성은 검증함.
- iOS Safari·Android Chrome 실제 가상 키보드와 VoiceOver/TalkBack.
- AWS/EKS 배포·장애 복구·RDS failover·실제 대량 부하.

최종 답변의 토큰 스트리밍은 현재 구현에서 활성화하지 않았습니다. 검토한 상위 프로토콜의 합성 성공·스트림 정상 종료 검증 한계 때문에 non-streaming 최종 응답을 검증하고 표시합니다. 실제 상태 SSE만 구현했습니다. 제약과 변경 조건은 `freellmapi.md`에 정리했습니다.
