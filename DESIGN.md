# Spettro Android — Architecture Contract

Android remote-control client for the `spettro` AI coding agent. Port of the iOS app
(`../Spettro`, target `SpettroMobile`).
Package root: `to.eyed.spettro.mobile`. UI: Jetpack Compose, Material 3 Expressive.

## Protocols

- **Protocol B — "Spettro Remote"** (the only mode, iOS parity): WebSocket + JSON-RPC 2.0
  to the Spettro desktop app host. Bonjour `_spettro-remote._tcp`, QR pair-once
  (HMAC-SHA256 challenge/response), durable device key. Spec:
  `../Spettro/docs/34-remote-protocol.md`,
  reference Swift: `../Spettro/Spettro/Core/Remote/*.swift`.
- **Protocol A — CLI HTTP+SSE** *(removed)*: the direct `spettro --headless` connection
  originally shipped alongside Protocol B, but was removed — the paired companion-app
  connection is the safer surface, so it is the only one the app offers. References to
  `core.headless` / headless screens below are historical.

## Package map & ownership

| Package | Contents | Owner |
|---|---|---|
| `core` | `SpettroJson` (shared Json), `B64` (base64url/std helpers) — DONE, do not edit | integrator |
| `core.rpc` | `JsonRpcPeer` — symmetric JSON-RPC 2.0 over OkHttp WebSocket | Agent A |
| `core.remote` | Protocol B: types, `RemoteClient` state machine, `RemoteDiscovery` (NsdManager), `RemotePairing` (QR parse + HMAC proof), `RemoteCredentialStore` | Agent A |
| `core.acp` | ACP payload parsing (session updates, config options both shapes, permission/question requests, extension types) | Agent B |
| `model` | `ChatMessage`, `ToolCallItem`, `TranscriptItem`, `ChatSession` (transcript state holder) | Agent B |
| `core.headless` | Protocol A: `HeadlessClient` (HTTP + SSE), event types | Agent C |
| `ui.theme` | Material 3 Expressive theme with Spettro tokens | Agent D |
| `ui.components` | Shared composables (transcript, tool rows, spinner, badges, markdown) | Agent D / wave 2 |
| `ui.screens` | Screens (pairing, chat list, chat, settings, providers, headless) | wave 2 |
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
- minSdk 24, targetSdk 36. Kotlin 2.2, kotlinx-serialization plugin applied.
- Available deps: okhttp 5.4, kotlinx-serialization-json, kotlinx-coroutines, navigation-compose,
  datastore-preferences, zxing-android-embedded (QR), mikepenz markdown-renderer-m3,
  material-icons-extended, compose BOM 2026.06.01 (material3 1.4.0 with Expressive APIs).

## Key contracts between modules

### Agent A exposes (package `core.remote`)

```kotlin
sealed interface RemoteState {
  data object Unpaired : RemoteState
  data object Searching : RemoteState
  data object Connecting : RemoteState
  data class Connected(val host: HostInfo, val agentReady: Boolean, val pairingOpen: Boolean = false) : RemoteState
  data class Offline(val reason: OfflineReason) : RemoteState
}
enum class OfflineReason { HostNotFound, Rejected, NotRecognised, HostStopped, NoLocalNetwork, Transport }

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

### Agent C exposes (package `core.headless`)

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

Design tokens from `../Spettro/docs/18-design-system.md`:
accent #526FFF light / #6073CC dark; canvas #F9F9F7 / #0E0E0E; surfaceRaised #FFFFFF / ~#1C1C1C;
diffAdded #40C977; diffRemoved #FA423E; agentAccent #AD7BF9; hairline 8% on/;
mode colors: plan #BD93F9, coding #34D399, ask #60A5FA, yellow #F59E0B, red #EF4444, purple #BD93F9, magenta #C084FC, cyan #60A5FA, green #34D399, blue #A78BFA.
Use `MaterialExpressiveTheme` (`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`) with
`expressiveLightColorScheme()` / dark scheme seeded from the accent, canvas/surface overrides.
Spacing 4/8/12/16/24; radii 8/12/18, bubble 16. User bubble = solid accent, white text;
assistant = full-width prose, no chrome; cards = surfaceRaised + 1dp hairline border, no shadow.

## UI screen inventory (wave 2)

1. **RootView** — routes on RemoteState: Unpaired→Pairing, Connected→ChatNavigator, else→Disconnected (5 states with distinct copy/action). Hosts permission/question sheets globally. Mode switcher entry to Headless (Protocol A) mode.
2. **PairingScreen** — 3-step intro, QR scan (zxing), manual paste fallback of `spettro-pair://` URL.
3. **ChatListScreen** — sections by project folder (collapsible), pinned first, swipe pin/archive/delete, search, pull-refresh, new-chat with project picker, archived sheet, agent-down banner.
4. **ChatScreen** — transcript (LazyColumn), status strip (run ticker/plan progress/context %), composer (attachments via PhotosPicker max 4 → JPEG longest edge 1568px q85, command palette, config summary chip, send/stop).
5. **ChatConfigSheet** — config options as grouped selects/toggles.
6. **PermissionSheet** / **QuestionSheet** — per iOS behavior; never preselect recommended; unanswered questions omitted from reply.
7. **SettingsScreen** — connection info, account (device-flow sign-in, plan badge, credits), providers link, diagnostics log, about.
8. **ProvidersScreen** — providers, API key connect, local endpoints (probe/add/remove), model list with favorites.
9. **HeadlessScreen(s)** — connect form (host, port, token or paste `SPETTRO_TOKEN=`), single conversation transcript + composer + approval/ask-user via same sheet components.

## Testing

Unit tests (JVM) for: pairing proof vector, QR parse, JSON-RPC framing, ACP parsing of both
config shapes, StoredSession decode, headless event decode, tool title parsing, replay suppression.
E2E: real `spettro --headless` on the Mac + app on device (Protocol A); macOS Spettro app host (Protocol B).
