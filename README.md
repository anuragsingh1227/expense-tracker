# Expense Tracker (Android, offline-first)

Automatic Indian expense tracker driven entirely by on-device SMS parsing. No network, no accounts, no telemetry.

## Status
MVP: SMS pipeline (receiver → parser → Room), **inbox backfill**, category chips + learned merchant rules (Groww→Investment, Instamart/Zepto→Groceries, …), **JSON backup/restore via system picker (Google Drive compatible)**, first-launch permission onboarding, dashboard, transaction list/search/detail. Charts, PDF/CSV export, SQLCipher, app lock, budgets remain follow-ups.

## Build

Requires JDK 17 and the Android SDK. On macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
brew install --cask android-commandlinetools
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties

./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The debug APK is emitted at `app/build/outputs/apk/debug/app-debug.apk`.

## Runtime permissions
- `RECEIVE_SMS` / `READ_SMS` — parse incoming banking/UPI SMS
- `POST_NOTIFICATIONS` — surface each parsed transaction
- `RECEIVE_BOOT_COMPLETED` — reserved for future scheduled rescans

## Structure

```
app/src/main/java/com/expensetracker
  ExpenseApp.kt              # Hilt application, notification channel
  di/AppModule.kt            # Room + repository providers
  domain/model/              # Money, Transaction, enums
  data/db/                   # Room DB, entities, DAOs, type converters
  data/repository/           # TransactionRepository + impl
  sms/                       # SmsReceiver, TransactionNotifier, BootReceiver
  sms/parser/                # SmsParser, MerchantDictionary, BankSenders, Categories
  ui/MainActivity.kt         # Compose entry, bottom nav
  ui/screen/                 # Dashboard, Transactions, Detail, Settings
  ui/theme/                  # Material 3 theme
app/src/test/                # Parser + Money unit tests, sample SMS fixtures
```

## SMS parser
`SmsParser.parse(RawSms)` returns a `Transaction` if the body looks like a financial event. Key components:
- Amount regex — `Rs|INR|₹` prefix + Indian comma grouping + optional 2-decimal fraction.
- Type detection — debit/credit keyword lists, first-hit wins on ambiguity.
- Merchant — dictionary match first (Swiggy/Zomato/Amazon/…​ mapped to canonical name + category), else `at MERCHANT` / `to MERCHANT` capture.
- Category — merchant dict → category, else keyword heuristics (SALARY, ATM, EMI, RENT, RECHARGE, MUTUAL FUND/SIP, UTILITIES).
- Payment mode — FASTAG > UPI (keyword or `foo@bank` VPA) > CREDIT/DEBIT CARD > NEFT/IMPS/RTGS > WALLET.
- Reference/account/card/UPI/balance — additional regexes.
- Dedupe hash — SHA-256 over sender, amount, minute-bucketed timestamp, reference (or fallback body prefix). Enforced by a `UNIQUE` index on `dedupeHash`.

Non-transactional SMS (OTPs, promos) are filtered by `isTransactional(body)` before parsing.

## Testing
`./gradlew :app:testDebugUnitTest` runs parser, money, inbox scanner, dashboard range, and permission-list tests. Fixtures live in `SampleSms.kt`.

## Privacy
- No `INTERNET` permission in the manifest.
- Auto-backup and data-extraction rules exclude the database and secure prefs.
- All parsing runs inside the app process; nothing leaves the device.
