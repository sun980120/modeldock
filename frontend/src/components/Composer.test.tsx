import React from "react";
import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { Composer, shouldSendEnter } from "./Composer";
describe("message entry", () => {
  it("sends desktop Enter while retaining newline and IME composition", () => {
    const enter = {
      key: "Enter",
      shiftKey: false,
      isComposing: false,
      keyCode: 13,
    };
    expect(shouldSendEnter(enter, false, false)).toBe(true);
    expect(shouldSendEnter({ ...enter, shiftKey: true }, false, false)).toBe(
      false,
    );
    expect(shouldSendEnter({ ...enter, isComposing: true }, false, false)).toBe(
      false,
    );
    expect(shouldSendEnter({ ...enter, keyCode: 229 }, false, false)).toBe(
      false,
    );
    expect(shouldSendEnter(enter, true, false)).toBe(false);
    expect(shouldSendEnter(enter, false, true)).toBe(false);
  });
  it("requires explicit submit on mobile and does not mistake usage errors for exhaustion", async () => {
    vi.stubGlobal("matchMedia", () => ({ matches: true }));
    const send = vi.fn().mockResolvedValue(true);
    render(
      <Composer
        send={send}
        stop={() => {}}
        busy={false}
        active={false}
        exhausted={false}
        usageError={true}
      />,
    );
    const input = screen.getByLabelText("메시지");
    fireEvent.change(input, { target: { value: "한글 입력" } });
    fireEvent.keyDown(input, { key: "Enter", keyCode: 13 });
    expect(send).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "메시지 전송" }));
    expect(send).toHaveBeenCalledWith("한글 입력");
    expect(input).not.toBeDisabled();
  });
});
