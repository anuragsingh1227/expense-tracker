# Expense Tracker (Android, offline-first)

Automatic Indian expense tracker with **on-device** parsing. No network permission, no accounts, no telemetry.

## Distribution flavors

| Flavor | Package | SMS inbox | Publish to |
|--------|---------|-----------|------------|
| **store** | `com.expensetracker.offline` | No (paste / share / manual add) | Google Play & other stores |
| **sms** | `…offline.sms` | Yes | GitHub / sideload / F-Droid |

See [docs/STORE_PUBLISHING.md](docs/STORE_PUBLISHING.md) and [docs/privacy-policy.html](docs/privacy-policy.html).

## Build

Requires JDK 17 and the Android SDK.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Play-safe release bundle
./gradlew :app:bundleStoreRelease

# Play-safe release APK
./gradlew :app:assembleStoreRelease

# SMS auto-import release APK (not for Play)
./gradlew :app:assembleSmsRelease

# Unit tests (store debug)
./gradlew :app:testStoreDebugUnitTest
```

Release signing: copy `keystore.properties.example` → `keystore.properties` and point at your `.keystore` (gitignored).

## Runtime permissions

**Store build:** `POST_NOTIFICATIONS` only (optional on older APIs).

**SMS build:** `RECEIVE_SMS` / `READ_SMS` (+ notifications on API 33+), `RECEIVE_BOOT_COMPLETED`.

## Privacy

- No `INTERNET` permission.
- Auto-backup / data-extraction rules exclude the database.
- Store build never reads the SMS inbox; you paste or share message text, or add cash manually.

## Features

- Spends dashboard (Day/Week/Month/Last/3 mo/Indian FY/card billing cycle), MoM rising categories, stacked month bars, investments bucket
- Activity search, category/bank filters, multi-select copy SMS or bulk delete, manual add (+)
- Transaction edit (amount, merchant, date, type, category, notes, `#tags`, split spend)
- Offline Splitwise-style P2P ledger with Debts / Owed to Me on Activity
- Credit-card statement SMS → local due reminders (3 days before + due date)
- Custom credit-card billing cycles in More
- Monthly category budgets with progress
- Owner-name onboarding for self-transfer detection (no hardcoded names)
- App lock (PIN + optional biometric, lockout after failed attempts) and hide-amounts (app-wide)
- JSON backup/restore (includes settings) + CSV export
- Home-screen month-spend widget
- Spam cleanup; refunds net against spend; Transfer pairing

Current version: see `versionName` / `versionCode` in `app/build.gradle.kts` (1.1.9 / 109).
