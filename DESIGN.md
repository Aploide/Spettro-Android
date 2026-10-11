# Spettro Android — Architecture Contract

Android remote-control client for the `spettro` AI coding agent. Born as a port of the iOS
app (`SpettroMobile`); it now follows the Electron desktop app (`../Spettro-Desktop`) — its
remote host, its tunnel and its design language.
Package root: `to.eyed.spettro.mobile`. UI: Jetpack Compose, Material 3 Expressive, dressed
in the desktop's tokens.

## Protocols

- **Protocol B — "Spettro Remote"** (the only mode), version 1: WebSocket + JSON-RPC 2.0
  to the Spettro desktop app host. Bonjour `_spettro-remote._tcp`, QR pair-once
  (HMAC-SHA256 challenge/response), durable device key. Reached on the LAN at
  `ws://<ip>:<port>/`, or from anywhere at `wss://<remoteHost>/` through the computer's
  Cloudflare named tunnel (`r` in the QR, `remoteHost` in the auth result and
  `host/state`; `""` = no tunnel). Routes are raced LAN-first with a 750 ms head start
  (`core/remote/RemoteConnection.kt`). Pairing over the tunnel is refused with `-33003`
  unless the desktop allows it; resuming always works. Newer host abilities are announced
  in `features` (`RemoteFeature`: configResult, notices, pendingReplay, mentions, rename,
  projectFiles, remoteHost, snapshotV2) rather than by a version bump. Spec:
  `../Spettro-Desktop/ARCHITECTURE.md` (Remote protocol; Remote over the internet), wire
  types in `../Spettro-Desktop/src/shared/remote.ts`, host in `src/main/remote/`.
- **Protocol A — CLI HTTP+SSE** *(removed)*: the direct `spettro --headless` connection
  originally shipped alongside Protocol B, but was removed — the paired companion-app
  connection is the safer surface, so it is the only one the app offers. References to
  `core.headless` / headless screens below are historical.

## Package map & ownership

| Package | Contents | Owner |
|---|---|---|
| `core` | `SpettroJson` (shared Json), `B64` (base64url/std helpers) — DONE, do not edit | integrator |
| `core.rpc` | `JsonRpcPeer` — symmetric JSON-RPC 2.0 over OkHttp WebSocket | Agent A |
| `core.remote` | Protocol B: types + `RemoteFeature`, `RemoteClient` state machine, `RemoteConnection` (endpoint plan, staggered race, offline reason), `RemoteDiscovery` (NsdManager), `RemotePairing` (QR parse + HMAC proof), `RemoteCredentialStore` | Agent A |
| `core.acp` | ACP payload parsing (session updates, config options both shapes, permission/question requests, extension types) | Agent B |
| `model` | `ChatMessage`, `ToolCallItem`, `TranscriptItem`, `ChatSession` (transcript state holder) | Agent B |
| `core.headless` | *(removed)* Protocol A: `HeadlessClient` (HTTP + SSE), event types | Agent C |
| `ui.theme` | Material 3 Expressive theme with Spettro tokens | Agent D |
| `ui.components` | Shared composables (transcript, tool rows, spinner, badges, markdown) | Agent D / wave 2 |
| `ui.screens` | Screens (pairing, chat list, chat, new session, settings, providers, disconnected) | wave 2 |
| root | `MainActivity`, `MobileModel` coordinator, navigation | integrator |

## Shared conventions (all agents)

- JSON via `to.eyed.spettro.mobile.core.SpettroJson` (`ignoreUnknownKeys`, `explicitNulls=false`).
- Raw ACP payloads cross module boundaries as `kotlinx.serialization.json.JsonObject` —
  the protocol layers (A/B) never parse ACP content; `core.acp` does.
- Dates on the wire are ISO-8601 strings; model them as `String` and format lazily.
- base64url unpadded (`B64.encodeUrl`) for challenge/proof/deviceKey/QR secret; standard
  padded (`B64.encodeStd`) for image attachments.
- Copy JSON key spellings exactly: `hostID`, `chatID`, `deviceID`, `promptID`, `configID`
  are capital-ID; `sessionId`, `optionId`, `questionId`, `toolCallId`, `acpSessionId` are not.
