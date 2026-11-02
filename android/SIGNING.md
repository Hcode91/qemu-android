# Release signing and authorisation

Debug APKs use Gradle's standard disposable debug signing. They are development
builds only. Production signing never uses this identity.

Generate **one persistent publisher key** on a trusted offline machine or secure
key-management service. This one-time key ceremony is not an APK build and does
not require Android Studio. For example, with the JDK keytool (passwords entered
interactively, never in shell history):

```sh
keytool -genkeypair -keystore qemu-android-publisher.jks -storetype JKS \
  -alias qemu-android -keyalg RSA -keysize 4096 -validity 10000
```

Keep encrypted offline backups of both the keystore and recovery information in
separate secure locations. Record the certificate SHA-256 fingerprint with
`keytool -list -v -keystore qemu-android-publisher.jks`. Losing the private key
prevents normal updates to installed APKs. Never generate a fresh key per build.
Do not send signing material in chat or commit it to this repository.

In repository Settings > Environments, create `android-release`, restrict it to
reviewed master/version tags and require an owner review. Configure these
**environment secrets**, not secrets visible to development jobs:

- `ANDROID_KEYSTORE_BASE64`: base64 encoding of the keystore (encoding is not encryption).
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: persistent key alias.
- `ANDROID_KEY_PASSWORD`: private-key password.

The release workflow handles the decoded keystore only in a mode-0600 temporary
file and deletes it on exit. Passwords use apksigner environment references,
never command-line literals. Secrets are never included in artifacts.

Review/merge the release commit yourself. The workflow never merges PRs. Set
`APPROVED_RELEASE_COMMIT` to its full SHA for tag-triggered builds, or provide
`approved_commit` on manual execution. The exact checkout must match that SHA
and be an ancestor of master. Tag versions must match the app/artifact version
(currently v0.1.0). Select that exact commit/tag when running the workflow.

With all four secrets present, CI signs, verifies, checks alignment and generates
SHA256SUMS. If any secret is missing it uploads an `UNSIGNED-INSPECTION.apk` and a
notice that **no installable signed release was produced**. It never falls back
to debug signing. Invalid provided credentials fail the job.

Ordinary commits never run release signing. Version tags can build artifacts
but do not automatically publish. Public publishing requires manually running
**Android release** on the approved version tag with `publish=true`. This is
explicit authorisation to create a public GitHub Release. Also configure the
`android-publication` environment with required owner review. Only the publish
job receives `contents: write`. No public release is authorised in this session.

Before enabling public distribution, complete licence/source delivery, compare
the signing certificate fingerprint with the backed-up publisher identity, and
resolve release security findings and real-device requirements in the status file.
