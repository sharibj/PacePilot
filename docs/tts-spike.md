# ElevenLabs TTS Spike

This spike validates the smallest end-to-end TTS path:

1. A client connects to backend WebSocket and registers `session_id`.
2. A manual endpoint (standing in for n8n output) receives text.
3. Backend calls ElevenLabs TTS.
4. Backend streams `pacer_audio` message to the registered WebSocket session.

## Backend components

- `backend/src/main/java/com/pacepilot/app/tts/TtsController.java`
- `backend/src/main/java/com/pacepilot/app/tts/TtsService.java`
- `backend/src/main/java/com/pacepilot/app/tts/ElevenLabsSynthesizer.java`
- `backend/src/main/java/com/pacepilot/app/realtime/TelemetryWebSocketHandler.java`

## Manual client

Open `http://localhost:8080/tts-spike.html` after backend starts.

The page:

- Opens WebSocket to `/ws/telemetry`.
- Sends register frame: `{"type":"register","session_id":"..."}`.
- Calls `POST /api/tts/speak`.
- Plays `audio_base64` from incoming `pacer_audio` messages.

## Required environment variables

- `ELEVENLABS_API_KEY`
- `ELEVENLABS_VOICE_ID`

Optional:

- `ELEVENLABS_BASE_URL` (default: `https://api.elevenlabs.io`)
- `ELEVENLABS_MODEL_ID` (default: `eleven_flash_v2_5`)
- `ELEVENLABS_OUTPUT_FORMAT` (default: `mp3_44100_128`)

These map to `pacer.tts.elevenlabs-*` properties read by `ElevenLabsSynthesizer`.

## Manual API test

```json
POST /api/tts/speak
{
  "session_id": "spike-session-1",
  "event_id": "evt-001",
  "text": "Relax your breathing and settle at tempo pace."
}
```

## Notes

- If ElevenLabs fails, backend sends text-only fallback (`audio_base64` is null).
- Endpoint is intentionally manual for spike validation and will be replaced by n8n-triggered flow later.

