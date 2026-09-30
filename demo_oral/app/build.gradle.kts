import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.demo_oral"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.demo_oral"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Only ship the native libs we need: real phones (arm64) + emulator (x86_64)
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
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

    // ONNX models are read straight from the APK, keep them uncompressed
    androidResources {
        noCompress += "onnx"
    }
}

/**
 * Downloads the speech models into src/main/assets/models (git-ignored, the Whisper decoder
 * is bigger than GitHub's 100 MB file limit). Files already present are skipped.
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
            logger.lifecycle("Downloading $fileName ...")
            val tmp = File(target.parentFile, "$fileName.part")
            URI(url).toURL().openStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            tmp.renameTo(target)
        }
    }
}

val whisperUrl = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/main"
val downloadModels = tasks.register<DownloadModelsTask>("downloadModels") {
    models.putAll(
        mapOf(
            "small-encoder.int8.onnx" to "$whisperUrl/small-encoder.int8.onnx",
            "small-decoder.int8.onnx" to "$whisperUrl/small-decoder.int8.onnx",
            "small-tokens.txt" to "$whisperUrl/small-tokens.txt",
            "silero_vad.onnx" to "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        )
    )
    outputDir.set(layout.projectDirectory.dir("src/main/assets/models"))
}
tasks.named("preBuild") { dependsOn(downloadModels) }

// The chat LLM (1.6 GB) does not belong in the APK: it is downloaded here once, into <root>/llm,
// and run.bat pushes it to the phone (see LocalLlm.modelFile). Not tied to any build task.
tasks.register<DownloadModelsTask>("downloadLlm") {
    models.putAll(
        mapOf(
            "qwen2.5-1.5b-instruct.task" to
                "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/" +
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
            // Tool router. Community mirror of Google's FunctionGemma "Mobile Actions" fine-tune:
            // the official litert-community/functiongemma-270m-ft-mobile-actions is gated.
            "functiongemma-270m-mobile-actions.litertlm" to
                "https://huggingface.co/JackJ1/functiongemma-270m-it-mobile-actions-litertlm/resolve/main/" +
                "mobile-actions_q8_ekv1024.litertlm",
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

    // sherpa-onnx (on-device speech recognition runtime, onnxruntime statically linked)
    // https://github.com/k2-fsa/sherpa-onnx/releases
    implementation(files("libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar"))

    // MediaPipe LLM Inference (on-device LLM runtime)
    implementation("com.google.mediapipe:tasks-genai:0.10.35")

    // LiteRT-LM (on-device LLM runtime with tool calling), runs the tool router
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")

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
    // Optional - Integration with LiveData
    implementation("androidx.compose.runtime:runtime-livedata")
    // Optional - Integration with RxJava
    implementation("androidx.compose.runtime:runtime-rxjava2")
}
