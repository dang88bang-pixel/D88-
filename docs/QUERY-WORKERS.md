# D88 – Query-Workers / Agent-Runtime

## Zweck

Query-Workers verarbeiten Agent-Aufgaben asynchron, deterministisch nachvollziehbar
und strikt über die Autorisierungskette. Sie sind die Umsetzungsstelle für
„geräteübergreifende Agentenarbeit“ (Spec Abschnitt 17) auf Prototyp-Ebene.

## Modell

```
AgentTask (deviceId, sessionId, intent, projectId)
   │
   ├─ USER_REQUEST (Audit)
   ├─ TASK_CREATED (Audit, parent = USER_REQUEST)
   ├─ Planner.plan(task) → AgentPlan (Steps mit Capability, Category, Risk, AdbCommand?)
   ├─ PLAN_CREATED (Audit, parent = TASK_CREATED)
   │
   └─ je Step (sequenziell, Worker-Thread):
        ActionRequest (actionId = act-<taskId>-<stepId>)
        │
        ├─ CAPABILITY_REQUESTED (Audit)
        ├─ AuthorizationChain.evaluate()
        │     ├─ DENIED             → StepState.DENIED
        │     ├─ HUMAN_GATE_REQUIRED → StepState.WAITING_HUMAN (Queue)
        │     └─ APPROVED
        │           ├─ ADB-Schritt → DebugGateway → Adapter
        │           │     ├─ realTransport → Executed
        │           │     └─ ohne Transport → TRANSPORT_UNAVAILABLE (ehrlich)
        │           └─ lokaler Schritt → Executed (lokal)
        ├─ DEVICE_ACTION / DEVICE_RESULT / VALIDATION (Audit)
        └─ CONTEXT_UPDATE (Audit, beim Task-Ende)
```

## Schrittzustände

`PENDING → RUNNING → { APPROVED / DONE | DENIED | WAITING_HUMAN | TRANSPORT_UNAVAILABLE }`

- `WAITING_HUMAN` blockiert **nur diesen Schritt** – der Worker liefert ihn in
  die Gate-Queue und der Task bleibt für die UI sichtbar.
- `resumeAfterApproval(actionId)`/`denyAction(actionId)` führt den Schritt
  nach Menschenentscheidung weiter (EXECUTION + VALIDATION + Audit).

## Threading

- Ein dedizierter Worker-Thread pro `AgentRuntime` (Daemon).
- UI-Kopplung nur über Listener-Callbacks → Main-Thread-Handler (ViewModel).
- Blockierende Transport-Aufrufe (z. B. `adb`-waitFor) laufen ausschließlich
  auf Worker-/Executor-Threads, nie auf dem Main-Thread.

## Ehrlichkeitsregeln (Abnahmerichtlinie)

1. Kein Mock-Erfolg: fehlender Transport → `TRANSPORT_UNAVAILABLE` + Audit.
2. Keine Kommandos außerhalb der ADB-Whitelist (Planner-Test erzwingt dies).
3. Keine Aktion ohne gültigen Session-Grant und bestandener Kette.
4. Simulation nur als solche gekennzeichnet (UI-Banner, `simulated`-Flag).

## Erweiterung M2/M3

- Schritt-Abhängigkeiten (DAG statt Sequenz)
- Geräteübergreifende Pläne: Schritt auf Gerät B (Daten), Gerät A (Aktion) –
  jeweils eigene Kette, keine Rechte-Vererbung
- Rückgabedaten von Steps als Evidence im Audit (hash-verknüpft)
- Backpressure/Queue-Größenlimits für parallele Tasks
