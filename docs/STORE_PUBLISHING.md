# Publishing Expense Tracker to app stores

## Critical: Google Play and SMS

Google Play **rejects** apps that declare `READ_SMS` / `RECEIVE_SMS` unless the app is the user’s **default SMS handler** (or a rare approved exception). An expense tracker is **not** an eligible exception.

This project therefore ships **two flavors**:

| Flavor | Application ID | SMS inbox | Where to publish |
|--------|----------------|-----------|------------------|
| **`store`** (default for stores) | `com.expensetracker.offline` | No — paste / share text only | Google Play, Amazon, Galaxy Store, etc. |
| **`sms`** | `com.expensetracker.offline.sms` | Yes — auto import | GitHub Releases, F-Droid (if accepted), direct APK |

## Build release artifacts

### 1. Create a release keystore (once)

```bash
keytool -genkeypair -v -keystore release.keystore -alias expense \
  -keyalg RSA -keysize 2048 -validity 10000
```

Copy `keystore.properties.example` → `keystore.properties` and fill passwords.  
**Never commit** `release.keystore` or `keystore.properties`.

### 2. Store / Play upload (AAB)

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :app:bundleStoreRelease
# → app/build/outputs/bundle/storeRelease/app-store-release.aab
```

Upload the AAB in Play Console. Use the same keystore for all future updates.

### 3. Universal release APK (Amazon / sideload of store build)

```bash
./gradlew :app:assembleStoreRelease
# → app/build/outputs/apk/store/release/app-store-release.apk
```

### 4. SMS-enabled APK (not for Play)

```bash
./gradlew :app:assembleSmsRelease
```

## Play Console checklist

1. **Privacy policy URL** — host `docs/privacy-policy.html` (GitHub Pages or your site) and paste the URL in Store listing + Data safety.
2. **Data safety form**
   - Data collected: none by the developer (on-device only).
   - Data shared: none.
   - Optional: note that user-exported backup files may contain message text the user chose to save.
3. **Permissions declaration** — store build should only show notifications (and nothing in the SMS group).
4. **Content rating** — complete IARC questionnaire (finance / personal finance).
5. **Target API** — currently 34; raise when Play requires a newer target.
6. **Screenshots** — phone screenshots of Spends / Activity / More (paste import).
7. **App access** — no login required.

## Amazon / Galaxy / other stores

Upload `app-store-release.apk` or AAB if the store accepts it. Reuse the same signing key when possible. Link the same privacy policy.

## Security / size posture (store build)

- No `INTERNET` permission
- R8 minify + resource shrink on release
- `allowBackup=false` + DB excluded from extraction rules
- Debuggable **off** on release
- Do **not** distribute `*-debug.apk` or commit APKs to git

## Versioning

- `versionName` `1.1.8`, `versionCode` `108` (bump `versionCode` on every store upload)
- Debug builds use `.debug` applicationId suffix so they can sit beside release installs
