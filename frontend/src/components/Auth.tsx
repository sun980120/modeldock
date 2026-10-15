"use client";
import { useState } from "react";
import { ArrowRight, Layers, ShieldCheck, Sparkles } from "lucide-react";
import { Brand } from "./Brand";
import { api, messageOf } from "@/lib/api";
import type { User } from "@/lib/types";
export function Auth({ onLogin }: { onLogin: (u: User) => void }) {
  const [signup, setSignup] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(e.currentTarget);
    const credentials = {
      email: String(data.get("email")),
      password: String(data.get("password")),
    };
    try {
      if (signup)
        await api("/auth/register", "POST", {
          ...credentials,
          displayName: String(data.get("name")),
        });
      onLogin(await api<User>("/auth/login", "POST", credentials));
    } catch (e) {
      setError(messageOf(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-page">
      <div className="auth-visual">
        <Brand />
        <div className="auth-pitch">
          <span className="eyebrow">YOUR TEAM’S AI WORKSPACE</span>
          <h1>
            다양한 관점이 모여,
            <br />더 명확한 답으로.
          </h1>
          <p>
            여러 AI 모델의 지식을 하나로 모으는
            <br />
            우리 팀의 새로운 작업 공간.
          </p>
          <div className="fusion-art" aria-hidden="true">
            <span>
              <Layers />
            </span>
            <i />
            <span className="fusion-center">
              <Sparkles size={32} />
            </span>
            <i />
            <span>
              <Layers />
            </span>
          </div>
          <span className="auth-caption">여러 모델 · 하나의 완성된 답변</span>
        </div>
        <small>ModelDock은 독립적인 AI 서비스입니다.</small>
      </div>
      <section className="auth-form-panel">
        <div className="mobile-brand">
          <Brand />
        </div>
        <form className="auth-form" onSubmit={submit}>
          <span className="eyebrow">WELCOME TO MODELDOCK</span>
          <h2>{signup ? "함께 시작해 볼까요?" : "다시 만나 반갑습니다"}</h2>
          <p>
            {signup
              ? "계정을 만든 후 관리자의 승인을 기다려 주세요."
              : "로그인하고 생각의 다음 단계를 이어가세요."}
          </p>
          {signup && (
            <label>
              이름
              <input name="name" autoComplete="name" required maxLength={60} />
            </label>
          )}
          <label>
            이메일
            <input
              name="email"
              type="email"
              autoComplete="username"
              placeholder="name@company.com"
              required
              maxLength={254}
            />
          </label>
          <label>
            비밀번호
            <input
              name="password"
              type="password"
              autoComplete={signup ? "new-password" : "current-password"}
              required
              minLength={signup ? 12 : 1}
              maxLength={72}
            />
          </label>
          {signup && <small>12자 이상, UTF-8 기준 최대 72바이트</small>}
          {error && (
            <p className="error-notice" role="alert">
              {error}
            </p>
          )}
          <button className="primary-button" disabled={busy}>
            {busy ? "처리 중…" : signup ? "가입하고 승인 요청" : "로그인"}
            <ArrowRight size={18} />
          </button>
          <p className="auth-switch">
            {signup ? "이미 계정이 있으신가요?" : "처음 방문하셨나요?"}
            <button
              type="button"
              className="text-button"
              onClick={() => {
                setSignup(!signup);
                setError("");
              }}
            >
              {signup ? "로그인" : "회원가입"}
            </button>
          </p>
          <small className="privacy-note">
            <ShieldCheck size={15} />
            대화는 본인만 볼 수 있습니다. 관리자도 열람할 수 없습니다.
          </small>
        </form>
      </section>
    </main>
  );
}
export function Pending({
  user,
  refresh,
  logout,
}: {
  user: User;
  refresh: () => void;
  logout: () => void;
}) {
  return (
    <main className="pending-page">
      <Brand />
      <div className="pending-card">
        <span className="large-symbol">
          <ShieldCheck size={32} />
        </span>
        <span className="eyebrow">ACCOUNT STATUS</span>
        <h1>
          {user.status === "PENDING"
            ? "승인을 기다리고 있어요"
            : "계정 상태를 확인해 주세요"}
        </h1>
        <p>
          {user.displayName}님,{" "}
          {user.status === "PENDING"
            ? "관리자가 가입을 승인하면 ModelDock을 이용할 수 있습니다."
            : "계정의 이용이 제한되어 있습니다. 관리자에게 문의해 주세요."}
        </p>
        <button className="primary-button" onClick={refresh}>
          승인 상태 확인
          <ArrowRight size={18} />
        </button>
        <button className="text-button" onClick={logout}>
          로그아웃
        </button>
      </div>
    </main>
  );
}
