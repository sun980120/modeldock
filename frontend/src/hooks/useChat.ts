"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { api, failureText, messageOf } from "@/lib/api";
import type { Active, Conversation, Message } from "@/lib/types";
export function useChat(enabled: boolean, onUsage: () => void) {
  const [conversations, setConversations] = useState<Conversation[]>([]),
    [selected, setSelected] = useState<string | null>(null),
    [messages, setMessages] = useState<Message[]>([]);
  const [active, setActive] = useState<Active | null>(null),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(false),
    [sending, setSending] = useState(false),
    [query, setQuery] = useState("");
  const selectedRef = useRef(selected);
  selectedRef.current = selected;
  const usageRef = useRef(onUsage);
  usageRef.current = onUsage;
  const refreshList = useCallback(async () => {
    try {
      setConversations(
        await api<Conversation[]>(
          `/conversations?q=${encodeURIComponent(query)}`,
        ),
      );
    } catch (e) {
      setError(messageOf(e));
    }
  }, [query]);
  const refreshMessages = useCallback(async (id: string) => {
    const data = await api<Message[]>(`/conversations/${id}/messages`);
    if (selectedRef.current === id) setMessages(data);
    return data;
  }, []);
  const select = useCallback(
    async (id: string | null) => {
      selectedRef.current = id;
      setSelected(id);
      setMessages([]);
      setError("");
      window.history.replaceState(
        null,
        "",
        id ? `/?c=${encodeURIComponent(id)}` : "/",
      );
      if (id) {
        setLoading(true);
        try {
          await refreshMessages(id);
        } catch (e) {
          if (selectedRef.current === id) setError(messageOf(e));
        } finally {
          if (selectedRef.current === id) setLoading(false);
        }
      }
    },
    [refreshMessages],
  );
  useEffect(() => {
    if (!enabled) return;
    const t = setTimeout(() => void refreshList(), 250);
    return () => clearTimeout(t);
  }, [enabled, refreshList]);
  useEffect(() => {
    if (!enabled) return;
    const id = new URLSearchParams(window.location.search).get("c");
    if (id && /^[a-f\d-]{36}$/i.test(id)) void select(id);
    void api<Active[]>("/generations/active")
      .then((rows) => {
        if (rows[0]) setActive(rows[0]);
      })
      .catch(() => {});
  }, [enabled, select]);
  useEffect(() => {
    if (!enabled || !active) return;
    const source = new EventSource(
      `/api/conversations/${active.conversationId}/generations/${active.id}/events`,
    );
    let disposed = false;
    const finish = async (status: string, code: string) => {
      source.close();
      if (disposed) return;
      if (status !== "COMPLETED")
        setError(
          failureText[code] ||
            "답변 생성이 완료되지 않았습니다. 직접 재시도해 주세요.",
        );
      try {
        await refreshMessages(active.conversationId);
      } catch (e) {
        setError(messageOf(e));
      }
      setActive(null);
      usageRef.current();
      void refreshList();
    };
    source.addEventListener("accepted", () => {
      if (!disposed) setActive((a) => (a ? { ...a, status: "RESERVED" } : a));
    });
    source.addEventListener("generating", () => {
      if (!disposed) setActive((a) => (a ? { ...a, status: "RUNNING" } : a));
    });
    for (const name of ["completed", "failed"])
      source.addEventListener(name, (e) => {
        const data = JSON.parse((e as MessageEvent).data);
        void finish(data.status, data.code);
      });
    source.onerror = () => {
      void api<{ status: string; errorCode: string }>(
        `/conversations/${active.conversationId}/generations/${active.id}`,
      )
        .then((g) => {
          if (!["RUNNING", "RESERVED"].includes(g.status))
            void finish(g.status, g.errorCode);
        })
        .catch(() => {
          if (!disposed)
            setError(
              "생성 상태에 다시 연결하는 중입니다. 답변을 중복 전송하지 마세요.",
            );
        });
    };
    return () => {
      disposed = true;
      source.close();
    };
    // Only identifiers reconnect the stream. Status transitions keep the same connection.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, active?.id, active?.conversationId, refreshMessages]);
  async function send(content: string) {
    if (!content.trim() || sending || active) return false;
    setSending(true);
    setError("");
    let id = selectedRef.current;
    try {
      if (!id) {
        const conversation = await api<Conversation>("/conversations", "POST");
        id = conversation.id;
        selectedRef.current = id;
        setSelected(id);
        window.history.replaceState(null, "", `/?c=${id}`);
      }
      const generation = await api<{ id: string }>(
        `/conversations/${id}/generations`,
        "POST",
        { requestId: crypto.randomUUID(), content: content.trim() },
      );
      setActive({ id: generation.id, conversationId: id, status: "RESERVED" });
      usageRef.current();
      await refreshMessages(id);
      void refreshList();
      return true;
    } catch (e) {
      setError(messageOf(e));
      return false;
    } finally {
      setSending(false);
    }
  }
  async function stop() {
    if (!active) return;
    try {
      await api(
        `/conversations/${active.conversationId}/generations/${active.id}/cancel`,
        "POST",
      );
      usageRef.current();
    } catch (e) {
      setError(messageOf(e));
    }
  }
  async function rename(id: string, title: string) {
    await api(`/conversations/${id}`, "PATCH", { title });
    await refreshList();
  }
  async function remove(id: string) {
    await api(`/conversations/${id}`, "DELETE");
    if (selectedRef.current === id) await select(null);
    await refreshList();
  }
  async function older() {
    if (!selected || !messages[0]) return;
    try {
      const rows = await api<Message[]>(
        `/conversations/${selected}/messages?before=${messages[0].id}`,
      );
      setMessages((current) => [...rows, ...current]);
      return rows.length;
    } catch (e) {
      setError(messageOf(e));
      return 0;
    }
  }
  async function more() {
    try {
      const rows = await api<Conversation[]>(
        `/conversations?q=${encodeURIComponent(query)}&offset=${conversations.length}`,
      );
      setConversations((current) => [
        ...current,
        ...rows.filter((row) => !current.some((c) => c.id === row.id)),
      ]);
      if (!rows.length) setError("이전 대화가 더 없습니다.");
    } catch (e) {
      setError(messageOf(e));
    }
  }
  return {
    conversations,
    selected,
    messages,
    active,
    error,
    setError,
    loading,
    sending,
    query,
    setQuery,
    select,
    send,
    stop,
    rename,
    remove,
    older,
    more,
    refreshList,
  };
}
