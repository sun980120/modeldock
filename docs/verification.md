# 검증 범위와 절차

자동화 결과는 명령 실행 결과로만 보고합니다. API fixture를 사용하는 화면 검증과 실제 공급자·기기 검증을 구분합니다.

## 자동화

| 요구사항 | 검증 위치 |
| --- | --- |
| 브라우저 model/fusion/userId 우회 거절 | `ModelDockIntegrationTest.clientCannotOverrideModelOrOwner` |
| 생성 소유권 서버 지정 | 위 테스트의 conversation 생성 후 DB 소유자 검사 |
| A가 B의 목록·검색·메시지·수정·삭제·생성·취소·상태·SSE 접근 | `everyConversationPathRejectsOtherOwnerAndAdmin` · ADMIN도 검사 |
| 실제 fusion 요청 필드·stream:false·중간 답변 비공개 | `FusionGatewayTest.wireRequestUsesVerifiedFusionFieldsAndBufferedCompletion` |
| canonical 모델·judge ID와 공급자 ID 차이 | `canonicalCatalogAliasesCanResolveToProviderModelIds`, `canonicalJudgeAliasCanResolveToProviderModelId` |
| 최소 응답·합성 실패·불완전 종료 구분, 중복 panel 거절 | `failedQuorumJudgeAndIncompleteAnswerHaveDistinctErrors`, `duplicatePanelEntriesDoNotSatisfyQuorum` |
| 실행 가능한 실제 모델만 목록에 표시 | `catalogExcludesVirtualSlotsAndUnavailableModels` |
| 일부 참여 실패 후 합성, 단일 모델·judge 실패 거절 | `partialPanelSuccessCanSynthesizeAndUsageIsNotAddedTwice`, `singleModelJudgeFailureAndUnknownMetadataAreNeverSuccess` |
| usage 누락·동일 합계 중복 가산 방지 | `missingUsageRemainsUnknown`, `reservationAndMissingUsageAreChargedOnce` |
| 동시 사용자·전체 예약, 활성 생성 제한 | `personalAndGlobalBudgetsReserveAtomically`, `userConcurrentRequestsAndGlobalConcurrencyAreLimited` |
| 예약 초과·실패·취소·재시작 복구 | `overrunIsRecordedAndBlocksFurtherReservations`, `cancelAndRestartDoNotLeaveReservations` |
| Asia/Seoul 자정, 지난 날짜 예약 정산 | `resetUsesSeoulMidnightAndServiceBudgetIsPrivate` |
| 상위 HTTP 취소·전체 타임아웃 | `FusionGatewayTest.cancellationClosesInFlightHttpRequest`, `overallRequestDeadlineIsEnforced` |
| CSRF·ADMIN·정지 계정 | `accountAndCsrfRulesAreEnforced` |
| 실제 JDBC 세션 가입·로그인·승인·로그아웃 | `registrationLoginApprovalAndLogoutUseRealSession` |
| 다른 탭 예약 알림·포커스 복귀 재조회 | `useUsage.test.tsx` |
| MariaDB 실제 migration·lock·소유권·예산 | `MariaDbIntegrationTest` · Testcontainers |
| Enter/Shift+Enter·IME·keyCode 229·모바일 전송 | `Composer.test.tsx` |
| Markdown 표·코드 복사 버튼·XSS | `Markdown.test.tsx` |
| 360/390/768/1280px, 긴 코드·표·URL, 가로 화면 | `e2e/workspace.spec.ts` |
| drawer 포커스 순환·복귀, 저장된 다크 테마 | 위 E2E |
| 모바일 가입→승인 확인→채팅→중지, 관리자 승인 | 위 E2E · API fixture 기반 |

공급자 shared quota 숫자는 현재 수집·합산하지 않습니다. 없는 전체 잔액을 만들지 않는 방식으로 중복 합산 자체를 피합니다. 향후 quota 연동 시 같은 provider/account/pool/metric/window의 여러 모델·키를 입력한 별도 중복 제거 테스트가 선행돼야 합니다.

## 실제 FreeLLMAPI staging

