plugins {
    id("org.gradle.java-library")
    id("org.gradle.maven-publish")
    id("org.gradle.application")

    id("io.freefair.lombok") version "8.6"
}

buildscript {
    dependencies {
        classpath("com.guardsquare", "proguard-gradle", "7.4.2")
    }
}

repositories {
    mavenLocal()
    mavenCentral()

    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local/com/formdev/") }
}

group = "eu.darkbot"
version = "1.131"
description = "DarkBot"
java.sourceCompatibility = JavaVersion.VERSION_11
java.targetCompatibility = JavaVersion.VERSION_11

application {
    applicationName = "DarkBot"
    mainClass.set("com.github.manolo8.darkbot.Bot")
}

publishing {
    java.withSourcesJar()

    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

configurations {
    compileOnly {
        isCanBeResolved = true
    }
}

dependencies {
    val apiVersion = "0.9.9"
    val flatLafVersion = "3.4"

    // use this if you want to use local(mavenLocal) darkbot API
    val useLocalMaven = true
    if (useLocalMaven) {
        api("eu.darkbot", "darkbot-impl", apiVersion)
        // darkbot-api publishes a dependency-less POM, so it is declared explicitly (AGENTS.md gotcha)
        api("eu.darkbot", "darkbot-api", apiVersion)
    } else api("eu.darkbot.DarkBotAPI", "darkbot-impl", apiVersion)

    // have to keep version 2.8.9, in newer versions GSON calls `toString` of config enums upon creation
    api("com.google.code.gson", "gson", "2.8.9")
    api("com.miglayout", "miglayout-swing", "11.3")
    api("com.formdev", "flatlaf", flatLafVersion)
    api("com.formdev", "flatlaf-extras", flatLafVersion)
    api("org.jgrapht", "jgrapht-core", "1.5.2")
    api("it.unimi.dsi", "fastutil-core", "8.5.13")

    // Unity modules (Fase 4, Camino A): unity-game state managers + unity-transport codec/relay.
    // unity-game's POM carries unity-transport/darkbot-util transitively, but declare them explicitly
    // because darkbot-api publishes a dependency-less POM (see AGENTS.md gotcha).
    implementation("eu.darkbot", "unity-game", "0.1.6")
    implementation("eu.darkbot", "unity-transport", "0.1.4")
    implementation("eu.darkbot", "darkbot-util", "0.9.9")

    // Testing stat time-series requires this
    //api("org.knowm.xchart", "xchart", "3.8.5")

    compileOnly("org.jetbrains", "annotations", "24.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("org.mockito:mockito-core:4.10.0")
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.withType<JavaExec> { systemProperty("file.encoding", "UTF-8") }

// JUnit 5 (jupiter) is the declared test framework; without this the Gradle test task
// defaults to JUnit 4 and silently runs zero tests (AGENTS.md documents `gradle test`).
tasks.test {
    useJUnitPlatform()
}

// All eu.darkbot dependencies now resolve from mavenLocal (darkbot-impl/api/util via the
// explicit coordinates above, unity-game/unity-transport via their own POMs). darkbot-util
// and darkbot-api end up on the classpath twice (direct + transitively through darkbot-impl
// and unity-game), with identical 0.9.9 content — dedupe the distributions like the fat jar
// already does (EXCLUDE).
tasks.withType<AbstractArchiveTask> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
tasks.withType<Sync> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.wrapper {
    gradleVersion = "8.6"

    // with gradle javadocs and sources
    distributionType = Wrapper.DistributionType.ALL
}

tasks.jar {
    manifest {
        attributes["SplashScreen-Image"] = "icon.png"
        attributes["Main-Class"] = application.mainClass
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map(::zipTree))
}

tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    val toExclude = createExcludes(
        "it.unimi.dsi.fastutil.bytes.**",
        "it.unimi.dsi.fastutil.chars.**",
        "it.unimi.dsi.fastutil.doubles.**",
        "it.unimi.dsi.fastutil.floats.**",
        "it.unimi.dsi.fastutil.shorts.**",
        "it.unimi.dsi.fastutil.booleans.**"
    )

    dontoptimize()
    dontobfuscate()

    dontwarn("java.lang.invoke.**")
    dontwarn("com.google.errorprone.annotations.**")

    keepattributes("Signature")
    keep("class com.github.manolo8.** { *; }")
    keep("class eu.darkbot.** { *; }")
    keep("class com.formdev.** { *; }")
    keep("class com.github.weisj.jsvg.** { *; }")

    injars(tasks.jar.get())
    outjars(toExclude, "build/DarkBot.jar")

    libraryjars(configurations.compileOnly.get().files)
    libraryjars(
        mapOf(
            "jarfilter" to "!**.jar",
            "filter" to "!module-info.class",
        ), "${System.getProperty("java.home")}/jmods"
    )

    dependsOn(tasks.build)
}

fun createExcludes(vararg excludes: String): Map<String, String> {
    return mapOf("filter" to excludes.joinToString(separator = ",", transform = { "!${it.replace(".", "/")}" }))
}
