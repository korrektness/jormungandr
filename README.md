# SnaKt: Kotlin Formal Verification Plugin

[SnaKt](https://github.com/jesyspa/SnaKt) is a plugin for `kotlinc`
that performs formal verification of Kotlin code by translating it to
[Viper](https://www.pm.inf.ethz.ch/research/viper.html).

The plugin is still in early development and large parts of Kotlin
syntax are not supported.

- [Writing specifications](SPECIFICATIONS.md): contracts, ownership and supported Kotlin constructs.
- [Configuration reference](docs/configuration.md): Gradle settings, compiler options and conversion-only runs.
- [Soundness and trust](docs/soundness.md): what a successful verification establishes.
- [Development](docs/developing.md): tests and repository checks.

## Structure

This repository consists of three published parts:

- `formver.compiler-plugin`: a K2 compiler plugin that performs formal verification.
- `formver.gradle-plugin`: a Gradle plugin that loads the compiler plugin.
- `formver.annotations`: definitions that are used for adding specifications
  to your code.

Additionally, `formver.common` contains some code shared between these parts.

At present, we do not distribute any part of the plugin through a central repository.
If you would like to use the plugin, clone it and use the `publishToMavenLocal`
task to put it in your local repository.

## Running the plugin

Once you've published to your local Maven repository, you can use the Gradle
plugin to enable verification of your project.
You can see an example setup at [jesyspa/snakt-usage-example](https://github.com/jesyspa/snakt-usage-example).

### Setup

In your `settings.gradle.kts`, configure your Gradle plugin repositories to allow local plugins:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}
```

Then in `build.gradle.kts`, enable the plugin. Make sure that you also enable the Maven
local repository here: it's necessary to find the compiler plugin for the plugin.

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.formver") version "0.1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
    mavenLocal()
}
```

Additionally, increase the stack size and metaspace of the Kotlin Daemon: the shaded plugin
jar bundles Silicon and the Scala runtime (~100 MB), and without a larger metaspace the daemon
dies with `OutOfMemoryError: Metaspace` after roughly a dozen compiles.

```kotlin
kotlin {
    // Set stack size to 30mb and raise the metaspace limit
    kotlinDaemonJvmArgs = listOf("-Xss30m", "-XX:MaxMetaspaceSize=1g")
}
```

### Plugin configuration

Plugin options can be enabled using the `formver` configuration block:

```kotlin
formver {
    logLevel("full_viper_dump")
}
```

See the [configuration reference](docs/configuration.md) for every option,
selection of checks and diagnostic visibility.

### Annotations

The plugin provides a number of annotations to add specifications to your code.
Applying the Gradle plugin automatically adds a dependency on `formver.annotations`.

### Running from the command line

Use a `kotlinc` version matching the build's Kotlin version in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). Build the shaded compiler
plugin with `./gradlew :formver.compiler-plugin:shadowJar` and the annotation
library with `./gradlew :formver.annotations:jar`. Load the compiler plugin with
`-Xplugin` and put the annotation library on the source classpath:

```sh
kotlinc -Xplugin=path-to-plugin.jar \
    -classpath path-to-annotations.jar \
    -Xreport-all-warnings myfile.kt
```

The [configuration reference](docs/configuration.md) maps Gradle settings to
`-P plugin:org.jetbrains.kotlin.formver:OPTION=VALUE` arguments.

### Z3

The plugin verifies with the SMT solver Z3, version 4.8.7, which it runs as an
external binary. It finds the binary through the `Z3_EXE` environment
variable: either an absolute path, or a command name looked up on `PATH`.
When `Z3_EXE` is unset, it looks up `z3` on `PATH`.

The build's verification test tasks provide Z3 themselves. They use `Z3_EXE`
when it is an absolute path to a file. Otherwise they download the official
4.8.7 release for Linux, macOS or Windows (x64), check its SHA-256, and cache
it under the Gradle user home in `formver/z3`.

To use the plugin outside the tests, download v4.8.7 from the
[Releases page](https://github.com/Z3Prover/z3/releases/tag/z3-4.8.7) and put
its `bin/z3` on `PATH` or point `Z3_EXE` at it.

## Contributing

See [Developing the plugin](docs/developing.md) for testing and the checks CI runs.

## Contact

Reach out to kameliya.golova@jetbrains.com if you'd like to use or contribute to the plugin!
We are open to supervising bachelor and master theses about this work.
