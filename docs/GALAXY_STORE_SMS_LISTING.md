# Galaxy Store listing — Offline Expense Tracker (SMS APK)

Upload the **`sms`** flavor, not the Play-safe `store` flavor.

| Field | Value |
|-------|--------|
| Package name | `com.expensetracker.offline.sms` |
| Binary | `app-sms-release.apk` from `./gradlew :app:assembleSmsRelease` |
| App title (listing) | Offline Expense Tracker |
| Default language | English (add Hindi later if you want) |
| Category | Finance → Personal finance / Expense |
| Price | Free |
| Age | Not directed at children under 13 |
| Countries | India (expand later) |
| Privacy policy URL | Host `docs/privacy-policy.html` on HTTPS |

## Permissions (paste into Seller Portal “permissions / description”)

| Permission | Required? | Why |
|------------|-----------|-----|
| `READ_SMS` | Optional | One-time / rescan import of bank & UPI payment SMS into the on-device ledger |
| `RECEIVE_SMS` | Optional | Detect new bank/UPI SMS while the app is installed |
| `RECEIVE_BOOT_COMPLETED` | Optional | Resume listening for payment SMS after device reboot |
| `POST_NOTIFICATIONS` | Optional | Notify when a payment SMS is imported |
| `USE_BIOMETRIC` | Optional | App lock |

No `INTERNET`. No ads. No account. No analytics.

## Short description (≤ 80 chars if limited)

Offline expense tracker for India — reads bank/UPI SMS on-device. No cloud.

## Full description (copy-paste)

```
Offline Expense Tracker is a private, on-device spending ledger for India.

HOW IT WORKS
• Automatically imports bank and UPI payment SMS into a local ledger
• Builds your Spends dashboard for this month (with a 3-month chart that fills over time)
• Categorizes merchants, detects transfers/investments, and keeps self-transfers out of spend
• Works fully offline — the app has no internet permission

PRIVACY
• All parsing and storage stay on your phone
• No account, no ads, no analytics, no cloud sync
• SMS is used only to extract payment amounts, merchants, and dates
• You can deny SMS permission and still add expenses manually or paste a message

PERMISSIONS
• Read SMS / Receive SMS — import banking and UPI alerts on this device only
• Boot completed — continue detecting payment SMS after reboot
• Notifications — optional alert when a payment is imported
• Biometric — optional app lock

Free. Made for Indian bank and UPI SMS formats.
```

## Review notes tip (Seller Portal comments, if available)

```
This app is an offline personal finance ledger. SMS permissions are used only
to parse banking/UPI payment notifications on-device. There is no INTERNET
permission, so SMS content cannot leave the device through the app.
Privacy policy URL is provided. No login required.
```

## Screenshot ideas

1. Spends dashboard (month spend + investment + 3-month chart)
2. Activity list with bank/UPI rows
3. Permission / onboarding screen explaining SMS stays on device
4. More → privacy / backup

## Build & sign

```bash
# Create release.keystore once, fill keystore.properties
./gradlew :app:assembleSmsRelease
# → app/build/outputs/apk/sms/release/app-sms-release.apk
```

Galaxy Store requires a stable **release** signature for updates. Keep that keystore backed up.
