import java.util.Properties
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
}
val local = Properties().apply {
    val source = rootProject.file("local.properties")
    if (source.exists()) source.inputStream().use(::load)
}
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("app"))
val homeAssets = rootProject.layout.buildDirectory.dir("generated/home-assets")
val exportHomeGame by tasks.registering(Exec::class) {
    val log=ByteArrayOutputStream()
    val project = rootProject.file("game/home")
    inputs.files(rootProject.fileTree(project) { exclude(".godot/**") })
    outputs.file(homeAssets.map { it.file("home.pck") })
    standardOutput=log
    errorOutput=log
    doFirst {
        val editor = providers.environmentVariable("GODOT_EXECUTABLE").orNull
            ?: throw GradleException("Set GODOT_EXECUTABLE to the Godot 4.7.2 console executable.")
        homeAssets.get().asFile.mkdirs()
        commandLine(editor, "--headless", "--path", project.absolutePath, "--export-pack", "Home", homeAssets.get().file("home.pck").asFile.absolutePath)
    }
    doLast {
        val output=log.toString("UTF-8")
        logger.lifecycle(output)
        if(Regex("SCRIPT ERROR|Parse Error|ERROR:").containsMatchIn(output))throw GradleException("Godot scene export reported errors; home.pck is not deliverable.")
        require(homeAssets.get().file("home.pck").asFile.length()>0) {"Godot did not export home.pck"}
    }
}
android {
    namespace = "com.ai.assistance.operit"
    compileSdk = 36
    ndkVersion = "27.3.13750724"
    defaultConfig {
        applicationId = "cn.localfirst.companion"
        minSdk = 26
        targetSdk = 36
        versionCode = 41
        versionName = "0.40.0"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("companion") {
            val location = local.getProperty("RELEASE_STORE_FILE")
            if (location != null) {
                storeFile = file(location)
                storePassword = local.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = local.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = local.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            signingConfig = signingConfigs.getByName("companion")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
    sourceSets.getByName("main").assets.srcDir(homeAssets)
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
}
kapt { arguments { arg("room.schemaLocation", "$projectDir/schemas") } }
// Both variants export the same schema. Serialize their writers to avoid a partially read JSON.
tasks.matching { it.name == "kaptReleaseKotlin" }.configureEach { mustRunAfter("kaptDebugKotlin") }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("org.godotengine:godot:4.7.2.stable")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
    implementation("net.zetetic:sqlcipher-android:4.10.0@aar")
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation(libs.okhttp)
    implementation(libs.coroutines)
    implementation(libs.mcp.client)
    implementation(libs.ktor.okhttp)
    implementation("org.hjson:hjson:3.1.0")
    // Protocol payloads and credentials must never reach a platform logging backend.
    implementation("org.slf4j:slf4j-nop:2.0.17")
    constraints {
        // MCP's transitive reflection runtime must match this project's Kotlin runtime.
        implementation("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlin.get()}")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation(libs.mockwebserver)
    testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
    testImplementation(libs.coroutines.test)
    testImplementation("io.github.oshai:kotlin-logging:7.0.14")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("io.github.oshai:kotlin-logging:7.0.14")
}
tasks.named("preBuild") { dependsOn(exportHomeGame) }
val verifyNoEmbeddedSigningMaterial by tasks.registering {
    doLast {
        val forbidden = Regex("(?i).*[.](jks|keystore|p12|pkcs12|pem|key|apk|zip|aar|jar|onnx|bin|so)$")
        val roots = listOf(file("src/main/assets"), file("src/main/res/raw"), file("src/main/jniLibs"))
        roots.filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { it.isFile }.forEach {
                require(!forbidden.matches(it.name)) { "Unexpected executable, model or signing asset: ${it.name}" }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyNoEmbeddedSigningMaterial) }
val verifyQuickJsSources by tasks.registering {
    doLast {
        val root = rootProject.file("third_party/quickjs")
        val lock = groovy.json.JsonSlurper().parse(root.resolve("source-lock.json")) as Map<*, *>
        require(lock["version"] == "2026-06-04")
        (lock["files"] as Map<*, *>).forEach { (name, expected) ->
            require(name is String && Regex("[A-Za-z0-9_.-]+").matches(name))
            val hash = MessageDigest.getInstance("SHA-256").digest(root.resolve(name).readBytes())
                .joinToString("") { "%02x".format(it) }
            require(hash == expected) { "QuickJS source differs from source-lock.json: $name" }
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyQuickJsSources) }
tasks.matching { it.name.startsWith("configureCMake") }.configureEach { dependsOn(verifyQuickJsSources) }
tasks.withType<Test>().configureEach { systemProperty("companion.repo", rootProject.projectDir.absolutePath) }
tasks.register("writeRuntimeInventory") {
    doLast {
        val runtime = configurations.getByName("releaseRuntimeClasspath")
        // ResolutionResult alone can omit failed variant dependencies and produce a partial inventory.
        runtime.files
        val entries = runtime.incoming.resolutionResult.allComponents
            .mapNotNull { it.id as? ModuleComponentIdentifier }
            .map { "${it.group}:${it.module}:${it.version}" }.sorted()
        val stage = providers.gradleProperty("evidenceStage").getOrElse("E5_AUDIO")
        require(Regex("[A-Z0-9_-]+").matches(stage))
        val output = rootProject.file("evidence/$stage/runtime-coordinates.txt")
        output.parentFile.mkdirs()
        output.writeText(entries.joinToString("\n") + "\n")
    }
}
