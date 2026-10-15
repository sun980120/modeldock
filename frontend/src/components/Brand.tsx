import { Layers } from "lucide-react";
export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <span className="brand">
      <span className="brand-mark">
        <Layers size={21} strokeWidth={2.1} />
      </span>
      {!compact && (
        <>
          ModelDock
          <span className="brand-dot" />
        </>
      )}
    </span>
  );
}
