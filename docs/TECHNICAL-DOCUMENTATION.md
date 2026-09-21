# D88 – Technische Dokumentation

Stand: Prototyp 0.1.0 · Sprache: Kotlin · Ziel: Android (minSdk 26, targetSdk 35)

## 1. Überblick

D88 ist eine private, lokal-first, nicht-kommerzielle Agentenplattform für einen
persönlichen Geräteverbund. Dieses Dokument beschreibt die Implementierung der
Architektur-Spezifikation in der `README.md` (Abschnittsnummern = Spec).

**Architektur-Regel des Prototyps:** Die gesamte Autorisierungs-, Audit- und
Agent-Logik ist **reines Kotlin/JVM ohne Android-Abhängigkeiten**
(Pakete `model`, `core`, `device`, `debug`, `agent`). Die UI (`ui`) ist eine
dünne Schicht darüber. Dadurch ist das Kernmodell deterministisch unit-testbar.

```
model/        Domänenmodelle (Identität, Trust, Capabilities, Scopes, Policy, Audit)
core/         TrustStateMachine, RoleEngine, CapabilityEngine, ScopeEngine,
              PolicyEngine, AuthorizationChain, HumanGate, AuditTrail, D88Core
device/       DeviceRegistry, DeviceManager, simulation/SimulationSeeder
debug/        AdbAdapter (Interface), LocalAdbAdapter, AdbCommandPolicy, DebugGateway
agent/        AgentSessionManager, Planner, AgentRuntime (Query-Workers)
data/         JsonStorage (lokal-first Persistenz)
ui/           ViewModel, Fragments, Adapter, KnowledgeBase (lokal)
```

## 2. Autorisierungskette (Spec 18)

```
DEVICE → IDENTITY → TRUST → ROLE → PROJECT → TASK → SESSION →
CAPABILITY → POLICY → HUMAN GATE → EXECUTION → VALIDATION → AUDIT
```

- `AuthorizationChain.evaluate()` führt Stufen 1–10 in fester Reihenfolge aus;
  jede Stufe schreibt einen `StageOutcome` in den Trace. Eine fehlende oder
  gescheiterte Stufe beendet die Kette **an genau dieser Stufe** (Fail-Closed).
- EXECUTION/VALIDATION werden vom `DebugGateway`/`AgentRuntime` ausgeführt,
  AUDIT schreibt `AuditTrail` in jedem Fall (auch bei DENIED).
- Es existiert kein Code-Pfad, der eine Stufe überspringt (Test:
  `AuthorizationChainTest.chain never skips stages`).

### Stufen-Semantik (umgesetzt)

| Stufe | Umsetzung |
|---|---|
| DEVICE | Existenz in `DeviceRegistry` |
| IDENTITY | device_key/d88_version/policy_version konsistent; Policy-Versionsmismatch = WARN (Kette läuft weiter, Neupairing empfohlen) |
| TRUST | ADMIN/CLIENT: nur `TRUSTED`; GAST: nur Gast-Zustandsraum (PAIRED/IDENTIFIED/TRUST_PENDING); REVOKED/BLOCKED: immer abgelehnt |
| ROLE | Rollen-Basisprüfung (CLIENT ohne Project-Scope = Verletzung) |
| PROJECT | Geräteseitige Project-Scope-Prüfung (GAST: keine Projekte, CLIENT: Bindung Pflicht) |
| TASK | Geräteseitige Task-Scope-Prüfung |
| SESSION | Session existiert, Grant aktiv + nicht abgelaufen, Gerät + Projekt-Übereinstimmung |
| CAPABILITY | Schnittmenge Gerät ∩ Session ∩ Policy (GAST-Deny-Liste) |
| POLICY | Risiko-Eskalation (kritische Kategorien → CRITICAL), NETWORK-Deny-by-Default, Gate-Pflichten |
| HUMAN GATE | CRITICAL (bzw. CLIENT ≥ MEDIUM, GAST ≥ HIGH) → Freigabe-Queue, blockiert bis Menschenentscheidung |

## 3. Human Gate (Spec 13)

