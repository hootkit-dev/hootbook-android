<a name="top"></a>
![Hootbook](banner.png)

[![License](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84)](https://developer.android.com/about/versions/oreo)

## Hootbook

Hootbook is a personal, manual bookkeeping app for Android, part of HootKit. It focuses on
Chinese and cross-border accounts: banks and brokers in mainland China, Hong Kong, Singapore and
the US, multi-currency accounts, credit card statements, budgets, subscriptions and lending.

Hootbook started as a fork of [Cashiro](https://github.com/ritesh-kanwar/Cashiro), itself based on
[PennyWise AI](https://github.com/sarim2000/pennywiseai-tracker). It is a separate app
(`io.github.hootkit_dev.hootbook`): it does not update an installed Cashiro. To move your data, export
a backup in Cashiro and restore it in Hootbook.

### Features

- **Accounts**: banks, credit cards, wallets and brokers, each holding one or more currencies;
  institution presets with names and icons
- **Credit cards**: statement day and due day, the open statement and what is still owed
- **Budgets, subscriptions, lending and borrowing, quick templates**
- **AI bookkeeping**: share screenshots, PDFs or text to Hootbook and review the proposed
  transactions before saving; cloud models only, with your own API key
- **Broker holdings**: read-only IBKR Flex sync
- **Multi-device sync** (standard build): Firebase, end-to-end encrypted with your passphrase
- **Backups**: local file, Google Drive or WebDAV, optionally encrypted; CSV export

## Screenshots

<table>
<tr>
<td><img src="screenshots/home.png" width="160"/></td>
<td><img src="screenshots/analytics.png" width="160"/></td>
<td><img src="screenshots/transactions.png" width="160"/></td>
<td><img src="screenshots/account_detail.png" width="160"/></td>
</tr>
<tr>
<td align="center">Home</td>
<td align="center">Analytics</td>
<td align="center">Transactions</td>
<td align="center">Account Details</td>
</tr>
<tr>
<td><img src="screenshots/budgets.png" width="160"/></td>
<td><img src="screenshots/budget_details.png" width="160"/></td>
<td><img src="screenshots/subscriptions.png" width="160"/></td>
<td><img src="screenshots/categories.png" width="160"/></td>
</tr>
<tr>
<td align="center">Budgets</td>
<td align="center">Budget Details</td>
<td align="center">Subscriptions</td>
<td align="center">Categories</td>
</tr>
</table>

Screenshots predate the rename and still show the Cashiro branding.

## Build

```bash
git clone https://github.com/hootkit-dev/hootbook-android.git
cd hootbook-android

./gradlew :app:assembleStandardDebug        # standard flavor (with Firebase sync)
./gradlew :app:assembleFdroidDebug          # F-Droid flavor (no Firebase)
./gradlew :app:testStandardDebugUnitTest    # unit tests
./gradlew :app:lintStandardDebug            # lint
```

Requires Android 8.0+ (API 26) and JDK 21.

**Architecture**: MVVM • Jetpack Compose • Material 3 • Room • Coroutines • Hilt. See
[docs/architecture.md](docs/architecture.md).

```
io.github.hootkit_dev.hootbook
├── data          Room, repositories, preferences, sync, backup, AI
├── domain        Use cases and models
├── presentation  Compose UI, feature ViewModels, navigation
├── di            Hilt modules
└── utils
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md), the [Code of Conduct](CODE_OF_CONDUCT.md) and the
[Security Policy](SECURITY.md). Bugs and feature requests:
[open an issue](https://github.com/hootkit-dev/hootbook-android/issues).

## Acknowledgements

- **[Cashiro](https://github.com/ritesh-kanwar/Cashiro)** by Ritesh Kanwar - the app this one is forked from.
- **[PennyWise AI](https://github.com/sarim2000/pennywiseai-tracker)** - the base of Cashiro.
- **[Microsoft Fluent Emojis](https://github.com/microsoft/fluentui-emoji)** - category icons.
- **[Haze](https://chrisbanes.github.io/haze/latest/)** - blur effects.
- **[Compose Charts](https://github.com/ehsannarmani/ComposeCharts)** - animated charts.
- **[Reorderable](https://github.com/Calvin-LL/Reorderable)** - re-ordering widgets.
- **[Peeps Ui8](https://peeps.ui8.net/)** - built-in user avatars.
- **[Iconax](https://iconsax.io/)** - icons.

## License

GNU Affero General Public License v3.0 - see [LICENSE](LICENSE)
