# D88 Private Device Fabric

## 1. Zweck

D88 ist eine private, nicht-kommerzielle, lokal-first Agentenplattform.

Verbundene private Geräte werden als D88-Nodes erkannt und eindeutig unterschieden. Die Verbindung eines Gerätes – insbesondere über ADB/Debugging – verleiht dem Gerät **nicht automatisch vollständige Rechte**.

Jedes Gerät besitzt:

* eine eindeutige D88 Device Identity
* eine D88-Rolle
* einen Vertrauensstatus
* definierte Capability-Rechte
* Projekt- und Aufgabenbereiche
* Session-Berechtigungen
* Datenzugriffsbereiche
* Netzwerkrechte
* Debug-/ADB-Rechte
* eine nachvollziehbare Audit-Historie

Grundprinzip:

**Verbunden ≠ vertrauenswürdig ≠ autorisiert ≠ ausführungsberechtigt**

---

# 2. Die drei privaten D88-Geräterollen

## ADMIN

Das persönliche Hauptgerät bzw. ein ausdrücklich als Administrationsgerät freigegebenes Gerät.

### Grundrechte

* vollständiger Zugriff auf die D88-Verwaltung
* Verwaltung verbundener Geräte
* Verwaltung von Projekten
* Verwaltung von Agent Sessions
* Verwaltung von Capabilities
* Verwaltung von Workflows
* Sandbox
* App-Erstellung und App-Deployment
* Debug-/ADB-Verwaltung
* lokale Speicherverwaltung
* Knowledge/RAG-Verwaltung
* Context Graph
* Agent-Konfiguration
* Netzwerk- und Verbindungsverwaltung
* Sicherheits- und Policy-Konfiguration
* Diagnose und Recovery

### Wichtige Einschränkung

ADMIN bedeutet nicht:

> Jede Aktion darf automatisch und unbeaufsichtigt ausgeführt werden.

Kritische Aktionen bleiben Human-in-the-Loop.

Beispielsweise:

* externe Geräte verändern
* Firmware installieren
* Systemkonfiguration verändern
* Daten löschen/überschreiben
* Netzwerkzugriffe aktivieren
* neue Capabilities installieren
* neue MCP-Verbindungen aktivieren
* externe Daten exportieren
* physische Aktionen auslösen
* unbekannte/neu gelernte Funktionen ausführen

Ablauf:

```text
ADMIN
  ↓
Agent/Task
  ↓
Plan
  ↓
Policy Engine
  ↓
CRITICAL?
  ↓
Human Gate
  ├── DENY → Block + Audit
  └── APPROVE → Execute
```

---

# 3. CLIENT

Ein privates Gerät, das D88 hauptsächlich zur Nutzung von Daten, Projekten und Agenten verwendet.

CLIENT ist ausdrücklich **kein halber ADMIN**.

### Standardrechte

* D88 verbinden
* freigegebene Projekte öffnen
* eigene bzw. freigegebene Daten analysieren
* RAG/Knowledge nutzen
* Agent Sessions verwenden
* freigegebene Workflows ausführen
* Ergebnisse anzeigen
* freigegebene Server-Capabilities verwenden
* Dateien im freigegebenen Bereich lesen/schreiben
* freigegebene Netzwerkfunktionen verwenden
* Aufgaben an den D88-Server senden

### Keine automatischen Rechte

Ein CLIENT darf nicht automatisch:

* D88-Policies ändern
* andere Geräte autorisieren
* Admin-Geräte umkonfigurieren
* globale Capabilities aktivieren
* beliebige ADB-Kommandos ausführen
* fremde Projekte lesen
* globale Datenbanken verändern
* Sicherheitsregeln umgehen
* neue externe Verbindungen ohne Freigabe erzeugen

Eine CLIENT-Anfrage wird immer in den Kontext gesetzt:

```text
Client Device
     ↓
Project
     ↓
Task
     ↓
Requested Capability
     ↓
Policy
     ↓
Authorization
     ↓
Human Gate (falls erforderlich)
     ↓
Execution
```

Dadurch kann beispielsweise ein Tablet Daten aus Projekt „Fahrzeug A“ analysieren, ohne Zugriff auf Projekt „Privat B“ zu erhalten.

---

# 4. GAST

