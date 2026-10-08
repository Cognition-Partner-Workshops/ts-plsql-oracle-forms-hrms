# Encryption Key Rotation Design (Step 1)

> **Status:** Draft for review. **Scope:** design only. No code, DDL or data in this repository is changed, and **no key material appears in this document**.
> **Target:** the current Oracle Database 19c system (`PKG_SECURITY`), before any Java migration.
> **Related:** [`TECHNICAL_DEBT_REPORT.md`](TECHNICAL_DEBT_REPORT.md) SEC-03, DRIFT-01; [`DATA_DICTIONARY.md`](DATA_DICTIONARY.md) sensitive data register; [`SESSION_MIGRATION_DESIGN.md`](SESSION_MIGRATION_DESIGN.md) (step 3).

## 1. Context and goals

The AES key used for SSN encryption is a string literal in `plsql/packages/PKG_SECURITY.pkb:7`. It is in git history, and anyone with `ALL_SOURCE`/`DBA_SOURCE` access on the schema can read it. **It must be treated as compromised.** Rotating it is the only Critical fix that can ship in the current Oracle system without waiting for the migration.

| # | Goal |
|---|---|
| G1 | No key material in source code, git, deployment scripts, logs or `*_SOURCE` views. |
| G2 | Re-encrypt every existing ciphertext under a new key; afterwards the leaked key decrypts nothing that is current. |
| G3 | Rotation becomes a repeatable operation (versioned keys) rather than a code release. |
| G4 | Fix the cipher weaknesses on the way: random IV per value, integrity check, no silent decrypt failures. |
| G5 | Decryption is restricted and audited. |
| G6 | Zero downtime for Forms users; restartable and reversible until the old key is destroyed. |

**Non-goals:** password hashing (SEC-04, step 2), the Java target design (§9 notes compatibility only).

## 2. Current state (as-is, from source)

