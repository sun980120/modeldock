"use client";
export default function ErrorPage({ reset }: { reset: () => void }) {
  return (
    <main className="loading-screen">
      <h1>화면을 불러오지 못했습니다.</h1>
      <button onClick={reset}>다시 시도</button>
    </main>
  );
}
