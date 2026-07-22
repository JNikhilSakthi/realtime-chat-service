import { useEffect, useMemo, useRef, useState } from 'react';
import { useChatSocket } from '../hooks/useChatSocket.js';
import { getHistory } from '../api/roomApi.js';

function formatTime(isoString) {
  if (!isoString) return '';
  return new Date(isoString).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

export default function ChatWindow({ roomCode, username, onLeave }) {
  const { messages, connected, sendMessage, sendTyping } = useChatSocket(roomCode, username);
  const [history, setHistory] = useState([]);
  const [draft, setDraft] = useState('');
  const [typingUser, setTypingUser] = useState(null);
  const bottomRef = useRef(null);
  const typingTimeoutRef = useRef(null);

  useEffect(() => {
    getHistory(roomCode, 0, 50)
      .then((page) => setHistory([...page.messages].reverse()))
      .catch(() => setHistory([]));
  }, [roomCode]);

  useEffect(() => {
    const latestTyping = [...messages].reverse().find((m) => m.type === 'TYPING' && m.sender !== username);
    if (latestTyping) {
      setTypingUser(latestTyping.sender);
      clearTimeout(typingTimeoutRef.current);
      typingTimeoutRef.current = setTimeout(() => setTypingUser(null), 3000);
    }
  }, [messages, username]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, history]);

  const displayEvents = useMemo(
    () => [...history, ...messages].filter((m) => m.type !== 'TYPING'),
    [history, messages]
  );

  const handleSubmit = (event) => {
    event.preventDefault();
    sendMessage(draft);
    setDraft('');
  };

  return (
    <div className="chat-window">
      <header className="chat-header">
        <div>
          <strong>{roomCode}</strong>
          <span className={`status-dot ${connected ? 'online' : 'offline'}`} title={connected ? 'Connected' : 'Disconnected'} />
        </div>
        <button onClick={onLeave} className="secondary">
          Leave room
        </button>
      </header>

      <div className="message-list">
        {displayEvents.map((msg, idx) => (
          <div key={idx} className={`message ${msg.type.toLowerCase()} ${msg.sender === username ? 'own' : ''}`}>
            {msg.type === 'CHAT' ? (
              <>
                <span className="sender">{msg.sender}</span>
                <span className="content">{msg.content}</span>
                <span className="timestamp">{formatTime(msg.sentAt)}</span>
              </>
            ) : (
              <span className="system-event">{msg.content}</span>
            )}
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      <div className="typing-indicator">{typingUser ? `${typingUser} is typing…` : ' '}</div>

      <form onSubmit={handleSubmit} className="message-form">
        <input
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            sendTyping();
          }}
          placeholder="Type a message…"
          maxLength={2000}
          disabled={!connected}
        />
        <button type="submit" disabled={!connected || !draft.trim()}>
          Send
        </button>
      </form>
    </div>
  );
}
