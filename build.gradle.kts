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
    // 【关键修复】优先查找本地 libs 文件夹
    // 你的截图显示 libs 文件夹内部是标准的 Maven 结构 (cn/superiormc/...)
    // 所以必须用 maven 方式引入，而不是 flatDir
    maven {
        url = uri("libs")
    }
    
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // Paper API (远程)
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // Vault (远程)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    
    // 【本地依赖】现在可以通过本地仓库找到了
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

// 注册混淆任务
tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    dependsOn("jar")
    configuration("src/main/resources/proguard.pro")
    injars(tasks.named("jar"))
    outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-protected.jar"))

    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        fileTree("${System.getProperty("java.home")}/jmods") {
            include("java.base.jmod", "java.desktop.jmod", "java.logging.jmod", "java.sql.jmod", "java.xml.jmod")
        }
    )

    libraryjars(configurations.compileClasspath.get())
    verbose()
}