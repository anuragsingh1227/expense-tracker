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

## Amazon / other APK stores

Upload `app-store-release.apk` (or AAB if accepted). Reuse the same signing key when possible. Link the same privacy policy.

## Samsung Galaxy Store (step-by-step)

Use the **`store`** flavor only (`com.expensetracker.offline`). Do **not** upload the SMS APK — Galaxy Store reviews permissions like Play and the SMS build requests `READ_SMS` / `RECEIVE_SMS`.

### A. Seller account (one-time)

1. Create a [Samsung account](https://account.samsung.com/) (prefer a company/private domain email; Gmail needs an explanation).
2. Register at [Seller Portal](https://seller.samsungapps.com/) — set Country/Region correctly first (hard to change later).
3. Apply for **commercial seller** status (required for free *and* paid apps):
   - Private seller (individual) or Corporate seller
   - Business verification: D‑U‑N‑S is easiest; otherwise contact Seller Portal Help → Contact us
   - Financial info: PayPal is usually simplest; bank country must match Seller Portal country
4. Wait for approval (often several days; bank/D‑U‑N‑S checks can take up to ~10 business days).

Docs: [Get started](https://developer.samsung.com/galaxy-store/prepare.html)

### B. Binary to upload

| Field | This project |
|-------|----------------|
| Package | `com.expensetracker.offline` |
| Flavor | `store` |
| `versionName` / `versionCode` | from `app/build.gradle.kts` (bump `versionCode` every upload) |
| `targetSdk` | 34 (≥ 33 required) |
| 64-bit | included (`arm64-v8a` in the APK) |
| Format | **APK or AAB** (AAB → Galaxy generates a universal APK; once AAB, you can’t go back to APK for that app) |

Build locally (release-signed):

```bash
# Requires keystore.properties + release.keystore (see above)
./gradlew :app:assembleStoreRelease
# → app/build/outputs/apk/store/release/app-store-release.apk

./gradlew :app:bundleStoreRelease
# → app/build/outputs/bundle/storeRelease/app-store-release.aab
```

**Signing:** Galaxy Store rejects/updates require a stable release key. Do not upload a debug-signed APK. Create `release.keystore` once and keep it backed up.

### C. Listing content to prepare

1. **Privacy policy URL** (required) — host `docs/privacy-policy.html` publicly, e.g. GitHub Pages:
   - Suggested path after enabling Pages on this repo:  
     `https://anuragsingh1227.github.io/expense-tracker/privacy-policy.html`  
   - Or any HTTPS page you control. Paste the same URL in Seller Portal + app overview.
2. **App title:** Expense Tracker  
3. **Short / long description** — offline Indian spend tracker; paste/share bank SMS; no account; no internet.  
4. **Category:** Finance / Personal finance  
5. **Screenshots** — phone captures of Spends, Activity, More (paste import). No device frame required; 16:9 or store-recommended sizes.  
6. **Icon** — use `@mipmap/ic_launcher` export (512×512 PNG for store icon if asked).  
7. **Age rating** — complete the questionnaire (finance / not for children under 13).  
8. **Data safety / privacy** — no data collected by developer; everything on-device; no ads; no account.  
9. **App access** — no login.  
10. **Countries** — start with India (and any others you want).  
11. **Price** — Free (no IAP in this app).

### D. Register & submit in Seller Portal

1. **Android app** → Add new app → Application.
2. Fill binary info → upload `app-store-release.apk` (or `.aab`).
3. Complete listing, privacy, rating, devices (phones; add tablets if you tested).
4. Save → **Submit** for review.
5. Watch email / Seller Portal for defects (permissions, privacy URL, crashes, incomplete listing).

### E. Common rejection traps for this app

| Trap | Fix |
|------|-----|
| Uploading `…offline.sms` APK | Use **store** flavor only |
| Debug-signed binary | Configure `keystore.properties` |
| Broken / missing privacy URL | Host `docs/privacy-policy.html` on HTTPS |
| Claiming auto-SMS in listing | Store build is paste/share only — say that clearly |
| Same package already on Play with different signing | Keep one signing key for all stores for this package, or use a Galaxy-specific applicationId if you must diverge |

### F. After approval

- Each update: bump `versionCode`, rebuild store release, upload new binary, resubmit.
- Prefer the same keystore forever for `com.expensetracker.offline`.

## Security / size posture (store build)

- No `INTERNET` permission
- R8 minify + resource shrink on release
- `allowBackup=false` + DB excluded from extraction rules
- Debuggable **off** on release
- Do **not** distribute `*-debug.apk` or commit APKs to git

## Versioning

- `versionName` `1.1.10`, `versionCode` `110` (bump `versionCode` on every store upload)
- Debug builds use `.debug` applicationId suffix so they can sit beside release installs
