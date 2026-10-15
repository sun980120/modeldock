"use client";
import { useEffect, useRef, useState } from "react";
import { ArrowUp, Square, Layers } from "lucide-react";
export function shouldSendEnter(
  event: {
    key: string;
    shiftKey: boolean;
    isComposing: boolean;
    keyCode: number;
  },
  composing: boolean,
  mobile: boolean,
) {
  return (
    event.key === "Enter" &&
    !event.shiftKey &&
    !event.isComposing &&
    event.keyCode !== 229 &&
    !composing &&
    !mobile
  );
}
export function Composer({
  send,
  stop,
  busy,
  active,
  exhausted,
  usageError,
  onDraft,
}: {
  send: (s: string) => Promise<boolean>;
  stop: () => void;
  busy: boolean;
  active: boolean;
  exhausted: boolean;
  usageError: boolean;
  onDraft?: string;
}) {
  const [value, setValue] = useState(""),
    composing = useRef(false),
    input = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    if (onDraft) {
      setValue(onDraft);
      input.current?.focus();
    }
  }, [onDraft]);
  useEffect(() => {
    if (input.current) {
      input.current.style.height = "auto";
      input.current.style.height = `${Math.min(input.current.scrollHeight, 180)}px`;
    }
  }, [value]);
  async function submit() {
    if (value.trim() && !busy && !active && !exhausted) {
      const sent = value;
      if (await send(sent))
        setValue((current) => (current === sent ? "" : current));
    }
  }
  return (
    <div className="composer-region">
      <div className="composer-wrap">
        {exhausted && (
          <p className="error-notice" role="status">
            오늘의 이용 예산을 모두 사용했습니다. 매일 오전 12시 초기화 · 한국
            시간
          </p>
        )}
        {usageError && (
          <p className="subtle-notice" role="status">
            사용량 조회에 실패했습니다. 전송 시 서버에서 가용 예산을 확인합니다.
          </p>
        )}
        <form
          className="composer"
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          <label className="sr-only" htmlFor="message">
            메시지
          </label>
          <textarea
            ref={input}
            id="message"
            placeholder="무엇이든 물어보세요"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            rows={1}
            maxLength={16000}
            disabled={exhausted}
            onCompositionStart={() => {
              composing.current = true;
            }}
            onCompositionEnd={() => {
              composing.current = false;
            }}
            onKeyDown={(e) => {
              if (
                shouldSendEnter(
                  e.nativeEvent,
                  composing.current,
                  window.matchMedia("(pointer: coarse)").matches,
                )
              ) {
                e.preventDefault();
                void submit();
              }
            }}
          />
          <div className="composer-bottom">
            <span className="composer-mode">
              <Layers size={15} />
              융합 모드
              <span className="small-dot" />
            </span>
            {active ? (
              <button
                type="button"
                className="send-button stop-button"
                aria-label="생성 중지"
                onClick={stop}
              >
                <Square size={16} fill="currentColor" />
              </button>
            ) : (
              <button
                className="send-button"
                aria-label="메시지 전송"
                disabled={!value.trim() || busy || exhausted}
              >
                <ArrowUp size={21} />
              </button>
            )}
          </div>
        </form>
        <p className="composer-note">
          여러 모델의 관점을 하나로. 중요한 정보는 한 번 더 확인해 주세요.
        </p>
      </div>
    </div>
  );
}
