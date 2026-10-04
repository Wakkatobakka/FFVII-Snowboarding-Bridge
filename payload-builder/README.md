FFVII SNOWBOARDING BRIDGE — PAYLOAD BUILDER v0.1.0

PURPOSE
This creates the private game-data ZIP used by FFVII Snowboarding Bridge v0.1.2.
No game files are included with this builder and nothing is uploaded anywhere.

YOU PROVIDE
- the supported game-offline-english.jar
- the matching game-offline-english.sp

THE BUILDER DOES LOCALLY
1. Verifies both supplied files by SHA-256.
2. Uses Android D8 to create game.dex.
3. Extracts the three original MLD tracks and creates Android-playable MIDI mirrors.
4. Creates lossless PNG mirrors of the four legacy 8-bit BMP screens.
5. Writes FFVII_Snowboarding_Data_for_Bridge_v0.1.2.zip.

REQUIREMENTS
- Python 3
- Java (Java 17 recommended)
- Android SDK API 35 + Build-Tools 35.0.0
  The builder auto-detects the normal Android Studio SDK location and ANDROID_SDK_ROOT / ANDROID_HOME.
  You can also set ANDROID_BUILD_TOOLS and ANDROID_JAR explicitly.

WINDOWS
Double-click RUN-PAYLOAD-BUILDER-WINDOWS.bat and paste the two requested paths.
You can also drag the JAR and SP onto the BAT together.

OUTPUT
FFVII_Snowboarding_Data_for_Bridge_v0.1.2.zip

Then open FFVII Snowboarding Bridge on Android, tap IMPORT GAME DATA, select that ZIP, and wait for the verified-import confirmation.
