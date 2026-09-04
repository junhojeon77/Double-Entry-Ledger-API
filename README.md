# Double-Entry Ledger API

A banking ledger service built in Java 21 / Spring Boot 4 / PostgreSQL, where the
only thing that matters is moving money **correctly** — under concurrency, under
retries, and under the assumption that the application code will eventually have a bug.

Built test-first, one behaviour at a time.

---

## Why this project exists

This is a **learner's project**. I built it to go deeper into Java than a tutorial
takes you, and to understand how banks actually model money — not the buzzwords, but
the specific mechanics: why balances are integers, why history is append-only, why a
retry must not move money twice, and why every rule worth having is enforced in two
places.

It is not a product and it is not trying to be one. Scope is deliberately narrow so
that the parts that exist can be correct rather than merely present. There is no user
registration, no account types, no interest, no statements, no UI.

The interesting problems here are the ones that only appear when two things happen at
the same time, and those are the ones I wanted to actually hit rather than read about.

## A note on AI assistance

I used AI while building this, and I want to be precise about how:
**for learning system design and for learning to write Java.**

That meant asking why a deferred constraint behaves differently from an immediate one,
what an optimistic lock actually does at the SQL level, how JUnit and Testcontainers
fit together, and having my code reviewed and explained back to me. The design
decisions, the schema, the tests and the direction of the project are mine, and the
point of the exercise was to understand the reasoning well enough to defend it — not
to produce code I could not explain.

---

## What it's building toward

One endpoint carries the whole design:

```http
POST /api/v1/transfers
Idempotency-Key: <client-supplied>

{ "fromAccountId": "...", "toAccountId": "...", "amountMinor": 2500, "currency": "CAD" }
```

Everything else exists to make that endpoint impossible to break.

### What one request actually does

Trace a single call and every piece of the project shows up in order:

1. **JWT is verified** — the caller holds `transfers:write` scope, or it's a 401. *(Cycle 7)*
2. **Body is validated** — positive amount, valid ISO currency, two distinct accounts, or a 400 with field-level detail. *(Cycle 7)*
3. **Idempotency key is claimed** — `INSERT` into the idempotency record. If the unique constraint rejects it, this is a retry: return the stored response, don't move money again. *(Cycle 5)*
4. **A transaction opens.** Both accounts load, sorted by ID so lock acquisition order is deterministic. *(Cycle 6)*
5. **`PostingEngine` decides** — sufficient funds, matching currency, both accounts open. It returns a balanced pair or throws. No database, no Spring, nothing injected. *(Cycle 2, done)*
6. **Two rows are written to `posting`**: one `DEBIT` 2500, one `CREDIT` 2500. Positive amounts, direction carrying the sign. *(Cycle 4)*
7. **The source balance updates**, and `@Version` goes from *n* to *n+1*. If a concurrent transfer already bumped it, this update matches zero rows, Hibernate throws, and the request retries with jittered backoff. *(Cycle 6)*
8. **`COMMIT`.** The deferred balance trigger fires and checks that the postings sum to zero. If they don't, the entire transaction dies here. *(Cycle 3, now)*

Steps 3, 7 and 8 are the reason this project is worth building. Step 5 is the reason it's testable in 70ms.

### The four properties, and their two layers

| Property | App layer | DB layer |
|---|---|---|
| Books always balance | `PostingEngine` returns balanced pairs | `posting_must_balance`, deferred to `COMMIT` |
| Retries don't double-spend | Idempotency record + stored response | `UNIQUE (client_id, idempotency_key)` |
| Concurrent debits can't overdraw | `@Version` optimistic lock | `CHECK` on balance vs overdraft limit |
| Posted history never changes | Nothing issues `UPDATE` | Append-only trigger + role `GRANT`s |

The doubling is the whole point. The left column is what I intended. The right column is
what happens when the left column has a bug — and it will, because everything does. That
gap is the difference between "I used optimistic locking" and "I assumed my application
code would eventually be wrong."

### Why this reads as bank work

A CRUD app demonstrates you can wire a framework. This demonstrates that money is
different: retries are inevitable so operations must be idempotent, concurrency is
adversarial so invariants need enforcement below the application, and history is evidence
so corrections are reversals rather than edits.

Those three ideas are the actual content of a payments or core-banking role. The Spring
Boot, the JWT, the Docker — those are table stakes you'd learn in a month on the job. The
reasoning is what transfers.

## Stack

| | |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.1 |
| PostgreSQL | 16 |
| Flyway | 12.4.0 (+ `flyway-database-postgresql`) |
| Testcontainers | 2.0.5 |
| Build | Maven wrapper |

No Lombok (several banks ban it; Java 21 records cover the value objects).
No H2 — it doesn't reproduce Postgres locking, deferred constraints or trigger
behaviour, so an H2 suite would pass while the concurrency bugs shipped.

---

## Running it

Requires **JDK 21** and a **running Docker daemon** — Testcontainers is not optional here.

```bash
# start the local database
docker compose up -d

# run the app against it
./mvnw spring-boot:run
```

To run against a throwaway container instead of the compose database, run
`TestLedgerApplication` — same app, fresh database, migrations applied from scratch.

### Tests

```bash
./mvnw test -Dtest='MoneyTest,PostingEngineTest'   # pure domain, ~50ms
./mvnw test                                         # everything, starts containers
```

Current state — **24 tests, all green**:

| Suite | Tests | Time | What it covers |
|---|---|---|---|
| `MoneyTest` | 12 | 30ms | currency validation, overflow, arithmetic |
| `PostingEngineTest` | 10 | 16ms | the accounting rules and every rejection |
| `SchemaConstraintsTest` | 1 | 7s | canary: migrations apply against real Postgres |
| `LedgerApplicationTests` | 1 | 14s | full context boots and migrates |

---

## Progress

- [x] **Cycle 1 — `Money`** value object; currency validation, overflow-safe arithmetic
- [x] **Cycle 2 — `PostingEngine`** balanced pairs, four validation rules, no framework
- [ ] **Cycle 3 — schema constraints** proving the database rejects what it should
- [ ] **Cycle 4 — `TransferService`** the transactional core
- [ ] **Cycle 5 — idempotency** same key twice moves money once
- [ ] **Cycle 6 — concurrency** 50 threads, one account, no overdraw; then deadlock ordering
- [ ] **Cycle 7 — web layer, security, OpenAPI, CI**

Cycles 1 and 2 are green: `Money` and `PostingEngine`. That's row one of the table, left
column — the accounting rules are locked down and provably correct in isolation.

Cycle 3 is the right column: proving the database independently refuses everything the
domain refuses. When it's green there are both layers of one guarantee, and a working
pattern for the other three.

Cycle 6 is the one this project is really for. The test gets written *before* the fix,
so the overdraw actually happens and the fix has something to prove.

---

## How it's built

Test-first, strictly. Red before green, always — a test that has never failed proves
nothing, since it may be asserting something already true or not running at all. Each
green is a commit, so the history shows the rhythm.

Every file carries a header explaining what it is, what it does, what it deliberately
does *not* do, and where it's going. Every test carries its inputs and expected output
plus the reason it exists. The comments are there because I wrote this to learn from,
and code that can't explain itself teaches nothing six months later.
