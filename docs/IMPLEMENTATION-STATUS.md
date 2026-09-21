# D88 – Implementierungsstand

Version: 0.1.0-prototype · Stand: `arena/01a0c254-d88`

## 1. Was umgesetzt ist

### Kern (reines Kotlin/JVM, unit-getestet)
| Bereich | Status |
|---|---|
| 13-stufige Autorisierungskette (Spec 18) | ✅ umgesetzt + getestet (Stufen-Reihenfolge, Fail-Closed, keine Überspringung) |
| Trust-State-Machine (Spec 6) | ✅ Hauptpfad + Sonderzustände, Invarianten getestet |
| Rollen ADMIN/CLIENT/GAST (Spec 2–4) | ✅ RoleEngine, GAST isoliert, CLIENT Projektbindung Pflicht |
| Device Identity (Spec 5) | ✅ vollständiges Datenmodell, Policy-Version, ohne Fingerprinting |
| Capabilities (Spec 8) | ✅ 22 Capabilities, Schnittmenge Gerät∩Session∩Policy |
| Projekt-/Aufgabenbindung (Spec 9) | ✅ ScopeEngine + Chain-Tests |
| Sessions (Spec 10) | ✅ Session-Security-Context, TTL, Grant-Verfall, Capping an Geräte-Capabilities |
| Datenzugriff (Spec 11) | ✅ DataScope-Trennung, GAST nur GUEST, Datenfluss-Prüfung |
| Netzwerk-Deny-by-Default (Spec 12) | ✅ Policy + Session + Gerät; keine INTERNET-Permission im Manifest |
| Human Gate (Spec 13) | ✅ Queue, TTL-Timeout=DENY, Freigabe-/Ablehnungs-UI, kausales Audit |
| Causal Audit (Spec 14) | ✅ Event-Typen der Spec, SHA-256-Hash-Kette, Tampering-Nachweis, UI-Verifikation |
| Geräteübersicht/-aktionen (Spec 15/16) | ✅ UI + Verwaltungsaktionen (Suspend/Sperren/Widerrufen/Entfernen/Rollenwechsel) – selbst auditiert |
| ADB/Debug-Gateway (Spec 7) | ✅ AdbCommandPolicy (Whitelist, Fail-Closed), DebugGateway, LocalAdbAdapter |
| Implementierungsstruktur (Spec 19) | ✅ Pakete device/, debug/, authorization (in core/), project (ScopeEngine), audit (in core/) |

### UI (Android, Kotlin + XML)
Dashboard (Systemstatus, Geräte, Simulation-Banner) · Agent-Chat (Task →
Plan → Kette → Gate) · Geräteverwaltung + Gerätedetail · Human-Gate-Queue
(Spec-UI) · Live Observatory (Audit-Stream, Tasks, Kettenverifikation) ·
Security-Ansicht (Policy, Privacy-Defaults, Rollenmatrix) · Wissen/RAG
(lokal, Keyword-Retrieval).

### Build & Prozesse
- Gradle 8.7 Wrapper, AGP 8.6.1, Kotlin 1.9.24, JDK 17, compileSdk 35, minSdk 26
- GitHub Actions: Unit-Tests + Debug-APK als Artefakt (Push/PR/Manual)
- Release-Job: signiert **nur** mit Repository-Secrets, sonst sauber übersprungen

## 2. Was verifiziert ist

- Unit-Tests der Kernlogik laufen in der CI (JVM) – siehe Workflow-Run.
- Debug-APK wird in der CI erzeugt (Artefakt `d88-debug-apk`).

## 3. Was NIECHT fertig ist (bewusst nicht behauptet)

1. **Keine signierte Release-APK** – Secrets fehlen noch (siehe RELEASE-CHECK).
2. **Keine echten Geräte-Transporte** – ADB/BLE/WLAN/VNC-Adapter sind
   Interfaces + lokaler ADB-Adapter; gegen echte Geräte ungetestet (M3).
   Die App meldet `TRANSPORT_UNAVAILABLE` statt Fake-Erfolg.
3. **Kein kryptografisches Pairing-Protokoll** – Statusautomat + Modell,
   kein Challenge-Response (M1).
4. **Kein reales Discovery-Protokoll** – Geräte werden registriert/gelegt
   (Simulation), nicht vom Netzwerk erkannt (M3).
5. **RAG = lokale Keyword-Suche** – kein Embedding-Index (M2).
6. **Multi-Device-Orchestrierung** – Modell vorhanden (Spec 17),
   serverseitige Koordination über mehrere Geräte folgt (M2).

## 4. Bekannte Einschränkungen des Prototyps

- UI läuft gegen einen lokalen Core (eingerichtete Geräte + Simulation);
  „Geräteverbinden“ aus dem Netzwerk ist nicht Teil des Prototyps.
- Agent-Planner ist regelbasiert/deterministisch (kein LLM) – bewusst,
  damit jede Entscheidung nachvollziehbar und testbar bleibt.
- Audit-Persistenz ist JSON (einfach, lokal); für große Historien folgt
  eine SQLite-Variante.
