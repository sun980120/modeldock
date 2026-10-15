# 운영 및 배포

## 추천 구성과 대안

초기 팀 규모에는 VPC 내부 ECS Fargate + RDS MariaDB를 추천합니다. 이미 EKS 운영 인력이 있는 팀은 같은 컨테이너를 EKS에 배포할 수 있습니다. 현재 저장소는 로컬 Compose와 빌드·검증 CI를 제공하며 실제 AWS 리소스를 생성하지 않았습니다.

| 관점 | ECS Fargate · 추천 | EKS · 대안 |
| --- | --- | --- |
| 선택 이유·장점 | 별도 Kubernetes control plane 운영 없이 컨테이너 배포·롤링 교체 | 조직의 기존 Kubernetes 도구·정책·배포 체계 재사용 |
| 단점 | AWS 의존, Kubernetes API와 차이 | control plane·노드·애드온·업그레이드 관리 부담 |
| 비용 | task vCPU·메모리, ALB, RDS, NAT/egress, 로그·백업 비용 | 동일한 워크로드 비용 외 cluster·노드·애드온 운영 비용 |
| 운영 난이도 | 초기 팀에 상대적으로 낮음 | 운영 역량과 온콜 체계가 필요 |
| 확장성 | API·web 별도 autoscaling, DB lock 경합을 관측하며 확장 | Deployment/HPA로 분리 확장. 예산 조정 행의 직렬화 한계는 동일 |
| 장애 대응 | Multi-AZ RDS, health 기반 task 교체, 예약 만료 복구 | Pod 재시작·분산·롤링 배포, DB failover와 만료 복구 |

정확한 가격은 리전·트래픽·가용성·백업 보존 정책에 따라 산정해야 합니다. 이 저장소는 확인하지 않은 월 비용 숫자를 제시하지 않습니다. 무료 공급자도 안정적인 SLA·할당량을 보장하지 않습니다. FreeLLMAPI 원 저장소는 개인 실험·학습 목적과 운영상 한계를 안내하므로 사내 운영 승인 전 공급자 약관·데이터 전송 범위·신뢰성을 검토해야 합니다.

## AWS 배치

- Route 53 → ACM 인증서가 적용된 HTTPS ALB → web/API의 경로별 target group. SSE 경로에서 buffering을 끄고 ALB idle timeout을 keepalive(15초)보다 길게 설정합니다.
- web/API/FreeLLMAPI는 private subnet, DB는 별도 DB subnet group에 배치합니다. API를 인터넷에서 직접 열지 않습니다. 라우터는 ModelDock API task에서만 접근하도록 보안 그룹을 제한합니다.
- RDS MariaDB는 암호화·자동 백업·PITR·Multi-AZ를 적용합니다. 애플리케이션 DB 사용자와 migration 사용자를 운영에서는 분리합니다.
- Secrets Manager에 DB·통합 키를 보관하고 task role은 필요한 secret ARN만 읽게 합니다. ECR 접근·로그 쓰기·secret 읽기 이외 권한은 분리합니다. 이미지나 NEXT_PUBLIC 변수에 키를 넣지 않습니다.
- S3/CloudFront는 정적 자산 캐시에 선택적으로 사용합니다. 인증·SSE·사용량 API는 캐시에서 제외합니다. 대화 본문을 S3에 별도 복제하지 않습니다.
- CloudWatch는 요청 수·5xx·p95 지연·활성 생성 수·만료 복구 수·차감 초과·DB lock 대기·CPU·메모리·DB 연결을 관측합니다. 대화·이메일·키·session ID를 로그 차원으로 넣지 않습니다. 이 저장소의 기본 Actuator는 health/info만 사용하며 별도 비즈니스 메트릭 대시보드는 운영 연동 작업이 필요합니다.

Kubernetes를 선택한다면 Deployment(web/API 분리), ClusterIP Service, HTTPS Ingress, ConfigMap(비민감 설정), Secret/External Secrets, HPA, Namespace, NetworkPolicy, requests/limits, readiness/liveness/startup probe, PodDisruptionBudget, rolling update, graceful termination을 구성합니다. API readiness는 `/actuator/health/readiness`, liveness는 `/actuator/health/liveness`를 사용합니다. DB 장애를 liveness 재시작의 원인으로 삼지 않습니다. 세션은 MariaDB에 공유되므로 로드밸런서 stickiness에 의존하지 않습니다.

## DB·마이그레이션

DDL은 Flyway `V1__modeldock.sql`, `V2__authentication_rate_limits.sql`에 있습니다. 운영 배포 후 기존 migration 파일은 수정하지 않고 새 버전을 추가합니다. 빈 DB에 baseline을 적용하고, 향후 변경은 expand → 코드 배포 → contract 단계로 진행합니다. 복구 전용 DB에서 스키마와 백업을 검증합니다.

