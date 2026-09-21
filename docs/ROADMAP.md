# D88 – Roadmap

## M0 – Prototyp (aktuell) ✅

- Vollständige Autorisierungskette (13 Stufen) als getestete Kernlogik
- Rollen ADMIN/CLIENT/GAST, Trust-State-Machine, Capabilities, Scopes, Sessions
- Human Gate mit Queue, Timeout-DENY, kausalem Audit
- Causal Audit mit SHA-256-Hash-Kette + UI-Verifikation
- ADB-Command-Policy (Whitelist, Fail-Closed) + DebugGateway mit ehrlichem
  Transport-Handling
- Interaktive UI: Dashboard, Agent-Chat, Geräteverwaltung, Freigaben,
  Observatory, Security, Wissen/RAG (lokal)
- GitHub Actions: Unit-Tests + Debug-APK als Artefakt
- Klar gekennzeichnete Simulation für Demo-Daten

## M1 – Pairing & Identität (kryptografisch)

- Challenge-Response-Pairing-Protokoll (kein Modell mehr)
- Device-Keys im Android Keystore, Attestation wo verfügbar
- Entdecken von Geräten (lokales Discovery-Protokoll), Pairing-Flow in der UI
- Wiederherstellung nach Verlust (Recovery-Code, auditiert)

## M2 – Echter Agent-Loop & Workflows

- Mehrstufige Pläne mit Abhängigkeiten (aktuell: sequenziell)
- Workflow-Definitionen (speicherbar, pro Projekt, mit eigenen Scopes)
- Geräteübergreifende Orchestrierung (Daten von CLIENT B → Analyse → Aktion
  auf Gerät A) ohne Rechte-Vererbung
- Knowledge/RAG: lokaler Embedding-Index (statt Keyword-Suche)

## M3 – Reale Transports

- Transport-Adapter: USB (ADB), WLAN (adb-over-network), BLE, VNC
  (alle hinter `AdbAdapter`/gemeinsamem Transport-Interface)
- Hardware-Tests auf echten Geräten; Transport-Zustände in Observatory live
- UI-Authentifizierung (Biometrie/PIN) vor kritischen Freigaben
- Debug-Gateway-Last- und Fehlertest (Timeouts, Abbrüche, Doppelbefehle)

## M4 – Release

- Signierte Release-APK (Secrets-basiert, siehe RELEASE-CHECK)
- Sideloading-Anleitung + Prüfsummen der Artefakte
- Stabilitäts-Härtung, Lint-Schwellwerte, Versionsverträglichkeit
  (Policy-Versionen, Migrationsregeln)

## explizit NICHT geplant

- Cloud-Dienste, Accounts, Telemetrie, kommerzielle Distribution
- Multi-Owner / öffentliches Multi-Tenant
