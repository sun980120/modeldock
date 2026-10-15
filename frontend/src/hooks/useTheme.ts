"use client";
import { useEffect, useState } from "react";
export type Theme = "system" | "light" | "dark";
export function useTheme() {
  const [theme, setTheme] = useState<Theme>("system");
  useEffect(() => {
    try {
      const saved = localStorage.getItem("modeldock-theme");
      if (saved === "dark" || saved === "light") setTheme(saved);
    } catch {}
  }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try {
      localStorage.setItem("modeldock-theme", theme);
    } catch {}
  }, [theme]);
  return { theme, setTheme };
}
