import proguard.gradle.ProGuardTask

plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1" // 替代 maven-shade-plugin
    id("com.guardsquare.proguard") version "7.4.1"      // 替代 proguard-maven-plugin
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
    // 对应 Maven 的 <scope>provided</scope> -> compileOnly
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    
    // 加载 libs 文件夹下的本地 JAR
    compileOnly(fileTree("libs") { include("UltimateShop-4.2.3.jar", "EcoBridge-1.0.jar") })
}

tasks {
    // 1. 配置编译编码
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // 2. 配置 ShadowJar (最小化打包)
    shadowJar {
        minimize() // 对应 <minimizeJar>true</minimizeJar>
        archiveClassifier.set("") // 不生成 -all 后缀，直接替换主 JAR
    }

    // 3. 配置资源过滤 (对应 Maven 的 <filtering>true</filtering>)
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    // 4. 配置 ProGuard 混淆任务
    register<ProGuardTask>("proguard") {
        dependsOn(shadowJar)
        
        // 输入 shadowJar 生成的文件
        injars(shadowJar.get().archiveFile)
        // 输出混淆后的保护包
        outjars("build/libs/${project.name}-${project.version}-protected.jar")

        // 自动查找当前 JDK 的 jmods
        val javaHome = System.getProperty("java.home")
        libraryjars("$javaHome/jmods")

        // 必须加入 compileOnly 的依赖作为库文件，否则混淆时找不到类
        configurations.compileOnly.get().forEach { libraryjars(it) }

        // --- ProGuard 选项 (原 pom.xml 移植) ---
        dontshrink()
        dontoptimize()
        keepattributes("Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod")

        keep("public class top.ellan.middleware.EcoBridgeMiddleware { *; }")
        keepclassmembers("class * { @org.bukkit.event.EventHandler *; }")
        keep("public class top.ellan.middleware.hook.UShopEconomyHook { *; }")
        
        // 保护拦截器关键 API
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