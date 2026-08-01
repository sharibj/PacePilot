import { useCallback, useEffect, useRef, useState } from 'react';
import type { ConnectionState, PacerAudioMessage, TelemetryFrame } from './types';

/** Derive the WebSocket base URL from the same env the REST client uses. */
export function resolveSocketBaseUrl(): string {
  const apiUrl = import.meta.env.VITE_API_URL;
  if (apiUrl) {
    try {
      const url = new URL(apiUrl);
      const wsProtocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
      return `${wsProtocol}//${url.host}`;
    } catch {
      // Fall through to the default if VITE_API_URL is malformed.
    }
  }
  return 'ws://localhost:8080';
}

const TELEMETRY_PATH = '/ws/telemetry';

export interface UseTelemetrySocketOptions {
  /** Called for each inbound pacer_audio cue. */
  onCue?: (cue: PacerAudioMessage) => void;
}

export interface TelemetrySocket {
  state: ConnectionState;
  error: string | null;
  connect: () => void;
  disconnect: () => void;
  /** Sends a frame only when the socket is open; returns whether it was sent. */
  send: (frame: TelemetryFrame) => boolean;
}

/**
 * Encapsulates the telemetry WebSocket lifecycle. Kept demo-simple:
 * explicit connect/disconnect, connection-state tracking, and inbound parsing.
 */
export function useTelemetrySocket(options: UseTelemetrySocketOptions = {}): TelemetrySocket {
  const { onCue } = options;
  const [state, setState] = useState<ConnectionState>('closed');
  const [error, setError] = useState<string | null>(null);

  const socketRef = useRef<WebSocket | null>(null);
  // Keep the latest callback without forcing connect() to be recreated.
  const onCueRef = useRef<UseTelemetrySocketOptions['onCue']>(onCue);
  useEffect(() => {
    onCueRef.current = onCue;
  }, [onCue]);

  const disconnect = useCallback(() => {
    const socket = socketRef.current;
    socketRef.current = null;
    if (socket) {
      socket.onopen = null;
      socket.onclose = null;
      socket.onerror = null;
      socket.onmessage = null;
      socket.close();
    }
    setState('closed');
  }, []);

  const connect = useCallback(() => {
    // Tear down any existing socket first so we never leak connections.
    if (socketRef.current) {
      socketRef.current.close();
      socketRef.current = null;
    }

    setError(null);
    setState('connecting');

    const url = `${resolveSocketBaseUrl()}${TELEMETRY_PATH}`;
    let socket: WebSocket;
    try {
      socket = new WebSocket(url);
    } catch (e) {
      setState('error');
      setError((e as Error).message);
      return;
    }
    socketRef.current = socket;

    socket.onopen = () => {
      if (socketRef.current !== socket) return;
      setState('open');
    };

    socket.onerror = () => {
      if (socketRef.current !== socket) return;
      setState('error');
      setError('WebSocket error');
    };

    socket.onclose = () => {
      if (socketRef.current !== socket) return;
      socketRef.current = null;
      setState('closed');
    };

    socket.onmessage = (event: MessageEvent) => {
      if (socketRef.current !== socket) return;
      handleMessage(event.data, onCueRef.current);
    };
  }, []);

  const send = useCallback((frame: TelemetryFrame): boolean => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) return false;
    socket.send(JSON.stringify(frame));
    return true;
  }, []);

  // Clean up on unmount.
  useEffect(() => disconnect, [disconnect]);

  return { state, error, connect, disconnect, send };
}

function handleMessage(data: unknown, onCue: UseTelemetrySocketOptions['onCue']): void {
  if (typeof data !== 'string') return;
  let parsed: unknown;
  try {
    parsed = JSON.parse(data);
  } catch {
    return;
  }
  if (!isPacerAudio(parsed)) return;

  if (parsed.audio_base64) {
    try {
      const audio = new Audio(`data:audio/mpeg;base64,${parsed.audio_base64}`);
      void audio.play().catch(() => {
        /* autoplay may be blocked; the text cue is still shown */
      });
    } catch {
      /* ignore audio construction failures */
    }
  }

  onCue?.(parsed);
}

function isPacerAudio(value: unknown): value is PacerAudioMessage {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as Record<string, unknown>;
  return record.type === 'pacer_audio' && typeof record.text === 'string';
}
