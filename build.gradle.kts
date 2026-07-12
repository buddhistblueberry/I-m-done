buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.5.0'
        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20'
    }
}

plugins {
    id 'org.jetbrains.kotlin.plugin.compose' version '2.0.20' apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
