"use client";
import {
  ChevronDown,
  LogOut,
  MessageSquare,
  MoreHorizontal,
  Plus,
  Search,
  Settings,
  Shield,
  Sun,
  Moon,
  Monitor,
  ArrowUpRight,
} from "lucide-react";
import { Brand } from "./Brand";
import { number } from "@/lib/api";
import type { Conversation, Usage, User } from "@/lib/types";
import type { Theme } from "@/hooks/useTheme";
export function Sidebar({
  user,
  conversations,
  selected,
  select,
  query,
  setQuery,
  edit,
  remove,
  usage,
  usageError,
  openUsage,
  openAdmin,
  logout,
  theme,
  setTheme,
  more,
}: {
  user: User;
  conversations: Conversation[];
  selected: string | null;
  select: (id: string | null) => void;
  query: string;
  setQuery: (s: string) => void;
  edit: (c: Conversation) => void;
  remove: (c: Conversation) => void;
  usage: Usage | null;
  usageError: boolean;
  openUsage: () => void;
  openAdmin: () => void;
  logout: () => void;
  theme: Theme;
  setTheme: (t: Theme) => void;
  more: () => void;
}) {
  return (
    <aside className="sidebar">
      <div className="sidebar-top">
        <Brand />
        <span className="workspace-label">TEAM WORKSPACE</span>
        <button className="new-chat" onClick={() => select(null)}>
          <Plus size={19} />
          <span>새 대화</span>
          <span className="key-hint">↗</span>
        </button>
        <label className="search-field">
          <Search size={16} />
          <input
            aria-label="대화 검색"
            placeholder="대화 검색"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </label>
      </div>
      <nav className="conversation-nav" aria-label="최근 대화">
        <span className="nav-label">최근 대화</span>
        {conversations.length === 0 ? (
          <p className="empty-history">
            {query ? "검색 결과가 없습니다." : "새로운 대화를 시작해 보세요."}
          </p>
        ) : (
          conversations.map((c) => (
            <div
              className={`conversation-row ${selected === c.id ? "selected" : ""}`}
              key={c.id}
            >
              <button
                className="conversation-link"
                onClick={() => select(c.id)}
                aria-current={selected === c.id ? "page" : undefined}
              >
                <MessageSquare size={15} />
                <span>{c.title}</span>
              </button>
              <details className="conversation-menu">
                <summary aria-label={`${c.title} 메뉴`}>
                  <MoreHorizontal size={17} />
                </summary>
                <div>
                  <button
                    onClick={(e) => {
                      e.currentTarget
                        .closest("details")
                        ?.removeAttribute("open");
                      edit(c);
                    }}
                  >
                    제목 수정
                  </button>
                  <button
                    className="danger-text"
                    onClick={(e) => {
                      e.currentTarget
                        .closest("details")
                        ?.removeAttribute("open");
                      remove(c);
                    }}
                  >
                    삭제
                  </button>
                </div>
              </details>
            </div>
          ))
        )}
        {conversations.length >= 50 && (
          <button className="text-button" onClick={more}>
            대화 더 불러오기
          </button>
        )}
      </nav>
      <div className="sidebar-bottom">
        <button className="usage-summary" onClick={openUsage}>
          <span className="usage-label">
            오늘 남은 토큰
            <ArrowUpRight size={14} />
          </span>
          <strong>
            {usageError
              ? "조회 실패"
              : usage
                ? number(usage.personal.remaining)
                : "불러오는 중…"}
            {usage && !usageError && (
              <small> / {number(usage.personal.limit)}</small>
            )}
          </strong>
          <span className="meter">
            <i
              style={{
                width: `${usageError ? 0 : (usage?.personal.usagePercent ?? 0)}%`,
              }}
            />
          </span>
          <small>매일 오전 12시 초기화 · 한국 시간</small>
        </button>
        <details className="account-menu">
          <summary>
            <span className="avatar">{user.displayName.slice(0, 1)}</span>
            <span>
              <strong>{user.displayName}</strong>
              <small>{user.role === "ADMIN" ? "관리자" : "팀 멤버"}</small>
            </span>
            <ChevronDown size={16} />
          </summary>
          <div className="account-options">
            <button onClick={openUsage}>
              <Settings size={16} />내 사용량
            </button>
            {user.role === "ADMIN" && (
              <button onClick={openAdmin}>
                <Shield size={16} />
                관리자 설정
              </button>
            )}
            <div className="theme-picker" aria-label="테마 선택">
              {(
                [
                  ["system", Monitor, "시스템"],
                  ["light", Sun, "라이트"],
                  ["dark", Moon, "다크"],
                ] as const
              ).map(([value, Icon, label]) => (
                <button
                  key={value}
                  aria-label={`${label} 테마`}
                  aria-pressed={theme === value}
                  onClick={() => setTheme(value)}
                >
                  <Icon size={16} />
                  {label}
                </button>
              ))}
            </div>
            <button onClick={logout}>
              <LogOut size={16} />
              로그아웃
            </button>
          </div>
        </details>
      </div>
    </aside>
  );
}
