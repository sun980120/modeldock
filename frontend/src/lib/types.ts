export type User = {
  id: string;
  email: string;
  displayName: string;
  role: "ADMIN" | "USER";
  status: "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";
  dailyLimit: number;
};
export type Conversation = { id: string; title: string; updatedAt: string };
export type Message = {
  id: string;
  role: "user" | "assistant";
  content: string;
  generationId: string;
  status: string;
  errorCode: string | null;
};
export type Budget = {
  remaining: number;
  limit: number;
  used: number;
  reserved: number;
  resetsAt: string;
  usagePercent: number;
};
export type Usage = {
  personal: Budget;
  service: Budget | null;
  quality: string;
  providerStatus: string;
  fetchedAt: string;
};
export type Active = { id: string; conversationId: string; status: string };
export type Settings = {
  fusion: {
    models: string[];
    judge: string;
    outputTokens: number;
    timeoutSeconds: number;
    concurrency: number;
  };
  serviceDailyLimit: number;
  shareServiceBudget: boolean;
};
export type AdminUser = User & { used: number; reserved: number };
