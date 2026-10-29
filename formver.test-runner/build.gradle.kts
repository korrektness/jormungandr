plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf<String>())
    }
    // Loaded in each request's class loader beside the tests, so it is compiled apart from the server and client.
    create("bridge") {
        java.setSrcDirs(listOf("bridge"))
        resources.setSrcDirs(listOf<String>())
    }
    test {
        java.setSrcDirs(listOf("test"))
        resources.setSrcDirs(listOf<String>())
    }
}

// The bridge runs against whichever launcher the test classpath brings. It is compiled against the oldest of
// those, the one the compiler-plugin tests resolve.
val junitPlatformFloor = "1.10.1"

val fixtureJunit: Configuration by configurations.creating

dependencies {
    "bridgeCompileOnly"("org.junit.platform:junit-platform-launcher:$junitPlatformFloor")

    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    fixtureJunit("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    fixtureJunit("org.junit.platform:junit-platform-launcher:$junitPlatformFloor")
}

val bridgeJar by tasks.registering(Jar::class) {
    archiveClassifier.set("bridge")
    from(sourceSets["bridge"].output)
}

// The bridge jar, for the builds that write runner specs.
val bridgeElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts.add(bridgeElements.name, bridgeJar)

tasks.register<Test>("integrationTest") {
    description = "Runs this module's integration tests"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
}

// Both entry points execute the complete integration tier; check retains its test dependency.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    val serverClasspath = sourceSets.main.get().output.classesDirs
    val bridge = bridgeJar.flatMap { it.archiveFile }
    inputs.files(serverClasspath, fixtureJunit).withPropertyName("runnerClasspaths")
    inputs.file(bridge).withPropertyName("bridgeJar")
    val fixtureFiles = files(fixtureJunit)
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "-DtestRunner.serverClasspath=${serverClasspath.asPath}",
            "-DtestRunner.bridgeClasspath=${bridge.get().asFile.absolutePath}",
            "-DtestRunner.junitClasspath=${fixtureFiles.asPath}",
        )
    })
}
