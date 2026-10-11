# Spettro for Android

A native Android client for the **`spettro` AI coding agent** — a Kotlin port
of the iOS companion app (`SpettroMobile`). The app ships no AI itself: it is
a remote control and transcript view for an agent that runs elsewhere.

UI is **Jetpack Compose with Material 3 Expressive**, dressed in the desktop
app's design language; package root is `to.eyed.spettro.mobile`. minSdk 33,
targetSdk/compileSdk 37, Kotlin + coroutines throughout (no RxJava, no Hilt —
manual DI via `AppContainer`).

---

## What this app is, in one paragraph

**Spettro for Android** talks to a `spettro` agent host and renders
everything it emits: chats, streamed tokens, tool calls, diffs, plans,
permission prompts, and ask-user questions. It pairs with the **Spettro
desktop app** on your computer — Linux, macOS, or Windows — over the local
network (Bonjour discovery, QR-code pairing, WebSocket + JSON-RPC 2.0), and
mirrors its chat list; the computer owns the agent. Every prompt, config change, and
permission reply is a request; every chat update is a notification. The app
owns the display transcript, the composer, and the sheets; the agent owns
the model, the tool loop, and the file edits.

---

## The connection

| | **Spettro Remote** (Protocol B, version 1) |
|---|---|
| Connects to | Spettro desktop app acting as host (Settings › Remote › sharing on) |
| Transport | WebSocket, JSON-RPC 2.0 (symmetric peer): `ws://<lan-ip>:<port>/` on the local network (or a USB port forward) |
| Discovery | Bonjour `_spettro-remote._tcp` via `NsdManager`, plus the QR's address hint and the last address that worked |
| Auth | QR pair-once, HMAC-SHA256 challenge/response, durable device key |
| Surface | Full chat list, projects, multi-session, config, permissions, questions, renames, @-mentions |
| Spec | `../Spettro-Desktop/ARCHITECTURE.md` (Remote protocol); the wire is `../Spettro-Desktop/src/shared/remote.ts` |

The companion-app pairing is the only supported connection: the durable,
HMAC-authenticated pairing is safer than exposing a raw agent endpoint, so
there is deliberately no direct/headless CLI mode.

`RemoteClient` exposes a `StateFlow<RemoteState>` state machine
(`Unpaired → Searching → Connecting → Connected`, plus `Offline` with a
typed reason), a diagnostics log, and `RemoteApi` typed wrappers for every
host request (`chatsList`, `chatsOpen`, `prompt`, `cancel`, `config`,
`rename`, `projectFiles`, `permissionReply`, `questionReply`, `agentCall`,
…). Raw ACP payloads cross module boundaries as `JsonObject` — the protocol
layers never parse ACP content; `core.acp` does.

### Pairing

The desktop shows a QR code, `spettro-pair://pair?v=1&h=<hostID>&n=<name>&k=<kind>&s=<secret>&a=<addr>&p=<port>`
(`RemotePairing.parse`; unknown keys, such as the `r` an older desktop
added for its internet tunnel, are ignored). Scan it from the pairing
screen, paste it, or open it as a link: the desktop's **Copy link** puts
the same URL on the clipboard, and tapping it on the phone opens the app on
`spettro-pair://pair` (a `VIEW` intent filter on `MainActivity`, which is
`singleTask` so the link reaches the running app). Unpaired, a link pairs as
if scanned. Already paired, `PairingLinkPrompt` asks first — "Pair with
“Studio iMac”?" — because pairing replaces the current computer; a link
from the computer already connected changes nothing and is dropped quietly.

### Routes