| Item | Evidence | Observation |
|---|---|---|
| Key | `PKG_SECURITY.pkb:7` | `c_encryption_key RAW(32) := UTL_RAW.CAST_TO_RAW('<redacted>')`. Package-body constant. The literal is **30 bytes**. |
| Cipher | `PKG_SECURITY.pkb:179-190` (`encrypt_ssn`), `:192-206` (`decrypt_ssn`) | `DBMS_CRYPTO.ENCRYPT_AES256 + CHAIN_CBC + PAD_PKCS5`, **no `iv` argument** (so the IV is fixed: same SSN gives the same ciphertext), no MAC. Output is `RAWTOHEX`. |
| Key length | same | *Inferred:* AES-256 needs a 32-byte key. With a 30-byte key, `DBMS_CRYPTO` most likely raises a key-length error (ORA-28234). If so, `encrypt_ssn` has **never worked**, and any SSN ciphertext in production was produced by something not in this repo. Must be confirmed in discovery (§6, R0). |
| Error handling | `PKG_SECURITY.pkb:203-205` | `decrypt_ssn` catches `WHEN OTHERS` and returns the string `'***DECRYPT_ERROR***'`: failures are silent and look like data. |
| Callers | repo-wide grep | **No caller** of `encrypt_ssn`/`decrypt_ssn` exists in any Form, PLL, package or trigger. How `SSN_ENCRYPTED` is populated in production is unknown. |
| Encrypted columns | `schema/tables/01_core_tables.sql:108` `EMPLOYEES.SSN_ENCRYPTED VARCHAR2(200)`; `:189` `EMPLOYEE_DEPENDENTS.SSN_ENCRYPTED VARCHAR2(200)`; `schema/tables/02_payroll_tables.sql:208` `EMPLOYEE_BANK_ACCOUNTS.ACCOUNT_NUMBER_ENC VARCHAR2(200) NOT NULL` | **No encrypt/decrypt code for `ACCOUNT_NUMBER_ENC` exists in the repo.** Whether it uses the same key is unknown (the technical debt report's "by the same pattern" is an inference). `ROUTING_NUMBER` is plaintext. |
| Seed data | `data/seed/*.sql` | No SSN or bank-account values are seeded; there is no in-repo ciphertext to test against. |
| Privileges | repo-wide grep | No `GRANT`/`SYNONYM` scripts in the repo, so who can execute `PKG_SECURITY` or read `*_SOURCE` is unknown. |
| Blocking trigger | `plsql/triggers/trg_employees.sql:62-64` | `TRG_EMP_BEFORE_UPDATE` fires `BEFORE UPDATE ON EMPLOYEES FOR EACH ROW` (every column). If it is `INVALID` (DRIFT-01: it inserts into non-existent `EMPLOYEE_HISTORY` columns), **every re-encryption `UPDATE` on `EMPLOYEES` fails** with ORA-04098. |

## 3. Key decisions

| ID | Decision | Rationale | Alternatives |
|---|---|---|---|
| K1 | **Envelope encryption with versioned data keys (DEKs) stored wrapped in a separate, locked schema `HRMS_KEYS`**, reachable only through a definer-rights package `PKG_KEYRING` declared `ACCESSIBLE BY (PACKAGE HRMS.PKG_PII)`. | Works on any 19c edition without extra licences; key material leaves source; rotation = insert a new version; no application connection can `SELECT` keys. | **TDE column encryption** (Advanced Security Option): preferred *if licensed* - keys in wallet/OKV/HSM, `ALTER TABLE ... REKEY`. Note TDE only protects data at rest; any user with `SELECT` still sees plaintext, so K4-K5 still apply. See Q1. |
| K2 | **Key-encryption key (KEK) outside the database tables**: in order of preference, Oracle Key Vault / HSM, an external KMS called over TLS (`UTL_HTTP` with wallet), or, as an interim minimum, a value supplied by the key custodian at instance start into a definer-rights package variable (never stored). | A DEK table that is only protected by grants is readable by DBAs and by anyone with export access; a separate KEK protects table exports and backups. | Storing DEKs in plaintext in `HRMS_KEYS` (one grant mistake exposes everything). |
| K3 | **New ciphertext format** `v2:<key_version>:<iv_hex>:<ct_hex>:<mac_hex>` using AES-256-CBC with a random 16-byte IV per value (`DBMS_CRYPTO.RANDOMBYTES`) and **encrypt-then-MAC** with `HMAC_SH256` under a separate MAC key. | `DBMS_CRYPTO` in 19c offers CBC but no authenticated (GCM) mode; HMAC adds tamper detection. The version prefix enables dual-read during rotation and future rotations. Size: ~8 + 32 + 32-64 + 64 hex ≈ 140-170 chars, so it **fits the existing `VARCHAR2(200)` columns**; no column widening. | Keep fixed-IV CBC (deterministic, reveals equal SSNs); widen columns (unneeded). |
| K4 | Move encrypt/decrypt into a new **`PKG_PII`** (SSN and account number). `PKG_SECURITY.encrypt_ssn/decrypt_ssn` become thin wrappers during transition, then are removed. Decrypt checks a dedicated role (e.g. `HRMS_PII_READER`) and **raises** on failure (no `'***DECRYPT_ERROR***'`). | One audited choke point; keeps `PKG_SECURITY` free of key handling; no new package cycles (`PKG_PII` calls only `PKG_KEYRING` and `PKG_AUDIT`). | Patch `PKG_SECURITY` in place (keeps crypto mixed with login code). |
| K5 | **Every decrypt is audited** (who, which table/row, when, never the value) via `PKG_AUDIT.log_action`, plus a Unified Auditing policy on `HRMS_KEYS` objects. | Detects misuse; supports breach-notification analysis. | No auditing (current). |
| K6 | The **leaked key becomes key version 1** in the keyring (read-only, decrypt-only) for the duration of the rotation, then is destroyed. | Allows dual-read with no downtime; removes the literal from code immediately. | Big-bang decrypt/re-encrypt in one transaction (long locks, no rollback path). |
| K7 | New DEKs are generated **inside the database** with `DBMS_CRYPTO.RANDOMBYTES(32)` by the key custodian, wrapped immediately under the KEK. | Key never appears in a script, ticket, terminal history or deployment artifact. | Generating keys on a workstation and pasting them into SQL (leak risk). |

## 4. Data model (illustrative, delivered by the implementation change)

```sql
-- Separate schema, no login for applications; owned by key custodian
CREATE TABLE HRMS_KEYS.KEYRING (
    KEY_VERSION     NUMBER(5)      PRIMARY KEY,
    PURPOSE         VARCHAR2(20)   NOT NULL,     -- PII_ENC | PII_MAC
    WRAPPED_KEY     RAW(512)       NOT NULL,     -- DEK encrypted under KEK
    STATUS          VARCHAR2(12)   NOT NULL,     -- ACTIVE | DECRYPT_ONLY | DESTROYED
    CREATED_DATE    DATE           DEFAULT SYSDATE NOT NULL,
    CREATED_BY      VARCHAR2(30)   NOT NULL,
    RETIRED_DATE    DATE,
    CONSTRAINT CHK_KR_STATUS  CHECK (STATUS IN ('ACTIVE','DECRYPT_ONLY','DESTROYED')),
    CONSTRAINT CHK_KR_PURPOSE CHECK (PURPOSE IN ('PII_ENC','PII_MAC'))
);

-- Re-encryption progress (no plaintext, no ciphertext)
CREATE TABLE HRMS_KEYS.REKEY_LOG (
    RUN_ID          NUMBER(10),
    TABLE_NAME      VARCHAR2(30),
    BATCH_NO        NUMBER(10),
    ROWS_DONE       NUMBER(10),
    ROWS_FAILED     NUMBER(10),
    STARTED_AT      DATE,
    FINISHED_AT     DATE
);
```

Interface (signatures to be fixed in the implementation change):

```sql
PACKAGE HRMS.PKG_PII AS
    FUNCTION encrypt(p_plain IN VARCHAR2) RETURN VARCHAR2;          -- always active key, v2 format
    FUNCTION decrypt(p_cipher IN VARCHAR2,
                     p_table  IN VARCHAR2,
                     p_row_id IN NUMBER) RETURN VARCHAR2;           -- v2 or legacy; audited; raises on failure
    FUNCTION needs_rekey(p_cipher IN VARCHAR2) RETURN BOOLEAN;      -- legacy or non-active version
END PKG_PII;
```

## 5. Rotation lifecycle

| State | Encrypt uses | Decrypt accepts | Entered when |
|---|---|---|---|
| S0 today | literal key | literal key | - |
| S1 dual-read | new key v2 (`ACTIVE`) | v2 **and** legacy (v1 `DECRYPT_ONLY`) | `PKG_PII` deployed, literal removed from source |
| S2 rekeyed | v2 | v2 and v1 | All rows verified as v2 (§6 R5) |
| S3 retired | v2 | v2 only | v1 marked `DESTROYED` and its wrapped key overwritten |

Future rotations repeat S1-S3 with version n+1; no code release is needed.

## 6. Runbook

| Step | Action | Check before proceeding |
|---|---|---|
| **R0 Discovery** (read-only) | Count non-null values in the three columns. On a **restored copy**, try decrypting a sample with the legacy key to confirm the key actually works (see §2 key-length issue). Find what writes `SSN_ENCRYPTED` and `ACCOUNT_NUMBER_ENC` in production (other apps, ETL, Forms not in repo): `DBA_DEPENDENCIES`, `DBA_SOURCE` search, audit trail. List who has `EXECUTE` on `PKG_SECURITY` and access to `*_SOURCE`. | Every ciphertext family has a known key and producer. If some values do not decrypt with the legacy key, stop and decide (Q3). |
| **R1 Prerequisites** | Fix or recompile `TRG_EMP_BEFORE_UPDATE` (DRIFT-01) so `UPDATE EMPLOYEES` works; agree a change window; take an **encrypted** backup and record its retention. Freeze schema changes to the three tables. | `SELECT STATUS FROM DBA_OBJECTS WHERE OBJECT_NAME='TRG_EMP_BEFORE_UPDATE'` = `VALID`; test `UPDATE` on a copy succeeds. |
| **R2 Keyring** | Create `HRMS_KEYS`, `KEYRING`, `PKG_KEYRING` (`ACCESSIBLE BY`), KEK integration (K2). Key custodian generates v2 `PII_ENC` + `PII_MAC` keys in-database (K7) and loads the legacy key as v1 `DECRYPT_ONLY` through a one-time custodian procedure (it is **not** typed into a deployment script). | `KEYRING` contains v1 `DECRYPT_ONLY`, v2 `ACTIVE`; application schemas get `ORA-00942`/`ORA-06550` when trying to read it. |
| **R3 Deploy code (S1)** | Deploy `PKG_PII`; replace the `PKG_SECURITY` bodies of `encrypt_ssn/decrypt_ssn` with wrappers; **delete the key literal** from `PKG_SECURITY.pkb`. Recompile the dependent objects. | `DBA_SOURCE` no longer contains the literal; new writes are `v2:`; legacy values still decrypt. |
| **R4 Re-encrypt** | Batch job per table: `SELECT pk, value ... WHERE value IS NOT NULL AND PKG_PII.needs_rekey(value)`, 1,000 rows per batch, `UPDATE ... SET value = PKG_PII.encrypt(PKG_PII.decrypt(value, ...))` with the same `needs_rekey` predicate (idempotent, safe to restart), `COMMIT` per batch, progress in `REKEY_LOG`. Run with SQL trace off; no `DBMS_OUTPUT` of values; bulk decrypts during rekey audited as one job-level record rather than per row. Order: `EMPLOYEE_DEPENDENTS`, `EMPLOYEE_BANK_ACCOUNTS`, then `EMPLOYEES` (after R1). | `REKEY_LOG.ROWS_FAILED = 0`. |
| **R5 Verify (S2)** | Before R4, store `STANDARD_HASH(plaintext,'SHA256')` per row in a temporary table in `HRMS_KEYS`; after R4 compare against the hash of the new decryption; drop the table. Count: rows with non-v2 values = 0. | 100% match; zero legacy values. |
| **R6 Retire (S3)** | After an agreed soak (proposal: 7 days) mark v1 `DESTROYED` and overwrite its wrapped key. Purge or re-encrypt backups and exports containing legacy ciphertext according to the backup retention policy (**they stay decryptable with the leaked key until they expire**). | Legacy key no longer exists anywhere under your control. |
| **R7 Close-out** | Revoke broad `EXECUTE` on `PKG_SECURITY`; grant `HRMS_PII_READER` only where needed; enable the Unified Auditing policy; record the incident (key exposure) per security policy. Decide on git-history rewrite (Q4). | Security sign-off. |

**Rollback:** possible up to R6. In S1/S2, redeploying the previous code works only if the data is decrypted back, so the supported rollback is "keep `PKG_PII`, mark v2 `DECRYPT_ONLY` and re-activate a new key" rather than reverting to the literal. After R6 the operation is irreversible by design.

**Downtime:** none expected; batches are small and keyed by primary key. Forms screens that do not touch SSN/bank fields are unaffected. Schedule R4 for `EMPLOYEES` outside payroll runs (direct-deposit file generation reads bank accounts).

## 7. Verification and acceptance criteria

| # | Test | Expected |
|---|---|---|
| T1 | Search `DBA_SOURCE`, git `HEAD`, deployment artifacts for the old literal | Not found (git history: see Q4) |
| T2 | `encrypt` of the same SSN twice | Different ciphertexts (random IV) |
| T3 | Tamper one hex digit of a v2 value and decrypt | Raises an error; never returns a sentinel string |
| T4 | Decrypt as a user without `HRMS_PII_READER` | Raises an authorization error; attempt audited |
| T5 | Decrypt as authorized user | Plaintext returned; `AUDIT_LOG` row with user, table, row id, no value |
| T6 | Application schema `SELECT` on `HRMS_KEYS.KEYRING` / direct call of `PKG_KEYRING` | Denied (`ACCESSIBLE BY` / no grant) |
| T7 | Re-encryption job killed mid-run, restarted | Completes; no row double-processed; R5 hashes match |
| T8 | After R6: decrypt a legacy-format value | Raises "key version destroyed" |
| T9 | Ciphertext length for the longest SSN and account number | ≤ 200 characters |
| T10 | `UPDATE EMPLOYEES SET SSN_ENCRYPTED = ...` on a copy | Succeeds (trigger valid) |

## 8. Risks and open questions

| # | Item | Owner |
|---|---|---|
| Q1 | Is Advanced Security Option (TDE) or Oracle Key Vault licensed? If yes, prefer TDE column encryption + OKV for at-rest protection and keep K4/K5 for access control. | DBA / licensing |
| Q2 | Which KEK option (K2) is available: OKV/HSM, external KMS, or custodian-supplied at startup? | Security |
| Q3 | If discovery finds ciphertext that the legacy key cannot decrypt (likely, given the key-length issue), which key and producer created it? | DBA + app owners |
| Q4 | Rewrite git history to remove the literal? It does not un-leak the key (rotation does), rewrites shared history and breaks forks/clones; recommended only if policy requires it. | Repo owner |
| R1 | Unknown external writers of the encrypted columns keep writing legacy ciphertext after S1. Mitigate: R0 discovery, and a check constraint or trigger rejecting non-`v2:` values once rekeyed. | Engineering |
| R2 | Backups and exports with legacy ciphertext remain exposed until they expire (R6). | Security / backup owner |
| R3 | `TRG_EMP_BEFORE_UPDATE` side effects (it fires on every update) during rekey, e.g. history rows or modified-by stamps. Review the trigger body once it compiles; rekey runs as a named service user. | Engineering |
| R4 | Possible regulatory notification obligations for a disclosed key protecting SSNs. | Legal / compliance |

## 9. Compatibility with the Java migration

The v2 format (AES-256-CBC, random IV, HMAC-SHA256, versioned) can be read by standard JCE code. At migration time the DEKs are re-wrapped under the cloud KMS used by the Java services (envelope encryption, as recommended for step 2/3) or the data is rotated once more into the Java scheme; either way no plaintext migration is needed, and Java never needs the leaked legacy key.

## 10. Traceability

| Finding | Addressed by |
|---|---|
| SEC-03 hard-coded key | K1-K2, K6-K7, R2-R6 |
| Fixed IV / no integrity (new, part of SEC-03) | K3 |
| Silent `'***DECRYPT_ERROR***'` | K4 |
| SEC-06 unprotected packages (PII part) | K4-K5, R7 |
| DRIFT-01 invalid employee trigger | R1 prerequisite |