- Coroutines + `StateFlow`/`SharedFlow` for all async state. No RxJava, no Hilt (manual DI).
- minSdk 33, targetSdk/compileSdk 37. Kotlin 2.2, kotlinx-serialization plugin applied.
- Available deps: okhttp 5.4, kotlinx-serialization-json, kotlinx-coroutines, navigation-compose,
  datastore-preferences, lifecycle-process, CameraX + ML Kit barcode scanning (QR), mikepenz
  markdown-renderer-m3, material-icons-extended, compose BOM 2026.06.01 (material3 pinned to
  1.5.0-alpha for the public Expressive APIs).
- Release builds run R8 (minify + resource shrinking). kotlinx.serialization needs no keep
  rules of its own: the library ships consumer rules, and every `@Serializable` class is
  reached through its generated serializer.

## Key contracts between modules

### Agent A exposes (package `core.remote`)

```kotlin
sealed interface RemoteState {
  data object Unpaired : RemoteState
  data object Searching : RemoteState
  data object Connecting : RemoteState
  data class Connected(
    val host: HostInfo, val agentReady: Boolean, val pairingOpen: Boolean = false,
    val via: RemoteRoute = RemoteRoute.Lan,          // Lan | Internet (the tunnel)
    val features: Set<String> = emptySet(),          // RemoteFeature; has(feature)
  ) : RemoteState
  data class Offline(val reason: OfflineReason) : RemoteState
}
enum class OfflineReason { HostNotFound, TunnelUnreachable, Rejected, VersionMismatch, NotRecognised,
                           HostStopped, NoLocalNetwork, Transport }

class RemoteClient(...) {
  val state: StateFlow<RemoteState>
  val diagnostics: StateFlow<List<String>>          // last 40 log lines
  val notifications: SharedFlow<RemoteNotification> // host->client notifications, parsed envelope
  suspend fun pair(payload: PairPayload)            // from QR / pasted URL
  fun start()                                       // resume with stored credential
  fun stop()
  fun forget()                                      // drop credential -> Unpaired
  suspend fun request(method: String, params: JsonObject): JsonElement  // throws RpcException(code, message)
}
// RemoteNotification: sealed class with one case per host notification
// (Hello, ChatUpdate(chatID, update: JsonObject), ChatUser, ChatState, ChatRemoved,
//  HostState, PermissionAsk(promptID, chatID?, request: JsonObject),
//  PermissionResolved, QuestionAsk(...), QuestionResolved, AgentNotification(method, params))
// Typed wrappers for requests: RemoteApi(client) with suspend funs
// (chatsList(): List<ChatSummary>, chatsOpen(id): ChatOpenResult { chat: JsonObject /*StoredSession*/,
//  configOptions/commands/plan/usage as JsonElement, isBusy }, chatsNew, chatsDelete, chatsFlag,
//  prompt(chatID, blocks: List<JsonObject>), cancel, config, permissionReply, questionReply,
//  agentCall(method, params): JsonElement, projectsList).
// ChatSummary is fully typed: id, title, projectPath, createdAt, updatedAt, isPinned,
// isArchived, isBusy, messageCount, preview.
```

### Agent B exposes (packages `core.acp`, `model`)

```kotlin
// core.acp — parse functions take JsonObject and return typed models:
object AcpParser {
  fun parseSessionUpdate(update: JsonObject): AcpSessionUpdate?   // sealed: MessageChunk, ThoughtChunk, ToolCall, ToolCallUpdate, CommandsUpdate, ConfigUpdate, Plan, Usage
  fun parseConfigOptionsAcp(arr: JsonArray): List<AcpConfigOption>      // ACP shape (type/currentValue/options)
  fun parseConfigOptionsStored(arr: JsonArray): List<AcpConfigOption>   // Swift-synthesized shape (kind.select/boolean)
  fun parsePermissionRequest(req: JsonObject): AcpPermissionRequest?
  fun parseQuestionRequest(req: JsonObject): AcpQuestionRequest?
  fun parseStoredSession(obj: JsonObject): StoredSessionData          // items: message|tool externally tagged
  fun parseCommands*/parsePlan*/parseUsage* (both shapes)
}
// extension types: AcpAccountStatus, AcpLoginStatus, AcpProvidersList, AcpModelsList, ...
// with parse(JsonElement) helpers, used via agent passthrough.

// model —
class ChatSession {   // plain state holder, exposes StateFlow<List<TranscriptItem>> + fields
  // restore(from: StoredSessionData), appendUserMessage, appendReasoning, appendAssistant
  // (with replay suppression), applyToolEvent, endStreaming, beginRun/endRun, plan/usage/commands/config state
}
class ToolCallItem { /* title parsing "[agent#n] name {args}", displayDetail (never raw JSON), diffStat, subAgent extraction */ }
```

