export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public status: number,
  ) {
    super(message);
  }
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const headers: Record<string, string> = {};
  if (method !== "GET") {
    const response = await fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
      signal,
    });
    if (!response.ok)
      throw new ApiError(
        "CONNECTION_FAILED",
        "서버의 보안 정보를 불러오지 못했습니다.",
        response.status,
      );
    const csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
    if (body !== undefined) headers["Content-Type"] = "application/json";
  }
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      credentials: "same-origin",
      cache: "no-store",
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(
      "CONNECTION_FAILED",
      "서버에 연결하지 못했습니다. 연결을 확인한 뒤 다시 시도해 주세요.",
      0,
    );
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new ApiError(
      data.code || "REQUEST_FAILED",
      data.message || "요청을 처리하지 못했습니다.",
      response.status,
    );
  return data as T;
}
export const messageOf = (e: unknown) =>
  e instanceof Error ? e.message : "요청을 처리하지 못했습니다.";
export const number = (value: number) =>
  new Intl.NumberFormat("ko-KR").format(value);
export const failureText: Record<string, string> = {
  FUSION_NOT_SYNTHESIZED: "충분한 모델 응답과 최종 합성을 확인하지 못했습니다.",
  FUSION_QUORUM_NOT_MET:
    "참여 모델의 성공 응답이 부족해 융합하지 못했습니다. 잠시 후 재시도해 주세요.",
  FUSION_JUDGE_FAILED:
    "참여 답변을 최종 합성하지 못했습니다. 잠시 후 재시도해 주세요.",
  FUSION_METADATA_MISSING:
    "융합 성공 정보가 응답에 없습니다. 관리자에게 FreeLLMAPI 버전 확인을 요청해 주세요.",
  FUSION_METADATA_INVALID:
    "참여 모델의 응답 개수를 검증하지 못했습니다. 관리자에게 확인을 요청해 주세요.",
  FUSION_UNEXPECTED_RESPONSE:
    "융합 API의 응답 형식이 올바르지 않습니다. 관리자에게 연결 설정 확인을 요청해 주세요.",
  FUSION_RESPONSE_INCOMPLETE:
    "최종 합성 답변이 정상적으로 끝나지 않았습니다. 재시도해 주세요.",
  FUSION_EMPTY_ANSWER: "최종 합성 답변이 비어 있습니다. 재시도해 주세요.",
  FUSION_AUTH_FAILED:
    "FreeLLMAPI 인증에 실패했습니다. 관리자에게 통합 API 키 확인을 요청해 주세요.",
  FUSION_ENDPOINT_NOT_FOUND:
    "FreeLLMAPI 요청 경로를 찾지 못했습니다. 관리자에게 API 주소 확인을 요청해 주세요.",
  FUSION_RATE_LIMITED:
    "상위 모델의 호출 한도에 도달했습니다. 잠시 후 재시도해 주세요.",
  GENERATION_TIMEOUT: "답변 생성 시간이 초과되었습니다.",
  GENERATION_INTERRUPTED: "서버 재시작 등으로 생성이 중단되었습니다.",
  CANCELLED: "답변 생성을 중지했습니다.",
  FUSION_UPSTREAM_FAILED: "융합 답변을 생성하지 못했습니다.",
  UPSTREAM_NOT_CONFIGURED: "관리자의 FreeLLMAPI 연결 설정이 필요합니다.",
};
