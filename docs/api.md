# API 및 보안 계약

동일 출처 `/api`로만 호출합니다. 세션 ID는 HttpOnly 쿠키로 전달하며 localStorage에 인증 토큰을 저장하지 않습니다. 변경 요청은 먼저 `GET /api/auth/csrf`에서 받은 headerName과 token을 헤더에 넣어야 합니다. CORS는 열지 않습니다.

## 인증·계정

| Endpoint | Request | Response | 규칙 |
| --- | --- | --- | --- |
| GET `/auth/csrf` | 없음 | `{token, headerName}` | 비로그인 허용, 세션에 CSRF 저장 |
| POST `/auth/register` | `{email,displayName,password}` | UserView | 이메일 254자, 이름 60자, 비밀번호 12자 이상·UTF-8 72바이트 이하. USER/PENDING은 서버 지정 |
| POST `/auth/login` | `{email,password}` | UserView | BCrypt 검증, 세션 ID 교체 |
| GET `/auth/me` | 없음 | UserView | 로그인 필요. 승인 대기도 사용 가능 |
| POST `/auth/logout` | 없음 | `{ok:true}` | 세션 폐기, CSRF 필요 |

UserView: `id,email,displayName,role,status,dailyLimit`. 비밀번호 해시를 반환하지 않습니다. 가입·로그인 시 각각 계정별 10회/15분, 접속 주소별 30회/15분 시도 제한을 DB에서 공유합니다. 원격 주소를 위조 가능한 임의 헤더에서 직접 읽지 않습니다. 프록시 실제 IP 구성은 운영 문서를 참고하세요.

## 대화

모든 경로는 APPROVED 계정과 **본인 소유권**을 요구합니다. ADMIN 예외가 없습니다. UUID가 다른 사용자의 것이거나 없거나 삭제됐다면 같은 404를 반환합니다.

| Endpoint | Request | Response |
| --- | --- | --- |
| GET `/conversations?q=&offset=0` | 제목 검색 120자 이하, 50개씩 | Conversation[] |
| POST `/conversations` | 없음 | `{id,title,updatedAt}`. 소유자는 인증 ID로 생성 |
| PATCH `/conversations/{id}` | `{title}` 1~120자 | `{ok:true}` |
| DELETE `/conversations/{id}` | 없음 | `{ok:true}`. 활성 생성은 409 |
| GET `/conversations/{id}/messages?before={messageId}` | 본인 대화의 메시지 cursor | 최근 100개, 시간 순서. 본문·상태·generationId |
| POST `/conversations/{id}/generations` | `{requestId:UUID,content}` 1~16,000자 | `{id,status:"RESERVED"}` |
| GET `/conversations/{id}/generations/{generation}` | 없음 | 본인 생성 상태·예약·보고량·오류 코드 |
| POST `/conversations/{id}/generations/{generation}/cancel` | 없음 | `{ok:true}`. DB 취소 요청 |
| GET `/conversations/{id}/generations/{generation}/events` | 없음 | SSE 상태 스트림 |
| GET `/generations/active` | 없음 | 본인 활성 생성. 탭 복구용 |

생성 서비스는 승인·소유권 → 모델 설정 검증 → 글로벌 lock → 사용자·전체 한도 → 예약·generation·질문 저장 → commit → 비동기 상위 HTTP 호출 순으로 처리합니다. 과거 실패·취소 질문은 새 요청의 모델 입력 이력에 자동 포함하지 않습니다. 사용자가 재시도하면 그 질문을 현재 질문으로 한 번 넣습니다.

SSE의 이벤트명은 `accepted`, `generating`, `completed`, `failed`입니다. data는 `{id,status,code}`이며 실제 DB 상태 전이만 반영합니다. 다른 탭에서 연결해도 DB의 소유권과 승인 상태를 반복 확인합니다. 연결 실패 시 생성 상태 API를 조회하고 재연결하며 답변을 자동 재생성하지 않습니다. SSE 연결 해제 자체는 생성 취소가 아닙니다. 중지는 명시적인 취소 API로 수행합니다.

## 사용량·관리

| Endpoint | 권한 | 동작 |
| --- | --- | --- |
| GET `/usage` | APPROVED | 개인 예산·선택적 서비스 예산·조회 시각 |
| GET `/usage/requests` | APPROVED | 본인 최근 30개 생성 정산. 대화 본문 없음 |
| GET `/admin/users?offset=0` | ADMIN | 50명씩 계정 상태·한도·당일 차감·예약. 본문 없음 |
| PATCH `/admin/users/{id}` | ADMIN | `{status,dailyLimit}`. 승인·거절·정지 및 한도 조정. 관리자 계정 변경 금지 |
| GET/PUT `/admin/settings` | ADMIN | `{fusion,serviceDailyLimit,shareServiceBudget}` |
| GET `/admin/models` | ADMIN | 통합 API의 실제 실행 가능 모델 목록 |
| GET `/admin/provider-quotas` | ADMIN | 현재 `UNKNOWN`, 빈 pool 목록, 확인 불가 이유. 임의 총잔액 없음 |

FusionSettings는 `models`(2~8개 distinct), `judge`, `outputTokens`(128~8192), `timeoutSeconds`(10~240), `concurrency`(1~32)입니다. 모델명은 160자 이내이고 `auto`, `fusion` 등 가상 라우터를 참여·합성 모델로 허용하지 않습니다. 일일 한도는 0~1조 사이입니다. UI에서 제공하는 설정과 API 지원 범위는 `freellmapi.md`를 참고하세요.

## 오류

```json
{"code":"FUSION_QUORUM_NOT_MET","message":"참여 모델의 성공 응답이 부족해 융합하지 못했습니다. 잠시 후 다시 시도해 주세요."}
```

- 400: 알 수 없는 필드, DTO validation, UUID·범위 오류.
- 401: 미인증·로그인 실패.
- 403: CSRF·역할·계정 승인 상태 오류.
- 404: 본인 대화·생성을 찾을 수 없음.
- 409: 활성 생성·중복 requestId·이메일 충돌.
- 413: 완료 대화 이력 크기 초과.
- 429: 예산·동시 생성·인증 시도 제한.
- 502/503: 공급자 실패·계약 위반·연결 미설정.

`@RestControllerAdvice`가 오류를 정규화합니다. 본문·공급자 오류 전문·API 키·SQL 파라미터를 오류 응답에 포함하지 않습니다. Backend는 DB Entity를 응답으로 직접 내보내지 않고 record DTO를 사용합니다. Markdown은 raw HTML을 실행하지 않으며 javascript 링크도 허용하지 않습니다.

생성 실패 코드는 최소 응답 미달(`FUSION_QUORUM_NOT_MET`), 합성 실패(`FUSION_JUDGE_FAILED`), 증명 메타데이터 누락·불일치, 불완전 종료, 빈 답변, 상위 인증 실패·경로 없음·429를 구분합니다. 로그에는 고정 코드·상태·개수만 기록합니다. 카탈로그 canonical ID와 공급자 실제 응답 ID는 달라질 수 있으며 FreeLLMAPI가 해석합니다.

검증 코드는 `ModelDockIntegrationTest`, `MariaDbIntegrationTest`, `FusionGatewayTest`, 프런트엔드 단위·E2E 테스트에 있습니다.
