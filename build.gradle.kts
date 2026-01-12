// 1. buildscript 放在最顶部
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
    // 【关键修复】本地仓库配置
    maven {
        url = uri("libs")
        // 告诉 Gradle：如果在本地找不到 .pom 文件，就直接查找 .jar 文件
        // 这解决了 "Could not find ... .pom" 的报错
        metadataSources {
            mavenPom()
            artifact() 
        }
    }
    
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    // 远程依赖
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    
    // 本地依赖 (对应 libs/cn/superiormc/UltimateShop/4.2.3/UltimateShop-4.2.3.jar)
    compileOnly("cn.superiormc:UltimateShop:4.2.3")
    // 本地依赖 (对应 libs/top/ellan/ecobridge/EcoBridge/1.0/EcoBridge-1.0.jar)
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
    
    // 输入
    injars(tasks.named("jar"))
    
    // 输出
    outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-protected.jar"))

    // Java 21 环境支持
    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        fileTree("${System.getProperty("java.home")}/jmods") {
            include("java.base.jmod", "java.desktop.jmod", "java.logging.jmod", "java.sql.jmod", "java.xml.jmod")
        }
    )

    // 编译依赖 (Paper, Vault, UltimateShop 等)
    libraryjars(configurations.compileClasspath.get())
    
    verbose()
}