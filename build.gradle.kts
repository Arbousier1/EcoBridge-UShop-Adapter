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
    maven("https://jitpack.io") // 用于 Vault
    
    // 本地仓库：请确保项目根目录下有 libs 文件夹，并将 UltimateShop 和 EcoBridge 的 jar 放进去
    flatDir {
        dirs("libs")
    }
}

dependencies {
    // 1. Paper API (1.21.1)
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")

    // 2. Vault API (排除旧版 Bukkit 防止 API 冲突)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit")
        exclude(group = "org.spigotmc")
    }

    // 3. 本地依赖 (文件名必须匹配，不要带版本号，或者你需要修改下面的名字)
    // 假设 libs/UltimateShop.jar
    compileOnly(name = "UltimateShop") 
    // 假设 libs/EcoBridge.jar
    compileOnly(name = "EcoBridge")
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
        archiveClassifier.set("") // 移除 -all 后缀
    }
}
