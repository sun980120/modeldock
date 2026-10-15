import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useUsage } from "./useUsage";
class Channel {
  static instances: Channel[] = [];
  onmessage: (() => void) | null = null;
  constructor(public name: string) {
    Channel.instances.push(this);
  }
  postMessage() {
    for (const other of Channel.instances)
      if (other !== this && other.name === this.name) other.onmessage?.();
  }
  close() {
    Channel.instances = Channel.instances.filter((c) => c !== this);
  }
}
afterEach(() => {
  vi.unstubAllGlobals();
  Channel.instances = [];
});
describe("usage revalidation", () => {
  it("refreshes a second tab after reservations and on window focus", async () => {
    let remaining = 1000;
    vi.stubGlobal("BroadcastChannel", Channel);
    const fetch = vi.fn(
      async () =>
        new Response(
          JSON.stringify({
            personal: { remaining },
            fetchedAt: new Date().toISOString(),
          }),
          { status: 200 },
        ),
    );
    vi.stubGlobal("fetch", fetch);
    const first = renderHook(() => useUsage(true)),
      second = renderHook(() => useUsage(true));
    await waitFor(() =>
      expect(second.result.current.usage?.personal.remaining).toBe(1000),
    );
    remaining = 600;
    act(() => first.result.current.changed());
    await waitFor(() =>
      expect(second.result.current.usage?.personal.remaining).toBe(600),
    );
    remaining = 400;
    act(() => window.dispatchEvent(new Event("focus")));
    await waitFor(() =>
      expect(first.result.current.usage?.personal.remaining).toBe(400),
    );
  });
  it("marks lookup failure as unknown without manufacturing exhausted balance", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
    const hook = renderHook(() => useUsage(true));
    await waitFor(() => expect(hook.result.current.error).toBe(true));
    expect(hook.result.current.usage).toBeNull();
  });
});
