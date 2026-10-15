"use client";
import { useEffect, useState } from "react";
import { RefreshCw, Info } from "lucide-react";
import { api, number } from "@/lib/api";
import type { Budget, Usage } from "@/lib/types";
function BudgetCard({ budget, label }: { budget: Budget; label: string }) {
  return (
    <section className="budget-detail">
      <span className="eyebrow">{label}</span>
      <h3>
        {number(budget.remaining)}
        <span> / {number(budget.limit)}</span>
      </h3>
      <div className="meter">
        <i style={{ width: `${budget.usagePercent}%` }} />
      </div>
      <dl>
        <div>
          <dt>사용 완료 · 예산 차감</dt>
          <dd>{number(budget.used)}</dd>
        </div>
        <div>
          <dt>진행 중 예약 토큰</dt>
          <dd>{number(budget.reserved)}</dd>
        </div>
        <div>
          <dt>사용률 · 예약 포함</dt>
          <dd>{budget.usagePercent.toFixed(1)}%</dd>
        </div>
      </dl>
      <small>
        매일 오전 12시 초기화 · 한국 시간
        <br />
        다음 초기화:{" "}
        {new Date(budget.resetsAt).toLocaleString("ko-KR", {
          timeZone: "Asia/Seoul",
        })}
      </small>
    </section>
  );
}
type Entry = {
  id: string;
  status: string;
  reserved_tokens: number;
  reported_tokens: number | null;
  charged_tokens: number | null;
  usage_quality: string;
};
export function UsagePanel({
  usage,
  error,
  refresh,
}: {
  usage: Usage | null;
  error: boolean;
  refresh: () => void;
}) {
  const [entries, setEntries] = useState<Entry[]>([]),
    [entryError, setEntryError] = useState(false);
  useEffect(() => {
    void api<Entry[]>("/usage/requests")
      .then(setEntries)
      .catch(() => setEntryError(true));
  }, [usage?.fetchedAt]);
  return (
    <div className="usage-panel">
      <div className="panel-intro">
        <p>ModelDock 이용 예산</p>
        <button
          className="icon-button"
          aria-label="사용량 새로고침"
          onClick={refresh}
        >
          <RefreshCw size={17} />
        </button>
      </div>
      {error ? (
        <p className="error-notice" role="alert">
          사용량을 조회하지 못했습니다. 잔여량은 확인 불가입니다.
        </p>
      ) : usage ? (
        <>
          <BudgetCard budget={usage.personal} label="나의 일일 예산" />
          {usage.service && (
            <BudgetCard budget={usage.service} label="서비스 전체 일일 예산" />
          )}
        </>
      ) : (
        <p>사용량을 불러오는 중…</p>
      )}
      <div className="info-box">
        <Info size={18} />
        <p>
          무료 모델도 각 공급자의 할당량을 소모합니다. 실패·취소 호출의 사용량은
          일부 누락될 수 있어 확인되지 않은 예약 예산은 보수적으로 차감합니다.
          내부 예산은 공급자 청구액의 절대 상한이 아닙니다.
        </p>
      </div>
      <section className="provider-section">
        <h3>
          공급자 잔여 할당량 <span className="neutral-badge">확인 불가</span>
        </h3>
        <p>
          공개 통합 API에서 계정별 잔여량을 제공하지 않습니다. 공급자별
          토큰·요청 제한과 갱신 시각은 FreeLLMAPI 관리자 화면에서 확인합니다.
        </p>
      </section>
      <section>
        <h3>최근 요청의 정산</h3>
        {entryError ? (
          <p>정산 이력을 조회하지 못했습니다.</p>
        ) : entries.length === 0 ? (
          <p className="muted">아직 생성 내역이 없습니다.</p>
        ) : (
          entries.map((e) => (
            <div className="ledger-row" key={e.id}>
              <div>
                <strong>
                  {e.status === "COMPLETED"
                    ? "완료"
                    : e.status === "CANCELLED"
                      ? "취소"
                      : e.status === "RUNNING" || e.status === "RESERVED"
                        ? "진행 중"
                        : "실패·중단"}
                </strong>
                <small>
                  {e.usage_quality === "PARTIAL"
                    ? "일부 누락"
                    : e.usage_quality === "CONFIRMED"
                      ? "확정"
                      : e.usage_quality === "ESTIMATED"
                        ? "추정"
                        : "확인 불가"}
                </small>
              </div>
              <div>
                <span>
                  예산 차감 {number(e.charged_tokens ?? e.reserved_tokens)}
                </span>
                <small>
                  상위 보고{" "}
                  {e.reported_tokens === null
                    ? "확인 불가"
                    : number(e.reported_tokens)}
                </small>
              </div>
            </div>
          ))
        )}
      </section>
      {usage && !error && (
        <small>
          갱신:{" "}
          {new Date(usage.fetchedAt).toLocaleTimeString("ko-KR", {
            timeZone: "Asia/Seoul",
          })}{" "}
          · 한국 시간
        </small>
      )}
    </div>
  );
}
