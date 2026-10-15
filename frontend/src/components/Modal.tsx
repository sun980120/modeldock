"use client";
import { useEffect, useRef } from "react";
import { X } from "lucide-react";
export function Modal({
  open,
  onClose,
  title,
  children,
  drawer = false,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  drawer?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null),
    close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      const previous = document.activeElement as HTMLElement | null;
      dialog.showModal();
      const closed = () => {
        close.current();
        previous?.focus();
      };
      dialog.addEventListener("close", closed, { once: true });
      return () => {
        dialog.removeEventListener("close", closed);
        dialog.close();
        previous?.focus();
      };
    }
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      className={drawer ? "modal drawer-modal" : "modal"}
      aria-label={title}
      onCancel={onClose}
      onKeyDown={(e) => {
        if (e.key !== "Tab") return;
        const focusable = Array.from(
          e.currentTarget.querySelectorAll<HTMLElement>(
            'button:not([disabled]),a[href],input:not([disabled]),textarea:not([disabled]),select:not([disabled]),summary,[tabindex]:not([tabindex="-1"])',
          ),
        ).filter((el) => {
          if (!el.getClientRects().length) return false;
          const closed = el.closest("details:not([open])");
          return !closed || el === closed.querySelector("summary");
        });
        e.preventDefault();
        if (!focusable.length) return;
        const index = focusable.indexOf(document.activeElement as HTMLElement);
        const next =
          index < 0
            ? 0
            : (index + (e.shiftKey ? -1 : 1) + focusable.length) %
              focusable.length;
        focusable[next].focus();
      }}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal-inner">
        <div className="modal-heading">
          <h2>{title}</h2>
          <button className="icon-button" aria-label="닫기" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        {open ? children : null}
      </div>
    </dialog>
  );
}