### Agent C exposes (package `core.headless`) — removed, kept for the record

```kotlin
class HeadlessClient(baseUrl: String, token: String) {
  val events: SharedFlow<HeadlessEvent>       // parsed SSE (sealed: State, UserMessage, AssistantMessage, Tool, Banner, ApprovalRequest, AskUser, Plan, Comment, ...)
  val connection: StateFlow<HeadlessConnState> // Connecting/Streaming/Disconnected(retryIn)
  suspend fun status(): HeadlessStatus
  suspend fun sendMessage(text: String): SubmitResponse   // 409 -> accepted=false, not an exception
  suspend fun interrupt()
  suspend fun approve(toolId: String, decision: String, instead: String? = null)
  suspend fun answerAskUser(questionId: String, answers: Map<String, String>)
  fun start(); fun stop()
}
```
Gotchas: `?token=` only valid on GET /events (use Authorization header everywhere);
`tool.args` may be JSON object OR string (+ `args_raw`); SSE replay = last 64 events,
dedupe by `seq`; heartbeat `: ping` every 15s; dedupe `ask_user` by `question_id`.

### Agent D exposes (package `ui.theme`, `ui.components`)

Design tokens are the desktop's (`Spettro-Desktop/src/renderer/src/design/theme.css`),
ported 1:1 in `SpettroColors.kt`: true neutral greys in both schemes and one accent used
sparingly — **Lilac** (default) or **Monochrome**, where the accent is the ink. One
`SpettroColors` per (scheme × accent); Monochrome overrides only the accent tokens.

| Token | Dark | Light |
|---|---|---|
| canvas / sidebar / raised / sunken | #1C1C1E / #161618 / #2C2C2E / #161618 | #FAFAFA / #F4F4F5 / #FFFFFF / #F2F2F3 |
| user bubble | #2A2A2D | #F0F0F2 |
| text primary / secondary / tertiary | #F5F5F7 / #AEAEB2 / #98989D | #1D1D1F / #5C5C61 / #6E6E73 |
| hairline / hover / selected / scrim | white 10% / 5.5% / 9% / black 50% | black 10% / 4.5% / 7% / black 25% |
| accent (Lilac) / contrast / text | #B9A3F5 / #1B1530 / #B9A3F5 | #7A66C6 / #FFFFFF / #6852B8 |
| accent (Mono) / contrast / text | #F5F5F7 / #1D1D1F / #F5F5F7 | #1D1D1F / #FFFFFF / #1D1D1F |
| success / danger / danger fill / warning | #4FBF7A / #EF6F65 / #D0433A / #E0A43F | #237A48 / #C93A30 / #C93A30 / #9A6210 |
| Ultra ember / flame / spark / core | #E0452B / #FF8A3D / #FFD36B / #FFFBEA | #C2341D / #E8641C / #F2A516 / #FFF1C2 |

Plus the mode colours (plan/coding/ask follow the accent; the named agent colours stay in
Monochrome), plan-badge tokens, Ultra's fire and the activation glow ramp. Every text token
passes WCAG AA on every surface — `ContrastTest` asserts it over (Lilac|Mono)×(light|dark).

- **No dynamic (Material You) colour.** The Material `ColorScheme` is derived from the tokens.
  Appearance (System/Light/Dark) and accent are phone-local settings (`ThemeStore`, DataStore),
  chosen in Settings › Appearance; the desktop doesn't relay its own.