The phone only reaches the computer on the local network; there is no
internet route. `EndpointPlan` (`core/remote/RemoteConnection.kt`) lists
where to try, best first: where Bonjour sees the computer now, then the last
LAN address that worked — only on Wi-Fi or Ethernet, except a loopback
address, which is a USB cable's `adb reverse` port forward and is tried on
any network. Pairing tries the code's address hint, then Bonjour.
`raceStaggered` races them "happy eyeballs" style — each next one after a
750 ms head start, or as soon as the one before fails — and the first to say
hello as the right host wins; the losers are closed before the winner
authenticates. Settings shows which way the connection goes ("Connected over
Wi-Fi", "over Ethernet", "over USB", "on your network"). When nothing
answers, `offlineReason` picks the story: Bonjour sees the computer (a
transport fault), the local-network permission is missing, or it isn't on
this network.

### Local network permission (Android 17)

Android 17 (API 37) guards the LAN behind `ACCESS_LOCAL_NETWORK`. The app
declares it, asks with a rationale before pairing with a LAN address
(`rememberLocalNetworkPermission`), and offers it again from the
disconnected screen. Without it the LAN routes are dropped from the plan —
the system would refuse the sockets anyway — so nothing is tried until it is
granted. Older Android ignores the permission.

### Features

The protocol version stays 1: a host refuses any other. What a host adds on
top it announces in `features` (in `hello` and the auth result; see
`RemoteFeature`), and the phone checks `Connected.has(...)` before relying
on any of it. An old host sends no list, which reads as "none of these":

| Feature | What the phone does with it |
|---|---|
| `configResult` | `chats/config` waits for `{configOptions}` (or an error after a rollback); without it, the two calls of one slider move go 300 ms apart |
| `notices` | desktop-side notices (`chat/state` with a `notice`, no `stopReason`) show in the transcript |
| `pendingReplay` | approvals and questions still waiting are re-asked after auth, so a reconnect never loses one |
| `mentions` | `chats/prompt` carries the `@`-mentioned files; without it they stay plain text |
| `rename` | Rename appears in the chat's menu and on a long press in the list |
| `projectFiles` | the `@` menu lists the project's files |
| `snapshotV2` | reopened chats keep steering, streaming, timing, mention and workflow fields |

### Staying connected

The connection follows the process lifecycle (`AppContainer`, via
`ProcessLifecycleOwner`): it starts when the app comes to the foreground and
is let go a little after it leaves, so the system photo picker doesn't drop
it. Failed connections retry on their own: exponential backoff from 1s to
30s, reset the moment anything succeeds, the app comes to the foreground, or
Bonjour sees the host reappear. A drop while a chat is open is ridden out in
place for up to 20 s — the transcript and the draft stay, a "Reconnecting…"
pill shows, and Send waits — before the disconnected screen takes over. That
screen says why in plain words (looking, can't reach, sharing off, turned
away, removed, update needed, local network access) and always keeps a
"Forget this computer…" escape hatch so a stale pairing can be discarded.
There are no background notifications; another chat that needs an answer is
marked "Needs you" in the list.

## Project layout

```
app/src/main/java/to/eyed/spettro/mobile/
├── MainActivity.kt            entry point; theme, and spettro-pair:// links
├── coordinator/               manual DI + app-wide coordinators
│   ├── AppContainer.kt        service locator; starts/stops the client with the process
│   └── remote/                MobileModel (root coordinator: mirrors the host's
│                              state), ChatOpenBuffer, DeleteScheduler (Undo window)
├── core/
│   ├── SpettroJson.kt, B64.kt shared Json instance + base64url/std helpers
│   ├── Humanize.kt            errors in plain words, raw text behind "Show details"
│   ├── WorkflowActivation.kt  the "ultracode" matcher; WorkflowBudget.kt the "+500k" directive
│   ├── rpc/                   JsonRpcPeer — symmetric JSON-RPC 2.0 over OkHttp WS
│   ├── remote/                Protocol B: RemoteClient, RemoteApi, RemoteProtocol
│   │                          (wire types, features), RemoteConnection (routes,
│   │                          endpoint plan, the race), discovery (NSD), pairing
│   │                          (QR + HMAC), credential store, notifications
│   └── acp/                   ACP payload parsing: session updates, config options
│                              (both wire shapes), permission/question requests,
│                              StoredSession decode, extension types
├── model/                     ChatSession transcript state holder, TranscriptItem,
│   │                          ToolCallItem, image attachments, and the pure logic
│   │                          the UI draws from:
│   ├── Orchestration.kt       folds the flat wire back into workflow runs (see below)
│   ├── Thinking*.kt, SliderMoves.kt, Meteor.kt, Embers.kt
│   │                          the thinking slider: one dial over thinking + Ultra,
│   │                          the commit queue, the meteor and the smoulder
│   ├── ToolGroups.kt, ToolPresentation.kt, UnifiedDiff.kt
│   │                          tool rows: folded reads, verbs and notes, real diffs
│   ├── Prompts.kt, QuestionForm.kt   the prompt dock's approvals and questions
│   └── Todos.kt, Mentions.kt, CommandCopy.kt, RunPhase.kt, TranscriptAnchors.kt
└── ui/
    ├── theme/                 the desktop's tokens (Lilac/Monochrome × light/dark), type,
    │                          motion, ThemeStore (appearance + accent, phone-local)
    ├── components/            shared composables (buttons, cards, chips, spinners,
    │                          local-network permission) and thinking/ (slider + chip)
    ├── screens/
    │   ├── home/              PairingScreen, QR scanner, ChatListScreen,
    │   │                      DisconnectedScreen, project picker, rename/delete dialogs
    │   ├── chat/              ChatScreen, composer, NewSessionScreen, transcript rows,
    │   │                      tool-call rows, PromptDock, TodoList, markdown,
    │   │                      config sheet, workflow cards + the live run strip
    │   ├── settings/          SettingsScreen, ProvidersScreen
    │   └── sheets/            PermissionSheet, QuestionSheet (prompts naming no chat)
    └── AppRoot.kt             routes on RemoteState; the split shell on tablets;
                               reconnecting pill, pairing-link prompt, global sheets

app/src/debug/.../screenshots/ScreenshotActivity.kt
                               the screenshot harness (see Building and running)
```

The architecture contract the modules were built against lives in
[`DESIGN.md`](./DESIGN.md) — package ownership, module APIs, wire-format
gotchas, and the design tokens.

### Orchestration: workflows

The CLI streams a workflow run as a **flat** sequence of tool calls — one
long-lived card for the run, one call per sub-agent, and every tool each
sub-agent runs, all interleaved in arrival order. Rendered literally that is a
wall of near-identical rows that drowns the conversation and never says which
agent did what.

`model/Orchestration.kt` reconstructs the real shape. It is pure and memo-free
on purpose: a session restored from disk must fold to exactly the same tree as
the live one that produced it, so the answer may depend only on the items
passed in. `ChatScreen` runs it once per transcript change and both the
transcript and the live strip read the result.

The run's state comes from `_meta["spettro.app/workflow"]` (version 1) on the
card when it is there — phases, members, pause, stop, size and budget, the log
tail — and from the text the CLI rendered into the card when it is not (an
older CLI, or a host that does not relay the meta in a snapshot). The meta
wins; the two paths are tested to draw the same tree. A few things about the
wire are load-bearing:

- **Card ids say which run and which turn.** `workflow-<run>` is a run's first
  card and `workflow-<run>-<n>` the one a later turn opened to continue it;
  `wf-<n>` is a run with no id (and every card of an older CLI). A run paused
  at a checkpoint and continued later has two cards: the first closes
  "continued in a later turn", the second lists every member so far, so
  members are pooled per run rather than per card.
- **A workflow arrives as two calls with the same name.** The model's
  invocation of the `workflow` *tool* carries the whole script as arguments;
  the card is what the run is built from. The first is folded into the second
  — except when it failed before any run existed, where it is the only
  evidence a workflow was attempted at all. A reply to a checkpoint
  (`continue_run_id`) folds away too, unless it was refused.
- **Progress traces can escape.** `workflow X ⏸ cp-1`, `▸ Phase` and `· log`
  traces carry `workflow` and no `agent`, exactly like a card, and must never
  open a phantom run.

Ultra swarms are gone from the CLI, and from the app with them.

`OrchestrationTest` is a case-for-case port of the desktop app's suite and
covers every one of those. Screenshots cannot catch any of it — a member
attached to the wrong run still renders beautifully.

## Building and running

Prerequisites: **Android Studio** (recent stable) with a JDK 11+ toolchain,
an emulator or device on **Android 13+ (API 33)**.

```bash
./gradlew assembleDebug      # build
./gradlew installDebug       # install on a connected device/emulator
./gradlew testDebugUnitTest  # JVM unit tests
```

**The debug build installs beside a Play install rather than replacing it.**
It carries `applicationIdSuffix = ".dev"` and is labelled *Spettro Dev*, so
`installDebug` on a phone that already has Spettro from the Play Store adds a
second app instead of failing on the signature mismatch — and, more to the
point, without wiping the pairing credentials, which live in app-private
storage with `allowBackup="false"` and cannot be recovered.

That means the debug package is `to.eyed.spettro.mobile.dev` while the class
namespace stays `to.eyed.spettro.mobile`, so anything addressing a component
by name has to spell it out in full rather than using the `.Foo` shorthand.
`tools/playstore-screenshots.sh` already does. Release builds are untouched:
still `to.eyed.spettro.mobile`, still labelled *Spettro*.

```bash
./gradlew assembleRelease    # R8-minified and resource-shrunk; signed with the debug key
```

To actually use the app you need the Spettro desktop app running on a
computer with sharing on (Settings › Remote). Pair on the same network as the
computer — scan its QR code, paste or open its `spettro-pair://` link — and
the phone reconnects whenever it's on that network again. Each application
id pairs separately, so the dev copy needs its own pairing.

### The screenshot harness

The debug build carries `ScreenshotActivity`, which renders one screen from
the preview fixtures with no client and no network. It serves every surface:
the chat and its tool rows and diffs, the prompt dock, the todo list, the
workflow cards, the thinking slider (frozen meteor and smoulder frames
included), the new-session view, every disconnected reason, reconnecting,
Settings on either route, and the pairing-link prompt. The full scene list
is at the top of `tools/playstore-screenshots.sh`; drive one with

```bash
adb shell am start -n \
    to.eyed.spettro.mobile.dev/to.eyed.spettro.mobile.screenshots.ScreenshotActivity \
    --es scene thinking_meteor --es theme dark --es form phone [--es accent mono]
```

`./tools/playstore-screenshots.sh [phone|tablet]` captures the Play listing
from it (boot the emulator with `./tools/start.sh`).

## Testing

JVM unit tests (`app/src/test`) cover the parts that must not drift:

- **Pairing and connection** — HMAC-SHA256 proof vector, `spettro-pair://`
  parse (with `r` and hostname normalisation), the endpoint plan, the
  staggered race, offline reasons, credential prefs, notification parsing
- **JSON-RPC** — framing and round-trips in `JsonRpcPeer` (MockWebServer)
- **ACP parsing** — both config-option wire shapes (ACP-native and the
  Swift-synthesized `StoredSession` shape), `StoredSession` decode, session
  updates, permission/question requests, extension types
- **Model** — `ChatSession` transcript mutation and replay suppression,
  `ToolCallItem` title parsing, tool groups and diffs, orchestration
  folding, prompts and questions, todos, mentions, the thinking dial, its
  moves and commit queue, the meteor's plan
- **UI logic** — disconnect reasons, transient drops, config summary,
  permission copy, session groups, theme prefs, and `ContrastTest`, which
  checks every text token for WCAG AA over (Lilac|Mono)×(light|dark)

End-to-end verification is manual: the Spettro desktop app hosting on a computer
plus this app on a device.

## Conventions

- All JSON goes through `core.SpettroJson` (`ignoreUnknownKeys`,
  `explicitNulls = false`). Key spellings are copied exactly from the wire:
  `hostID`/`chatID`/`deviceID` are capital-ID; `sessionId`/`toolCallId` are not.
- base64url **unpadded** for pairing material; standard padded base64 for
  image attachments.
- Async state is coroutines + `StateFlow`/`SharedFlow` everywhere.
- Design tokens mirror the desktop design system: a Lilac (`#7A66C6` light /
  `#B9A3F5` dark) or Monochrome accent, chosen on the phone, on true neutral
  greys (canvas `#FAFAFA` / `#1C1C1E`); flat hairline cards, with shadows only
  on floating layers (composer, menus, sheets, toasts); a neutral user
  bubble, assistant = full-width prose. See `DESIGN.md`.

## Related repositories

- `../Spettro-Desktop` — the desktop app this pairs with: the remote host
  and the design system (`src/renderer/src/design/`) this app
  follows; its `ARCHITECTURE.md` documents the protocol
- `../spettro` — the agent itself (the CLI)
