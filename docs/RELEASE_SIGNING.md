# Aura Gallery release signing

Release signing uses a keystore owned and backed up by the project owner. **Never commit the keystore, passwords, private keys or encoded keystore contents to Git.**

## Configure GitHub Actions

Add these repository or environment Actions secrets:

- `AURA_RELEASE_KEYSTORE_BASE64` — base64 encoding of the complete JKS/PKCS12 keystore
- `AURA_RELEASE_STORE_PASSWORD` — keystore password
- `AURA_RELEASE_KEY_ALIAS` — alias of the signing key
- `AURA_RELEASE_KEY_PASSWORD` — signing key password

The workflow decodes the keystore into the ephemeral runner's temporary directory, runs the minified `:app:assembleRelease` variant using those environment values, then uploads a separate signed artifact. The temporary keystore is not added to the workspace source or an artifact.

For a JKS file on Linux, produce the secret value with:

```bash
base64 -w 0 aura-release.p12
```

On macOS:

```bash
base64 < aura-release.p12 | tr -d '\n'
```

## Create a new keystore (only when no existing app signing key is available)

Keep the output outside the repository and back it up securely. Losing the key can prevent compatible updates to existing installs.

```bash
keytool -genkeypair -v \
  -keystore aura-release.p12 \
  -storetype PKCS12 \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -alias aura-gallery
```

Use the exact alias and passwords as the Actions secrets. The workflow defaults to PKCS12; for an existing JKS keystore, also set the Actions variable `AURA_RELEASE_STORE_TYPE=JKS` (or export that environment variable locally). Do not share the keystore in chat or upload it as a build artifact.

## Local signed build

Set the same four `AURA_RELEASE_*` environment variables in the shell running Gradle, with `AURA_RELEASE_STORE_FILE` pointing to your local keystore, then run:

```bash
./gradlew --no-daemon :app:assembleRelease
```

## Artifact distinction

- `aura-ai-gallery-debug` is the installable debug APK used for testing.
- `aura-ai-gallery-release-unsigned` validates release/minification but is **not** a distributable signed release.
- `aura-ai-gallery-release-signed` is emitted only when all four Actions secrets are configured.

No signing key or production credential is supplied or stored in this repository by this change.
