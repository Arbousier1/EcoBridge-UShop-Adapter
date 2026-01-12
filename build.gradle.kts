// 1. buildscript 必须放在第一行
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.5.0")
    }
}

plugins {
    `java`
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "top.ellan.middleware"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // 依赖项
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("cn.superiormc:UltimateShop:4.2.3")
    compileOnly("top.ellan.ecobridge:EcoBridge:1.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// 2. 注意：这里删除了 import 语句，改在下方直接使用全名
// 3. 注册混淆任务
tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    // 确保在 jar 任务之后运行
    dependsOn("jar")

    // 读取混淆配置
    configuration("src/main/resources/proguard.pro")

    // 输入：读取 jar 任务的产物
    injars(tasks.named("jar"))

    // 输出：生成 -protected.jar
    outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-protected.jar"))

    // Java 21 运行环境支持
    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        fileTree("${System.getProperty("java.home")}/jmods") {
            include("java.base.jmod", "java.desktop.jmod", "java.logging.jmod", "java.sql.jmod", "java.xml.jmod")
        }
    )

    // 添加编译依赖
    libraryjars(configurations.compileClasspath.get())

    verbose()
}