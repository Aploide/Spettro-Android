# Spettro for Android

A native Android client for the **`spettro` AI coding agent** — a Kotlin port
of the iOS companion app (`SpettroMobile`), with an extra direct-CLI mode on
top. The app ships no AI itself: it is a remote control and transcript view
for an agent that runs elsewhere.

UI is **Jetpack Compose with Material 3 Expressive**; package root is
`to.eyed.spettro.mobile`. minSdk 33, targetSdk 36, Kotlin + coroutines
throughout (no RxJava, no Hilt — manual DI via `AppContainer`).

---

## What this app is, in one paragraph

**Spettro for Android** talks to a `spettro` agent over one of two wire
protocols and renders everything it emits: chats, streamed tokens, tool
calls, diffs, plans, permission prompts, and ask-user questions. In the
primary mode it pairs with the **Spettro macOS app** over the local network
(Bonjour discovery, QR-code pairing, WebSocket + JSON-RPC 2.0) and mirrors
its chat list — the Mac owns the agent. In the second mode it connects
**directly to a headless `spettro` CLI** (`spettro --headless`) over HTTP +
SSE with a bearer token, with no Mac in the middle. Either way, every
prompt, config change, and permission reply is a request; every chat update
is a notification. The app owns the display transcript, the composer, and
the sheets; the agent owns the model, the tool loop, and the file edits.

---

## Two connection modes

| | **Spettro Remote** (Protocol B, primary) | **Headless CLI** (Protocol A) |
|---|---|---|
| Connects to | Spettro macOS app acting as host | `spettro --headless` / `/remote` |
| Transport | WebSocket, JSON-RPC 2.0 (symmetric peer) | HTTP + Server-Sent Events |
| Discovery | Bonjour `_spettro-remote._tcp` via `NsdManager` | Manual host/port entry |
| Auth | QR pair-once, HMAC-SHA256 challenge/response, durable device key | Bearer token (32 hex chars), default port 7878 |
| Surface | Full chat list, projects, multi-session, config, permissions, questions | Single conversation, approvals, ask-user |
| Spec | `../Spettro/docs/34-remote-protocol.md` | `../spettro-CLI/internal/remote/server.go` |

`RemoteClient` exposes a `StateFlow<RemoteState>` state machine
(`Unpaired → Searching → Connecting → Connected`, plus `Offline` with a
typed reason), a diagnostics log, and `RemoteApi` typed wrappers for every
host request (`chatsList`, `chatsOpen`, `prompt`, `cancel`, `config`,
`permissionReply`, `questionReply`, `agentCall`, …). Raw ACP payloads cross
module boundaries as `JsonObject` — the protocol layers never parse ACP
content; `core.acp` does.

## Project layout

```
app/src/main/java/to/eyed/spettro/mobile/
├── MainActivity.kt            entry point; starts/stops the client with lifecycle
├── coordinator/               manual DI + app-wide coordinators
│   ├── AppContainer.kt        service locator (the "DI graph")
│   ├── AppPrefs.kt            persisted preferences (DataStore)
│   ├── remote/MobileModel.kt  root coordinator for Remote mode
│   └── headless/              HeadlessConnection, store, ACP adapters
├── core/
│   ├── SpettroJson.kt, B64.kt shared Json instance + base64url/std helpers
│   ├── rpc/                   JsonRpcPeer — symmetric JSON-RPC 2.0 over OkHttp WS
│   ├── remote/                Protocol B: RemoteClient, RemoteApi, discovery (NSD),
│   │                          pairing (QR + HMAC), credential store, notifications
│   ├── acp/                   ACP payload parsing: session updates, config options
│   │                          (both wire shapes), permission/question requests,
│   │                          StoredSession decode, extension types
│   └── headless/              Protocol A: HeadlessClient (HTTP + SSE), events
├── model/                     ChatSession transcript state holder, TranscriptItem,
│                              ToolCallItem, image attachments
└── ui/
    ├── theme/                 Spettro colors/type — M3 Expressive with Spettro tokens
    ├── components/            shared composables (cards, chips, badges, spinners…)
    ├── screens/
    │   ├── home/              PairingScreen, QR scanner, ChatListScreen,
    │   │                      DisconnectedScreen, CliConnectScreen, project picker
    │   ├── chat/              ChatScreen, composer, transcript rendering,
    │   │                      tool-call rows, markdown, config sheet, status strip
    │   ├── settings/          SettingsScreen, ProvidersScreen
    │   └── sheets/            PermissionSheet, QuestionSheet (shared by both modes)
    └── AppRoot.kt             routes on RemoteState / AppMode; hosts global sheets
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

To actually use the app you need one of:

- **Remote mode:** the Spettro macOS app running on the same network with
  remote hosting enabled — scan its pairing QR code from the app's pairing
  screen (or paste the `spettro-pair://` URL manually).
- **Headless mode:** a reachable `spettro --headless` instance — enter
  host, port (default 7878), and token (or paste `SPETTRO_TOKEN=…`).

## Testing

JVM unit tests (`app/src/test`) cover the parts that must not drift:

- **Pairing** — HMAC-SHA256 proof vector, `spettro-pair://` QR payload parse
- **JSON-RPC** — framing and round-trips in `JsonRpcPeer` (MockWebServer)
- **ACP parsing** — both config-option wire shapes (ACP-native and the
  Swift-synthesized `StoredSession` shape), `StoredSession` decode, session
  updates, permission/question requests, extension types
- **Model** — `ChatSession` transcript mutation and replay suppression,
  `ToolCallItem` title parsing
- **Headless** — SSE event decode, endpoint parsing, reconnect logic

End-to-end verification is manual: a real `spettro --headless` on the Mac
plus the app on a device (Protocol A), and the macOS Spettro app host
(Protocol B).

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

- `../Spettro` — the macOS app (and iOS companion) this is a port of; its
  `docs/` folder is the protocol and design-system reference
- `../spettro-CLI` — the agent itself; source of truth for the headless
  HTTP+SSE protocol