GAST ist für Geräte gedacht, die D88 benutzen dürfen, aber nicht Bestandteil des vertrauenswürdigen privaten Geräteverbunds sind.

### Standardrechte

* eigener D88-Speicherbereich
* freigegebene Dateien
* freigegebene Netzwerkfunktionen
* definierte lokale Datenverarbeitung
* ggf. freigegebene Chat-/Agent-Funktionen

### Standardmäßig verboten

* Administration
* ADB
* Debug Gateway
* App Deployment
* Zugriff auf andere private Geräte
* Zugriff auf fremde Projekte
* Zugriff auf globale Memory-/Knowledge-Bereiche
* Capability-Installation
* Policy-Änderungen
* Geräteautorisierung
* globale Datenexporte
* Systemänderungen

GAST wird damit nicht „nutzlos“, sondern erhält einen **isolierten privaten Arbeitsbereich**.

---

# 5. Device Identity

Jedes Gerät erhält eine D88-Identität.

```text
DeviceIdentity
├── device_id
├── device_key
├── device_type
├── platform
├── platform_version
├── d88_version
├── role
├── owner_scope
├── trust_state
├── pairing_state
├── authentication_state
├── capabilities
├── project_scopes
├── task_scopes
├── data_scopes
├── network_scopes
├── debug_scopes
├── session_scopes
├── policy_version
└── created_at / updated_at
```

Sensible Hardwarekennungen werden nur verwendet, soweit sie für die Funktion notwendig sind.

D88 soll keine unnötigen Gerätefingerprints sammeln.

---

# 6. Vertrauensstatus

Ein Gerät besitzt zusätzlich zur Rolle einen Trust State.

```text
DISCOVERED
    ↓
PAIRING_REQUIRED
    ↓
PAIRED
    ↓
IDENTIFIED
    ↓
TRUST_PENDING
    ↓
TRUSTED
```

Mögliche weitere Zustände:

```text
SUSPENDED
REVOKED
EXPIRED
BLOCKED
UNKNOWN
```

Wichtig:

```text
DISCOVERED ≠ TRUSTED
PAIRED ≠ AUTHORIZED
ADB_CONNECTED ≠ ADMIN
```

---

# 7. ADB / Debug Gateway

ADB wird als Transport- und Debugmechanismus integriert.

```text
D88 Server
    │
    ▼
Device Gateway
    │
    ├── Device Registry
    ├── Identity
    ├── Trust
    ├── Role
    └── Policy
    │
    ▼
Debug Gateway
    │
    ▼
ADB Adapter
    │
    ▼
Android Device
```

Der Agent darf **niemals direkt beliebige ADB-Kommandos erzeugen und ausführen**.

Stattdessen:

```text
Agent
 ↓
Capability Request
 ↓
ADB Command Policy
 ↓
Policy Engine
 ↓
Human Gate
 ↓
Debug Gateway
 ↓
ADB Adapter
 ↓
Device
```

Damit wird ADB zu einer kontrollierten D88-Capability.

---

# 8. Device Capabilities

Capabilities werden getrennt von der Rolle verwaltet.

Beispiele:

```text
DEVICE_INFO
APP_INSTALL
APP_REMOVE
APP_START
APP_STOP
LOG_READ
SCREEN_CAPTURE
SCREEN_STREAM
CAMERA
USB
USB_STORAGE
USB_SERIAL
BLE
WLAN
NETWORK
FILE_READ
FILE_WRITE
ADB
SHELL
DEBUG
SANDBOX
REMOTE_AGENT
```

Eine Rolle definiert also nur die **Grundlage**.

Die tatsächlich nutzbare Berechtigung entsteht aus:

```text
Role
+
Trust
+
Capability
+
Project
+
Task
+
Session
+
Policy
```

---

# 9. Projekt- und Aufgabenbindung

Rechte werden nicht nur geräteweit vergeben.

Beispiel:

```text
Tablet
CLIENT

Projekt:
    Werkstatt

Aufgabe:
    Messdaten analysieren

Erlaubt:
    READ_PROJECT_DATA
    LOCAL_ANALYSIS
    RAG_QUERY
    AGENT_SESSION
```

Für eine andere Aufgabe:

```text
Aufgabe:
    Firmware aktualisieren

→ Capability nicht automatisch vorhanden
→ zusätzliche Autorisierung erforderlich
→ Human Gate
→ signierter Auftrag
→ Audit
```

