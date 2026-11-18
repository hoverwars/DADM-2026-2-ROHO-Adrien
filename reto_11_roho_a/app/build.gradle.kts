import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.reto_11_roho_a"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.reto_11_roho_a"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only ship the native libs we need: real phones (arm64)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // android.util.Log & co. do nothing in JVM unit tests instead of throwing
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * The LLM (2.6 GB) does not belong in the APK: it is downloaded here once, into <root>/llm, and
 * run.bat pushes it to the phone (see LocalLlm.modelFile). Not tied to any build task.
 * Gemma 4 E2B, the litert-community build for LiteRT-LM (public, no Hugging Face login needed).
 */
abstract class DownloadModelsTask : DefaultTask() {
    @get:Input
    abstract val models: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        models.get().forEach { (fileName, url) ->
            val target = outputDir.file(fileName).get().asFile
            if (target.exists() && target.length() > 0) return@forEach
            target.parentFile.mkdirs()
            logger.lifecycle("Downloading $fileName ...")
            val tmp = File(target.parentFile, "$fileName.part")
            URI(url).toURL().openStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(target)
        }
    }
}

tasks.register<DownloadModelsTask>("downloadLlm") {
    models.putAll(
        mapOf(
            "gemma-4-E2B-it.litertlm" to
                "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/" +
                "gemma-4-E2B-it.litertlm",
        )
    )
    outputDir.set(rootProject.layout.projectDirectory.dir("llm"))
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // LiteRT-LM: on-device LLM runtime with native tool calling, runs Gemma 4
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.19.1")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // UI Tests
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Optional - Add window size utils
    implementation("androidx.compose.material3.adaptive:adaptive")

    // Optional - Integration with activities
    implementation("androidx.activity:activity-compose:1.13.0")
    // Optional - Integration with ViewModels
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    // Navigation between the menu and the chat (each screen gets its own ViewModel)
    implementation("androidx.navigation:navigation-compose:2.10.2")
    // Optional - Integration with LiveData
    implementation("androidx.compose.runtime:runtime-livedata")
    // Optional - Integration with RxJava
    implementation("androidx.compose.runtime:runtime-rxjava2")
}
