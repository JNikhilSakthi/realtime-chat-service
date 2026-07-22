import { useState } from 'react';
import JoinForm from './components/JoinForm.jsx';
import ChatWindow from './components/ChatWindow.jsx';
import './App.css';

export default function App() {
  const [session, setSession] = useState(null); // { roomCode, username }

  if (!session) {
    return <JoinForm onJoin={(roomCode, username) => setSession({ roomCode, username })} />;
  }

  return (
    <ChatWindow
      roomCode={session.roomCode}
      username={session.username}
      onLeave={() => setSession(null)}
    />
  );
}
