plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.enginotes.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.enginotes.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Fixed debug keystore (app/debug.keystore) so CI always signs debug builds
    // with the same SHA-1 fingerprint. Without this, Android Gradle Plugin
    // auto-generates a fresh, different debug keystore on every GitHub Actions
    // runner, which would silently break Google Sign-In each time it happens
    // (the OAuth client is locked to one specific SHA-1).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/MANIFEST.MF"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")

    // LaTeX equation rendering (√, fractions, superscript/subscript, integrals, etc.) inside
    // TextItem — see LaTeXSpan in DrawingView.kt. Note this specific artifact is GPL 2.0
    // licensed (unlike everything else in this dependency list) — a deliberate, known tradeoff:
    // it's the one LaTeX-rendering library confirmed to actually resolve from Maven Central
    // right now. The Apache-licensed alternative (NanoMichael/AndroidLaTeXMath) was only ever
    // published to JCenter, which has been permanently shut down since 2022, so it can't be
    // pulled in as a normal Gradle dependency without independently confirming it resolves via
    // JitPack first. The actual "render this LaTeX string" call is isolated to one function
    // (LaTeXSpan.renderDrawable()) specifically so swapping the underlying engine later, if the
    // GPL licensing turns out to matter, doesn't require touching anything else in this feature.
    implementation("ru.noties:jlatexmath-android:0.2.0")
    // Greek letters (θ, α, β, Δ, Σ, ...) show up constantly in engineering formulas — without
    // this font pack they'd render as tofu/missing-glyph boxes instead of the actual symbol.
    implementation("ru.noties:jlatexmath-android-font-greek:0.2.0")

    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // BiometricPrompt (fingerprint/face unlock) for the app-lock/encryption feature —
    // SecurityManager's getBiometricCipherForUnlock() is built around
    // androidx.biometric.BiometricPrompt's CryptoObject flow specifically.
    implementation("androidx.biometric:biometric:1.1.0")

    // Recovery-code QR: zxing generates the QR image shown at setup time; ML Kit's barcode
    // scanner decodes it back (from either a captured camera photo or an imported gallery
    // image) during the recovery flow.
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Google Sign-In (lets the user authenticate with their Google account)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Google Drive REST API client (for saving/loading notes to the user's own Drive)
    implementation("com.google.api-client:google-api-client-android:2.7.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20240914-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0") {
        exclude(group = "org.apache.httpcomponents")
    }
}