Damit kann ein CLIENT sehr leistungsfähig arbeiten, ohne globale Administratorrechte zu besitzen.

---

# 10. Session-basierte Rechte

Jede Agent Session erhält einen eigenen Security Context.

```text
AgentSession
├── device_id
├── role
├── project_id
├── task_id
├── capabilities
├── data_scope
├── network_scope
├── device_scope
├── policy_version
├── authorization_state
└── human_gate_state
```

Eine Berechtigung kann dadurch nur für eine bestimmte Session gelten.

Beispiel:

```text
CLIENT
  ↓
Projekt A
  ↓
Task 4711
  ↓
CAMERA + LOCAL_ANALYSIS
  ↓
Session
  ↓
Ende
  ↓
Grant verfällt
```

---

# 11. Datenzugriff

D88 trennt:

```text
GLOBAL
PROJECT
TASK
SESSION
DEVICE
PRIVATE
GUEST
```

Ein Gast kann beispielsweise:

```text
GUEST
 └── /guest/<device-id>/
```

nutzen.

Ein Client:

```text
CLIENT
 ├── /projects/project-a/
 └── /personal/<device-id>/
```

Ein Admin kann entsprechend freigegebene globale Bereiche verwalten.

Die Rolle allein entscheidet jedoch nicht über jeden Datenzugriff.

---

# 12. Netzwerkzugriff

Netzwerk wird ebenfalls als Capability behandelt.

Standard:

```text
NETWORK = DENY
```

Wenn erforderlich:

```text
Capability Request
      ↓
Network Policy
      ↓
Target
      ↓
Protocol
      ↓
Purpose
      ↓
Authorization
      ↓
Connection
```

Keine stillen Analytics-, Telemetrie-, Crash- oder Cloud-Verbindungen.

---

# 13. Human-in-the-Loop

Der Human Gate wird zentral umgesetzt.

```text
Action Request
├── action_id
├── device_id
├── role
├── project_id
├── task_id
├── capability
├── target
├── requested_change
├── reason
├── data_flow
├── network_flow
├── risk_class
└── evidence
```

UI:

```text
┌───────────────────────────────────┐
│ AKTION ERFORDERT FREIGABE         │
├───────────────────────────────────┤
│ Gerät: Tablet                     │
│ Rolle: CLIENT                     │
│ Projekt: Werkstatt                │
│ Aufgabe: Diagnose                 │
│ Aktion: Messdaten auswerten       │
│ Daten: lokales Projektarchiv      │
│ Netzwerk: nein                    │
│ Änderung: keine                   │
├───────────────────────────────────┤
│       [ ABLEHNEN ] [ FREIGEBEN ] │
└───────────────────────────────────┘
```

Bei kritischen Aktionen werden zusätzlich Ziel, konkrete Änderung und mögliche Auswirkungen angezeigt.

---

# 14. Causal Audit

Jede relevante Geräteaktion wird kausal verknüpft:

```text
USER_REQUEST
   ↓
TASK_CREATED
   ↓
PLAN_CREATED
   ↓
CAPABILITY_REQUESTED
   ↓
POLICY_EVALUATED
   ↓
HUMAN_APPROVAL
   ↓
DEVICE_ACTION
   ↓
DEVICE_RESULT
   ↓
VALIDATION
   ↓
CONTEXT_UPDATE
```

Jeder Vorgang erhält:

```text
action_id
device_id
session_id
project_id
task_id
parent_event_id
capability
policy_decision
authorization
timestamp
result
evidence
hash/reference
```

Damit kann später nachvollzogen werden:

> Warum wurde diese Aktion auf diesem Gerät ausgeführt?

---

# 15. Geräteübersicht im D88

Die Geräteansicht zeigt beispielsweise:

```text
MEINE GERÄTE

🟢 Pixel 10
   ADMIN
   Vertrauensstatus: TRUSTED
   ADB: verbunden
   Agent: aktiv
   Session: Werkstatt-Diagnose

🟢 Tablet
   CLIENT
   Vertrauensstatus: TRUSTED
   Projekt: Werkstatt
   Agent: bereit

🟡 Zweites Smartphone
   CLIENT
   Vertrauensstatus: eingeschränkt

⚪ Fremdes Gerät
   GAST
   Speicher: erlaubt
   Netzwerk: eingeschränkt
```

