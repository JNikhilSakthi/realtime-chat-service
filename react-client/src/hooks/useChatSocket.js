import { useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * Owns the STOMP-over-SockJS connection lifecycle for a single chat room.
 *
 * Connects once per (roomCode, username) pair, subscribes to /topic/room/{roomCode}, sends the
 * JOIN frame on connect, and exposes sendMessage/sendTyping helpers plus the rolling message log
 * and connection status for the UI to render.
 */
export function useChatSocket(roomCode, username) {
  const [messages, setMessages] = useState([]);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    if (!roomCode || !username) {
      return undefined;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 4000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/room/${roomCode}`, (frame) => {
          const body = JSON.parse(frame.body);
          setMessages((prev) => [...prev, body]);
        });
        client.publish({
          destination: `/app/chat.addUser/${roomCode}`,
          body: JSON.stringify({ type: 'JOIN', sender: username, content: '' })
        });
      },
      onDisconnect: () => setConnected(false),
      onWebSocketClose: () => setConnected(false)
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [roomCode, username]);

  const sendMessage = useCallback(
    (content) => {
      if (!clientRef.current?.connected || !content.trim()) {
        return;
      }
      clientRef.current.publish({
        destination: `/app/chat.sendMessage/${roomCode}`,
        body: JSON.stringify({ type: 'CHAT', sender: username, content })
      });
    },
    [roomCode, username]
  );

  const sendTyping = useCallback(() => {
    if (!clientRef.current?.connected) {
      return;
    }
    clientRef.current.publish({
      destination: `/app/chat.typing/${roomCode}`,
      body: JSON.stringify({ type: 'TYPING', sender: username, content: '' })
    });
  }, [roomCode, username]);

  return { messages, connected, sendMessage, sendTyping };
}
