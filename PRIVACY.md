# Privacy Policy

**Last Updated: September 8, 2026**

## Our Commitment to Privacy

Hootbook is built with privacy as the core principle. We believe your financial data should remain yours alone.

## Local Processing and Optional Connections

**Core bookkeeping happens locally. Optional connections make network requests as described below.**

- ✅ **Local by default** - Core bookkeeping does not require a Hootbook server
- ✅ **User-directed sharing** - Optional integrations transmit the data needed for the features you enable
- ✅ **No tracking** - No analytics, no telemetry, no user tracking
- ✅ **No ads** - No advertising networks or tracking pixels
- ✅ **No SMS or notification-listener access** - The app does not read SMS or other apps' notifications

## Data Storage

### What We Store (Locally Only)
- Transactions, accounts, categories, budgets, and notes you enter
- App preferences and settings

### Where It's Stored
- Bookkeeping data is stored in a local SQLite database; preferences use local app storage
- Optional brokerage credentials and holdings use a separate encrypted, backup-excluded file
- Database is protected by Android's app sandboxing
- Data is only accessible to Hootbook app

### Data Deletion
- Uninstalling the app completely removes all data
- You can delete individual transactions at any time
- Export your data before uninstalling if you want to keep records

## Permissions

### Internet Permission
- **App Updates**: Google Play Store variant uses Play Services for app updates (F-Droid variant does not)
- **Optional integrations**: Brokerage sync contacts IBKR directly. Configured cloud backups, exchange rates, and webhooks use the services selected by the user.

### Notifications
- **Purpose**: Optional bill reminders and daily summaries you enable in Settings
- The app does not request notification-listener access and does not read other apps' notifications

### No Other Permissions Required
- No SMS read access
- No location tracking
- No contact access
- No camera or microphone access

## Optional IBKR Brokerage Connection

When you explicitly connect or refresh a brokerage account, Hootbook sends your Flex Token,
Query ID / report reference, protocol version and User-Agent directly over HTTPS to
`ndcdyn.interactivebrokers.com`. IBKR can see your source IP. Hootbook retrieves the report
and parses holdings locally; it does not upload your bookkeeping transactions or route
brokerage credentials through a Hootbook server. IBKR processes access under its own policies.

The connection is read-only: no orders, trades or transfers. Tokens grant access to reports
and should be kept private. Credentials, account IDs and holdings are encrypted with
AES-GCM using an Android Keystore key and stored in `noBackupFilesDir`, outside system
backup/device transfer and existing Hootbook exports/cloud backups. There is no plaintext
fallback. Token entry is masked and its dialog blocks screenshots. Raw API URLs, responses
and credentials are not logged by this integration.

Disconnect deletes the local credentials and cached holdings. It does not revoke the token
at IBKR: generate a new token there to invalidate the old one. Reinstalling or migrating to
another device requires reconnecting. See [setup and security details](docs/brokerage-connections.md).

## Third-Party Services

Hootbook does **NOT** use analytics or advertising services. Network integrations are listed separately:
- Optional APIs include updates, exchange rates, user-configured backups/webhooks and IBKR Flex. These are separate from analytics or advertising.
- ❌ Analytics services (Google Analytics, Firebase, etc.)
- ❌ Crash reporting services
- ❌ Advertising networks
- ❌ Social media SDKs
- ❌ Payment processors

**Note**: The Google Play Store variant includes Play Services for app updates only. The F-Droid variant has no Google services.

## Data Export

When you export your data:
- CSV/PDF files are created locally on your device
- You control where to share or save them
- Local exports do not automatically upload themselves. User-enabled cloud backup or webhook features have their own network behavior.
- Brokerage credentials and holdings are excluded from existing exports and backups.

## Open Source Transparency

Hootbook is fully open source:
- Review our code at [GitHub](https://github.com/hootkit-dev/hootbook-android)
- Verify our privacy claims yourself
- Contribute to make it even better

## Children's Privacy

Hootbook is not directed at children under 13. We do not knowingly collect information from children.

## Changes to Privacy Policy

Any changes to this privacy policy will be:
- Updated in the app repository
- Reflected in the "Last Updated" date
- Communicated through release notes

## Contact

For privacy concerns or questions:
- Open an issue on [GitHub](https://github.com/hootkit-dev/hootbook-android/issues)

## Summary

**Core records stay local; optional connections share only when enabled or requested.**

- No Hootbook server needed for IBKR sync
- User-controlled connections and exports
- No tracking
- No ads
- Encrypted local brokerage storage

---

*Hootbook - Privacy-first expense tracking*