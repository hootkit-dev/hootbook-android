import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val coreSource = providers.gradleProperty("hootkitCoreDir")
    .orElse(rootProject.file("../hootkit-core").absolutePath)
// Match the app's existing property-presence semantics. Ordinary release/test builds omit it.
val slimCoreNative = project.hasProperty("slimDebug")
val verifyCoreSource by tasks.registering(Exec::class) {
    // Always check before reusing outputs: a checkout can move without changing source bytes.
    commandLine("python3", rootProject.file("scripts/core/build.py"), "--source", coreSource.get(), "--verify-only")
}
val generateCoreBindings by tasks.registering(Exec::class) {
    dependsOn(verifyCoreSource)
    inputs.files(rootProject.file("scripts/core/build.py"), file("core.lock.json"))
    // Rust changes must invalidate generation, even with an unchanged lock file.
    inputs.dir(coreSource.map { file("$it/src") })
    inputs.files(coreSource.map { file("$it/Cargo.lock") }, coreSource.map { file("$it/uniffi.toml") })
    outputs.dir(layout.buildDirectory.dir("generated/kotlin"))
    outputs.file(layout.buildDirectory.file("generated/bindings-provenance.json"))
    commandLine("python3", rootProject.file("scripts/core/build.py"), "--source", coreSource.get())
}
val buildCoreNative by tasks.registering(Exec::class) {
    // Cross-check the real generated default loader before producing any native package.
    dependsOn(generateCoreBindings)
    inputs.files(rootProject.file("scripts/core/build.py"), file("core.lock.json"))
    inputs.property("slimDebug", slimCoreNative)
    inputs.dir(coreSource.map { file("$it/src") })
    inputs.files(coreSource.map { file("$it/Cargo.lock") }, coreSource.map { file("$it/uniffi.toml") })
    outputs.dir(layout.buildDirectory.dir("generated/jniLibs"))
    outputs.file(layout.buildDirectory.file("generated/native-provenance.json"))
    commandLine(listOf("python3", rootProject.file("scripts/core/build.py"), "--source", coreSource.get(), "--native") +
        if (slimCoreNative) listOf("--abis", "arm64-v8a") else emptyList<String>())
}

android {
    namespace = "dev.hootkit.bindings"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    // The build script already strips these libraries and hashes the shipped bytes.
    packaging { jniLibs.keepDebugSymbols += "**/libuniffi_hootkit_core.so" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
}

// Use the common source-set API while the app retains AGP's legacy DSL compatibility mode.
extensions.getByType<com.android.build.gradle.BaseExtension>().sourceSets.getByName("main").apply {
    java.srcDir(layout.buildDirectory.dir("generated/kotlin").get().asFile)
    jniLibs.srcDir(layout.buildDirectory.dir("generated/jniLibs").get().asFile)
}

tasks.named("preBuild").configure { dependsOn(generateCoreBindings) }
tasks.configureEach {
    if (name.endsWith("JniLibFolders")) dependsOn(buildCoreNative)
}

dependencies {
    // UniFFI's JNA-facing types are part of the generated API.
    api("${libs.jna.get()}@aar")
    api(libs.kotlinx.coroutines.core)
    implementation("androidx.annotation:annotation:1.9.1")
}
