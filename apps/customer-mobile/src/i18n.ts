import type { Locale } from "@matbakhy/design-tokens";

const ar = {
  language: "English",
  loading: "جاري التحميل…",
  signIn: "تسجيل الدخول",
  signOut: "تسجيل الخروج",
  switchAccount: "تبديل الحساب",
  signedInAs: (name: string) => `أهلًا ${name}`,
  signInFailed: "تعذر تسجيل الدخول. حاول مرة أخرى.",
  sessionEnded: "انتهت الجلسة. سجّل الدخول مرة أخرى.",
  crashTitle: "حصل خطأ غير متوقع.",
  crashRetry: "حاول مرة أخرى",
  configError: "إعدادات التطبيق غير صحيحة.",
};

export type Strings = typeof ar;

const en: Strings = {
  language: "العربية",
  loading: "Loading…",
  signIn: "Sign in",
  signOut: "Sign out",
  switchAccount: "Switch account",
  signedInAs: (name: string) => `Hello ${name}`,
  signInFailed: "Sign-in failed. Please try again.",
  sessionEnded: "Your session ended. Please sign in again.",
  crashTitle: "Something went wrong.",
  crashRetry: "Try again",
  configError: "The app configuration is invalid.",
};

export const strings: Record<Locale, Strings> = { ar, en };
