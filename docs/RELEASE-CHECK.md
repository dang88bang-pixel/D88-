# D88 – Release-Checkliste

## Status (ehrlich)

- [x] Debug-APK wird in der CI gebaut und als Artefakt bereitgestellt
- [ ] Signierte Release-APK: **no**ch nicht – benötigt eigene Secrets
- [ ] Funktionstests auf echten Geräten: noch offen (M3)

## Vor dem ersten signierten Release

1. **Keystore erstellen** (auf eigenem Gerät, sicherer Ort):
   ```
   keytool -genkey -v -keystore d88-release.keystore -alias d88 \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. **GitHub Secrets einrichten** (Repository → Settings → Secrets):
   - `D88_RELEASE_KEystore_BASE64` → `base64 -w0 d88-release.keystore`
   - `D88_RELEASE_KEystore_PASSWORD`
   - `D88_RELEASE_KEY_ALIAS`
   - `D88_RELEASE_KEY_PASSWORD`
3. **Kein Key im Repo/Chat** – der private Schlüssel darf weder im
   Repository noch im Chat verweilen.
4. Release-Tag auf `main` setzen → CI baut + signiert + lädt Artefakt hoch.

## Prüfungen

- [ ] `./gradlew :app:testDebugUnitTest` grün (alle JVM-Tests)
- [ ] `./gradlew :app:assembleRelease` mit Secrets signiert
- [ ] `apksigner verify --print-certs app-release.apk`
- [ ] APK-Prüfsumme (SHA-256) dokumentiert und mit dem Artefakt verknüpft
- [ ] Smoke-Test auf echtem Gerät: Start, Dashboard, Chat-Task,
      Freigabe-Flow, Audit-Verifikation
- [ ] Manifest-Check: keine INTERNET-Permission (Privacy-Default)
- [ ] Versionen: versionCode/versionName hochgesetzt, Policy-Version geprüft

## Rollback

- Versionierung über versionCode; letzte signierte APK wird lokal
  archiviert (kein Cloud-Store).
