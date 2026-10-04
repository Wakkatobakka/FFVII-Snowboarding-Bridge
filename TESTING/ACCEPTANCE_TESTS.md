# FFVII Snowboarding Bridge v0.1.2 — phone acceptance

This pass validates the public packaging/import conversion. Gameplay/control/audio tuning is frozen from the accepted private baseline.

1. Install `FFVII_Snowboarding_Bridge_v0.1.2.apk`.
2. Confirm it appears as **FFVII Snowboarding Bridge** and installs separately from the old Omni prototype package.
3. Before import, **HIT THE SLOPES** must be disabled and the launcher must say game data is required.
4. Tap **IMPORT GAME DATA** and select `FFVII_Snowboarding_Data_for_Bridge_v0.1.2.zip`.
5. Confirm the app reports **FFVII Snowboarding game data imported / verified (13 files)**.
6. Confirm **HIT THE SLOPES** becomes enabled.
7. Start the game and verify title/menu rendering.
8. Start a race and verify the same phone-verified audio behavior.
9. Verify the custom two-handed fixed-button layout behaves exactly like the accepted v0.1.1 layout.
10. Switch to Keypad and verify the native keypad remains unchanged.
11. Return to the bridge and resume the same session.
12. Re-open the app and verify the imported payload remains ready without re-importing.

Do not revise gameplay, controls, renderer, or audio during this acceptance pass. Any failure should be treated as a public-import/package regression first.
