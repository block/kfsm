# KFSM Example - Document Upload Workflow

This module demonstrates a realistic implementation of the kFSM state machine framework with:

- **MySQL database** for persistent storage via Testcontainers
- **Transactional outbox pattern** using `lib-jooq` for reliable effect execution
- **Mock virus scanner** running on a separate thread to simulate async external services
- **Mock file storage** to simulate S3-like storage

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Document Upload Workflow                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────┐    ┌──────────┐    ┌──────────────┐    ┌────────┐ │
│  │ Created │───▶│ Uploading│───▶│ AwaitingScan │───▶│Scanning│ │
│  └─────────┘    └──────────┘    └──────────────┘    └────────┘ │
│                      │                 │                 │      │
│                      ▼                 ▼                 ▼      │
│                 ┌────────┐        ┌────────┐       ┌──────────┐ │
│                 │ Failed │        │ Failed │       │ Accepted │ │
│                 └────────┘        └────────┘       └──────────┘ │
│                                                         │       │
│                                                         ▼       │
│                                                   ┌────────────┐│
│                                                   │ Quarantined││
│                                                   └────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Components

### Domain (`DocumentUpload.kt`)
- `DocumentUpload` - The value being transitioned
- `DocumentState` - Sealed class of all states with transition rules
- `DocumentEffect` - Side effects to execute (upload, scan, notify)

### Transitions (`DocumentTransitions.kt`)
Transition classes with pure `decide()` functions that determine state changes and effects.

### Infrastructure (`infra/`)
- `JooqDocumentRepository` - Persists documents and outbox messages atomically
- `JooqOutbox` (from lib-jooq) - Reads and manages outbox message lifecycle with SKIP LOCKED
- `DocumentOutboxSerializer` - Serializes DocumentEffect for database storage
- `MockVirusScanner` - Simulates async virus scanning on a separate thread
- `MockFileStorage` - In-memory file storage simulation

### Effect Handler (`DocumentEffectHandler.kt`)
Executes effects by calling external services and returns follow-up transitions.

## Running Tests

```bash
# Requires Docker to be running for Testcontainers
./gradlew :example:test
```

## Key Patterns Demonstrated

1. **Transactional Outbox**: State changes and effects are persisted atomically using lib-jooq's `JooqOutbox`
2. **SKIP LOCKED**: Concurrent effect processing without blocking using lib-jooq's entity-based claiming
3. **Async Effect Execution**: Virus scanner runs on separate thread with callbacks
4. **Terminal Effects**: Some effects (notifications, deletions) don't produce follow-up transitions
5. **Pure Decision Logic**: All transitions use `decide()` for testable business logic
6. **Error Handling**: Graceful transitions to Failed state on errors
