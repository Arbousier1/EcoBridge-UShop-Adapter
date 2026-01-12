plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.7.1" // Paper 开发核心插件
    id("xyz.jpenilla.run-paper") version "2.3.0" // 可选：用于本地运行测试服务器
    id("com.github.johnrengelman.shadow") version "8.1.1" // 用于打包
}

group = "top.ellan"
version = "1.0.2-RELEASE"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21)) // 强制使用 Java 21
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
    // 1. Paper API (1.21.1) - 包含 Adventure/MiniMessage
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")

    // 2. Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")

    // 3. 本地依赖 (文件名必须匹配，不要带版本号，或者你需要修改下面的名字)
    // 假设 libs/UltimateShop.jar
    compileOnly(name = "UltimateShop") 
    // 假设 libs/EcoBridge.jar
    compileOnly(name = "EcoBridge")
}

tasks {
    // 配置资源处理 (自动替换 plugin.yml 中的 ${version})
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    // 编译选项
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    //构建时只需生成普通 jar，因为所有依赖都是 compileOnly (由服务器提供)
    shadowJar {
        archiveClassifier.set("") // 移除 -all 后缀
    }
}