# SnaKt: Kotlin Formal Verification Plugin

[SnaKt](https://github.com/jesyspa/SnaKt) is a plugin for `kotlinc`
that performs formal verification of Kotlin code by translating it to
[Viper](https://www.pm.inf.ethz.ch/research/viper.html).

The plugin is still in early development and large parts of Kotlin
syntax are not supported.

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

However, keep in mind that the Viper dump is provided as an info message: this message will not be shown
unless you run `gradle` with the `--info` flag.

### Annotations

The plugin provides a number of annotations to add specifications to your code.
Applying the Gradle plugin automatically adds a dependency on `formver.annotations`.

### Running from the command line

To execute the plugin directly, build the plugin and then
specify the plugin `.jar` with `-Xplugin=`:

```sh
kotlinc -language-version 2.0 -Xplugin=path-to-plugin.jar myfile.kt
```

The plugin accepts a number of command line options which can be passed via
`-P plugin:org.jetbrains.kotlin.formver:OPTION=SETTING`:

- Option `log_level`: permitted values `only_warnings`, `short_viper_dump`, `full_viper_dump` (default:
  `only_warnings`).
- Option `error_style`: permitted values `user_friendly`, `original_viper` and `both` (default: `user_friendly`).
- Options `conversion_targets_selection` and `verification_targets_selection`: permitted values `no_targets`,
  `targets_with_contract`, `all_targets` (default: `targets_with_contract`).

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

See docs/developing.md for testing and the checks CI runs.

## Contact

Reach out to kameliya.golova@jetbrains.com if you'd like to use or contribute to the plugin!
We are open to supervising bachelor and master theses about this work.
