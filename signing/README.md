# Release signing

`app/build.gradle.kts` reads `signing/signing.properties` (git-ignored) to sign `assembleRelease`.
Without it the release build is produced unsigned; the submitted, signed APK lives in `apk/`.

To rebuild a signed APK locally:

1. Create a keystore (see `signing.properties.example`).
2. Copy `signing.properties.example` to `signing.properties` and fill in the passwords.
3. `./gradlew :app:assembleRelease`

Release APK certificate (submitted build): SHA-256 `fd4e9475db87601d665ae60903fcf961959e674c5de961ed403a2157cce4dc10`.
