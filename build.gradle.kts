plugins {
    `java`
    // 引入 Shadow 插件 (通常用于打包依赖，如果不需要可以删掉)
    id("io.github.goooler.shadow") version "8.1.7" 
    // 【新增】ProGuard 混淆插件
    id("com.github.guardsquare.proguard") version "7.4.2"
}

group = "top.ellan"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 请在这里保留你原有的依赖，例如：
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // implementation(libs.ultimateshop) // 如果你用了 version catalog
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// 设置编码
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// 【新增】配置 ProGuard 混淆任务
import proguard.gradle.ProGuardTask

tasks.register<ProGuardTask>("proguard") {
    // 确保在 jar 任务之后运行
    dependsOn("jar")

    // 1. 读取混淆配置文件 (位于 src/main/resources/proguard.pro)
    configuration("src/main/resources/proguard.pro")

    // 2. 设置输入 Jar (读取标准 jar 任务的产物)
    // 如果你使用了 shadowJar 打包依赖，请将下面这行改为: injars(tasks.named("shadowJar"))
    injars(tasks.named("jar"))

    // 3. 设置输出 Jar (在 build/libs/ 下生成 -protected.jar)
    outjars(layout.buildDirectory.file("libs/${project.name}-${project.version}-protected.jar"))

    // 4. 【关键】添加 Java 21 运行环境 (Java 9+ 使用 jmods 而非 rt.jar)
    libraryjars(
        mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
        fileTree("${System.getProperty("java.home")}/jmods") {
            include("java.base.jmod", "java.desktop.jmod", "java.logging.jmod", "java.sql.jmod", "java.xml.jmod")
        }
    )

    // 5. 添加编译时的依赖库 (防止 ProGuard 误删引用的 Paper/Bukkit 类)
    libraryjars(configurations.compileClasspath.get())

    // 打印详细日志，方便 CI 排查
    verbose()
}