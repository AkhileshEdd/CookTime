# CookTime

A native, offline-first Android cooking companion. Kotlin, Jetpack Compose, Material 3. Warm terracotta and sage colours, light/dark themes, bundled decorative food illustrations, no account, ads, analytics or recipe API.

## Features

**Free:** 60 original recipes (30 Indian / 30 international), mood/vibe, occasion and special-day discovery, snacks, desserts and drinks, time and diet filters, ingredient exclusions, pantry-based recommendations, pantry quantities, favourites, search by dish or ingredient, serving scaling, guided cooking with screen wake lock, in-app timer, shopping-list aggregation, sharing, and moving checked groceries into the pantry.

**One-time Pro:** weekly breakfast/lunch/dinner planner, week-to-shopping-list conversion, pantry expiry dates, custom recipe creation, JSON backup and restore using Android's document picker.

All content and cooking features work offline. Google Play is needed to buy or restore Pro; a locally verified purchase is retained for offline access. No cloud AI or network recipe dependency.

## Build

Requires JDK 17, Android SDK 36 and Gradle 8.13. Open in Android Studio and sync, or:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug bundleRelease
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Release bundle: `app/build/outputs/bundle/release/app-release.aab` (unsigned until you supply signing configuration). The Android verification workflow also builds and uploads both artifacts. Never distribute a debug APK as a paid production build.

If using a standalone Gradle installation, `gradle` can replace `./gradlew`.

## Play Console setup required before sales

1. Confirm the permanent application ID (`com.cooktime.app`) before your first Play upload.
2. Create a **one-time, non-consumable** product named `cooktime_pro_lifetime`. Configure a single buy option and regional price; do not create a subscription or rental offer.
3. Supply the app's Base64 Google Play licensing public key using `COOKTIME_PLAY_LICENSE_KEY` in your private Gradle properties. This public key verifies signed purchase data. With no key the release build fails closed: purchasing cannot unlock Pro.
4. Configure an upload signing key outside the repository, enable Play App Signing, upload the signed bundle to internal testing, activate the product, and add licence testers.
5. Test purchase, cancellation, pending-to-purchased, restore, refunds/revocation, offline restart and acknowledgement retry. Real purchase tests cannot run from an ordinary sideloaded debug app.
6. Complete the Play store listing, privacy policy, data safety and content rating declarations, and recheck current target API / billing requirements before submission.

`ProBilling` uses Billing Library 9.1.0, fresh product details, pending purchase handling, signed purchase verification, acknowledgement, startup restore and successful-query revocation. Network errors preserve a previously verified offline entitlement. It never consumes a lifetime purchase. A debug-only **Toggle Pro preview** lets testers inspect premium screens without changing a stored purchase. This control is absent from release builds.

This is a client-only purchase design, suitable for an offline app but not tamper-proof. A secure verification backend with Play Developer API / real-time notifications is a possible future hardening step; it is not included. Offline refunds cannot be detected until the next successful Play refresh.

## Data and behaviour

- Explicit, versioned JSON stored atomically in app-private storage; writes run on an IO coroutine. Android automatic backup is disabled. User-selected exports never include purchase entitlement.
- Restore validates the complete backup before confirmation/replacement and caps input at 2 MB.
- Pantry matching compares normalized names and a small explicit alias map. It does **not** estimate whether pantry quantities are sufficient. Expired items are excluded.
- Shopping amounts aggregate only for identical ingredient names and units; no unsafe unit conversions. Adding a recipe or week repeatedly adds its quantities again.
- Recipe ingredients scale; times and water in method text are baseline guidance, not automatically scaled.
- Vegan / vegetarian labels are broad recipe categories. Exclusion is a text filter, not allergen certification; inspect product labels. Vegetarian excludes eggs in this collection.
- Timer is an in-app aid. Keep the cooking screen open; it is not a background notification alarm. Screen stays awake during guided cooking. Active countdown uses an absolute deadline across rotation/backgrounding while that screen survives.
- Date input uses ISO `YYYY-MM-DD`. UI and recipes are English. Offline custom recipes can be created, edited and deleted.
- Food art is original procedural decoration, not a photographic representation of a dish.

## Verification

JVM tests cover expiry boundaries, diet and exclusion filters, pantry matching, ranking, serving scaling / grocery aggregation, backup validation and recipe integrity. CI runs tests, Android lint, debug APK compilation and release bundle compilation. Device UX and licensed Google Play purchase tests remain required before store launch.

See [PRIVACY.md](PRIVACY.md) and [TESTING.md](TESTING.md). Recipes are original starter content and should be kitchen-tested before publication; cooking times depend on equipment and ingredients.


## 1.1.0 — Cook for the moment

Adds 36 recipes and tags across the whole catalogue. Discover and Recipes support combined mood, occasion and special-day filters; search includes tags. Tags are editorial inspiration, not automatic holiday scheduling or religious/dietary certification. Custom recipes can have moment tags too. Existing recipe IDs stay stable, and version-1 backups without tag fields still load. The collection contains 30 Indian and 30 international recipes; no recipe download or additional payment is needed.
