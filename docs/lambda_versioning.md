# Automatic Lambda Version Numbering

How a lambda version's `version` number (e.g. `2.4.0`) is assigned. Developers **do not type a
version string**; they choose a *bump level* and the server derives the actual
`major.minor.patch` number. This removes the two failure modes of developer-supplied versions:
picking a **wrong** number and picking a **duplicate** one.

---

## 1. Background: the version lifecycle

A lambda has at most one version in each leading role plus any number of archived versions
(see `LambdaVersionState`):

| Role | Statuses | Count |
|---|---|---|
| `devVersion` | `DRAFT`, `TESTING`, `DISCARDED` | at most one |
| `pubVersion` | `PRODUCTION` | at most one |
| `fallbackVersion` | `FALLBACK` | at most one |
| `archivedVersions` | `ARCHIVED` | any number |

A developer edits the single **dev version** repeatedly through
`LambdaManagementServiceImpl.setLambdaConfiguration()` before publishing it. The `version` column is
`lambda_version varchar(50)` in `lambda_versions`; there is no DB-level uniqueness constraint — uniqueness
is a service-layer concern.

---

## 2. The API: bump level instead of a version string

`LambdaConfigAction.RQLambdaVersion` carries a `VersionBump` enum rather than a `version` string:

```java
public @NotNull(message = "bump") VersionBump bump;   // MAJOR | MINOR | PATCH
```

`PUT` lambda-configuration responds with the **server-assigned** version so the client learns its
number:

```json
{ "lambdaVersionId": 123, "version": "2.4.0" }
```

---

## 3. `VersionBump` — deriving the next number

`core/src/main/java/dev/olegz/vf/core/domain/lambdaassignment/VersionBump.java`

`VersionBump.next(current)` applies the bump to the current highest version and always emits three
components:

| `current` | MAJOR | MINOR | PATCH |
|---|---|---|---|
| (none / blank) | `1.0.0` | `0.1.0` | `0.0.1` |
| `2.3` | `3.0.0` | `2.4.0` | `2.3.1` |
| `2.3.1` | `3.0.0` | `2.4.0` | `2.3.2` |
| `10.7.4` | `11.0.0` | `10.8.0` | `10.7.5` |

Rules:

- **MAJOR** bumps major, zeroes minor and patch. **MINOR** bumps minor, zeroes patch. **PATCH**
  bumps patch.
- A `null`, blank or non-numeric `current` parses as `0.0.0` (so the first MAJOR release is
  `1.0.0`). Legacy hand-typed values like `v1` parse as zero.
- Only the leading three numeric components are read; any suffix (`-beta`, `-rc1`, `-SNAPSHOT`) or
  extra component is ignored. This matches the leniency of `StringUtil.compareVersions`.

---

## 4. The basis: a high-water mark on `lambdas`

The bump is applied to a persistent **high-water mark** stored on the lambda, not to a value scanned
from the version rows:

```sql
-- lambdas
max_version varchar(50)   -- highest version ever published; NULL until the first publish
```

`Lambda.maxVersion` mirrors this column. It advances **only when a version reaches `PRODUCTION`**
(`promoteTestVersionToProduction`):

```java
devVersion.updateStatus(PRODUCTION);
lambda.maxVersion = devVersion.version;
lambdaDao.updateLambdaMaxVersion(lambda.lambdaId, lambda.maxVersion);
```

The number itself is assigned in `setLambdaConfiguration()` off that mark:

```java
lambdaVersion.version = newConfig.bump.next(lambda.maxVersion);
```

Because the mark only moves on publish, the dev version's own (unpublished) number is never part of
the basis.

---

## 5. Why this is correct

- **Concurrency-safe.** Both `setLambdaConfiguration` and the version transition methods run inside the existing
  `getLambdaForUpdate` row lock, so the read of `maxVersion` and its advance are serialized per lambda.
- **Idempotent during construction.** `maxVersion` does not change while a draft is edited, so
  re-saving with the same bump yields the same number and switching the bump level (e.g.
  MINOR → PATCH) simply recomputes it off the same basis.
- **Strictly monotonic — even across rollback and deletion.** The mark only ever increases (the
  published number is `bump.next(maxVersion)`), and it lives on `lambdas` independently of the version
  rows. A rollback that re-promotes an older `FALLBACK` to `PRODUCTION` leaves `maxVersion`
  untouched, and deleting old version rows can't lower it. So a newly assigned number is always
  greater than every version ever published — duplicates are impossible by construction, which is
  why the former `checkDuplicateVersion` guard was removed.

---

## 6. Edge cases

- **First version** → `maxVersion` is `NULL`, so the `0.0.0` base is bumped by the chosen level
  (MAJOR → `1.0.0`).
- **Re-opened `DISCARDED` draft** recomputes to the same value for the same bump, since `maxVersion`
  is unchanged while unpublished. Numbers assigned to drafts that are never published are freely
  reusable — only publishing burns a number.
- **After a rollback** (`2.4.0` withdrawn, `2.3.0` re-promoted to production, `2.4.0` archived) the
  mark stays at `2.4.0`, so the next release is numbered above it — a fix *supersedes* the withdrawn
  version (MINOR → `2.5.0`, PATCH → `2.4.1`) rather than reusing or regressing below it. This is
  deliberate: the model supports a single forward-moving line, not parallel maintenance branches, so
  you cannot issue a patch *on the re-lived older line* (there is no `2.3.1` once `2.4.0` exists).
- **Legacy / migrated data.** For a lambda with pre-existing published versions but a `NULL`
  `max_version`, backfill it once with `UPDATE lambdas b SET max_version = (SELECT MAX(lambda_version) ...)`
  before relying on auto-numbering; otherwise the first new number restarts from `0.0.0`. (The
  project rebuilds the schema from `create_tables.sql` rather than migrating, so this only matters
  for a real deployment with live data.)

---

## 7. Alternatives considered

- **Fully automatic (no bump input):** always increment minor. Simplest, but loses the
  breaking-vs-non-breaking distinction — worse for a lambda marketplace.
- **Validate-only (keep developer input):** enforce a SemVer regex plus "must be strictly greater
  than current max." Least code, but keeps the door open to developer confusion and support tickets.

The semi-automatic bump approach was chosen as the balance between removing error-prone input and
preserving the developer's release-intent signal.

---

## 8. Tests

- `../core/src/test/java/dev/olegz/vf/core/domain/lambdaassignment/VersionBumpTest.java` — the bump
  arithmetic: the three output rules, the reset behaviour, and lenient parsing of absent, blank,
  suffixed and non-numeric prior versions.
- `../core/src/test/java/dev/olegz/vf/core/dao/LambdaMaxVersionDaoTest.java` — the `max_version`
  high-water mark: `null` for a new lambda, and `updateLambdaMaxVersion` persisting a value that
  round-trips through the lambda read mappers and serves as the basis for the next number.
