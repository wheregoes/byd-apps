# Claude Rules

## Repository Updates

Every modification, finding, discovery, or change made to the BYD Dolphin must be committed and pushed to this repository. This includes:

- New findings from ADB exploration
- Apps installed or removed
- System settings changed
- New scripts or tools created
- Documentation updates
- Any workaround or hack discovered

Never leave findings only in conversation — always persist them here.

## Release Signing

The APK signing key lives at `keystore/byd-apps.keystore` (alias `byd-apps`, store and key password
`bydapps`). It is gitignored, and `apps/common/build-common.sh` **silently generates a new one when
the file is missing** — which is how the 1.2.0 key was lost and why Door Sound 1.3.0 forced every
user to uninstall first.

Rules:

- Before publishing anything, check the certificate:
  `apksigner verify --print-certs <apk> | grep "SHA-256 digest"`.
  Current key: `67397b8ca97a25fa0de930c9c08e0e4a05fcf2b3a0f4e810146dd8ac34a6db32` (Door Sound ≥ 1.3.0).
  Pre-1.3.0 releases used `7ef5cb43b15006e4b741c453462de5830af648bea7f0359c918d288ea40f36d9`, whose
  private key no longer exists, so Engine Sound, Cabin and BYD Probe each owe their users one more
  uninstall when they are next released.
- A replacement key must be **RSA 2048 with SHA256withRSA**. A 4096-bit key defaults to SHA384withRSA
  and its v1 (JAR) signature then fails to verify, which DiLink's Android 10 needs.
- Backups of the current key: `~/.local/share/byd-apps/byd-apps.keystore` (same machine) and
  `umbrel:~/backups/byd-apps/byd-apps.keystore` (off-machine). Keep both in sync —
  `sha256 73ece771bc271ba217765d2fd1e65577b205092b15203b2dfc012e1db3deae3e`.
