# Release verification checklist

## Automated
- `./gradlew testDebugUnitTest lintDebug assembleDebug bundleRelease`
- Review lint results in `app/build/reports/lint-results-debug.html`.

## Device / emulator
- Fresh start: welcome screen, each diet choice, Discover, all bottom tabs.
- Airplane mode: browse every bundled recipe; use pantry matching, favourites and search; cold restart and verify persistence.
- Add/edit/remove pantry items. Today is usable; yesterday is excluded. Invalid dates cannot save.
- Scale servings; add missing ingredients twice and verify aggregation; different units remain distinct.
- Check/uncheck, remove/share groceries; move checked groceries to pantry.
- Pro debug preview: weekly navigation, all meal slots, replacement, deletion and week shopping generation.
- Create a custom recipe; reject malformed amounts and zero servings.
- Export backup; modify state; restore with confirmation. Corrupt / oversized / unsupported backup must leave data intact.
- Light/dark themes, Android back navigation, rotation, keyboard visibility, TalkBack, large font, small phone and tablet.
- Cooking mode: next/previous/finish; timer completion and cancellation; rotate; return after backgrounding. No background alarm is promised.
- Reset data confirmation and cancellation.

## Google Play internal testing (release build)
- Configure signing, public licensing key and the lifetime product first.
- Localized price is supplied by Play, never hard-coded.
- Successful purchase unlocks Pro and is acknowledged; cancellation does not unlock.
- Pending payment remains locked; completion unlocks once signed purchase is received.
- Restore an existing purchase, restart offline, clear app data then restore online.
- Refund/revoke and refresh online; entitlement is removed after a successful empty purchase query.
- Disconnect network: cached verified access survives a failed query.
- Verify Pro preview control is absent in release.
- Exercise acknowledgement retry and ensure no unacknowledged purchase reaches automatic refund.

## Content / publication
- Kitchen-test the recipes, review timings and dietary labels, and confirm safe storage and preparation guidance.
- Review the final privacy policy and Play data safety form.
- Check package ID ownership, app name availability, artwork, listing copy and screenshots.
- Confirm current Play target SDK and Billing deadlines before submission.
