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
        require(request.versionName.isNotBlank()) { "La versión está vacía." }

        val files = linkedMapOf<String, String>()
        files["settings.gradle.kts"] = settingsGradle()
        files["build.gradle.kts"] = rootBuildGradle()
        files["gradle.properties"] = gradleProperties()
        files["app/build.gradle.kts"] = appBuildGradle(request)
        files["app/src/main/AndroidManifest.xml"] = manifest(request)
        files["app/src/main/res/values/strings.xml"] = strings(request)
        files[PORT_INTERFACE_PATH] = generatedPortContract()
        files["app/src/main/java/generated/port/GeneratedPortActivity.kt"] = generatedActivity()
        files["README-PORT.md"] = readme(request, translation)

        translation.sourceFiles.forEach { source ->
            require(isSafeRelativePath(source.path)) { "Ruta de fuente no permitida: ${source.path}" }
            require(source.path.endsWith(".kt") || source.path.endsWith(".java") ||
                source.path.endsWith(".c") || source.path.endsWith(".cpp") ||
                source.path.endsWith(".h") || source.path.endsWith(".hpp")) {
                "Tipo de fuente no permitido: ${source.path}"
            }
            require(source.path !in files) { "El backend intenta sobrescribir un archivo reservado: ${source.path}" }
            files[source.path] = source.content
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.toSortedMap().forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
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
                segment.isNotEmpty() && segment.first().isLetter() &&
                    segment.all { it.isLetterOrDigit() || it == '_' }
            }

    private fun isSafeRelativePath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || '\\' in path) return false
        val parts = path.split('/')
        return parts.all { it.isNotBlank() && it != "." && it != ".." } &&
            !path.startsWith("app/src/main/AndroidManifest.xml")
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