외래 키로 사용자·대화·generation·메시지 참조를 보장합니다. 사용자 삭제 기능은 초기 범위에 없으며 외래 키 우회를 통한 무결성 해제를 하지 않습니다. 대화 삭제는 메시지 물리 삭제와 tombstone을 함께 사용하여 회계 이력을 보존합니다. 대량 정리 시 batch 단위로 처리하여 긴 lock과 undo 증가를 피합니다.

인덱스는 사용자·삭제 여부·최근순 대화, 대화별 message sequence cursor, 상태별 만료 generation, 사용자별 활성 generation, 일별 scope 예산 PK를 지원합니다. 메시지는 cursor pagination으로 긴 이력의 OFFSET 비용을 피합니다. 대화·관리자 목록은 제한된 50개 OFFSET 페이지를 사용하며, 수십만 행 규모에서는 cursor pagination으로 교체하는 것이 다음 최적화 지점입니다. 선행 `%` 제목 검색은 사용자별 범위를 인덱스로 좁힌 뒤 scan하므로 대화가 매우 많아지면 별도 검색 인덱스를 검토합니다.

비즈니스 트랜잭션은 Service에 두고 HTTP 공급자 호출은 DB 트랜잭션 밖에서 수행합니다. 단일 글로벌 조정 lock은 소규모 팀의 일관성을 위해 선택했습니다. 병목이 관측되면 일별 서비스·사용자 행의 고정 lock 순서와 별도 lease 원장으로 나누되, 전체 동시성·예약의 원자성을 유지하는 동시성 테스트가 필요합니다. JDBC 명시적 조회를 사용하여 JPA Lazy Loading/N+1을 피합니다.

## 취소·재시작·롤링 배포

API를 교체해도 생성 원장은 DB에 남습니다. 작업 인스턴스가 살아 있으면 DB 취소 플래그를 확인하고 HTTP 연결을 닫습니다. 작업 인스턴스가 사라지면 전체 타임아웃+15초 후 `INTERRUPTED`로 정산됩니다. 활성 예약을 시작하자마자 일괄 환급하는 startup 작업은 다른 인스턴스의 정상 작업을 침해하므로 두지 않았습니다.

운영 배포는 readiness가 정상인 새 task를 올린 뒤 기존 task를 drain합니다. 공급자 HTTP 작업이 drain을 넘기면 만료 복구를 적용합니다. 완전한 무중단 답변 이어쓰기는 현재 제공하지 않으며 사용자에게 중단 오류와 수동 재시도를 표시합니다.

## 프록시·세션 보안

HTTPS에서는 `COOKIE_SECURE=true`가 필수입니다. API는 동일 출처 프록시를 통해 호출합니다. Nginx 예제는 요청 본문·검색어·대화 ID를 access log에 남기지 않으며, SSE buffering을 끕니다. ALB/Ingress access log를 추가한다면 URI/query를 포함하는 기본 형식에 대화 식별자·제목 검색어가 남을 수 있으므로 비식별화·접근 제한·보존 기간을 설정해야 합니다.

인증 시도 제한은 기본적으로 직접 접속한 주소를 사용합니다. API 앞에 프록시를 두면 여러 사용자가 하나의 proxy IP로 묶일 수 있습니다. 실제 IP를 사용할 때는 **접속을 허용한 프록시만 신뢰하도록** Tomcat RemoteIpValve와 trusted proxy 범위를 구성하고, 게이트웨이는 클라이언트가 보낸 forwarding 헤더를 덮어써야 합니다. 임의 `X-Forwarded-For`를 애플리케이션에서 직접 신뢰하지 마세요.

기본 frontend는 선택적 Google Fonts CSS를 사용하며 사용 불가 시 시스템 한글 폰트로 표시합니다. 외부 폰트 통신을 금지하는 조직에서는 해당 import를 제거하거나 허가된 폰트를 자체 호스팅하세요. 브라우저 분석 SDK나 대화 내용 telemetry는 추가하지 않았습니다.

## 운영 전 필요한 실제 검증

검토 커밋의 FreeLLMAPI와 연결한 staging에서 참여·합성 모델을 설정하고 정상 합성·일부 실패·judge 실패·전체 실패·quota 거절·실제 취소 전파를 확인합니다. 공급자 결제 한도도 별도로 설정합니다. AWS 장애 복구, RDS failover·백업 복원, 실기기 키보드와 대량 부하 검증을 통과하기 전에는 운영 검증 완료로 간주하지 않습니다.
