import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("org.jetbrains.changelog") version "2.2.1" // Gradle Changelog Plugin
}
kotlin {
    jvmToolchain(17)

//    compilerOptions {
//    //显示启用k2编译器
//        freeCompilerArgs.add("-Xuse-k2")
//    }
}

group = "org.autojs.autojs"
var pluginVersion = providers.gradleProperty("pluginVersion")
var pluginId = providers.gradleProperty("pluginId")
var pluginName = providers.gradleProperty("pluginName")

changelog {
    version.set(pluginVersion.get())
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1.7") //会下载一个社区版来测试
        //https://docs.namichong.com/intellij-platform-sdk/tools-intellij-platform-gradle-plugin-types.html#IntelliJPlatformType
        //gradle会去下载一个指定版本的编辑器,用来测试
//        create("IC", "2024.1.7") //idea

        javaCompiler()
        pluginVerifier()
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
    // Ktor for WebSocket server
    implementation("io.ktor:ktor-server-core:2.3.13")
    implementation("io.ktor:ktor-server-netty:2.3.13")
    implementation("io.ktor:ktor-server-websockets:2.3.13")
    
    // QR Code generation
    implementation("com.google.zxing:core:3.5.2")
    implementation("com.google.zxing:javase:3.5.2")
    
    // Gson for JSON processing
    implementation("com.google.code.gson:gson:2.10.1")
    
    // SLF4J for logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.12")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.mockito:mockito-core:5.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

intellijPlatform{
    projectName = "${pluginName.get()}_${pluginVersion.get()}" //配置打包后的文件名(不包含.zip部分)
    pluginConfiguration{
        id = pluginId.get()
        name = pluginName.get() //这关系到插件在idea中显示的名字
        version = pluginVersion.get()
        changeNotes = file("changelog.md").readText()//每次先执行tasks中的publishPlugin会生成新的changelog.md文件
        vendor {
            name = "WgoW"
            email = "lvzhongyiforchrome@gmail.com"
        }
        //从readme中截取 start 到 end的文本设置到插件描述中
        description = providers.provider {
            val files = listOf(
                layout.projectDirectory.file("README_en.md"),
                layout.projectDirectory.file("README.md"),
                layout.projectDirectory.file("README_JP.md"),
                layout.projectDirectory.file("README_ko.md")
            )

            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            fun extractDescription(content: String): String {
                return with(content.lines()) {
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
                }
            }

            // 读取所有文件内容并提取描述部分
            files.map { file ->
                providers.fileContents(file).asText.get()
            }.joinToString("\n", transform = ::extractDescription)
        }


        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
    signing {
        certificateChain = providers.environmentVariable("I_CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("I_PRIVATE_KEY")
        password = providers.environmentVariable("I_PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("I_PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = pluginVersion.map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }
    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}
