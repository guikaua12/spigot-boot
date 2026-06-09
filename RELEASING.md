# Releasing

This is the maintainer runbook for publishing `tech.guilhermekaua.spigot-boot` to
[Maven Central](https://central.sonatype.com/) via the
[Central Portal](https://central.sonatype.org/publish/publish-portal-maven/).

> **TL;DR — cutting a release is one push.** Everything happens in CI; your machine
> only needs `git`. Commit, tag `vX.Y.Z`, push the tag. The
> [`Release to Maven Central`](.github/workflows/release.yml) workflow does the rest.
> The one-time setup below is only needed to (re)create the GitHub secrets after you
> format your PC, or to deploy manually as a fallback.

---

## 1. Cut a release (the normal path)

1. Be on `master` with a clean working tree and **everything committed** — including any
   publishing-related POM changes (see [§3](#3-which-modules-publish-and-which-dont), this
   bit us once: a fix that only lived in the working tree was not in the tag CI built from).
2. Verify the build is green locally (use JDK 17 or 21 — **never 25**, see
   [troubleshooting](#lombok-crashes-with-typetag--unknown)):
   ```powershell
   .\mvnw.cmd clean test
   ```
3. Pick the version `X.Y.Z`. You do **not** need to edit the POM version first — the
   workflow runs `versions:set` from the tag (see [§2](#2-how-the-pipeline-works)). Keeping
   the committed POM version in sync is still nice for clarity, but the tag is the source of truth.
4. Tag and push:
   ```powershell
   git tag v3.0.1
   git push origin v3.0.1
   ```
5. Watch **Actions → Release to Maven Central**. With `autoPublish=true`, a successful run
   publishes automatically — no manual "release" click on the portal.
6. Confirm in ~10–30 min on [central.sonatype.com](https://central.sonatype.com/) (your
   deployments) and eventually at
   `https://repo1.maven.org/maven2/tech/guilhermekaua/spigot-boot/`.

That's the whole day-to-day flow.

---

## 2. How the pipeline works

[`.github/workflows/release.yml`](.github/workflows/release.yml) triggers on tags matching
`v[0-9]+.[0-9]+.[0-9]+` and, on `ubuntu-latest` with **JDK 17 (temurin)**:

1. **Imports the GPG key** (`crazy-max/ghaction-import-gpg`) from the `GPG_PRIVATE_KEY` /
   `GPG_PASSPHRASE` secrets.
2. **Sets the version from the tag**:
   `mvn versions:set -DnewVersion=<tag-without-v> -DprocessAllModules=true -DgenerateBackupPoms=false`.
3. **Builds, signs, and deploys**: `mvn clean deploy -Dgpg.passphrase=***`.

The actual upload is done by the **central-publishing-maven-plugin** (`pom.xml`, version
`0.10.0`, `publishingServerId=central`, `autoPublish=true`). It bundles every reactor module
that is **not** skipped into one deployment and uploads it. Signing is the
**maven-gpg-plugin** (`3.2.7`, `--pinentry-mode loopback`), bound to the `deploy` phase.

Required GitHub repo secrets (Settings → Secrets and variables → Actions):

| Secret | Used for |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Central Portal **user token** username |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal **user token** password |
| `GPG_PRIVATE_KEY` | ASCII-armored private signing key |
| `GPG_PASSPHRASE` | passphrase for that key |

---

## 3. Which modules publish — and which don't

This is the part that's easy to forget and the source of the most confusing failure.
**`mvn deploy` tries to publish every reactor module by default.** Some modules must *not*
be published, and two mechanisms keep them out:

- **`<skipPublishing>true</skipPublishing>`** — a project property the
  central-publishing-maven-plugin reads **per module** (since plugin 0.9.0). A module with
  this set is excluded from the upload bundle entirely.
- **`maven-shade-plugin` with `<createDependencyReducedPom>true</createDependencyReducedPom>`** —
  bundles other modules' classes into one published jar and writes a *reduced* POM that drops
  the bundled deps so consumers never try to resolve them.

Modules deliberately **not** published:

| Module | Why | How it's excluded |
|---|---|---|
| `test-plugin` | sample plugin, not a library | `skipPublishing` |
| `platform-spigot/inventory-api/nms-1_8_R3` … `nms-1_19_R3` | compile against `org.spigotmc:spigot` (the full **server**, a BuildTools-only, non-redistributable `-SNAPSHOT`) | `skipPublishing` |

The per-version NMS classes still ship — they are **shaded into**
`spigot-boot-inventory-api-nms`, whose reduced POM drops both those modules and their
`org.spigotmc:spigot` dependency. The runtime selector picks the right impl reflectively.

> **Do not set `<promoteTransitiveDependencies>true</promoteTransitiveDependencies>`** on
> that shade execution — it would pull the SNAPSHOT `spigot` dep back into the reduced POM and
> reintroduce the failure below.

### The SNAPSHOT-dependency rule

Central rejects any **release** deployment whose POMs reference a `-SNAPSHOT` dependency.
That's why API-only modules use a **timestamped** Paper coordinate
(`io.papermc.paper:paper-api:1.20.1-R0.1-20230917.004407-174`, managed in
`platform-spigot/pom.xml`) instead of `...-SNAPSHOT`: the timestamped string is a fixed
snapshot build and passes validation. The NMS modules **can't** use that trick — `paper-api`
has no server internals — so they are excluded from publishing instead (above).

---

## 4. One-time setup (new machine / recreating secrets)

You only need this after formatting your PC, rotating credentials, or to deploy manually.
Day-to-day releases need none of it.

### 4a. Central Portal account & namespace

1. Sign in at [central.sonatype.com](https://central.sonatype.com/).
2. The namespace **`tech.guilhermekaua`** must be verified once on the account. Custom
   (reverse-domain) namespaces are verified by adding a **DNS TXT record** to
   `guilhermekaua.tech` as instructed by the portal. (A `io.github.<user>` namespace would be
   auto-verified instead, but this project uses the vanity groupId.) This is tied to the
   **account**, not the machine — you don't redo it when you reformat, only if you lose the account.

### 4b. Generate a publishing user token

1. Go to [central.sonatype.com/usertoken](https://central.sonatype.com/usertoken) →
   **Generate User Token**.
2. It is shown **once**. Copy both halves immediately:
   - username → `MAVEN_CENTRAL_USERNAME`
   - password → `MAVEN_CENTRAL_PASSWORD`

   These are API credentials, **not** your portal login.

### 4c. Generate & publish the GPG signing key

```bash
# 1. generate (choose RSA & RSA, 4096 bits; set a passphrase you'll keep)
gpg --full-generate-key

# 2. find the long key id (the hex after "rsa4096/")
gpg --list-secret-keys --keyid-format=long

# 3. publish the PUBLIC key — Central validates signatures against these keyservers
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --keyserver keys.openpgp.org    --send-keys <KEY_ID>
# propagation can take several minutes

# 4. export the PRIVATE key (ASCII-armored) for the GitHub secret
gpg --armor --export-secret-keys <KEY_ID> > private-key.asc
```

- `GPG_PRIVATE_KEY` = the entire contents of `private-key.asc` (including the
  `-----BEGIN/END PGP PRIVATE KEY BLOCK-----` lines).
- `GPG_PASSPHRASE` = the passphrase you chose.
- **Delete `private-key.asc`** afterward and never commit it.

### 4d. Set the four GitHub secrets

Repo → **Settings → Secrets and variables → Actions → New repository secret**, for each of
`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`.

After this, releasing is just [§1](#1-cut-a-release-the-normal-path) again.

---

## 5. Manual / emergency deploy from your machine

Only if CI is unavailable. Requires the token in `~/.m2/settings.xml` and a local GPG key.

`~/.m2/settings.xml` (keep it out of any repo):

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>TOKEN_USERNAME</username>
      <password>TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

Then, with **JDK 17 or 21**:

```powershell
.\mvnw.cmd versions:set -DnewVersion=3.0.1 -DprocessAllModules=true -DgenerateBackupPoms=false
.\mvnw.cmd clean deploy -Dgpg.passphrase=YOUR_PASSPHRASE
```

`autoPublish=true` means this also releases automatically — there's no staging gate to undo.

---

## 6. Troubleshooting

### `Dependencies to SNAPSHOT versions not allowed for dependency: org.spigotmc:spigot`
A per-version NMS module reached the upload bundle. Confirm every `nms-1_*` module has
`<skipPublishing>true</skipPublishing>` **and that it's committed and present in the tagged
commit** — `skipPublishing` living only in your working tree means the tag CI built from
didn't have it. Verify with `git grep skipPublishing <tag> -- '**/pom.xml'`. Also check the
shade execution on `inventory-api/nms` still has `promoteTransitiveDependencies` unset (default false).

### Lombok crashes with `TypeTag :: UNKNOWN`
You're building with JDK 25. Lombok 1.18.36 can't handle it — build with **JDK 17 or 21**.
(CI uses 17.)

### `gpg: signing failed: No secret key` / signature errors
The key wasn't imported, or `GPG_PRIVATE_KEY`/`GPG_PASSPHRASE` are wrong. Re-export with
`gpg --armor --export-secret-keys` and re-set the secrets.

### Validation: public key can't be found
Your public key hasn't propagated. Re-run the `--send-keys` commands ([§4c](#4c-generate--publish-the-gpg-signing-key)) and wait a few minutes.

### `401 Unauthorized` on upload
The user token is wrong or revoked. Generate a new one ([§4b](#4b-generate-a-publishing-user-token)) and update the secrets.

### `Namespace ... is not allowed` / not verified
`tech.guilhermekaua` isn't verified on the account. Complete DNS verification on the portal
([§4a](#4a-central-portal-account--namespace)).

---

## Pre-release checklist

- [ ] On `master`, working tree clean, **all** publishing changes committed
- [ ] `.\mvnw.cmd clean test` green on JDK 17/21
- [ ] Version decided
- [ ] `git tag vX.Y.Z && git push origin vX.Y.Z`
- [ ] Actions run green; artifact visible on central.sonatype.com
