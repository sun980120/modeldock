# FreeLLMAPI 연동 검증

## 확인한 기준

- 저장소: https://github.com/tashfeenahmed/freellmapi
- 검토 커밋: `bf1660454fd4e1889fe0c1b5cd10f2af6605afa0`
- API 설명: https://github.com/tashfeenahmed/freellmapi/blob/main/docs/en/api/01-rest-api.md
- 구현: `server/src/services/fusion.ts`, `server/src/routes/proxy.ts`, `server/src/routes/settings.ts`, `server/src/routes/health.ts`.
- 확인 방식: 공식 문서·로컬 소스 검토 후, 지정한 서버의 통합 키로 모델 목록과 실제 융합 합성 응답 확인. 원격 배포의 정확한 commit hash는 미확인.

배포할 때 검토한 커밋에 고정하세요. 업데이트 시 이 문서의 계약 테스트와 실제 공급자 검증을 다시 실행해야 합니다. 별도 FreeLLMAPI 배포판을 사용하는 경우 재검증이 필요합니다.

## 지정한 원격 서버 접속 검사

사용자가 지정한 호스트는 `llm-api.intin.myds.me`입니다. `.env.example`, Compose, Backend의 기본 API 주소를 `https://llm-api.intin.myds.me/v1`로 통일했습니다. 2026-10-15 오후 재검증에서 `/v1/models`와 `/v1/chat/completions`가 실제로 동작함을 확인했습니다.

2026-10-15 · Asia/Seoul에 자격 증명 없이 다음을 확인했습니다.

| 검사 | 관측 결과 |
| --- | --- |
| HTTPS `/`, `/v1/models`, `/v1/openapi.json` | TLS 호스트 이름 검증 실패. HTTP 응답에 도달하지 못함 |
| 서버 인증서 Subject | `CN=intin.myds.me` |
| 서버 인증서 SAN | `DNS:intin.myds.me`만 존재. `llm-api.intin.myds.me` 미포함 |
| 인증서 유효기간 | 2026-10-04 20:14:48 UTC ~ 2027-01-02 20:14:47 UTC. 검사 시점에 만료되지 않음 |
| HTTP `/` HEAD | nginx의 `200 text/html`. HTTPS 리디렉션이 아니며 FreeLLMAPI API의 동작 증거는 아님 |
| 로컬 ModelDock `FREELLMAPI_KEY` | 미설정. 통합 키를 사용한 원격 요청 미실행 |

위 표는 오전 최초 검사 기록입니다. 오후에는 인증서 검증을 유지한 HTTPS 요청으로 아래를 확인했습니다. 통합 키는 서버 환경 변수 또는 무시되는 `.env`에서만 읽으며 URL·브라우저·로그에 기록하지 않습니다.

| 오후 재검증 | 관측 결과 |
| --- | --- |
| `/v1/models?execution_status=ready` | HTTP 200. 실제 모델과 가상 라우터 슬롯을 함께 반환 |
| `/v1/chat/completions` · `model=fusion`, 명시적 3개 panel, 지정 judge, `stream=false` | HTTP 200, 약 8.66초, `_fusion.synthesized=true` |
| 실제 성공 panel | Google `gemini-3.5-flash-lite`, NVIDIA `nvidia/nemotron-3.5-lightning-30b-a3b` |
| 최종 합성 | Google `gemini-3.5-flash-lite`, `finish_reason=stop`, 최종 답변 존재 |
| 상위 보고 usage | 입력 400 + 출력 494 = 합계 894. 실패 호출을 포함한 확정 전체량으로 간주하지 않음 |

### 정상 응답을 거절하던 ID 별칭 오류

카탈로그는 `nemotron-3.5-lightning-30b-a3b`를 노출하지만 `_fusion.panel`은 공급자 실제 ID인 `nvidia/nemotron-3.5-lightning-30b-a3b`를 반환했습니다. 기존 ModelDock은 둘을 문자열로 비교하여 정상 합성도 `FUSION_NOT_SYNTHESIZED`로 거절했습니다. judge의 canonical 별칭도 동일한 문제가 있었습니다.

참여·합성 모델은 계속 관리자 설정 그대로 상위 API에 전달하고, 별칭 해석은 FreeLLMAPI의 `resolveFusionCandidate` 계약을 따릅니다. 응답에서는 합성 여부, 서로 다른 성공 `(platform,model)` 2개 이상, 중복·과다 panel 방지, judge 정보, 완료 상태, 최종 답변을 검증합니다. canonical ID와 공급자 ID의 단순 문자열 일치는 요구하지 않습니다. 별칭을 접두사 제거 같은 추측으로 변환하지도 않습니다. 실패·중간 답변·원본 오류 전문은 브라우저나 로그로 전달하지 않습니다.

