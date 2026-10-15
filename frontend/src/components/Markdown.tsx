"use client";
import { Children, isValidElement, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Check, Copy } from "lucide-react";
function Code({ children }: { children?: React.ReactNode }) {
  const [copied, setCopied] = useState(false),
    [failed, setFailed] = useState(false);
  const child = Children.toArray(children).find(isValidElement) as
    | React.ReactElement<{ className?: string; children?: React.ReactNode }>
    | undefined;
  const language = child?.props.className?.replace("language-", "") || "text";
  async function copy() {
    try {
      await navigator.clipboard.writeText(String(child?.props.children || ""));
      setCopied(true);
      setFailed(false);
    } catch {
      setFailed(true);
    }
  }
  return (
    <div className="code-block">
      <div className="code-heading">
        <span>{language}</span>
        <button onClick={copy} aria-label="코드 복사">
          {copied ? <Check size={14} /> : <Copy size={14} />}{" "}
          {failed ? "복사 실패" : copied ? "복사됨" : "복사"}
        </button>
      </div>
      <pre tabIndex={0}>{children}</pre>
    </div>
  );
}
export function Markdown({ content }: { content: string }) {
  return (
    <div className="markdown">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          pre: ({ children }) => <Code>{children}</Code>,
          table: ({ children }) => (
            <div className="table-scroll" tabIndex={0}>
              <table>{children}</table>
            </div>
          ),
          a: ({ children, href }) => (
            <a href={href} target="_blank" rel="noopener noreferrer">
              {children}
            </a>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
