# ModelDock

Spring Boot 3.5 · Java 21 · Next.js 16 App Router · React 19 · TypeScript · MariaDB 기반의 팀 AI 채팅입니다. 모든 생성은 서버에서 FreeLLMAPI의 `model: "fusion"`으로 고정합니다. 모델 선택 권한은 관리자에게만 있습니다.

이 폴더에는 기존 코드가 없어 신규 구현했습니다. FreeLLMAPI 계약은 로컬 `/Users/hong-woosung/Desktop/freellmapi`의 커밋 `bf1660454fd4e1889fe0c1b5cd10f2af6605afa0`과 공식 API 문서를 기준으로 검토했습니다. 이후 실제 통합 키로 모델 목록과 non-streaming 융합 응답을 확인했습니다.

사용자가 지정한 서버는 `llm-api.intin.myds.me`이며 기본 API 주소는 `https://llm-api.intin.myds.me/v1`입니다. 초기에는 인증서 호스트 불일치가 있었지만 2026-10-15 오후 재검증에서는 HTTPS 검증, 통합 키 인증, 모델 목록 조회, 실제 융합 합성이 성공했습니다. 카탈로그 ID와 공급자 응답 ID의 차이로 정상 답변을 거절하던 검증 오류를 수정했습니다. [접속·융합 검사 결과](docs/freellmapi.md#지정한-원격-서버-접속-검사)

## 실행

1. Java 21, Node.js 24, Docker를 준비합니다.
2. `.env.example`을 `.env`로 복사하고 DB 비밀번호, 초기 관리자 이메일·비밀번호(12자 이상), FreeLLMAPI 통합 키를 입력합니다. 키는 브라우저에 전달되지 않습니다.
3. `docker compose up --build -d`를 실행합니다. `http://localhost:8088`에서 접속합니다. 로컬 HTTP에만 `COOKIE_SECURE=false`를 사용합니다.
4. 초기 관리자 계정으로 로그인하여 계정 메뉴 → 관리자 설정 → 융합 설정에서 실제 사용 가능한 참여 모델 2~8개와 합성 모델을 저장합니다.
5. 일반 사용자가 가입하면 관리자가 승인합니다. 승인 전에는 채팅·사용량 API를 호출할 수 없습니다.

초기 관리자 환경 변수는 최초 생성에만 사용합니다. 생성 후 배포 환경에서 제거하세요. 관리자 자동 비밀번호 재설정이나 이메일만 같은 기존 사용자 승격은 하지 않습니다. FreeLLMAPI의 대시보드와 공급자 키는 별도로 준비해야 합니다.

컨테이너 밖에서 실행하려면 DB를 준비하고 백엔드 프로세스에 `DATABASE_PASSWORD`, `DATABASE_URL`, `FREELLMAPI_KEY` 등을 설정한 뒤 다음을 실행합니다. 프런트엔드 `.env.local`의 `BACKEND_URL`은 기본값 `http://127.0.0.1:8080`을 사용합니다.

```sh
cd backend
./gradlew bootRun
```

```sh
cd frontend
npm ci
npm run dev
```

## 구현

- 본인 소유의 대화만 목록·검색·메시지·제목 수정·삭제·생성·취소·SSE 연결 가능. ADMIN도 동일한 소유권 규칙 적용.
- Spring Security 세션, HttpOnly·SameSite 쿠키, CSRF 검사, BCrypt, 로그인·가입 시도 제한, 매 요청 계정 상태 재확인.
- DB의 단일 조정 행을 잠근 뒤 사용자·서비스 예산과 동시 생성 상한을 같은 트랜잭션에서 검사·예약.
- 합성 결과 `_fusion.synthesized`, 참여 모델 2개 이상, 지정한 합성 모델, 최종 완료 상태를 검증. 단일 생존 모델·합성 실패를 성공으로 표시하지 않음.
- 완료·실패·중지·타임아웃 후 정산. 여러 인스턴스에서 취소 신호 공유. 재시작으로 남은 예약은 만료 후 회수·보수적 차감.
- 반응형 사이드바, 모바일 drawer, Markdown·표·코드 복사, 한국어 IME 보호, 모바일 명시적 전송, 시스템·라이트·다크 테마, 사용량 상세, 계정 승인 관리.
- 사용량 재조회: 예약·완료·실패·취소, 탭 포커스·복귀, BroadcastChannel, 30초 주기.

## 중요한 연동 제약

현재 FreeLLMAPI는 합성 답변 스트리밍을 지원하지만, 검토한 버전의 스트리밍 경로는 합성 성공 메타데이터를 최종 프레임에 보내지 않으며 합성 도중 오류를 부분 답변 성공으로 처리할 수 있습니다. ModelDock은 요구된 실패 검증을 우선하여 **`stream:false`로 완성 응답을 검증한 뒤 표시**합니다. SSE는 DB에 기록된 `accepted`, `generating`, `completed`, `failed` 상태만 전달합니다. 모델별 중간 답변·추론·가짜 타이핑은 표시하지 않습니다.

성공한 참여 모델과 합성 모델의 상위 `usage.total_tokens`를 한 번만 기록합니다. 실패·취소 호출과 내부 재시도 소비량은 완전히 확인되지 않으므로 확정 총량으로 표시하지 않습니다. 현재 정책은 확인 불가분의 예약을 유지하여 예산에서 차감합니다. 사용량 상태·예약식·초과 처리·이중 집계 방지는 [사용량 정책](docs/accounting.md)을 확인하세요.

공급자 잔여량은 공개 통합 API에서 제공되지 않아 `확인 불가`로 표시합니다. 관리자 전용 FreeLLMAPI 대시보드의 집계치를 모델별로 합산하거나 크레딧을 토큰으로 환산하지 않습니다.

## 검증

```sh
cd backend
./gradlew test
./gradlew mariaDbTest
```

```sh
cd frontend
npm ci
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

`mariaDbTest`는 Docker가 필요합니다. 프런트엔드 E2E는 테스트 전용 API fixture로 화면 흐름을 검증하며 실제 공급자 호출 검증을 대신하지 않습니다. 기기별 검증과 운영 전 점검은 [검증 절차](docs/verification.md)를 따릅니다.

Docker 실행이 제한된 환경에서는 `MODELDOCK_TEST_DATABASE_URL=jdbc:mariadb://127.0.0.1:포트/modeldock_test_이름`과 선택적 `MODELDOCK_TEST_DATABASE_USER`, `MODELDOCK_TEST_DATABASE_PASSWORD`로 **폐기 가능한 전용 MariaDB**를 지정할 수 있습니다. 테스트가 데이터를 삭제하므로 운영 DB는 지정하지 마세요. 외부 DB 이름은 `modeldock_test_` 접두사만 허용합니다.

- [실제 FreeLLMAPI 계약과 지원 범위](docs/freellmapi.md)
- [API·인증·소유권 규칙](docs/api.md)
- [예산 예약과 정산 정책](docs/accounting.md)
- [배포·DB·AWS 운영 방향](docs/operations.md)
- [요구사항 추적과 검증 범위](docs/verification.md)
- [실행한 검증 결과와 미검증 범위](docs/test-results.md)
