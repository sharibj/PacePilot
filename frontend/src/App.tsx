import { useEffect, useState } from 'react';
import { createItem, deleteItem, listItems, type Item } from './shared/api/items';
import { sendChat } from './shared/api/chat';

function ItemsPage() {
  const [items, setItems] = useState<Item[]>([]);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const refresh = () =>
    listItems()
      .then(setItems)
      .catch((e: Error) => setError(e.message));

  useEffect(() => {
    refresh();
  }, []);

  const add = async () => {
    if (!name.trim()) return;
    setError(null);
    try {
      await createItem(name.trim());
      setName('');
      refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  const remove = async (id: number) => {
    setError(null);
    try {
      await deleteItem(id);
      refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="card">
      <h2>Items</h2>
      <div className="row">
        <input
          value={name}
          placeholder="New item name"
          onChange={(e) => setName(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && add()}
        />
        <button onClick={add}>Add</button>
      </div>
      {error && <p className="error">{error}</p>}
      <ul>
        {items.map((item) => (
          <li key={item.id}>
            <span>{item.name}</span>
            <button className="link" onClick={() => remove(item.id)}>
              delete
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function ChatBox() {
  const [message, setMessage] = useState('');
  const [reply, setReply] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ask = async () => {
    if (!message.trim()) return;
    setLoading(true);
    setError(null);
    setReply('');
    try {
      const res = await sendChat(message.trim());
      setReply(res.reply);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="card">
      <h2>Chat (via LiteLLM)</h2>
      <textarea
        value={message}
        placeholder="Ask the model something…"
        onChange={(e) => setMessage(e.target.value)}
      />
      <button onClick={ask} disabled={loading}>
        {loading ? 'Thinking…' : 'Send'}
      </button>
      {error && <p className="error">{error}</p>}
      {reply && <p className="reply">{reply}</p>}
    </section>
  );
}

export default function App() {
  return (
    <main className="app">
      <h1>scaffold-fullstack-java</h1>
      <ItemsPage />
      <ChatBox />
    </main>
  );
}
