import { useEffect, useState } from 'react';
import { createRoom, listRooms } from '../api/roomApi.js';

export default function JoinForm({ onJoin }) {
  const [rooms, setRooms] = useState([]);
  const [username, setUsername] = useState('');
  const [selectedRoomCode, setSelectedRoomCode] = useState('');
  const [newRoomName, setNewRoomName] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshRooms = () => {
    listRooms()
      .then(setRooms)
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  };

  useEffect(refreshRooms, []);

  const handleCreateRoom = async (event) => {
    event.preventDefault();
    if (!newRoomName.trim()) {
      return;
    }
    try {
      const room = await createRoom(newRoomName.trim(), '');
      setNewRoomName('');
      setRooms((prev) => [...prev, room]);
      setSelectedRoomCode(room.roomCode);
    } catch (err) {
      setError(err.message);
    }
  };

  const handleJoin = (event) => {
    event.preventDefault();
    if (!username.trim() || !selectedRoomCode) {
      setError('Pick a display name and a room first.');
      return;
    }
    onJoin(selectedRoomCode, username.trim());
  };

  return (
    <div className="join-card">
      <h1>Realtime Chat</h1>
      <p className="subtitle">Spring Boot WebSocket (STOMP over SockJS) demo</p>

      {error && <div className="error-banner">{error}</div>}

      <form onSubmit={handleJoin} className="join-form">
        <label>
          Display name
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="e.g. alice"
            maxLength={50}
          />
        </label>

        <label>
          Room
          <select value={selectedRoomCode} onChange={(e) => setSelectedRoomCode(e.target.value)}>
            <option value="" disabled>
              {loading ? 'Loading rooms…' : 'Select a room'}
            </option>
            {rooms.map((room) => (
              <option key={room.roomCode} value={room.roomCode}>
                {room.name} ({room.roomCode}) · {room.onlineUsers} online
              </option>
            ))}
          </select>
        </label>

        <button type="submit">Join room</button>
      </form>

      <form onSubmit={handleCreateRoom} className="create-room-form">
        <input
          value={newRoomName}
          onChange={(e) => setNewRoomName(e.target.value)}
          placeholder="New room name"
          maxLength={100}
        />
        <button type="submit">Create room</button>
        <button type="button" onClick={refreshRooms} className="secondary">
          Refresh
        </button>
      </form>
    </div>
  );
}
