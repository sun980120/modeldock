import { test, expect, type Page } from "@playwright/test";
const conversation = "10000000-0000-0000-0000-000000000001",
  generation = "20000000-0000-0000-0000-000000000001";
async function fixture(
  page: Page,
  options: { signedIn?: boolean; role?: string; long?: boolean } = {},
) {
  const state = {
    signedIn: options.signedIn ?? true,
    status: "APPROVED",
    generation: "RUNNING",
    content: "",
    registered: false,
  };
  const budget = {
    remaining: 84200,
    limit: 100000,
    used: 12000,
    reserved: 3800,
    usagePercent: 15.8,
    resetsAt: "2026-10-16T00:00:00+09:00",
  };
  const long =
    '## 긴 답변\n\n```typescript\nconst longValue = "' +
    "x".repeat(3000) +
    '";\n```\n\n| A | B | C | D | E |\n| - | - | - | - | - |\n| 긴 표 내용 | 설명 | 값 | 상세 내용 | 결과 |\n\nhttps://example.test/' +
    "longurl".repeat(200);
  const user = () => ({
    id: "user-a",
    email: "member@example.test",
    displayName: "홍우성",
    role: options.role ?? "USER",
    status: state.status,
    dailyLimit: 100000,
  });
  await page.route("**/api/**", async (route) => {
    const req = route.request(),
      path = new URL(req.url()).pathname,
      method = req.method();
    let body: unknown = {};
    if (path === "/api/auth/csrf")
      body = { headerName: "X-CSRF-TOKEN", token: "test" };
    else if (path === "/api/auth/register") {
      state.registered = true;
      state.status = "PENDING";
      body = user();
    } else if (path === "/api/auth/login") {
      state.signedIn = true;
      body = user();
    } else if (path === "/api/auth/me") {
      if (!state.signedIn) {
        await route.fulfill({
          status: 401,
          json: { code: "UNAUTHENTICATED", message: "로그인이 필요합니다." },
        });
        return;
      }
      body = user();
    } else if (path === "/api/auth/logout") {
      state.signedIn = false;
      body = { ok: true };
    } else if (path === "/api/usage")
      body = {
        personal: budget,
        service: null,
        quality: "PARTIAL",
        providerStatus: "UNKNOWN",
        fetchedAt: new Date().toISOString(),
      };
    else if (path === "/api/usage/requests") body = [];
    else if (path === "/api/generations/active") body = [];
    else if (path === "/api/conversations")
      body =
        method === "POST"
          ? {
              id: conversation,
              title: "새 대화",
              updatedAt: new Date().toISOString(),
            }
          : [
              {
                id: conversation,
                title: "프로젝트 아키텍처 검토",
                updatedAt: new Date().toISOString(),
              },
            ];
    else if (path.endsWith("/messages"))
      body = options.long
        ? [
            {
              id: "m1",
              role: "user",
              content: "자세히 설명해 줘",
              generationId: generation,
              status: "COMPLETED",
              errorCode: null,
            },
            {
              id: "m2",
              role: "assistant",
              content: long,
              generationId: generation,
              status: "COMPLETED",
              errorCode: null,
            },
          ]
        : state.content
          ? [
              {
                id: "m1",
                role: "user",
                content: state.content,
                generationId: generation,
                status: state.generation,
                errorCode:
                  state.generation === "CANCELLED" ? "CANCELLED" : null,
              },
            ]
          : [];
    else if (path.endsWith("/generations") && method === "POST") {
      state.content = req.postDataJSON().content;
      state.generation = "RUNNING";
      body = { id: generation, status: "RESERVED" };
    } else if (path.endsWith("/cancel")) {
      state.generation = "CANCELLED";
      body = { ok: true };
    } else if (path.endsWith("/events")) {
      await route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: `event: ${state.generation === "CANCELLED" ? "failed" : "generating"}\ndata: ${JSON.stringify({ status: state.generation, id: generation, code: state.generation === "CANCELLED" ? "CANCELLED" : "" })}\n\n`,
      });
      return;
    } else if (path.includes("/generations/"))
      body = {
        id: generation,
        status: state.generation,
        errorCode: state.generation === "CANCELLED" ? "CANCELLED" : null,
      };
    else if (path === "/api/admin/users")
      body = [
        {
          ...user(),
          id: "pending-user",
          role: "USER",
          status: "PENDING",
          used: 0,
          reserved: 0,
        },
      ];
    else if (path.startsWith("/api/admin/users/")) body = { ok: true };
    else if (path === "/api/admin/settings")
      body = {
        fusion: {
          models: ["a", "b"],
          judge: "judge",
          outputTokens: 1024,
          timeoutSeconds: 120,
          concurrency: 4,
        },
        serviceDailyLimit: 10000000,
        shareServiceBudget: false,
      };
    await route.fulfill({ json: body });
  });
  return state;
}
for (const width of [360, 390, 768, 1280])
  test(`layout and long content at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 844 });
    await fixture(page, { long: true });
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "어떤 생각을 함께 펼쳐볼까요?" }),
    ).toBeVisible();
    await page.screenshot({
      path: `test-results/welcome-${width}.png`,
      fullPage: true,
    });
    if (width < 768)
      await page.getByRole("button", { name: "대화 메뉴 열기" }).click();
    await page
      .getByRole("button", { name: "프로젝트 아키텍처 검토", exact: true })
      .filter({ visible: true })
      .click();
    await expect(page.getByText("긴 답변", { exact: true })).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    const box = await page.getByLabel("메시지", { exact: true }).boundingBox();
    expect(box!.x + box!.width).toBeLessThanOrEqual(width);
    await page.screenshot({
      path: `test-results/modeldock-${width}.png`,
      fullPage: true,
    });
    if (width < 768)
      await expect(
        page.getByRole("dialog", { name: "대화 목록" }),
      ).not.toBeVisible();
  });
test("drawer traps focus and returns focus; theme persists", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await fixture(page);
  await page.goto("/");
  const menu = page.getByRole("button", { name: "대화 메뉴 열기" });
  await menu.click();
  const drawer = page.getByRole("dialog", { name: "대화 목록" });
  await expect(drawer).toBeVisible();
  for (let i = 0; i < 15; i++) {
    await page.keyboard.press("Tab");
    expect(
      await drawer.evaluate((el) => el.contains(document.activeElement)),
    ).toBe(true);
  }
  await page.keyboard.press("Escape");
  await expect(menu).toBeFocused();
  await menu.click();
  await drawer.locator(".account-menu summary").click();
  await drawer.getByRole("button", { name: "다크 테마" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await page.keyboard.press("Escape");
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
});
test("mobile signup, pending approval, chat and stop", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const state = await fixture(page, { signedIn: false });
  await page.goto("/");
  await page.getByRole("button", { name: "회원가입", exact: true }).click();
  await page.getByLabel("이름", { exact: true }).fill("테스트 사용자");
  await page.getByLabel("이메일", { exact: true }).fill("member@example.test");
  await page.getByLabel("비밀번호", { exact: true }).fill("test-password-123");
  await page.getByRole("button", { name: "가입하고 승인 요청" }).click();
  await expect(
    page.getByRole("heading", { name: "승인을 기다리고 있어요" }),
  ).toBeVisible();
  state.status = "APPROVED";
  await page.getByRole("button", { name: "승인 상태 확인" }).click();
  await page.getByLabel("메시지", { exact: true }).fill("융합 테스트");
  await page.getByRole("button", { name: "메시지 전송" }).click();
  await expect(page.getByRole("button", { name: "생성 중지" })).toBeVisible();
  await page.getByRole("button", { name: "생성 중지" }).click();
  await expect(
    page.getByRole("button", { name: "재시도", exact: true }),
  ).toBeVisible({ timeout: 10000 });
});
test("mobile administrator can approve an account", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 780 });
  await fixture(page, { role: "ADMIN" });
  await page.goto("/");
  await page.getByRole("button", { name: "대화 메뉴 열기" }).click();
  const drawer = page.getByRole("dialog", { name: "대화 목록" });
  await drawer.locator(".account-menu summary").click();
  await drawer.getByRole("button", { name: "관리자 설정" }).click();
  await expect(
    page.getByRole("dialog", { name: "워크스페이스 관리" }),
  ).toBeVisible();
  const request = page.waitForRequest(
    (r) => r.method() === "PATCH" && r.url().includes("/admin/users/"),
  );
  await page.getByRole("button", { name: "승인·한도 저장" }).click();
  expect((await request).postDataJSON().status).toBe("APPROVED");
});
test("landscape and scrolled-up conversation retain layout", async ({
  page,
}) => {
  await page.setViewportSize({ width: 844, height: 390 });
  await fixture(page, { long: true });
  await page.goto(`/?c=${conversation}`);
  await expect(page.getByText("긴 답변", { exact: true })).toBeVisible();
  await page.locator(".message-scroller").evaluate((el) => {
    el.scrollTop = 0;
    el.dispatchEvent(new Event("scroll"));
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await expect(page.getByRole("button", { name: "메시지 전송" })).toBeVisible();
});
