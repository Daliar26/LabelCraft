#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p \
  gradle/wrapper \
  .github/workflows \
  build \
  app/src/main/java/dev/openlabel/labelcraft \
  app/src/main/res/values \
  app/src/main/res/mipmap-anydpi-v26 \
  app/src/test/java/dev/openlabel/labelcraft \
  core/printer/src/main/java/dev/openlabel/core/printer/{transport,protocol,calibration,queue,hardware} \
  core/printer/src/test/java/dev/openlabel/core/printer \
  core/label/src/main/java/dev/openlabel/core/label/{model,render,codec,calibration,csv} \
  core/label/src/test/java/dev/openlabel/core/label \
  feature/editor/src/main/java/dev/openlabel/feature/editor \
  feature/devices/src/main/java/dev/openlabel/feature/devices \
  feature/templates/src/main/java/dev/openlabel/feature/templates

# Keep the project bootstrap repeatable: generated source files are not overwritten.
ensure_file() { local path="$1"; shift; if [[ ! -e "$path" ]]; then cat > "$path"; fi; }
ensure_file settings.gradle.kts <<'EOT'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "LabelCraft"
include(":app", ":core:printer", ":core:label", ":feature:editor", ":feature:devices", ":feature:templates")
EOT
ensure_file build.gradle.kts <<'EOT'
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}

tasks.register("test") {
    group = "verification"
    description = "Runs unit tests for every Android module's debug variant."
}

gradle.projectsEvaluated {
    val unitTestTasks = subprojects.mapNotNull { it.tasks.findByName("testDebugUnitTest") }
    tasks.named("test") { dependsOn(unitTestTasks) }
}

tasks.register("clean", Delete::class) { delete(rootProject.layout.buildDirectory) }
EOT
ensure_file gradle.properties <<'EOT'
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
EOT
ensure_file gradle/wrapper/gradle-wrapper.properties <<'EOT'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOT
ensure_file gradlew <<'EOT'
#!/usr/bin/env sh
# Wrapper launcher. The standard gradle-wrapper.jar is generated with:
#   gradle wrapper --gradle-version 8.13
# This source bundle intentionally does not fabricate or vendor a binary wrapper JAR.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
  if [ -n "${JAVA_HOME:-}" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
  exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  echo "[LabelCraft] gradle-wrapper.jar is absent; using installed Gradle $(gradle --version | sed -n '3p' | tr -d '\r')." >&2
  exec gradle "$@"
fi
echo "Gradle wrapper JAR is not bundled and no system Gradle was found." >&2
echo "Install Gradle 8.13, then run: gradle wrapper --gradle-version 8.13" >&2
exit 127
EOT
ensure_file gradlew.bat <<'EOT'
@echo off
set APP_HOME=%~dp0
if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  java -Dorg.gradle.appname=gradlew -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (gradle %*) else (echo Gradle 8.13 is required. Generate wrapper.jar with: gradle wrapper --gradle-version 8.13 & exit /b 127)
EOT
ensure_file .gitignore <<'EOT'
.gradle/
.idea/
local.properties
**/build/
.DS_Store
*.iml
*.jks
*.keystore
keystore.properties
*.apk
*.aab
captures/
.externalNativeBuild/
.cxx/
EOT
ensure_file app/src/main/AndroidManifest.xml <<'EOT'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-feature android:name="android.hardware.bluetooth" android:required="false" />
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <application android:name=".LabelCraftApplication" android:allowBackup="false" android:label="LabelCraft" android:supportsRtl="true" android:theme="@style/Theme.LabelCraft">
        <activity android:name=".MainActivity" android:exported="true" android:screenOrientation="unspecified">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity>
    </application>
</manifest>
EOT
ensure_file app/src/main/res/values/styles.xml <<'EOT'
<resources><style name="Theme.LabelCraft" parent="android:style/Theme.Material.Light.NoActionBar"><item name="android:fontFamily">sans</item><item name="android:colorAccent">#315C9B</item><item name="android:windowLightStatusBar">true</item></style></resources>
EOT
for module in core/printer core/label feature/editor feature/devices feature/templates; do
  ensure_file "$module/src/main/AndroidManifest.xml" <<'EOT'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
EOT
done

ensure_file gradle/libs.versions.toml <<'EOT'
[versions]
agp = "8.13.2"
kotlin = "2.2.21"
ksp = "2.2.21-2.0.4"
hilt = "2.57.2"
compose-bom = "2025.11.00"
activity-compose = "1.11.0"
navigation-compose = "2.9.5"
lifecycle = "2.9.4"
coroutines = "1.10.2"
serialization = "1.9.0"
zxing = "3.5.3"
junit = "4.13.2"
detekt = "1.23.8"
ktlint = "12.3.0"

[libraries]
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation-compose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }
zxing-core = { module = "com.google.zxing:core", version.ref = "zxing" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlint" }
EOT
ensure_file app/build.gradle.kts <<'EOT'
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}
android {
    namespace = "dev.openlabel.labelcraft"
    compileSdk = 36
    defaultConfig { applicationId = "dev.openlabel.labelcraft"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    buildFeatures { compose = true }
}
dependencies { implementation(platform(libs.androidx.compose.bom)); implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.foundation); implementation(libs.androidx.compose.material3); implementation(project(":core:printer")); implementation(project(":core:label")) }
EOT
ensure_file core/label/build.gradle.kts <<'EOT'
plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization) }
android { namespace = "dev.openlabel.core.label"; compileSdk = 36; defaultConfig { minSdk = 26 } }
dependencies { implementation(libs.zxing.core); implementation(libs.kotlinx.serialization.json); testImplementation(libs.junit) }
EOT
ensure_file core/printer/build.gradle.kts <<'EOT'
plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization) }
android { namespace = "dev.openlabel.core.printer"; compileSdk = 36; defaultConfig { minSdk = 26 } }
dependencies { implementation(project(":core:label")); implementation(libs.kotlinx.coroutines.android); implementation(libs.hilt.android); implementation(libs.kotlinx.serialization.json); testImplementation(libs.junit) }
EOT
for module in feature/editor feature/devices feature/templates; do
  ns=$(printf '%s' "$module" | tr '/' '.')
  ensure_file "$module/build.gradle.kts" <<EOT
plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android { namespace = "dev.openlabel.$ns"; compileSdk = 36; defaultConfig { minSdk = 26 }; buildFeatures { compose = true } }
dependencies { implementation(platform(libs.androidx.compose.bom)); implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.foundation); implementation(libs.androidx.compose.material3) }
EOT
done
ensure_file .github/workflows/android-ci.yml <<'EOT'
name: Android CI
on: [push, pull_request, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
        with: { gradle-version: '8.13' }
      - run: sdkmanager 'platforms;android-36' 'build-tools;36.0.0'
      - run: gradle assembleDebug assembleRelease testDebugUnitTest
      - uses: actions/upload-artifact@v4
        with: { name: labelcraft-apks, path: 'app/build/outputs/apk/**/*.apk' }
EOT
ensure_file .github/workflows/release.yml <<'EOT'
name: Signed Android Release
on: { push: { tags: ['v*'] } }
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
        with: { gradle-version: '8.13' }
      - run: gradle assembleRelease
      - run: gh release create "$GITHUB_REF_NAME" app/build/outputs/apk/release/*.apk
        env: { GH_TOKEN: '${{ github.token }}' }
EOT
ensure_file README.md <<'EOT'
# LabelCraft

Offline Bluetooth thermal label editor. See `README_EN.md` for English documentation.
EOT
ensure_file README_EN.md <<'EOT'
# LabelCraft

Offline Bluetooth thermal label editor. See `README.md` for Russian documentation.
EOT
ensure_file DECISIONS.md <<'EOT'
# DECISIONS

Implementation choices and hardware caveats are recorded here.
EOT

# Android Studio and CI can regenerate the binary wrapper from the checked-in wrapper properties.
chmod +x gradlew
printf 'Scaffold created at %s\n' "$ROOT"
printf 'Gradle wrapper properties: %s\n' "$ROOT/gradle/wrapper/gradle-wrapper.properties"
printf 'Note: gradle-wrapper.jar must be generated with Gradle 8.13; network is unavailable in this environment.\n'
