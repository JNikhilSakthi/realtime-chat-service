const BASE = '/api/rooms';

async function handle(response) {
  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {
      // response had no JSON body; keep the generic message
    }
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

export async function listRooms() {
  const res = await fetch(BASE);
  return handle(res);
}

export async function createRoom(name, description) {
  const res = await fetch(BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, description })
  });
  return handle(res);
}

export async function getRoom(roomCode) {
  const res = await fetch(`${BASE}/${roomCode}`);
  return handle(res);
}

export async function getHistory(roomCode, page = 0, size = 20) {
  const res = await fetch(`${BASE}/${roomCode}/messages?page=${page}&size=${size}`);
  return handle(res);
}

export async function getParticipants(roomCode) {
  const res = await fetch(`${BASE}/${roomCode}/participants`);
  return handle(res);
}
