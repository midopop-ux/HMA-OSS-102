plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.refine)
    alias(libs.plugins.kotlin)
}

val appPackageName: String by rootProject.extra

android {
    namespace = "org.frknkrc44.hma_oss.xposed"

    buildFeatures {
        buildConfig = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.common)

    implementation(libs.androidx.annotation.jvm)
    implementation(libs.dev.rikka.hidden.compat)
    compileOnly(libs.libxposed.api)
    compileOnly(libs.dev.rikka.hidden.stub)
}
