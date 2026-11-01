// Plugins go on the root classpath so Kotlin and the Android plugin share a class loader.
buildscript {
    val coreOnly = providers.gradleProperty("coreOnly").isPresent
    repositories {
        mavenCentral()
        gradlePluginPortal()
        if (!coreOnly) google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.1.0")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0")
        if (!coreOnly) classpath("com.android.tools.build:gradle:8.7.3")
    }
}
