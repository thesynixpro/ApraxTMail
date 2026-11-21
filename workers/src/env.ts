export interface Env {
  DB: D1Database;
  MAIL_BUCKET: R2Bucket;
  JWT_SECRET: string;
  WEBHOOK_SECRET?: string;
  EMAILIT_API_KEY?: string;
  EMAILIT_API_URL: string;
  PUBLIC_BASE_URL: string;
  /** Days to keep messages before cron deletes them (default 30). */
  MESSAGE_TTL_DAYS?: string;
}

export interface AuthUser {
  id: string;
  email: string;
}

declare module 'hono' {
  interface ContextVariableMap {
    user: AuthUser;
  }
}