Der Agent kann diese Unterschiede eindeutig berücksichtigen.

---

# 16. Geräteaktionen

Für jedes Gerät:

```text
Gerät öffnen
├── Übersicht
├── Rolle
├── Vertrauen
├── Sessions
├── Projekte
├── Capabilities
├── Speicher
├── Netzwerk
├── ADB / Debug
├── Apps
├── Logs
├── Live Status
├── Audit
└── Sicherheit
```

Für ADMIN zusätzlich:

```text
Rolle ändern
Vertrauen widerrufen
Capabilities verwalten
Gerät sperren
Session beenden
ADB deaktivieren
Gerät entfernen
```

Diese Änderungen selbst werden auditiert.

---

# 17. Geräteübergreifende Agentenarbeit

Damit entsteht der eigentliche D88-Vorteil:

```text
                D88 SERVER
                    │
       ┌────────────┼────────────┐
       │            │            │
    ADMIN         CLIENT       CLIENT
       │            │            │
    Kamera        Sensor       Display
       │            │            │
       └────────────┼────────────┘
                    │
                 Agent
                    │
             Context / RAG
                    │
             Causal Audit
```

Ein Agent kann dadurch beispielsweise:

1. auf ADMIN-Gerät A eine Aufgabe planen,
2. Daten von CLIENT-Gerät B anfordern,
3. Sensorinformationen von CLIENT-Gerät C verwenden,
4. lokal analysieren,
5. Ergebnisse zusammenführen,
6. eine Aktion vorbereiten,
7. bei kritischer Aktion auf menschliche Freigabe warten,
8. anschließend die autorisierte Aktion über das zuständige Gerät ausführen.

Dabei werden **nicht automatisch alle Rechte zwischen den Geräten vererbt**.

---

# 18. Grundregel des gesamten Systems

D88 verwendet deshalb folgende Autorisierungskette:

```text
DEVICE
  ↓
IDENTITY
  ↓
TRUST
  ↓
ROLE
  ↓
PROJECT
  ↓
TASK
  ↓
SESSION
  ↓
CAPABILITY
  ↓
POLICY
  ↓
HUMAN GATE
  ↓
EXECUTION
  ↓
VALIDATION
  ↓
AUDIT
```

Der Agent darf niemals eine Ebene überspringen.

---

# 19. Implementierungsstruktur

Die Architektur wird in D88 als eigene Module umgesetzt:

```text
device/
├── DeviceIdentity
├── DeviceRegistry
├── DeviceRole
├── DeviceTrust
├── DeviceSession
├── DeviceScope
├── DeviceCapability
└── DeviceManager

debug/
├── DebugGateway
├── AdbAdapter
├── AdbDeviceDiscovery
├── AdbPairing
├── AdbCommandPolicy
└── DebugSession

authorization/
├── RoleEngine
├── CapabilityEngine
├── ScopeEngine
├── PolicyEngine
├── AuthorizationRequest
├── AuthorizationGrant
└── HumanGate

project/
├── ProjectScope
├── TaskScope
└── SessionScope

audit/
├── DeviceAudit
├── AuthorizationAudit
├── ActionTrace
└── CausalGraph
```

---

# 20. Endzustand

Damit ist D88 für den privaten Geräteverbund eindeutig definiert:

**ADMIN**
→ vollständige private D88-Verwaltung, aber kritische Aktionen mit Human-in-the-Loop.

**CLIENT**
→ leistungsfähige Nutzung von D88, Datenanalyse und serverweiten Funktionen innerhalb von Projekt, Aufgabe, Session und Freigabe.

**GAST**
→ begrenzte Nutzung von Speicher und Netzwerk in isoliertem Umfang.

ADB/Debugging ist lediglich ein Transport-/Steuerkanal und niemals selbst die Autorisierung.

Das gesamte Modell ist lokal-first, privat, nachvollziehbar und geräteübergreifend.

**Zentrale D88-Regel:**

> Ein verbundenes Gerät erhält niemals Rechte allein dadurch, dass es verbunden ist. Der Agent muss Identität, Rolle, Vertrauen, Projekt, Aufgabe, Session, Capability und Freigabe zusammenführen, bevor eine Aktion ausgeführt werden darf.
