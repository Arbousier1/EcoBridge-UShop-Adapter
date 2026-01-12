// 1. 插件引导配置 (必须放在文件最顶部)
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // 使用官方 ProGuard Gradle 插件
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

plugins {
    `java`
    // 如果您需要 Shade 功能（类似 Maven Shade Plugin），请保留此插件
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "top.ellan.middleware"
version = "1.0.0"

repositories {
    mavenCentral()
    // Paper API 仓库
    maven("https://repo.papermc.io/repository/maven-public/")
    // JitPack 仓库 (用于 Vault, UltimateShop, EcoBridge)
    maven("https://jitpack.io")
}

dependencies {
    // 对应 pom.xml 中的 scope provided -> Gradle 使用 compileOnly
    
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    
    // Vault
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    
    // UltimateShop (jitpack)
    compileOnly("cn.superiormc:UltimateShop:4.2.3")
    
    // EcoBridge (注意版本已对齐为 1.0)
    compileOnly("top.ellan.ecobridge:EcoBridge:1.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// 资源文件处理 (替换 plugin.yml 中的变量)
tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// 编译选项
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// 2. 引入 ProGuard 任务类
import proguard.gradle.ProGuardTask

// 3. 注册混淆任务
tasks.register<ProGuardTask>("proguard") {
    // 确保在构建 jar 之后运行
    dependsOn("jar")

    // 1. 读取混淆配置文件
    // ⚠️ 请确保您将复杂的规则（如 ShopInterceptor 的保护）写入此文件
    configuration("src/main/resources/proguard.pro")

    // 2. 输入：读取 jar 任务的产物
    injars(tasks.named("jar"))

    // 3. 输出：生成 -protected.jar
    outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-protected.jar"))

    // 4. Java 21 运行环境支持 (修复 jmods 路径问题)
    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        fileTree("${System.getProperty("java.home")}/jmods") {
            include("java.base.jmod", "java.desktop.jmod", "java.logging.jmod", "java.sql.jmod", "java.xml.jmod")
        }
    )

    // 5. 添加编译时的依赖库 (防止 ProGuard 误删 Paper/Bukkit/EcoBridge API)
    libraryjars(configurations.compileClasspath.get())

    // 开启详细日志
    verbose()
}