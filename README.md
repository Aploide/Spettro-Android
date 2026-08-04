# Spettro for Android

A native Android client for the **`spettro` AI coding agent** — a Kotlin port
of the iOS companion app (`SpettroMobile`). The app ships no AI itself: it is
a remote control and transcript view for an agent that runs elsewhere.

UI is **Jetpack Compose with Material 3 Expressive**; package root is
`to.eyed.spettro.mobile`. minSdk 33, targetSdk 36, Kotlin + coroutines
throughout (no RxJava, no Hilt — manual DI via `AppContainer`).

---

## What this app is, in one paragraph

**Spettro for Android** talks to a `spettro` agent host and renders
everything it emits: chats, streamed tokens, tool calls, diffs, plans,
permission prompts, and ask-user questions. It pairs with the **Spettro
desktop app** on your PC — Linux, macOS, or Windows — over the local network
(Bonjour discovery, QR-code pairing, WebSocket + JSON-RPC 2.0) and mirrors
its chat list; the PC owns the agent. Every prompt, config change, and
permission reply is a request; every chat update is a notification. The app
owns the display transcript, the composer, and the sheets; the agent owns
the model, the tool loop, and the file edits.

---

## The connection

| | **Spettro Remote** (Protocol B) |
|---|---|
| Connects to | Spettro desktop app acting as host |
| Transport | WebSocket, JSON-RPC 2.0 (symmetric peer) |
| Discovery | Bonjour `_spettro-remote._tcp` via `NsdManager` |
| Auth | QR pair-once, HMAC-SHA256 challenge/response, durable device key |
| Surface | Full chat list, projects, multi-session, config, permissions, questions |
| Spec | `../Spettro/docs/34-remote-protocol.md` |

The companion-app pairing is the only supported connection: the durable,
HMAC-authenticated pairing is safer than exposing a raw agent endpoint, so
there is deliberately no direct/headless CLI mode.

`RemoteClient` exposes a `StateFlow<RemoteState>` state machine
(`Unpaired → Searching → Connecting → Connected`, plus `Offline` with a
typed reason), a diagnostics log, and `RemoteApi` typed wrappers for every
host request (`chatsList`, `chatsOpen`, `prompt`, `cancel`, `config`,
`permissionReply`, `questionReply`, `agentCall`, …). Raw ACP payloads cross
module boundaries as `JsonObject` — the protocol layers never parse ACP
content; `core.acp` does.

Failed connections retry on their own: exponential backoff from 1s to 30s,
reset the moment anything succeeds, the app comes to the foreground, or
Bonjour sees the host reappear. While the app is offline, the disconnected
screen keeps a "Forget This PC" escape hatch so a stale pairing can always
be discarded.

## Project layout

```
app/src/main/java/to/eyed/spettro/mobile/
├── MainActivity.kt            entry point; starts/stops the client with lifecycle
├── coordinator/               manual DI + app-wide coordinators
│   ├── AppContainer.kt        service locator (the "DI graph")
│   └── remote/MobileModel.kt  root coordinator: mirrors the host's state
├── core/
│   ├── SpettroJson.kt, B64.kt shared Json instance + base64url/std helpers
│   ├── rpc/                   JsonRpcPeer — symmetric JSON-RPC 2.0 over OkHttp WS
│   ├── remote/                Protocol B: RemoteClient, RemoteApi, discovery (NSD),
│   │                          pairing (QR + HMAC), credential store, notifications
│   └── acp/                   ACP payload parsing: session updates, config options
│                              (both wire shapes), permission/question requests,
│                              StoredSession decode, extension types
├── model/                     ChatSession transcript state holder, TranscriptItem,
│                              ToolCallItem, image attachments
└── ui/
    ├── theme/                 Spettro colors/type — M3 Expressive with Spettro tokens
    ├── components/            shared composables (cards, chips, badges, spinners…)
    ├── screens/
    │   ├── home/              PairingScreen, QR scanner, ChatListScreen,
    │   │                      DisconnectedScreen, project picker
    │   ├── chat/              ChatScreen, composer, transcript rendering,
    │   │                      tool-call rows, markdown, config sheet, status strip
    │   ├── settings/          SettingsScreen, ProvidersScreen
    │   └── sheets/            PermissionSheet, QuestionSheet
    └── AppRoot.kt             routes on RemoteState; hosts global sheets
```

The architecture contract the modules were built against lives in
[`DESIGN.md`](./DESIGN.md) — package ownership, module APIs, wire-format
gotchas, and the design tokens.

## Building and running

Prerequisites: **Android Studio** (recent stable) with a JDK 11+ toolchain,
an emulator or device on **Android 13+ (API 33)**.

```bash
./gradlew assembleDebug      # build
./gradlew installDebug       # install on a connected device/emulator
./gradlew testDebugUnitTest  # JVM unit tests
```

To actually use the app you need the Spettro desktop app running on a PC on
the same network with remote hosting enabled — scan its pairing QR code from
the app's pairing screen (or paste the `spettro-pair://` URL manually).

## Testing

JVM unit tests (`app/src/test`) cover the parts that must not drift:

- **Pairing** — HMAC-SHA256 proof vector, `spettro-pair://` QR payload parse
- **JSON-RPC** — framing and round-trips in `JsonRpcPeer` (MockWebServer)
- **ACP parsing** — both config-option wire shapes (ACP-native and the
  Swift-synthesized `StoredSession` shape), `StoredSession` decode, session
  updates, permission/question requests, extension types
- **Model** — `ChatSession` transcript mutation and replay suppression,
  `ToolCallItem` title parsing

End-to-end verification is manual: the Spettro desktop app hosting on a PC
plus this app on a device.

## Conventions

- All JSON goes through `core.SpettroJson` (`ignoreUnknownKeys`,
  `explicitNulls = false`). Key spellings are copied exactly from the wire:
  `hostID`/`chatID`/`deviceID` are capital-ID; `sessionId`/`toolCallId` are not.
- base64url **unpadded** for pairing material; standard padded base64 for
  image attachments.
- Async state is coroutines + `StateFlow`/`SharedFlow` everywhere.
- Design tokens mirror the desktop design system: accent `#526FFF` light /
  `#6073CC` dark, canvas `#F9F9F7` / `#0E0E0E`, hairline borders instead of
  shadows, user bubble = solid accent, assistant = full-width prose.

## Related repositories

- `../Spettro` — the desktop app (and iOS companion) this is a port of; its
  `docs/` folder is the protocol and design-system reference
- `../spettro-CLI` — the agent itself