모델 목록에서는 `execution_status=ready`, `available=true`인 실제 모델만 선택 후보로 표시하며, `auto`·`fusion`과 실행 상태가 없는 가상 Claude 슬롯은 제외합니다.

### Qwen의 context 오류 문구

`request (~1005 tokens) does not fit this model's context window`는 검토한 소스에서 문맥 크기 **또는 모델 TPM**이 예상 토큰보다 작을 때 모두 사용되는 문구입니다. `rowFitsRequestSize`가 두 제한을 검사하고 `runModelCall`이 동일한 오류로 표시합니다. 예상 토큰에는 입력뿐 아니라 출력 예약량도 포함됩니다.

원격 공개 모델 목록에서 `qwen3.8-27b`의 문맥 크기는 262,144로 확인했습니다. 사용자가 확인한 모델 상세 값도 문맥 262,144, RPM 30, RPD 250, TPM 1,000입니다. 예상 요청 1,005가 TPM 1,000보다 커서 이 크기 검사에 걸리며, 문맥 262K를 넘은 것이 아닙니다. 동일한 요청을 다음 분에 재시도해도 정적 크기 검사에서 다시 거절됩니다.

[Groq 공식 rate limit 문서](https://console.groq.com/docs/rate-limits)는 현재 Qwen `qwen/qwen3.8-27b`의 기본 무료 한도를 TPM 8,000으로 안내하지만 실제 조직별 한도는 Groq Console의 Limits에서 확인해야 합니다. 해당 조직의 실제 TPM이 8,000이면 FreeLLMAPI의 모델 TPM을 그 값에 맞춰 정정합니다. 실제 공급자 제한이 1,000이면 출력 예약을 줄여 입력+출력이 한도에 맞도록 해야 하며, 여러 참여 답변을 재입력하는 합성 단계는 특히 더 큰 예산이 필요합니다. 추측으로 한도를 올리거나 해제하지 않습니다.

인증서 검증을 끄거나 인증 정보를 HTTP로 전송하는 fallback은 추가하지 않았습니다. non-streaming 합성 이외의 원격 스트리밍·실패·취소·공급자 청구 전체 검증은 아직 별도입니다. 아래 설정 지원 표는 검토 커밋의 계약을 기준으로 합니다.

## 실제 요청

`POST {FREELLMAPI_URL}/chat/completions`, `Authorization: Bearer <서버 통합 키>`.

```json
{
  "model": "fusion",
  "messages": [{"role": "user", "content": "사용자 질문"}],
  "stream": false,
  "max_tokens": 1024,
  "fusion": {
    "models": ["관리자가 확인한 모델 ID 1", "관리자가 확인한 모델 ID 2"],
    "judge": "관리자가 확인한 합성 모델 ID",
    "strategy": "synthesize",
    "expose_panel": false
  }
}
```

위 모델 ID는 설명용이며 운영 기본값으로 등록하지 않습니다. 실제 목록은 서버가 `GET /v1/models?execution_status=ready`에서 읽습니다. 일반 사용자의 생성 DTO에는 `requestId`, `content`만 허용합니다. `model`, `fusion`, `userId`, `stream` 등 알 수 없는 필드는 400으로 거절합니다. 공급자 라우터를 브라우저에서 직접 접근할 수 없도록 네트워크를 제한해야 합니다.

## 설정 지원 표

| 요구 설정 | FreeLLMAPI 지원 | ModelDock 처리 |
| --- | --- | --- |
| 참여 모델 | `fusion.models` | 관리자 설정 DB에 저장, 매 요청 서버가 전달 |
| 합성 모델 | `fusion.judge` | 관리자 설정 그대로 전달, 별칭 해석은 FreeLLMAPI가 수행, 응답의 합성 성공·judge 정보 검증 |
| 합성 전략 | `fusion.strategy` | 항상 `synthesize` 고정, UI 선택 없음 |
| 중간 답변 공개 | `fusion.expose_panel` | 항상 false, 브라우저에 중간 답변 전달 안 함 |
| 최소 성공 수 변경 | API 없음. 상위 소스의 합성 quorum은 2 | ModelDock 검증 기준 2 고정. 동작하지 않는 입력 UI 없음 |
| 모델별 타임아웃 | 일반 fusion API에 없음 | UI 없음. 상위 공급자 어댑터의 제한을 운영자가 검토·배포 |
| 전체 생성 타임아웃 | fusion 전용 필드 없음 | ModelDock 내부 제한. HTTP future·응답 body 닫기로 취소 전파 |
| 모델별 개별 출력 토큰 | 모델별 맵 필드 없음 | UI 없음. 공통 `max_tokens`만 연동 |
| 전체 동시 생성 수 | fusion 요청 필드 없음 | ModelDock DB에서 여러 인스턴스 합산 제한 |

FreeLLMAPI 자체 대시보드의 Fusion 설정도 존재하지만 ModelDock은 DB에 저장한 명시적 모델·judge·전략을 매 요청 전달하므로 대시보드 기본값에 의존하지 않습니다. 모델·키·프로필 활성화는 FreeLLMAPI 관리자 화면에서 관리합니다. `fusion_tool_timeout_ms`는 도구 호출 전용이며 일반 텍스트 모델 타임아웃처럼 사용하면 안 됩니다.

지원하지 않는 최소 성공 수나 개별 모델 제한이 꼭 필요하면 상위 게이트웨이 소스를 변경하고 계약 테스트를 추가해야 합니다. 존재하지 않는 API 필드를 보내 설정됐다고 표시하지 않습니다. 합성 모델이 비활성화되거나 더 이상 응답하지 못하면 실패를 반환합니다.

## 일부 실패와 단일 모델 반환

명시적 panel은 자동 대체 모델 없이 각 지정 모델에서 키 회전을 수행합니다. 소스상 참여 슬롯은 최대 4번, judge는 최대 6번 시도합니다. 2개 이상의 텍스트 응답이 있으면 judge 합성이 가능합니다. 단일 생존 응답이 있거나 judge가 실패하면 상위 서비스는 단일 답변을 200으로 반환할 수 있습니다.

ModelDock은 `_fusion.synthesized == true`, 서로 다른 성공 참여 응답 2개 이상, 중복·요청 수 초과 panel 없음, judge 정보 존재, `finish_reason == stop`, 비어 있지 않은 최종 답변을 검증합니다. 불충족하면 최소 응답 미달·합성 실패·메타데이터 누락·불완전 종료 등을 별도 오류 코드로 반환하고 답변을 저장하지 않습니다. 재시도는 사용자가 버튼으로 실행하고 새로운 예산을 예약합니다.

## 스트리밍

상위 `stream:true`는 `_fusion.event: panel|judge` 프레임과 judge의 `choices[].delta.content`를 보냅니다. panel 프레임에는 중간 답변이 포함될 수 있으므로 그대로 브라우저로 프록시하면 안 됩니다.

확인한 버전에는 두 가지 제약이 있습니다.

1. 종료 프레임에 `_fusion.synthesized`가 없습니다.
2. judge 스트림 도중 오류 후 텍스트가 있으면 `runJudgeStreaming`이 부분 답변을 성공으로 반환할 수 있습니다.

따라서 이 구현은 검증 가능한 non-streaming 응답만 사용합니다. 로딩 후 완성 답변을 표시하며 실제 DB 상태만 SSE로 전달합니다. 토큰 스트리밍을 활성화하려면 상위 서비스에서 정상 종료와 합성 메타데이터를 보장하는 프로토콜이 먼저 필요합니다. 기존 동작을 검증 없이 스트리밍 성공으로 간주하는 옵션은 두지 않았습니다.

## usage와 잔여 할당량

성공 생존 panel의 usage를 합산하고 성공 judge의 usage를 더해 `usage`로 반환합니다. 실패 시도·취소 소비량의 완전한 합산은 보장하지 않습니다. judge streaming usage에는 추정값도 사용됩니다. `x_fusion.panel`의 토큰을 다시 더하지 않습니다. 응답의 `_fusion`에는 공급자·모델 ID는 있지만 모델별 입력·출력 토큰은 없습니다. 이를 임의 배분하지 않습니다.

공급자 quota 관측은 관리자 세션의 `/api/health` 등 별도 대시보드 API에 있습니다. 통합 키로 `/v1`의 모델·생성 API를 사용하는 ModelDock에 동일한 관리 권한이 있다고 가정하지 않습니다. 현재 커넥터에서는 일일·분당 token/request/account/model별 잔액을 자동 제공할 수 없어 숫자를 표시하지 않습니다. 공급자별 갱신 시각도 확인 불가입니다.

FreeLLMAPI 대시보드의 `free-tier` 집계는 문서상 예산·계정 키 수 기반 값도 혼합합니다. 이를 실제 공급자 잔여 토큰으로 취급하거나 모델별로 재합산하지 마세요. 향후 관측 API를 연동한다면 공급자·계정 pool·metric·window를 모두 키로 사용하고, 같은 계정의 여러 키가 공유하는 pool도 중복 합산하지 않는 검증이 필요합니다.
