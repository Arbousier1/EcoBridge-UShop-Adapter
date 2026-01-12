plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.7.1"
    id("xyz.jpenilla.run-paper") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "top.ellan"
version = "1.0.2-RELEASE"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    // flatDir { dirs("libs") } // 如果使用下面的 files() 方法，这行可以删掉
}

dependencies {
    // 1. Paper API (1.21.1)
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")

    // 2. Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

    // 3. 本地依赖 (修复后的写法)
    // 使用 files() 是最简单直接的方式，避免 Gradle 解析错误
    compileOnly(files("libs/UltimateShop.jar"))
    compileOnly(files("libs/EcoBridge.jar"))
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    shadowJar {
        archiveClassifier.set("")
    }
}
