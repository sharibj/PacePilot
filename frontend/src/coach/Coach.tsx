import { useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { api } from '../shared/api/client';

interface Message {
  id: string;
  role: 'user' | 'coach';
  text: string;
}

interface CoachApiResponse {
  reply: string;
}

export default function Coach() {
  // One session id per mounted tab so the Planner's memory threads the conversation.
  const sessionId = useRef(crypto.randomUUID());
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const send = async () => {
    const text = input.trim();
    if (!text || sending) return;

    setError(null);
    setInput('');
    setMessages((prev) => [...prev, { id: crypto.randomUUID(), role: 'user', text }]);
    setSending(true);

    try {
      const res = await api.post<CoachApiResponse>('/coach', {
        message: text,
        sessionId: sessionId.current,
      });
      setMessages((prev) => [...prev, { id: crypto.randomUUID(), role: 'coach', text: res.reply }]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Request failed');
    } finally {
      setSending(false);
    }
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  };

  return (
    <section className="card coach">
      <h2>Coach</h2>
      <div className="coach-log">
        {messages.length === 0 && (
          <p className="coach-empty">
            Ask your running coach anything, e.g. "What are my next three runs?"
          </p>
        )}
        {messages.map((m) => (
          <div key={m.id} className={`coach-msg coach-msg-${m.role}`}>
            {m.role === 'coach' ? (
              <div className="coach-markdown">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.text}</ReactMarkdown>
              </div>
            ) : (
              m.text
            )}
          </div>
        ))}
        {sending && (
          <div className="coach-msg coach-msg-coach coach-thinking">Coach is thinking…</div>
        )}
      </div>
      {error && <p className="coach-error">{error}</p>}
      <div className="row">
        <input
          type="text"
          value={input}
          placeholder="Message your coach…"
          disabled={sending}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <button onClick={send} disabled={sending || !input.trim()}>
          {sending ? 'Sending…' : 'Send'}
        </button>
      </div>
    </section>
  );
}
