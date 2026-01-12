import proguard.gradle.ProGuardTask

// 1. 引入 ProGuard 核心依赖，但不作为插件直接应用
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.4.1")
    }
}

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "top.ellan.middleware"
version = "1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    
    // 递归读取 libs 目录下的所有 jar
    compileOnly(fileTree("libs") { include("**/*.jar") })
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    shadowJar {
        minimize()
        archiveClassifier.set("") 
    }

    // 2. 声明 ProGuard 任务
    register<ProGuardTask>("proguard") {
        dependsOn(shadowJar)
        
        injars(shadowJar.get().archiveFile)
        outjars("build/libs/${project.name}-${project.version}-protected.jar")

        val javaHome = System.getProperty("java.home")
        libraryjars("$javaHome/jmods")

        // 核心：必须将所有 compileOnly 依赖包含进 libraryjars，否则会报类缺失
        configurations.compileOnly.get().forEach { libraryjars(it) }

        // 混淆规则
        dontshrink()
        dontoptimize()
        keepattributes("Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod")

        keep("public class top.ellan.middleware.EcoBridgeMiddleware { *; }")
        keepclassmembers("class * { @org.bukkit.event.EventHandler *; }")
        keep("public class top.ellan.middleware.hook.UShopEconomyHook { *; }")
        
        // 保护 ShopInterceptor 的静态 API
        keepclassmembers("""
            class top.ellan.middleware.listener.ShopInterceptor {
                public static void init(...);
                public static void buildIndex();
                public static void clearAllCaches();
                public static int getItemSignature(...);
                public static double safeCalculatePrice(...);
                public static java.lang.Double getAndRemoveCache(...);
                public static *** getShopItemByStack(...);
            }
        """.trimIndent())

        keepclassmembers("record * { *; }")
        dontwarn("**")
    }
}