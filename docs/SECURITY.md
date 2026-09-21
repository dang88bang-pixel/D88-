# D88 – Security-Dokumentation

## 1. Grundprinzip

**Verbunden ≠ vertrauenswürdig ≠ autorisiert ≠ ausführungsberechtigt.**
Jede Geräteaktion durchläuft die 13-stufige Kette
(Details: `TECHNICAL-DOCUMENTATION.md`, `README.md` Abschnitt 18).
Fail-Closed an jeder Stufe; kein Code-Pfad mit Stufen-Sprung.

## 2. Threat Model (Threats des Prototyps und Gegenmaßnahmen)

| # | Threat | Gegenmaßnahme im Prototyp | Status |
|---|--------|---------------------------|--------|
| T1 | Fremdes Gerät im LAN meldet sich an | Device Identity + Pairing-Status; DISCOVERED ≠ TRUSTED; GAST bleibt im isolierten GUEST-Bereich | umgesetzt (Modell), Discovery-Protokoll folgt M3 |
| T2 | Kompromittiertes CLIENT-Gerät greift fremde Projekte zu | Project-Scope + Session-Bindung pro Projekt; Kette bricht bei PROJECT-Stage | umgesetzt + getestet |
| T3 | Agent erzeugt beliebige ADB-Kommandos | `AdbCommandPolicy` Whitelist, Fail-Closed; Agent erhält kein Shell; Kommandos nur aus geprüften Plan-Schritten | umgesetzt + getestet |
| T4 | Kritische Aktion läuft unbeaufsichtigt | Human Gate (CRITICAL immer, CLIENT ≥ MEDIUM, GAST ≥ HIGH), Timeout = DENY | umgesetzt + getestet |
| T5 | Stille Netzwerk-/Telemetrie-Verbindungen | NETWORK default DENY (Policy + Session + Gerät); keine INTERNET-Permission im Manifest | umgesetzt |
| T6 | Audit-Manipulation | SHA-256-Hash-Kette mit parent-Link; UI-verifizierbar; Tampering-Tests | umgesetzt + getestet |
| T7 | Rechte-Vererbung über Geräte/Workflows | Keine Vererbung:effective Berechtigung immer aus Gerät+Session+Policy neu berechnet; Geräteübergreifende Arbeit über Server-Orchestrierung (Roadmap M3) | Modell umgesetzt, Multi-Device-Orchestrierung folgt |
| T8 | Key-/Identitäts-Replay | device_key + pairing/auth-State; Endzustände REVOKED/BLOCKED terminal; EXPIRED erzwingt Re-Pairing | Modell umgesetzt, kryptografische Attestation folgt M3 |
| T9 | Vorgetäuschte Funktionalität (Mock als „fertig“) | Abnahmerichtlinie: fehlender Transport = `TRANSPORT_UNAVAILABLE`; Simulation nur als Banner gekennzeichnet | umgesetzt |

## 3. Human-Gate-Semantik

- Pflichten: CRITICAL (alle Rollen, inkl. ADMIN), CLIENT ab MEDIUM-HIGH, GAST ab HIGH.
- Kritische Kategorien (unabhängig von deklariertem Risiko): CONFIG_CHANGE,
  DATA_DELETE, DATA_EXPORT, FIRMWARE, NETWORK_ENABLE, CAPABILITY_INSTALL,
  MCP_CONNECT, PHYSICAL, UNKNOWN_FUNCTION.
- UI zeigt: Gerät, Rolle, Projekt, Aufgabe, Aktion, Datenfluss, Netzwerk, Risiko.
- Entscheidungen werden mit kausalem Parent-Link auditiert; Timeout = DENY.

## 4. Schlüssel- und Identitätsverwaltung

- `device_id` (stabil) + `device_key` (Pairing-Kennwert) pro Gerät.
- Keine unnötigen Hardwarekennungen; kein Geräte-Fingerprinting.
- **Offen für M3:** Schlüsselableitung/Attestation (z. B. Key-Attest auf
  Android), Key-Rotation, sichere Speicherung von Pairing-Geheimnissen
  (Android Keystore), Pairing-Protokoll über sichere Transportkanäle.
- Release-Keystore: ausschließlich über GitHub Secrets (Base64 + Passwörter);
  niemals im Repository oder Chat.

## 5. Datenhaltung

- Lokal (filesDir, JSON). Keine Cloud, keine Backup-Dienste, keine Analytics.
- Datenbereiche: GLOBAL/PROJECT/TASK/SESSION/DEVICE/PRIVATE/GUEST
  (GAST nur `/guest/<device-id>/`-äquivalenter Bereich).

## 6. Verbleibende Angriffsflächen (ehrliche Einschätzung)

1. **Kein Peer-to-Peer-Transport** noch implementiert – aktuell nur lokaler
   Server + optionaler lokaler ADB-Adapter. Netzwerk-Aspekte sind Policy-
   modelliert, aber ohne echten Gegenpart.
2. **Pairing ist modellhaft** (Statusautomat), noch ohne kryptografisches
   Challenge-Response-Protokoll.
3. **UI-Authentifizierung** des Admin-Geräts (z. B. Biometrie vor kritischen
   Freigaben) folgt M3.
4. Single-User-Annahme: D88 ist für einen privaten Verbund ausgelegt;
  Multi-Owner wird nicht unterstützt.
