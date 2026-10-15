"use client";
import { useEffect, useState } from "react";
import {
  Check,
  RefreshCw,
  Shield,
  SlidersHorizontal,
  Users,
} from "lucide-react";
import { api, messageOf, number } from "@/lib/api";
import type { AdminUser, Settings } from "@/lib/types";
export function AdminPanel({ onUsage }: { onUsage: () => void }) {
  const [tab, setTab] = useState<"users" | "fusion">("users"),
    [users, setUsers] = useState<AdminUser[]>([]),
    [settings, setSettings] = useState<Settings | null>(null),
    [models, setModels] = useState<{ id: string; name: string }[]>([]);
  const [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [offset, setOffset] = useState(0);
  async function load(page = offset) {
    try {
      setUsers(await api<AdminUser[]>(`/admin/users?offset=${page}`));
      setError("");
    } catch (e) {
      setError(messageOf(e));
    }
  }
  useEffect(() => {
    void load(offset);
    void api<Settings>("/admin/settings")
      .then(setSettings)
      .catch((e) => setError(messageOf(e)));
  }, [offset]);
  async function action(user: AdminUser, status: string) {
    setBusy(true);
    try {
      await api(`/admin/users/${user.id}`, "PATCH", {
        status,
        dailyLimit: user.dailyLimit,
      });
      await load();
      setNotice("계정 상태를 변경했습니다.");
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }
  async function save(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!settings) return;
    setBusy(true);
    setError("");
    const normalized = {
      ...settings,
      fusion: {
        ...settings.fusion,
        models: settings.fusion.models.map((m) => m.trim()).filter(Boolean),
        judge: settings.fusion.judge.trim(),
      },
    };
    try {
      setSettings(await api<Settings>("/admin/settings", "PUT", normalized));
      setNotice("융합 설정을 저장했습니다. 다음 요청부터 적용됩니다.");
      onUsage();
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }
  function fusion<K extends keyof Settings["fusion"]>(
    key: K,
    value: Settings["fusion"][K],
  ) {
    setSettings((s) =>
      s ? { ...s, fusion: { ...s.fusion, [key]: value } } : s,
    );
  }
  return (
    <div className="admin-panel">
      <div className="tab-bar">
        <button
          aria-pressed={tab === "users"}
          onClick={() => {
            setTab("users");
            setNotice("");
          }}
        >
          <Users size={17} />
          사용자 관리
        </button>
        <button
          aria-pressed={tab === "fusion"}
          onClick={() => {
            setTab("fusion");
            setNotice("");
          }}
        >
          <SlidersHorizontal size={17} />
          융합 설정
        </button>
      </div>
      {error && (
        <p role="alert" className="error-notice">
          {error}
        </p>
      )}
      {notice && (
        <p role="status" className="success-notice">
          <Check size={16} />
          {notice}
        </p>
      )}
      {tab === "users" ? (
        <>
          <div className="panel-intro">
            <p>승인 상태와 이용 예산</p>
            <button
              className="icon-button"
              onClick={() => void load()}
              aria-label="사용자 새로고침"
            >
              <RefreshCw size={17} />
            </button>
          </div>
          <p className="muted small">
            <Shield size={14} /> 관리자는 다른 사용자의 대화에 접근할 수
            없습니다.
          </p>
          <div className="admin-users">
            {users.map((user) => (
              <article className="admin-user" key={user.id}>
                <div>
                  <strong>{user.displayName}</strong>
                  <small>{user.email}</small>
                  <span
                    className={`status-badge status-${user.status.toLowerCase()}`}
                  >
                    {
                      {
                        PENDING: "승인 대기",
                        APPROVED: "승인됨",
                        REJECTED: "거절됨",
                        SUSPENDED: "정지됨",
                      }[user.status]
                    }
                  </span>
                </div>
                <label>
                  일일 토큰 한도
                  <input
                    type="number"
                    min={0}
                    max={1000000000000}
                    value={user.dailyLimit}
                    disabled={user.role === "ADMIN"}
                    onChange={(e) =>
                      setUsers((all) =>
                        all.map((u) =>
                          u.id === user.id
                            ? { ...u, dailyLimit: Number(e.target.value) }
                            : u,
                        ),
                      )
                    }
                  />
                  <small>
                    사용 {number(user.used)} · 예약 {number(user.reserved)}
                  </small>
                </label>
                {user.role === "ADMIN" ? (
                  <span className="neutral-badge">관리자</span>
                ) : (
                  <div className="user-actions">
                    <button
                      disabled={busy}
                      onClick={() => void action(user, "APPROVED")}
                    >
                      승인·한도 저장
                    </button>
                    <button
                      disabled={busy}
                      onClick={() => void action(user, "REJECTED")}
                    >
                      거절
                    </button>
                    <button
                      className="danger-text"
                      disabled={busy}
                      onClick={() => void action(user, "SUSPENDED")}
                    >
                      정지
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
          <div className="pagination">
            <button
              disabled={offset === 0}
              onClick={() => setOffset((o) => Math.max(0, o - 50))}
            >
              이전
            </button>
            <span>{offset / 50 + 1} 페이지</span>
            <button
              disabled={users.length < 50}
              onClick={() => setOffset((o) => o + 50)}
            >
              다음
            </button>
          </div>
        </>
      ) : settings ? (
        <form onSubmit={save} className="settings-form">
          <p>FreeLLMAPI에 실제 전달하는 설정</p>
          <button
            type="button"
            className="secondary-button"
            onClick={() => {
              void api<{ id: string; name: string }[]>("/admin/models")
                .then(setModels)
                .catch((e) => setError(messageOf(e)));
            }}
          >
            사용 가능한 모델 조회
          </button>
          <datalist id="fusion-models">
            {models.map((m) => (
              <option key={m.id} value={m.id}>
                {m.name}
              </option>
            ))}
          </datalist>
          <label>
            융합 참여 모델 · 2~8개
            <textarea
              rows={5}
              required
              placeholder="확인한 모델 ID를 한 줄에 하나씩 입력"
              value={settings.fusion.models.join("\n")}
              onChange={(e) => fusion("models", e.target.value.split("\n"))}
            />
            <small>
              모델 목록:{" "}
              {models.length
                ? models.map((m) => m.id).join(", ")
                : "위 버튼으로 실제 연결된 모델을 확인하세요."}
            </small>
          </label>
          <label>
            최종 합성 모델
            <input
              required
              list="fusion-models"
              value={settings.fusion.judge}
              onChange={(e) => fusion("judge", e.target.value)}
            />
          </label>
          <label>
            공통 최대 출력 토큰
            <input
              type="number"
              min={128}
              max={8192}
              value={settings.fusion.outputTokens}
              onChange={(e) => fusion("outputTokens", Number(e.target.value))}
            />
            <small>
              max_tokens로 모든 참여·합성 호출에 같은 상한을 전달합니다.
            </small>
          </label>
          <div className="settings-divider">ModelDock 내부 통제</div>
          <label>
            전체 생성 제한 시간 · 초
            <input
              type="number"
              min={10}
              max={240}
              value={settings.fusion.timeoutSeconds}
              onChange={(e) => fusion("timeoutSeconds", Number(e.target.value))}
            />
          </label>
          <label>
            서비스 전체 동시 생성 상한
            <input
              type="number"
              min={1}
              max={32}
              value={settings.fusion.concurrency}
              onChange={(e) => fusion("concurrency", Number(e.target.value))}
            />
          </label>
          <label>
            서비스 일일 예산
            <input
              type="number"
              min={0}
              max={1000000000000}
              value={settings.serviceDailyLimit}
              onChange={(e) =>
                setSettings({
                  ...settings,
                  serviceDailyLimit: Number(e.target.value),
                })
              }
            />
          </label>
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={settings.shareServiceBudget}
              onChange={(e) =>
                setSettings({
                  ...settings,
                  shareServiceBudget: e.target.checked,
                })
              }
            />
            사용자에게 서비스 전체 예산 공개
          </label>
          <div className="info-box">
            <p>
              최소 2개 응답과 합성 성공을 서버에서 검증합니다. 최소 성공 수
              변경, 모델별 타임아웃, 모델별 개별 출력 상한은 현재 FreeLLMAPI가
              지원하지 않습니다. 모델별 운영 설정은 저장소의 연동 문서를
              확인하세요.
            </p>
          </div>
          <button className="primary-button" disabled={busy}>
            설정 저장
            <Check size={17} />
          </button>
        </form>
      ) : (
        <p>설정을 불러오는 중…</p>
      )}
    </div>
  );
}