- Spacing 2/4/8/12/16/24/32; radii 6/8/12/20, bubble 18, prompt card 16, option 10.
- Type: prose 15/24, UI body 13, titles 17 / 15 semibold, greeting 28; eyebrows 11sp semibold,
  uppercased at render time (pass sentence case).
- Motion: `MotionScheme.standard()` and the desktop's short ease-outs (`SpettroMotion`); nothing springs.
- User bubble = neutral `userBubble` under primary text (the activation phrase stays lit in it);
  assistant = full-width prose, no chrome; cards = raised + 1dp hairline, never a shadow —
  only floating layers (menus, sheets, dialogs, toasts, the composer) cast one.
- Buttons are pills (`SpettroButton`): neutral by default, one accent action per pane, in-chat
  primaries (send, Allow once, Send answer) in the ink. Copy is sentence case, plain words,
  "computer" not "PC", "…" on actions that open more UI; errors go through `core/Humanize`.
- Amber (`warning`) means "waiting on you": an approval, an answer, a chat's "Needs you".

### The thinking slider (`ui/components/thinking/`, `model/Thinking*.kt`)

A port of the desktop's ThinkingSlider: one dial over two CLI options, the `thinking` select
and the `ultra` boolean. Stops are Low · Medium · High · Extra high · Max (whatever subset the
CLI offers, always in that order), then **Ultra** — which means thinking *high* plus
ultracode, not max. Off is never a stop; it is only shown when something else set it. The
caption under the track says what the stop does; Ultra paused under Ask first offers
Restricted instead. It opens from the composer's thinking chip as a sheet.

- The track's fill is the accent up to the thumb; toward Ultra it warms through the fire
  tokens. A lit Ultra paints the whole track in fire with the smoulder (`Embers`, seed 11,
  a wave every 3.8 s) and a breathing white-hot thumb.
- Moving into Ultra flies a **meteor** (`Meteor`, seeded by the flight count) from the stop
  the thumb stood on; the thumb and lit fill take over when the head lands. Reduced motion
  swaps the flight for the thumb glowing up, and keeps the fades.
- Moves go to the CLI one call at a time (`ThinkingCommitter`, one per chat): moves made
  while one is in flight collapse into the latest, and Ultra's two calls never race. A
  move's preview outlives its calls by 1.2 s if the options never come round to it; a held
  key sends after 150 ms of quiet. All of it is pure and tested without a screen.

## UI screen inventory

1. **AppRoot** — routes on RemoteState: Unpaired→Pairing, Connected→RemoteNavigator (phone
   stack, or the desktop-style split shell at ≥ 780×500dp), else→Disconnected. A transient
   drop keeps the chat mounted for up to 20 s behind a "Reconnecting…" pill. Hosts the
   pairing-link prompt (a `spettro-pair://` link while already paired) and the sheets for
   prompts that name no chat.
2. **PairingScreen** — three steps, QR scan (CameraX + ML Kit), paste a pairing link; the
   local-network permission is asked before pairing with a LAN address.
3. **DisconnectedScreen** — one calm screen per reason (looking, can't reach — over the
   internet too when there is a tunnel —, lost the connection, sharing off, local network
   access, turned away, update needed, removed), with Try again / Pair again / Allow access
   and always "Forget this computer…".
4. **ChatListScreen** — the desktop sidebar: sections by project folder (collapsible), pinned
   first, search (titles and folders), long-press actions (pin, rename, archive, delete with
   Undo), pull-refresh, archived sheet, "Needs you" on a chat waiting for an answer.
5. **NewSessionScreen** — what "+" opens: greeting, composer, "Working in" folder chip,
   starter prompts; the chat is created on first send.
