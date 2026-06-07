# Architecture Decision Record — Transaction Service
> Java Code Challenge — Mendel  
> Stack: Java 17 · Spring Boot 3 · In-Memory (production: AWS + DynamoDB)

---

## Table of Contents

1. [Domain Model](#1-domain-model)
2. [Accumulated Sum Strategy](#2-accumulated-sum-strategy)
3. [Upward Tree Navigation](#3-upward-tree-navigation)
4. [Propagation Strategy](#4-propagation-strategy)
5. [Consistency and Atomicity](#5-consistency-and-atomicity)
6. [Concurrency](#6-concurrency)
7. [Idempotency](#7-idempotency)
8. [In-Memory Implementation](#8-in-memory-implementation)
9. [DynamoDB Schema Design](#9-dynamodb-schema-design)
10. [API Contract](#10-api-contract)

---

## 1. Domain Model

### Decision adopted

The `Transaction` entity contains the following attributes:

| Attribute | Type | Description |
|---|---|---|
| `id` | `long` | Unique identifier, provided by the client in the PUT path |
| `amount` | `double` | Transaction amount. Can be negative to represent a correction |
| `type` | `String` | Transaction category |
| `parentId` | `Optional<Long>` | Reference to the parent node, optional |
| `path` | `String` | Materialized path from the root, e.g.: `"10/11/12"` |
| `accumulatedSum` | `double` | Own amount + all descendants |
| `version` | `long` | Version for optimistic locking |
| `propagated` | `boolean` | Indicates whether propagation to ancestors was completed |
| `createdAt` | `String` | ISO creation timestamp |

**On corrections:** an existing transaction is never updated. In case of an error, a new transaction with a negative `amount` is created to compensate the incorrect value. The ancestors' `accumulatedSum` is updated incrementally with that negative value, just like any other transaction.

---

## 2. Accumulated Sum Strategy

### Decision adopted

Each node maintains its `accumulatedSum` updated incrementally. When a new node is inserted, its `amount` is added to all its ancestors. `GET /sum/{id}` is O(1) — it only reads the `accumulatedSum` field of the node.

### Discarded decision

**Lazy tree traversal on each query**
Calculate the sum by traversing all descendants at the time of `GET /sum/{id}`. Discarded because it is O(n) per query. With many reads and an ever-growing tree, this strategy does not scale.

---

## 3. Upward Tree Navigation

### Decision adopted

Each node stores its **materialized path** from the root as a hierarchical string:

```
tx10 → path: "10"
tx11 → path: "10/11"
tx12 → path: "10/11/12"
```

The path is built at the time of the `PUT`:

```
if parentId present:  path = parent.path + "/" + transactionId
if parentId absent:   path = String(transactionId)
```

When propagating, the path is parsed to obtain all `ancestorIds` and a single `BatchGetItem` is performed — all ancestors in a single database call.

### Discarded decisions

**N serial roundtrips**
Navigate node by node reading each `parentId` until reaching the root. Discarded because it is O(depth) in roundtrips — costly in latency and DynamoDB read units.

**`ancestorIds` list as an attribute**
Store `[10, 11]` in each node instead of a path string. Discarded in favor of the materialized path: more compact, readable, debuggable, and enables future subtree queries with `begins_with(path, "10/11")`.

---

## 4. Propagation Strategy

### Decision adopted

**Asynchronous propagation** via DynamoDB Streams → SQS → Lambda:

```
Spring Boot API
    → writes tx to DynamoDB (propagated = false)
    → DynamoDB Streams detects INSERT
    → sends event to SQS
    → Lambda SumPropagationHandler consumes the event
    → reads all ancestors with BatchGetItem (1 call)
    → updates accumulatedSum with TransactWrite + optimistic locking
    → marks tx.propagated = true
```

**Why asynchronous:**
- `PUT` is always fast and independent of tree depth.
- Scales horizontally without contention between writes.
- Retries handled by SQS itself (maxReceiveCount). If exhausted, the message goes to the DLQ for manual inspection and reprocessing.
- The `eventName = INSERT` filter on the Event Source Mapping prevents propagation loops.

**Known limitation:** `GET /sum/{id}` may return a stale value during the propagation window (eventual consistency).

### Discarded decision

**Synchronous propagation**
Propagate in the same thread as the `PUT`. Discarded because `PUT` latency grows linearly with tree depth, and under high concurrency it generates contention on the most frequently accessed ancestor nodes.

---

## 5. Consistency and Atomicity

### Decision adopted

The `propagated` field on the `Transaction` entity itself acts as a state indicator:

- `propagated = false` → transaction saved, propagation pending or failed.
- `propagated = true` → propagation successfully completed.

Transactions without `parentId` are initialized with `propagated = true` — they have no ancestors to update.

In case of failure, the message remains in the SQS queue and is automatically retried. If the configured retries are exhausted, it is sent to the DLQ for manual inspection and reprocessing.

`propagated = false` is written in the initial `TransactWrite` together with the transaction. The Lambda updates `propagated = true` in the same `TransactWrite` that updates the ancestors — guaranteeing complete atomicity.

---

## 6. Concurrency

### Decision adopted

**Optimistic locking with a `version` field per node.**

Each `UpdateItem` on an ancestor includes:

```
ConditionExpression: version = :expected
UpdateExpression:    SET accumulatedSum = accumulatedSum + :amount,
                         version = version + 1
```

If the condition fails due to a version conflict, the Lambda retries with exponential backoff re-reading the current version. No distributed locks — each node is updated independently within the `TransactWrite`.

---

## 7. Idempotency

There are two instances where idempotency is guaranteed:

**Transaction creation**
The `PUT` uses `ConditionExpression: attribute_not_exists(transactionId)` when writing to DynamoDB. If the `transactionId` already exists, the operation fails with `409 Conflict`. This guarantees that the same id cannot be registered twice, regardless of how many times the request is received.

**Propagation to ancestors**
Idempotency is guaranteed via a `ConditionExpression` within the same `TransactWrite` that updates the ancestors:

```
ConditionExpression: propagated = false
UpdateExpression:    SET propagated = true, accumulatedSum = ...
```

No prior read of the `propagated` field is performed — between a read and a write, another instance could have completed the propagation, causing a double update of `accumulatedSum`. The condition is evaluated atomically in DynamoDB at write time.

If the condition fails → `propagated` was already `true` → propagation was completed by another process → the event is silently discarded.

---

## 8. In-Memory Implementation

The in-memory implementation (`InMemoryTransactionRepository`) simulates DynamoDB behavior using Java concurrency primitives. Each AWS behavior has a direct equivalent.

### Atomic creation — `save`

| DynamoDB | In-memory |
|---|---|
| `PutItem` with `ConditionExpression: attribute_not_exists(transactionId)` | `ConcurrentHashMap.putIfAbsent(id, transaction)` |

`putIfAbsent` is atomic in `ConcurrentHashMap`: if the id already exists it returns the existing value (non-null), and the repository throws `TransactionAlreadyExistsException`. There is no race condition window between the check and the write.

### Ancestor read — `findAncestors`

| DynamoDB | In-memory |
|---|---|
| `BatchGetItem` with list of `transactionId` extracted from the path | Path parsing + direct `ConcurrentHashMap` read per id |

All path segments except the last one (which is the transaction itself) are parsed, and the `Transaction` objects are returned with their current state — including `version` and `accumulatedSum` — to be used by `TransactionPropagatorService` to compute the new values.

### Atomic propagation — `applyPropagation`

| DynamoDB | In-memory |
|---|---|
| `TransactWrite` — all-or-nothing operation with `ConditionExpression` per item | `ReentrantLock` covering validation + update of all ancestors + marking `propagated` |

The operation under lock does three things in sequence, with no possibility of interleaving:

1. **Idempotency guard**: verifies `transaction.isPropagated() == false`. If already `true`, throws `AlreadyPropagatedException` — equivalent to DynamoDB's `ConditionExpression: propagated = false`.

2. **Version validation**: iterates all `AncestorUpdate` entries and verifies `current.version == update.expectedVersion()`. If any does not match, throws `VersionConflictException` — equivalent to DynamoDB's `ConditionExpression: version = :expected`. The entire operation fails if any version is incorrect (all-or-nothing).

3. **Apply**: updates `accumulatedSum` on each ancestor and increments its `version`. Marks `transaction.propagated = true`.

The computation of the new `accumulatedSum` (`ancestor.accumulatedSum + amount`) is the responsibility of `TransactionPropagatorService` before calling the repository. The repository only receives the final value through the `AncestorUpdate(transactionId, newAccumulatedSum, expectedVersion)` record.

### Conflict handling and retries

`TransactionPropagatorService` implements the retry loop on `VersionConflictException`:

```
for each attempt (max. MAX_RETRIES):
    findAncestors(path)           ← re-reads fresh versions
    buildUpdates(ancestors)       ← recomputes new values
    applyPropagation(tx, updates) ← attempts the atomic write
    if VersionConflictException → retry
    if AlreadyPropagatedException → discard (already propagated by another process)
```

Each retry starts from a fresh `findAncestors`, guaranteeing that the versions used in validation are current at the time of the attempt. This replicates the behavior of the production Lambda, which re-reads with `BatchGetItem` before each `TransactWrite`.

### Equivalence summary

| AWS mechanism | In-memory equivalent |
|---|---|
| `PutItem` + `attribute_not_exists` | `ConcurrentHashMap.putIfAbsent` |
| `BatchGetItem` | Direct map read by list of ids |
| `TransactWrite` all-or-nothing | `ReentrantLock` covering validation + write |
| `ConditionExpression: version = :expected` | `version` comparison under lock |
| `ConditionExpression: propagated = false` | `isPropagated()` check under lock |
| Lambda retry on `TransactWrite` failure | Retry loop in `TransactionPropagatorService` |

---

## 9. DynamoDB Schema Design

### TransactionTable

| Attribute | Type | Role |
|---|---|---|
| `transactionId` | Long | PK |
| `amount` | Double | |
| `type` | String | GSI PK → TypeIndex |
| `parentId` | Long (nullable) | |
| `path` | String | materialized path |
| `accumulatedSum` | Double | |
| `version` | Long | optimistic locking |
| `propagated` | Boolean | propagation state |
| `createdAt` | String (ISO) | |

### GSI — TypeIndex

| Attribute | Role |
|---|---|
| `type` | PK |
| `transactionId` | SK |
| projection | ALL |

`ALL` because `GET /types/{type}` returns the list of ids without needing an additional `GetItem` per result.

### Access patterns

| # | Operation | Mechanism | Complexity |
|---|---|---|---|
| AP1 | Get tx by id | `GetItem(transactionId)` | O(1) |
| AP2 | Get ids by type | `Query GSI TypeIndex` | O(1) |
| AP3 | Read ancestors | `BatchGetItem(ancestorIds)` | O(1) roundtrips |
| AP4 | Atomic write | `TransactWrite (max 25 items)` | O(depth) |

### Known constraint

The 25-item limit per `TransactWrite` implies a maximum of 24 depth levels per operation. For deeper trees, a partitioned propagation strategy would be required — documented as a future improvement.

---

## 10. API Contract

### Endpoints

#### `PUT /transactions/{transactionId}`

```json
{ "amount": 5000.0, "type": "cars", "parent_id": 10 }
```

| Code | Situation |
|---|---|
| `201 Created` | Transaction created successfully |
| `409 Conflict` | The `transactionId` already exists |
| `404 Not Found` | The referenced `parent_id` does not exist |
| `400 Bad Request` | Validation failed (empty type, invalid id) |

#### `GET /transactions/types/{type}`

`200 OK` always — empty list if no results. Response: `[10, 11, 12]`

#### `GET /transactions/sum/{transactionId}`

| Code | Situation |
|---|---|
| `200 OK` | `{ "sum": 20000.0 }` |
| `404 Not Found` | Transaction does not exist |

### Error handling — uniform structure

```json
{
  "error": "TRANSACTION_ALREADY_EXISTS",
  "message": "Transaction with id 10 already exists",
  "timestamp": "2026-06-07T12:00:00Z"
}
```

Handled centrally with `@RestControllerAdvice`.
