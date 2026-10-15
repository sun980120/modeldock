import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Markdown } from "./Markdown";
describe("assistant content", () => {
  it("renders tables and code with isolated scrolling and language labels", () => {
    const { container } = render(
      <Markdown
        content={
          "```typescript\nconst x = 1;\n```\n\n| A | B |\n| - | - |\n| 1 | 2 |"
        }
      />,
    );
    expect(screen.getByText("typescript")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "코드 복사" }),
    ).toBeInTheDocument();
    expect(container.querySelector(".table-scroll table")).not.toBeNull();
  });
  it("does not execute raw HTML or javascript links", () => {
    const { container } = render(
      <Markdown
        content={"<script>alert(1)</script>\n\n[bad](javascript:alert%281%29)"}
      />,
    );
    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector('a[href^="javascript:"]')).toBeNull();
  });
});
