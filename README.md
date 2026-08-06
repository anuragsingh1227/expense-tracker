# Expense Tracker (Android, offline-first)

Automatic Indian expense tracker with **on-device** parsing. No network permission, no accounts, no telemetry.

## Distribution flavors

| Flavor | Package | SMS inbox | Publish to |
|--------|---------|-----------|------------|
| **store** | `com.expensetracker.offline` | No (paste / share only) | Google Play & other stores |
| **sms** | `com.expensetracker.offline.sms` | Yes | GitHub / sideload / F-Droid |

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
- Store build never reads the SMS inbox; you paste or share message text.

## Features

Spends dashboard with Day/Week/Month/Last/3 mo filters, MoM rising categories, 3-month stacked bars, Activity search, label rules from raw SMS text, JSON backup via system picker (Drive-compatible), spam cleanup.
