#!/usr/bin/env bash
# check-gradle-consumer.sh — Builds a tiny Gradle project against the published
# plugin. Gradle hands the compiler plugin its relocated embeddable compiler,
# which the test harness does not, so only this run sees an unrelocated
# dependency in the shadow jar. Needs network access for the consumer's own
# dependencies; takes no arguments.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
repo="$work/repo"
consumer="$work/consumer"

# A private repository keeps the shared ~/.m2 untouched.
./gradlew --no-daemon -Dmaven.repo.local="$repo" \
    :formver.gradle-plugin:publishToMavenLocal \
    :formver.compiler-plugin:publishToMavenLocal \
    :formver.annotations:publishToMavenLocal \
    :formver.common:publishToMavenLocal

mkdir -p "$consumer/src/main/kotlin" "$consumer/gradle"
# The same daemon JVM as this build: the newest JDKs cannot run Gradle's script
# or Kotlin compilers.
cp gradle/gradle-daemon-jvm.properties "$consumer/gradle/"
cat >"$consumer/settings.gradle" <<SETTINGS
pluginManagement {
    repositories {
        mavenCentral()
        maven { url "file://$repo" }
        gradlePluginPortal()
    }
}
plugins {
    id "org.gradle.toolchains.foojay-resolver-convention" version "1.0.0"
}
rootProject.name = "consumer"
SETTINGS
cat >"$consumer/build.gradle" <<'BUILD'
plugins {
    id "org.jetbrains.kotlin.jvm" version "2.3.0"
    id "org.jetbrains.kotlin.formver" version "0.1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven { url System.getProperty("formver.repo") }
}

kotlin {
    kotlinDaemonJvmArgs = ["-Xss30m", "-XX:MaxMetaspaceSize=1g"]
}

// Conversion and the uniqueness analysis run on every function; skipping
// verification keeps Z3 out of the picture.
formver {
    verificationTargetsSelection("no_targets")
}
BUILD
cat >"$consumer/src/main/kotlin/Main.kt" <<'SRC'
fun answer(x: Int): Int = x + 1
SRC

# The consumer is not part of this build, so the repository's own gradle
# invocation does not apply; the wrapper is reused for its Gradle version.
log="$work/consumer.log"
if ! "$ROOT/gradlew" --no-daemon -p "$consumer" -Dformver.repo="file://$repo" \
    compileKotlin >"$log" 2>&1; then
    cat "$log" >&2
    echo "check-gradle-consumer.sh: consumer build failed" >&2
    exit 1
fi
echo "check-gradle-consumer.sh: consumer build passed"
