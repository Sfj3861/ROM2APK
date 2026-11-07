package com.sfj3861.rom2apk.core.porting

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a source-only Android project from backend-produced native source.
 * It never copies the original ROM into the output project.
 */
object AndroidPortProjectGenerator {
    private const val PORT_INTERFACE_PATH =
        "app/src/main/java/generated/port/GeneratedGamePort.kt"

    fun generate(
        request: PortRequest,
        translation: PortTranslation
    ): ByteArray {
        require(translation.success) { "No se puede generar un proyecto desde una traducción fallida." }
        require(translation.sourceFiles.isNotEmpty()) { "El backend no ha generado código fuente." }
        require(isValidApplicationId(request.applicationId)) { "Identificador de aplicación no válido." }
        require(request.applicationName.isNotBlank()) { "El nombre de la aplicación está vacío." }
        require(request.versionName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,49}"))) { "La versión debe tener entre 1 y 50 caracteres seguros." }
        require(translation.backendId.isNotBlank()) { "El backend no tiene identificador." }
        require(translation.gameTitle.isNotBlank()) { "La traducción no tiene título." }

        val files = linkedMapOf<String, String>()
        files["settings.gradle.kts"] = settingsGradle()
        files["build.gradle.kts"] = rootBuildGradle()
        files["gradle.properties"] = gradleProperties()
        files["app/build.gradle.kts"] = appBuildGradle(request)
        files["app/src/main/AndroidManifest.xml"] = manifest(request)
        files["app/src/main/res/values/strings.xml"] = strings(request)
        files[PORT_INTERFACE_PATH] = AndroidInputTemplates.gamePortSource()
        files["app/src/main/java/generated/port/VirtualControlsView.kt"] =
            AndroidInputTemplates.controlsViewSource()
        files["app/src/main/java/generated/port/GeneratedPortActivity.kt"] =
            AndroidInputTemplates.activitySource()
        files["README-PORT.md"] = readme(request, translation)

        require(translation.sourceFiles.map { it.path }.distinct().size == translation.sourceFiles.size) {
            "El backend ha repetido una ruta de archivo."
        }
        translation.sourceFiles.forEach { source ->
            require(isSafeRelativePath(source.path)) { "Ruta de fuente no permitida: ${source.path}" }
            require(source.path.endsWith(".kt") || source.path.endsWith(".java")) {
                "El generador actual compila fuentes Kotlin/Java. El backend debe emitir una de ellas: ${source.path}"
            }
            require(source.path !in files) { "El backend intenta sobrescribir un archivo reservado: ${source.path}" }
            require(source.content.isNotBlank()) { "El archivo de fuente está vacío: ${source.path}" }
            files[source.path] = source.content
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.toSortedMap().forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path).apply { time = 0L })
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun isValidApplicationId(value: String): Boolean =
        value.length in 3..200 &&
            value.split('.').size >= 2 &&
            value.split('.').all { segment ->
                segment.isNotEmpty() &&
                    segment.first() in ('A'..'Z') + ('a'..'z') &&
                    segment.all { it in ('A'..'Z') || it in ('a'..'z') || it in ('0'..'9') || it == '_' }
            }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path) return false
        if (!path.startsWith("app/src/main/java/")) return false
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return false
        if (!path.endsWith(".kt") && !path.endsWith(".java")) return false
        if (path.startsWith("app/src/main/java/generated/port/")) return false
        return true
    }

    private fun settingsGradle() = """
        pluginManagement {
            repositories { google(); mavenCentral(); gradlePluginPortal() }
        }
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories { google(); mavenCentral() }
        }
        rootProject.name = "PortedGame"
        include(":app")
    """.trimIndent()

    private fun rootBuildGradle() = """
        plugins {
            id("com.android.application") version "8.13.2" apply false
            id("org.jetbrains.kotlin.android") version "2.2.20" apply false
        }
    """.trimIndent()

    private fun gradleProperties() = """
        org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
        android.useAndroidX=true
        kotlin.code.style=official
    """.trimIndent()

    private fun appBuildGradle(request: PortRequest) = """
        plugins {
            id("com.android.application")
            id("org.jetbrains.kotlin.android")
        }

        android {
            namespace = "${request.applicationId}"
            compileSdk = 36
            defaultConfig {
                applicationId = "${request.applicationId}"
                minSdk = 26
                targetSdk = 35
                versionCode = 1
                versionName = "${request.versionName}"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            kotlinOptions { jvmTarget = "17" }
        }
    """.trimIndent()

    private fun manifest(request: PortRequest) = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application
                android:allowBackup="false"
                android:label="@string/app_name"
                android:supportsRtl="true"
                android:theme="@android:style/Theme.Material.Light.NoActionBar">
                <activity
                    android:name="generated.port.GeneratedPortActivity"
                    android:exported="true">
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN" />
                        <category android:name="android.intent.category.LAUNCHER" />
                    </intent-filter>
                </activity>
            </application>
        </manifest>
    """.trimIndent()

    private fun strings(request: PortRequest) =
        """<?xml version="1.0" encoding="utf-8"?><resources><string name="app_name">${xmlEscape(request.applicationName)}</string></resources>"""

    private fun generatedPortContract() = """
        package generated.port

        interface GeneratedGamePort {
            val title: String
            fun createSession(): GeneratedGameSession
        }

        interface GeneratedGameSession {
            fun onCreate()
            fun onPause()
            fun onResume()
            fun onDestroy()
        }
    """.trimIndent()

    private fun generatedActivity() = """
        package generated.port

        import android.app.Activity
        import android.os.Bundle

        class GeneratedPortActivity : Activity() {
            private var session: GeneratedGameSession? = null

            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                // The selected port backend supplies GeneratedGameImplementation.
                session = GeneratedGameImplementation().createSession()
                session?.onCreate()
            }

            override fun onPause() {
                session?.onPause()
                super.onPause()
            }

            override fun onResume() {
                super.onResume()
                session?.onResume()
            }

            override fun onDestroy() {
                session?.onDestroy()
                session = null
                super.onDestroy()
            }
        }
    """.trimIndent()

    private fun readme(request: PortRequest, translation: PortTranslation) = """
        # Native port project

        - App: ${request.applicationName}
        - Application ID: ${request.applicationId}
        - Backend: ${translation.backendId}
        - Translation: ${translation.gameTitle}

        This project contains generated native source code, not a bundled console emulator.
        The backend must provide GeneratedGameImplementation and all required platform-native
        source files before the Android project can compile.

        On-screen multi-touch controls and keyboard/gamepad shortcuts feed the same GameAction set:
        arrows/WASD, X/Z/C/V, Enter, Shift, Q and E. The port backend receives onInputChanged;
        it implements game logic and rendering, not an emulator.

        Build with JDK 17, Android SDK 36 and Gradle 8.13:
        gradle :app:assembleRelease

        Sign the resulting APK with an owner-controlled signing key. Never commit private keys.
    """.trimIndent()

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