1. 검토한 커밋을 staging에 고정하고 가능한 서로 다른 모델 3개와 judge를 연결합니다. ModelDock 관리자 목록 조회에 실제 모델 ID가 나타나는지 확인합니다.
2. 정상 질문: 상위 요청에 `model=fusion`, 명시적 panel·judge, `strategy=synthesize`, `stream=false`가 전달되는지 서버 간 요청 메타데이터로 확인합니다. 대화 본문·키를 운영 로그에 남기지 않습니다.
3. 참여 1개를 비활성화하거나 테스트 공급자가 실패하게 한 후 나머지 2개와 judge 합성이 성공하는지 확인합니다.
4. 참여 모델을 1개만 성공시키고, judge도 별도로 실패시킵니다. 둘 다 단일 답변으로 조용히 완료되지 않고 재시도 버튼이 나타나야 합니다.
5. 모든 참여 실패·429·장시간 응답·응답 절단을 각각 재현합니다. 오류 전문이나 중간 답변이 브라우저로 전달되지 않아야 합니다.
6. 생성 중지 후 공급자 요청이 취소되는지 상위의 요청 상태로 확인합니다. 상위 취소 수용을 확인하기 전에는 비용 중단까지 검증했다고 보고하지 않습니다.
7. 참여·judge 성공 usage 합계를 응답 한 번으로 기록하는지 비교합니다. 실패 attempt의 누락 가능성은 PARTIAL, usage 미제공은 UNKNOWN이어야 합니다.
8. 진행 중 API 인스턴스를 종료·재시작하고 최대 설정 시간+15초 후 예약이 정산되는지 확인합니다. 같은 DB에 연결한 2개 인스턴스에서도 서비스 동시 상한을 넘지 않아야 합니다.

## 실제 모바일 키보드 · 미검증 항목

실제 iOS Safari와 Android Chrome 각각에서 기종·OS·브라우저 버전·날짜를 기록해 아래를 실행합니다. 데스크톱의 viewport 에뮬레이션은 실제 가상 키보드 검증을 대신하지 않습니다.

1. 360px 또는 390px 정도의 세로 화면에서 회원가입 폼의 이름·이메일·비밀번호를 순서대로 입력합니다. 자동 확대를 막는 viewport 설정이 없어야 하며 확대 후에도 조작 가능해야 합니다.
2. 키보드가 열린 상태로 입력창·전송 버튼이 visual viewport 안에 남는지 확인합니다. 브라우저 주소 표시줄 확장·축소와 iPhone 홈 인디케이터 safe area도 확인합니다.
3. 한글 `안녕하세요`를 조합 중 Enter/완료 키로 확정합니다. 조합 확정만으로 전송되지 않아야 합니다. 모바일 Enter는 줄바꿈, 전송은 별도 버튼으로 수행합니다.
4. 여러 줄을 붙여 넣어 최대 높이 이후 입력창 내부가 스크롤되는지, 마지막 줄과 전송 버튼이 접근 가능한지 확인합니다.
5. 키보드를 열고 닫으면서 세로↔가로 회전하고, 긴 코드·표·URL 대화를 엽니다. 표·코드는 내부 가로 스크롤되고 페이지 전체는 가로로 움직이지 않아야 합니다.
6. 생성 중 과거 메시지로 스크롤합니다. 화면이 강제로 하단으로 이동하면 실패입니다. 최신 메시지 버튼으로만 하단 복귀가 가능해야 합니다. 현재는 완성 답변 일괄 표시 모드로 검사합니다.
7. drawer를 열고 대화 선택 시 닫히는지 확인합니다. 외부 키보드 Tab/Shift+Tab, Escape, iOS VoiceOver·Android TalkBack으로 포커스가 modal 밖으로 빠지지 않는지, 닫은 뒤 호출 버튼으로 돌아오는지 확인합니다.
8. 시스템·라이트·다크 테마를 바꾸고 새로고침합니다. 시스템 테마 변경도 반영되어야 합니다. 44×44px 미만의 주 기능 터치 버튼이 없어야 합니다.
9. 가입→승인 대기→관리자 모바일 승인→승인 확인→채팅→생성 중지→명시적 재시도를 끝까지 실행합니다. 승인·거절·정지를 hover 없이 수행합니다.

## 다중 탭·오류 수동 검증

동일 계정의 두 탭을 열어 A에서 생성 시작 후 B의 남은 예산이 예약량만큼 갱신되는지 확인합니다. B를 백그라운드로 두었다가 복귀한 경우에도 재조회해야 합니다. 별도 브라우저 세션에서는 포커스 복귀 또는 최대 30초 주기를 확인합니다. 완료·실패·취소 후 예약이 0으로 돌아오고 정산 차감이 반영되는지 확인합니다.

네트워크 패널에서 `/usage`만 실패시켜도 토큰 소진 메시지나 임의 0이 나타나면 안 됩니다. 생성 중 SSE를 끊었다 복구했을 때 새 공급자 호출이 자동으로 발생하지 않아야 합니다. 사용자 A에서 로그아웃한 뒤 B로 로그인할 때 A의 대화·사용량이 잠시라도 나타나지 않아야 합니다.
