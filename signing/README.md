# MudLife permanent release identity

The owner authorized replacement of the lost old signing key on 2026-10-15.
The first release in this new signing series is 1.3.18 (version code 26).
This identity cannot update old-signature 1.3.17 or the debug `.refresh` app.

Certificate SHA-256: `00da3d63a98a2f73565b306c18c67ca2f4fe27fc2ae71599544f96593637c2df`.
The PEM file here contains only the public certificate. It cannot sign APKs.

## Local build

The private keystore is `%USERPROFILE%\.android\mudlife-signing\mudlife-release-2026.p12`.
Credentials are `%USERPROFILE%\.android\mudlife-signing.properties` (UTF-8).
The build reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` from that file.
Alias: `mudlife-release`. No passwords or private keystores belong in Git.

Backups on this computer:

- Key: `%USERPROFILE%\Documents\MudLife-签名备份\mudlife-release-2026.p12`
- Credentials: `%USERPROFILE%\Documents\MudLife-签名凭据备份\mudlife-signing.properties`

These are same-computer backups. Keep an additional private copy off this computer.
Never regenerate the key if the original configuration is absent. Restore it from backup.
Release builds stop if the key is missing or its certificate does not match the pinned fingerprint.

## GitHub Actions

Configure repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
`KEYSTORE_BASE64` is a base64 encoding of the exact permanent PKCS12 file, not an encryption format.
The workflow restores it only into the runner's temporary directory and uses environment variables.
No secrets were uploaded as part of this local maintenance task.