6. **ChatScreen** — transcript (LazyColumn over *folded rows*, not raw items): neutral user
   bubble, full-width assistant prose, one quiet line per tool call (reads folded, edits as
   real unified diffs, `$ command` and output), sub-agent cards, a run ticker at the tail.
   Above the floating composer: the prompt dock, the todo tab and, on tablets and phones
   alike, the live run strip. Composer: attachments via PhotosPicker (max 4 → JPEG longest
   edge 1568px q85), `@`-mentions of project files, slash commands in plain words, the
   thinking chip, the mode/model/permission chip, send/stop/guide, the `ultracode` and
   `+500k` highlight.
   - **PromptDock** — approvals and questions for the open chat, inline above the composer
     (one at a time, "1 of 2"; questions one by one with Skip all). Answers are held back
     for the first 600 ms; a swipe never denies or declines.
   - **TodoList** — the agent's plan as a tab tucked under the composer: open while it
     works, folded to one line once it stops or while a prompt waits.
   - **WorkflowCard** — a workflow run as one object rather than N rows. Collapsed by default
     (a failed run opens expanded); the collapsed header carries the meter, the ratio, the
     per-member cell strip and any failures. A paused run says what it waits for, a stopped
     one why it stopped, and a card a later turn continued says "Continued below".
   - **OrchestrationLiveStrip** — one pinned line showing who is working and on what,
     expanding into a sheet with the whole declared plan.
7. **ChatConfigSheet** — mode, model and permission as the desktop's segmented controls and
   pickers (thinking and Ultra live in the slider).
8. **PermissionSheet** / **QuestionSheet** — only for prompts that name no chat; never
   preselect recommended; unanswered questions omitted from reply.
9. **SettingsScreen** — connection (computer, route: Wi-Fi or over the internet, internet
   address, agent), subscription, models & providers link, appearance (System/Light/Dark,
   Lilac/Monochrome), connection log, about. A centered modal on tablets.
10. **ProvidersScreen** — providers, API key connect, local endpoints (probe/add/remove),
    model list with favorites.

The debug-only `ScreenshotActivity` renders any of these from fixtures; its scene list
lives at the top of `tools/playstore-screenshots.sh`.

## Orchestration derivation (`model/Orchestration.kt`)

`groupTranscript` folds the flat ACP transcript back into the shape a workflow
actually had: runs absorb their members, members absorb their own
tool calls, everything else passes through untouched and in order. It is pure
and memo-free by contract — a session restored from disk must fold identically
to the live one that produced it — and it is memoised once in `ChatScreen`, not
per surface.

A run's state is `_meta["spettro.app/workflow"]` (version 1) on its card when
the card carries it, and the card's rendered text otherwise. The wire facts
that drive the fold, and what each falls back to when it is absent:

| Clue | When it is missing | What we fall back to |
|---|---|---|
| `_meta["spettro.app/workflow"]` | an older CLI; a snapshot from a host that does not relay it; a version > 1 | `parseRenderedWorkflow` reads the same state from the card's text |
| card id `workflow-<run>[-<n>]` | an older CLI (`wf-<n>`) or a run with no id | args with `workflow` and no `agent`, then a `workflow …` title |
| `run_id` on a workflow member | a run with no id | the nearest open run, then the last one |
| `phases` / `description` in args | after the finish update overwrites them, and forever once reloaded | the meta, else the CLI's own rendered tree |
| `[instance] ` title prefix | plain delegations (the CLI only brackets names containing `#`) | the call stays a flat row rather than being guessed at |

A tint note: `SpettroColors.modeColor` is the TUI's palette, and the TUI is
always dark. On the light appearance every hue is darkened toward black so it
clears 4.5:1 as ink; the hue is what carries the meaning and is preserved
exactly. Status, not identity, drives the cell strip's colours — running takes
the accent rather than the member's spec tint, because half the palette is a
green and a fan-out of `code` agents would otherwise draw running-green beside
done-green.

## Testing

Unit tests (JVM) for: pairing proof vector, QR parse (with `r`), the endpoint plan, the race and
offline reasons, JSON-RPC framing, ACP parsing of both config shapes, StoredSession decode, tool
title parsing, tool groups and unified diffs, replay suppression, prompts and question forms, todos,
mentions, the thinking dial, its moves and commit queue, the meteor's plan, `groupTranscript` (a
case-for-case port of the desktop suite — screenshots cannot catch a member attached to the wrong
run), the `ultracode` activation matcher and the `+500k` budget directive against the Go
implementation's own golden vectors, and `ContrastTest` over (Lilac|Mono)×(light|dark).
E2E: the Spettro desktop app hosting on a computer (LAN and tunnel) + this app on a device.
