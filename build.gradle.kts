import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.nav.safeargs.kotlin) apply false
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val out = providers.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
    }
    return out.standardOutput.asText.get().trim()
}

val localProperties = Properties().apply {
    val localFile = file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val ciBuild = providers.environmentVariable("CI").isPresent
val officialBuild by extra(localProperties.getProperty("officialBuild", "false") == "true")

@Suppress("unused")
val crowdinProjectId: String by extra(localProperties.getProperty("crowdinProjectId", ""))

@Suppress("unused")
val crowdinApiKey: String by extra(localProperties.getProperty("crowdinApiKey", ""))

fun getUncommittedSuffix(): String {
    if (officialBuild) return ""

    if (ciBuild) {
        val headRefVal = providers.environmentVariable("GITHUB_HEAD_REF").orElse("HEAD").get()
        return "-$headRefVal"
    }

    var returnedVal = ""

    try {
        val branch = "git rev-parse --abbrev-ref HEAD".execute().split("/").last()
        if (branch != "master") {
            returnedVal += "-$branch"
        }
    } catch (_: Throwable) {}

    val result = "git status -s".execute()
    if (result.isEmpty()) {
        return returnedVal
    }

    return "$returnedVal-dirty+${result.count { it == '\n' } + 1}"
}

val minSdkVer by extra(29)
val targetSdkVer by extra(36)

val appVerCode by extra(
    providers.environmentVariable("APP_VERSION_CODE").orNull?.toIntOrNull() ?: 1
)
val appVerName by extra(
    providers.environmentVariable("APP_VERSION_NAME").orNull ?: "0.1"
)

/*
 * configVerCode, serviceVerCode and minBackupVerCode is used by other build.gradle.kts files
 *
 * DO NOT REMOVE THESE LINES
*/

@Suppress("unused")
val configVerCode by extra(93)

@Suppress("unused")
val serviceVerCode by extra(102)

@Suppress("unused")
val minBackupVerCode by extra(65)

@Suppress("unused")
val appPackageName by extra("org.midopop_ux.hma_102")

@Suppress("unused")
val localBuild by extra(localProperties.getProperty("localBuild", "false") == "true")

val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    extensions.findByType<BaseExtension>()?.run {
        compileSdkVersion(targetSdkVer)

        defaultConfig {
            minSdk = minSdkVer
            targetSdk = targetSdkVer
            versionCode = appVerCode
            versionName = appVerName

            consumerProguardFiles("proguard-rules.pro")
        }

        val config = localProperties.getProperty("fileDir")?.let {
            signingConfigs.create("config") {
                storeFile = file(it)
                storePassword = localProperties.getProperty("storePassword")
                keyAlias = localProperties.getProperty("keyAlias")
                keyPassword = localProperties.getProperty("keyPassword")
            }
        }

        buildTypes {
            all {
                signingConfig = config ?: signingConfigs["debug"]
            }
            named("release") {
                isMinifyEnabled = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    extensions.findByType<ApplicationExtension>()?.run {
        buildTypes {
            named("release") {
                isShrinkResources = true
            }
        }

        dependenciesInfo {
            // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
            includeInApk = false
            // Disables dependency metadata when building Android App Bundles (for Google Play)
            includeInBundle = false
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        configureBaseExtension()
    }
    plugins.withId("com.android.library") {
        configureBaseExtension()
    }
}
