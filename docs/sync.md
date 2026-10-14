# Multi-device sync

Settings → Ledger sync opens the dedicated sync page. Settings → Backup & restore
contains local file export and restore. Data Privacy contains only device access
protection (app lock and its timeout); it has no backup or sync actions.

Core owns the encrypted sync protocol, change tracking and conflict records. The Android
standard flavor provides Firebase Auth and the Firestore v2 RemoteStore callbacks. Firebase
configuration and Google sign-in are managed by `CoreFirebaseSyncPlatform`; no ledger data
is transformed into an Android database model.

`CoreSyncCoordinator` serializes authentication handoffs and sync runs. It closes the remote
gate before changing identities, cancels scheduled work and listeners, publishes the accepted
account, calls Core `authChanged`, then opens the gate. Remote leases carry a UID and epoch
so stale requests cannot cross an authentication change.

The installation identity store permits the original UID and blocks different-account
switching. An existing ledger without ownership evidence also keeps remote access closed.
The app remains usable offline, including local backup export. WorkManager schedules retries;
foreground events and explicit user sync actions use the same coordinator.

The restored sync ViewModel exposes Core status, notices and conflicts directly. UI resolution
choices issue Core commands; field grouping is presentation only. Passphrase handling,
encryption and conflict semantics belong to Core. See [Core integration](core-integration.md).