- Queue mit TTL (Default 5 Minuten). **Timeout = DENY** (sichere Seite).
- Jede Anfrage: `HUMAN_REQUEST`-Audit; Entscheidung: `HUMAN_APPROVAL`/
  `HUMAN_DENIAL`/`HUMAN_TIMEOUT` mit kausalem `parentEventId`.
- UI: „AKTION ERFORDERT FREIGABE“-Karten mit Gerät/Rolle/Projekt/Aufgabe/Aktion/
  Daten/Netzwerk/Risiko und `[ABLEHNEN] [FREIGEBEN]`.

## 4. ADB/Debug-Gateway (Spec 7)

- `AdbCommandPolicy`: Whitelist-Prinzip. Nicht erkannte Kommandos = DENIED
  (Fail-Closed). Ändernde Kommandos = REQUIRES_GATE.
- `DebugGateway` ist der **einzige** Weg zu Geräteaktionen:
  Chain → (Gate) → Adapter.
- `LocalAdbAdapter`: prüft echtes `adb`-Binary. Fehlt es (Standard in einer
  Android-App-Sandbox), meldet das Gateway `TRANSPORT_UNAVAILABLE` und führt
  **nichts** aus – kein vorgetäuschter Erfolg (Abnahmerichtlinie).
- Reale Transport-Adapter (USB/BLE/WLAN/VNC) implementieren `AdbAdapter`
  und folgen mit Hardware-Tests (Roadmap M3).

## 5. Causal Audit (Spec 14)

- `AuditTrail`: jedes Event trägt `eventId, type, actionId, deviceId, sessionId,
  projectId, taskId, capability, policyDecision, authorization, parentEventId,
  timestamp, result, evidence, hash, parentHash`.
- `hash = SHA-256(parentHash | type | actionId | timestamp | result | evidence)`
  → Hash-Kette; Manipulation bricht die Kette und ist in der UI verifizierbar
  („Audit-Kette verifizieren“).
- Persistenz: lokal in `filesDir` (JSON), keine Cloud.

## 6. Sessions & Query-Workers (Spec 10)

- `AgentSessionManager`: Session-Security-Context mit TTL; CLIENT-Projektbindung
  Pflicht, GAST nur GUEST-Bereich; Capabilities dürfen nie über die des Geräts
  hinausgehen; Netzwerk-Default der Session = DENY.
- `AgentRuntime` (Details in `QUERY-WORKERS.md`): Task → Plan → je Schritt
  ActionRequest → Chain → (Gate) → Ausführung → Audit. Worker-Thread pro
  Runtime, UI-Updates asynchron.

## 7. Persistenz & Privacy (Spec 11, 12)

- `JsonStorage`: Geräte + Audit lokal (filesDir). Kein Backup-Dienst, kein Upload.
- Das Manifest deklariert **keine INTERNET-Permission** – Netzwerk ist physisch
  nicht ohne zusätzliche Berechtigung und Policy-Grant verfügbar.
- Keine Telemetrie, keine Analytics, keine Crash-Uploads.

## 8. Build & Tests

- Toolchain: Gradle 8.7 (Wrapper), AGP 8.6.1, Kotlin 1.9.24, JDK 17, compileSdk 35.
- Unit-Tests (JVM, `app/src/test`): Trust-State-Machine, Policy-Engine,
  Autorisierungskette (alle Stufen), Human Gate, Audit-Hash-Kette,
  ADB-Command-Policy, Session-Manager, Planner, DebugGateway, ScopeEngine.
- CI (`.github/workflows/build.yml`): auf Push/PR `:app:testDebugUnitTest` +
  `:app:assembleDebug`; Debug-APK als Artefakt. Release-Job signiert **nur**
  mit Repository-Secrets, sonst sauber übersprungen (kein Fake-Signing).
- Build-Befehle lokal: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

## 9. Simulation

Der Prototyp legt beim ersten Start vier **klar gekennzeichnete SIMULIERTE**
Geräte an (ADMIN/CLIENT/CLIENT/GAST), damit UI und Autorisierungsmodell
interaktiv prüfbar sind. Die UI zeigt einen orangenen Banner
(„SIMULATION – diese Geräte sind Demo-Daten“). Entfernbarkeit: Registry leer
machen (App-Daten zurücksetzen).
