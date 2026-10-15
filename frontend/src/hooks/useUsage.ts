"use client";
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Usage } from "@/lib/types";
export function useUsage(enabled: boolean) {
  const [usage, setUsage] = useState<Usage | null>(null),
    [error, setError] = useState(false);
  const refresh = useCallback(async () => {
    if (!enabled) return;
    try {
      setUsage(await api<Usage>("/usage"));
      setError(false);
    } catch {
      setError(true);
    }
  }, [enabled]);
  useEffect(() => {
    if (!enabled) {
      setUsage(null);
      return;
    }
    void refresh();
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") void refresh();
    }, 30000);
    const focus = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    window.addEventListener("focus", focus);
    document.addEventListener("visibilitychange", focus);
    const channel =
      typeof BroadcastChannel !== "undefined"
        ? new BroadcastChannel("modeldock-usage")
        : null;
    if (channel) channel.onmessage = () => void refresh();
    return () => {
      clearInterval(timer);
      window.removeEventListener("focus", focus);
      document.removeEventListener("visibilitychange", focus);
      channel?.close();
    };
  }, [enabled, refresh]);
  const changed = useCallback(() => {
    void refresh();
    if (typeof BroadcastChannel !== "undefined") {
      const c = new BroadcastChannel("modeldock-usage");
      c.postMessage("changed");
      c.close();
    }
  }, [refresh]);
  return { usage, error, refresh, changed };
}
