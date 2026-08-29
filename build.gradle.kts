plugins {
    application
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "io.github.dicur3x"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

javafx {
    version = "21.0.12"
    modules("javafx.controls")
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "io.github.dicur3x.lss.LocalSubtitleStudioApplication"
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration", "manual")
    }
}

tasks.register<Test>("integrationTest") {
    description = "Runs tests that require ffmpeg and ffprobe."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform {
        includeTags("integration")
    }
}

tasks.register<Test>("realMediaTest") {
    description = "Runs opt-in local Whisper checks against a real media file."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform {
        includeTags("manual")
    }
    providers.gradleProperty("realMedia").orNull?.let { systemProperty("lss.real.media", it) }
    providers.gradleProperty("realModel").orNull?.let { systemProperty("lss.real.model", it) }
    providers.gradleProperty("realDurationSeconds").orNull?.let {
        systemProperty("lss.real.duration.seconds", it)
    }
    providers.gradleProperty("realReport").orNull?.let { systemProperty("lss.real.report", it) }
    providers.gradleProperty("realExpectedFirstMillis").orNull?.let {
        systemProperty("lss.real.expected.first.ms", it)
    }
    providers.gradleProperty("realTailToleranceSeconds").orNull?.let {
        systemProperty("lss.real.tail.tolerance.seconds", it)
    }
}

val prepareJpackageInput = tasks.register<Sync>("prepareJpackageInput") {
    description = "Collects the application and runtime libraries for jpackage."
    group = "distribution"
    dependsOn(tasks.jar)
    from(tasks.jar.flatMap { it.archiveFile })
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("jpackage/input"))
}

val cleanJpackageOutput = tasks.register<Delete>("cleanJpackageOutput") {
    delete(layout.buildDirectory.dir("jpackage/output"))
}

tasks.register<Exec>("packageAppImage") {
    description = "Builds a self-contained Windows app image with a native launcher and bundled Java runtime."
    group = "distribution"
    dependsOn(prepareJpackageInput, cleanJpackageOutput)
    val launcher = javaToolchains.launcherFor(java.toolchain)
    val executableName = if (System.getProperty("os.name").startsWith("Windows")) "jpackage.exe" else "jpackage"
    doFirst {
        executable(launcher.get().metadata.installationPath.file("bin/$executableName").asFile)
        val input = layout.buildDirectory.dir("jpackage/input").get().asFile
        val output = layout.buildDirectory.dir("jpackage/output").get().asFile
        output.mkdirs()
        args(
            "--type", "app-image",
            "--name", "Local Subtitle Studio",
            "--description", "Create local subtitles without uploading video",
            "--app-version", project.version.toString().substringBefore('-'),
            "--input", input.absolutePath,
            "--dest", output.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", "io.github.dicur3x.lss.DesktopLauncher",
            "--java-options", "--enable-native-access=ALL-UNNAMED"
        )
    }
}

val preparePortableImage = tasks.register<Sync>("preparePortableImage") {
    description = "Copies the app image and enables self-contained portable data storage."
    group = "distribution"
    dependsOn("packageAppImage")
    from(layout.buildDirectory.dir("jpackage/output/Local Subtitle Studio"))
    into(layout.buildDirectory.dir("jpackage/portable/Local Subtitle Studio"))
    doLast {
        destinationDir.resolve("portable.mode").writeText(
            "Store Local Subtitle Studio settings and managed components in the data folder beside this launcher.\n"
        )
    }
}

tasks.register<Zip>("packagePortableZip") {
    description = "Builds a portable Windows ZIP with its own Java runtime and local data folder mode."
    group = "distribution"
    dependsOn(preparePortableImage)
    from(layout.buildDirectory.dir("jpackage/portable"))
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    archiveFileName.set("Local-Subtitle-Studio-${project.version.toString().substringBefore('-')}-Windows-portable.zip")
}
