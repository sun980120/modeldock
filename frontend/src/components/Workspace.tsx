"use client";
import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import {
  ArrowDown,
  ArrowUpRight,
  Check,
  ChevronDown,
  Code2,
  FileText,
  Layers,
  Lightbulb,
  Menu,
  PenLine,
  Plus,
  RotateCcw,
  Sparkles,
  TrendingUp,
  X,
} from "lucide-react";
import { api, failureText, messageOf, number } from "@/lib/api";
import type { Conversation, User } from "@/lib/types";
import { useChat } from "@/hooks/useChat";
import { useUsage } from "@/hooks/useUsage";
import { useTheme } from "@/hooks/useTheme";
import { Auth, Pending } from "./Auth";
import { Brand } from "./Brand";
import { Sidebar } from "./Sidebar";
import { Modal } from "./Modal";
import { Markdown } from "./Markdown";
import { Composer } from "./Composer";
import { UsagePanel } from "./UsagePanel";
import { AdminPanel } from "./AdminPanel";

export function Workspace() {
  const [user, setUser] = useState<User | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState("");
  const theme = useTheme();
  const refresh = useCallback(async () => {
    try {
      setUser(await api<User>("/auth/me"));
      setError("");
    } catch (e) {
      if (e instanceof Error && "status" in e && e.status === 401) {
        setUser(null);
        setError("");
      } else setError(messageOf(e));
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  useEffect(() => {
    if (user && user.status !== "APPROVED") {
      const t = setInterval(() => void refresh(), 30000);
      return () => clearInterval(t);
    }
  }, [user, refresh]);
  async function logout() {
    try {
      await api("/auth/logout", "POST");
      setUser(null);
      window.history.replaceState(null, "", "/");
    } catch (e) {
      setError(messageOf(e));
    }
  }
  if (loading)
    return (
      <main className="loading-screen">
        <Brand />
        <p>워크스페이스를 불러오는 중…</p>
      </main>
    );
  if (error)
    return (
      <main className="loading-screen">
        <Brand />
        <p role="alert">{error}</p>
        <button onClick={() => void refresh()}>다시 연결</button>
      </main>
    );
  if (!user) return <Auth onLogin={setUser} />;
  if (user.status !== "APPROVED")
    return (
      <Pending
        user={user}
        refresh={() => void refresh()}
        logout={() => void logout()}
      />
    );
  return (
    <SessionWorkspace
      key={user.id}
      user={user}
      logout={() => void logout()}
      {...theme}
    />
  );
}
function SessionWorkspace({
  user,
  logout,
  theme,
  setTheme,
}: { user: User; logout: () => void } & ReturnType<typeof useTheme>) {
  const usage = useUsage(true),
    chat = useChat(true, usage.changed);
  const [drawer, setDrawer] = useState(false),
    [panel, setPanel] = useState<"usage" | "admin" | null>(null),
    [edit, setEdit] = useState<Conversation | null>(null),
    [remove, setRemove] = useState<Conversation | null>(null),
    [title, setTitle] = useState("");
  const [draft, setDraft] = useState(""),
    [jump, setJump] = useState(false),
    [mutation, setMutation] = useState(false),
    [olderAvailable, setOlderAvailable] = useState(true);
  const scroller = useRef<HTMLDivElement>(null),
    stick = useRef(true),
    bottom = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const viewport = window.visualViewport;
    const sync = () => {
      document.documentElement.style.setProperty(
        "--app-height",
        `${viewport?.height ?? window.innerHeight}px`,
      );
      document.documentElement.style.setProperty(
        "--app-top",
        `${viewport?.offsetTop ?? 0}px`,
      );
    };
    sync();
    viewport?.addEventListener("resize", sync);
    viewport?.addEventListener("scroll", sync);
    window.addEventListener("resize", sync);
    return () => {
      viewport?.removeEventListener("resize", sync);
      viewport?.removeEventListener("scroll", sync);
      window.removeEventListener("resize", sync);
      document.documentElement.style.removeProperty("--app-height");
      document.documentElement.style.removeProperty("--app-top");
    };
  }, []);
  useEffect(() => {
    stick.current = true;
    setJump(false);
    setOlderAvailable(true);
  }, [chat.selected]);
  useLayoutEffect(() => {
    if (stick.current && scroller.current)
      scroller.current.scrollTop = scroller.current.scrollHeight;
  }, [chat.messages, chat.active?.status]);
  function select(id: string | null) {
    setDrawer(false);
    stick.current = true;
    void chat.select(id);
  }
  const showUsage = () => {
    setDrawer(false);
    setPanel("usage");
  };
  const selected = chat.conversations.find((c) => c.id === chat.selected);
  const isActive = chat.active?.conversationId === chat.selected;
  async function saveTitle(e: React.FormEvent) {
    e.preventDefault();
    if (!edit) return;
    setMutation(true);
    try {
      await chat.rename(edit.id, title);
      setEdit(null);
    } catch (e) {
      chat.setError(messageOf(e));
    } finally {
      setMutation(false);
    }
  }
  async function deleteConversation() {
    if (!remove) return;
    setMutation(true);
    try {
      await chat.remove(remove.id);
      setRemove(null);
    } catch (e) {
      chat.setError(messageOf(e));
    } finally {
      setMutation(false);
    }
  }
  const sidebar = (
    <Sidebar
      user={user}
      conversations={chat.conversations}
      selected={chat.selected}
      select={select}
      query={chat.query}
      setQuery={chat.setQuery}
      edit={(c) => {
        setDrawer(false);
        setEdit(c);
        setTitle(c.title);
      }}
      remove={(c) => {
        setDrawer(false);
        setRemove(c);
      }}
      usage={usage.usage}
      usageError={usage.error}
      openUsage={showUsage}
      openAdmin={() => {
        setDrawer(false);
        setPanel("admin");
      }}
      logout={logout}
      theme={theme}
      setTheme={setTheme}
      more={() => void chat.more()}
    />
  );
  const examples = [
    {
      Icon: PenLine,
      title: "아이디어를 글로",
      text: "신규 프로젝트 제안서의 구조를 잡아 줘",
    },
    {
      Icon: Code2,
      title: "코드를 더 명확하게",
      text: "코드 리뷰에서 확인해야 할 핵심 항목을 알려 줘",
    },
    {
      Icon: Lightbulb,
      title: "생각에 새로운 관점",
      text: "팀 회의를 더 효율적으로 만드는 아이디어를 제안해 줘",
    },
    {
      Icon: TrendingUp,
      title: "복잡한 내용을 간단히",
      text: "클라우드 비용 최적화 방법을 쉽게 설명해 줘",
    },
  ];
  return (
    <main className="app-shell">
      <div className="desktop-sidebar">{sidebar}</div>
      <section className="chat-workspace" aria-label="대화 공간">
        <header className="chat-header">
          <div className="header-left">
            <button
              className="icon-button mobile-only"
              aria-label="대화 메뉴 열기"
              onClick={() => setDrawer(true)}
            >
              <Menu size={22} />
            </button>
            <span className="header-title">{selected?.title || "새 대화"}</span>
            <ChevronDown className="muted desktop-only" size={15} />
            <span className="fusion-badge">
              <Layers size={13} />
              Fusion
            </span>
          </div>
          <div className="header-right">
            <button className="mobile-balance" onClick={showUsage}>
              {usage.error
                ? "사용량 확인 불가"
                : usage.usage
                  ? `${number(usage.usage.personal.remaining)} 토큰`
                  : "사용량 조회 중"}
            </button>
            <span className="private-label">
              <span className="small-dot" />
              나만의 대화
            </span>
            <button
              className="icon-button"
              aria-label="새 대화"
              onClick={() => select(null)}
            >
              <PenLine size={19} />
            </button>
          </div>
        </header>
        <div
          className="message-scroller"
          ref={scroller}
          onScroll={() => {
            const el = scroller.current;
            if (el) {
              stick.current =
                el.scrollHeight - el.scrollTop - el.clientHeight < 100;
              setJump(!stick.current);
            }
          }}
        >
          {chat.loading ? (
            <div className="conversation-loading" role="status">
              <div />
              <div />
              <div />
              <span>대화를 불러오는 중…</span>
            </div>
          ) : chat.messages.length === 0 ? (
            <div className="welcome">
              <span className="welcome-symbol">
                <Layers size={33} strokeWidth={1.5} />
              </span>
              <span className="eyebrow">
                A FEW PERSPECTIVES. ONE CLEAR ANSWER.
              </span>
              <h1>
                어떤 생각을
                <br className="mobile-only" /> 함께 펼쳐볼까요?
              </h1>
              <p>여러 AI의 관점을 모아, 더 나은 답을 만듭니다.</p>
              <div className="example-grid">
                {examples.map(({ Icon, title, text }) => (
                  <button
                    key={title}
                    onClick={() => {
                      setDraft("");
                      requestAnimationFrame(() => setDraft(text));
                    }}
                  >
                    <Icon size={20} />
                    <strong>{title}</strong>
                    <span>{text}</span>
                    <ArrowUpRight className="example-arrow" size={15} />
                  </button>
                ))}
              </div>
              <div className="welcome-foot">
                <span className="small-dot" />
                항상 융합 모드로 생성됩니다
              </div>
            </div>
          ) : (
            <div className="message-list">
              {chat.messages.length >= 100 && olderAvailable && (
                <button
                  className="text-button older-button"
                  onClick={() => {
                    const el = scroller.current;
                    const top = el?.scrollTop ?? 0,
                      height = el?.scrollHeight ?? 0;
                    void chat.older().then((count) => {
                      if (!count) setOlderAvailable(false);
                      requestAnimationFrame(() => {
                        if (el) el.scrollTop = top + el.scrollHeight - height;
                      });
                    });
                  }}
                >
                  이전 메시지 불러오기
                </button>
              )}
              {chat.messages.map((message) => (
                <article
                  className={`message message-${message.role}`}
                  key={message.id}
                  aria-label={
                    message.role === "user" ? "내 메시지" : "ModelDock 답변"
                  }
                >
                  {message.role === "assistant" && (
                    <div className="assistant-label">
                      <Brand compact />
                      <strong>ModelDock</strong>
                      <span>융합 답변</span>
                    </div>
                  )}
                  <div className="message-content">
                    {message.role === "assistant" ? (
                      <Markdown content={message.content} />
                    ) : (
                      <p>{message.content}</p>
                    )}
                  </div>
                  {message.role === "user" &&
                    !["COMPLETED", "RUNNING", "RESERVED"].includes(
                      message.status,
                    ) && (
                      <div className="generation-failure">
                        <span>
                          {failureText[message.errorCode || ""] ||
                            "답변 생성에 실패했습니다."}
                        </span>
                        <button
                          disabled={!!chat.active || chat.sending}
                          onClick={() => void chat.send(message.content)}
                        >
                          <RotateCcw size={14} />
                          재시도
                        </button>
                      </div>
                    )}
                </article>
              ))}
              {isActive && (
                <div className="generation-progress" role="status">
                  <span className="thinking-dot" />
                  <span>
                    {chat.active?.status === "RESERVED"
                      ? "요청을 접수했습니다"
                      : "답변을 생성하고 있습니다"}
                  </span>
                </div>
              )}
              <div ref={bottom} />
            </div>
          )}
        </div>
        {jump && (
          <button
            className="jump-button"
            onClick={() => {
              stick.current = true;
              scroller.current?.scrollTo({
                top: scroller.current.scrollHeight,
                behavior: "smooth",
              });
              setJump(false);
            }}
          >
            <ArrowDown size={15} />
            최신 메시지로 이동
          </button>
        )}
        {chat.error && (
          <div className="chat-notice" role="alert">
            <span>{chat.error}</span>
            <button
              className="icon-button"
              aria-label="알림 닫기"
              onClick={() => chat.setError("")}
            >
              <X size={17} />
            </button>
          </div>
        )}
        {chat.active && !isActive && (
          <button
            className="active-banner"
            onClick={() => select(chat.active!.conversationId)}
          >
            다른 대화에서 답변 생성 중 · 이동하기
            <ArrowUpRight size={15} />
          </button>
        )}
        <Composer
          send={chat.send}
          stop={() => void chat.stop()}
          busy={chat.sending || !!chat.active}
          active={!!chat.active}
          exhausted={!usage.error && usage.usage?.personal.remaining === 0}
          usageError={usage.error}
          onDraft={draft}
        />
      </section>
      <Modal
        open={drawer}
        onClose={() => setDrawer(false)}
        title="대화 목록"
        drawer
      >
        {sidebar}
      </Modal>
      <Modal
        open={panel !== null}
        onClose={() => setPanel(null)}
        title={panel === "admin" ? "워크스페이스 관리" : "내 사용량"}
      >
        {panel === "admin" && user.role === "ADMIN" ? (
          <AdminPanel onUsage={usage.changed} />
        ) : (
          <UsagePanel
            usage={usage.usage}
            error={usage.error}
            refresh={() => void usage.refresh()}
          />
        )}
      </Modal>
      <Modal open={!!edit} onClose={() => setEdit(null)} title="대화 제목 수정">
        <form className="small-form" onSubmit={saveTitle}>
          <label>
            제목
            <input
              autoFocus
              required
              maxLength={120}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </label>
          <button className="primary-button" disabled={mutation}>
            저장
            <Check size={16} />
          </button>
        </form>
      </Modal>
      <Modal
        open={!!remove}
        onClose={() => setRemove(null)}
        title="대화를 삭제할까요?"
      >
        <div className="small-form">
          <p>
            “{remove?.title}”의 메시지가 삭제됩니다. 삭제한 대화는 복구할 수
            없습니다.
          </p>
          <button
            className="danger-button"
            disabled={mutation}
            onClick={() => void deleteConversation()}
          >
            대화 삭제
          </button>
          <button className="secondary-button" onClick={() => setRemove(null)}>
            취소
          </button>
        </div>
      </Modal>
    </main>
  );
}
