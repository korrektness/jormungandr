#!/usr/bin/env bash
# check-gradle-consumer.sh — Builds a tiny Gradle project against the published
# plugin. Gradle hands the compiler plugin its relocated embeddable compiler,
# which the test harness does not, so only this run sees an unrelocated
# dependency in the shadow jar.
#
# The consumer verifies calls into a stand-in library through an external spec
# of it, and calls through the bundled standard-library specs. The check fails
# if a spec declaration reaches the compiled output, if a reference to one from
# compiled code is accepted, if a spec binding to a missing library declaration
# is accepted, if a contradictory spec is accepted, if a false claim about a
# specified call verifies, if a multiplatform consumer's common, platform or
# platform test code does not
# verify through the bundled specs, if a default build does not show the trust
# summary of the verified declarations, if a build with verification errors
# drops its warnings, if a spec binding to a declaration that another module
# verified is not reported, or if a caller stays verified after the spec it
# relies on changes. The
# multiplatform consumer uses explicit API mode, extra warnings and warnings as
# errors, which the bundled specs must compile under.
#
# Needs network access for the consumer's own dependencies, and Z3: Z3_EXE, z3
# on PATH, or the copy that the test tasks provision under the Gradle user
# home. Exits 2 when there is no Z3. Takes no arguments.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

z3="${Z3_EXE:-}"
if [[ -z "$z3" ]]; then
    z3="$(command -v z3 || true)"
fi
if [[ -z "$z3" ]]; then
    z3="$(ls "${GRADLE_USER_HOME:-$HOME/.gradle}"/formver/z3/*/bin/z3 2>/dev/null | head -n 1 || true)"
fi
if [[ -z "$z3" || ! -x "$z3" ]]; then
    echo "check-gradle-consumer.sh: no Z3 found; set Z3_EXE or run ./gradlew test once to provision it" >&2
    exit 2
fi
export Z3_EXE="$z3"

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

mkdir -p "$consumer/src/main/kotlin" "$consumer/src/badReference/kotlin" \
    "$consumer/src/badClaim/kotlin" "$consumer/specs" "$consumer/gradle" \
    "$consumer/lib/src/main/kotlin" "$consumer/vlib/src/main/kotlin"
# The same daemon JVM as this build: the newest JDKs cannot run Gradle's script
# or Kotlin compilers.
cp gradle/gradle-daemon-jvm.properties "$consumer/gradle/"
# A shared Kotlin daemon keeps the environment it started with, so Z3_EXE
# reaches the plugin only when the compiler runs inside this build.
cat >"$consumer/gradle.properties" <<'PROPERTIES'
kotlin.compiler.execution.strategy=in-process
org.gradle.jvmargs=-Xss30m -Xmx2g -XX:MaxMetaspaceSize=1g
PROPERTIES
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
include "lib", "vlib"
SETTINGS

# The stand-in library: compiled without the plugin, so its bodies are not
# visible to the consumer.
cat >"$consumer/lib/build.gradle" <<'BUILD'
plugins {
    id "org.jetbrains.kotlin.jvm"
}

repositories {
    mavenCentral()
}
BUILD
cat >"$consumer/lib/src/main/kotlin/Counter.kt" <<'SRC'
package stand.lib

class Counter(private var current: Int) {
    fun value(): Int = current
    fun advance(step: Int): Int {
        current += step
        return current
    }
}

fun create(initial: Int): Counter = Counter(initial)
fun twice(x: Int): Int = x * 2
fun thrice(x: Int): Int = x * 3
SRC

# A library that SnaKt verifies, which the consumer's specs then bind.
cat >"$consumer/vlib/build.gradle" <<'BUILD'
plugins {
    id "org.jetbrains.kotlin.jvm"
    id "org.jetbrains.kotlin.formver"
}

repositories {
    mavenCentral()
    maven { url System.getProperty("formver.repo") }
}
BUILD
cat >"$consumer/vlib/src/main/kotlin/Half.kt" <<'SRC'
package stand.vlib

import org.jetbrains.kotlin.formver.plugin.*

fun half(x: Int): Int {
    preconditions { x >= 0 }
    postconditions<Int> { result -> result <= x }
    return x / 2
}

class Box {
    val size: Int
        get() = 1
}
SRC

cat >"$consumer/specs/VerifiedSpecs.kt" <<'SRC'
package stand.specs.verified

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("stand.vlib.half")
@Pure fun halfModel(x: Int): Int = 0

@SpecOf("halfModel")
fun halfSpec(x: Int) {
    preconditions { x >= 0 }
    postconditions<Int> { result -> result <= x }
}

@ExternalSpec("stand.vlib.Box")
interface BoxModel {
    @Pure @get:Unique val size: Int

    @Law @Pure @Unique fun single(): Boolean = size == 1
}
SRC

cat >"$consumer/specs/CounterSpecs.kt" <<'SRC'
package stand.specs

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("stand.lib.Counter")
interface CounterModel {
    @Pure @Unique fun value(): Int

    @Law @Pure @Unique fun nonnegative(): Boolean = value() >= 0

    @Unique @Borrowed fun advance(step: Int): Int

    @SpecOf("advance")
    @Unique @Borrowed fun advanceSpec(step: Int) {
        preconditions { step >= 0; value() <= 2147483647 - step }
        postconditions<Int> { result ->
            result == old(value()) + step
            value() == result
        }
    }
}

@ExternalFunction("stand.lib.create")
fun createModel(initial: Int): @Unique CounterModel = TODO()

@SpecOf("createModel")
fun createSpec(initial: Int) {
    preconditions { initial >= 0 }
    postconditions<@Unique CounterModel> { result -> result.value() == initial }
}

@ExternalFunction("stand.lib.twice")
@Pure fun twiceModel(x: Int): Int = 0

@SpecOf("twiceModel")
fun twiceSpec(x: Int) {
    preconditions { -1073741824 <= x && x <= 1073741823 }
    postconditions<Int> { result -> result == x * 2 }
}
SRC

# Loaded only by the badBinding build, and referenced from nowhere: the bindings
# alone must fail the compilation.
cat >"$consumer/specs/BadBinding.kt" <<'SRC'
package stand.specs.bad

import org.jetbrains.kotlin.formver.plugin.*

@ExternalSpec("stand.lib.Missing")
interface MissingModel

@ExternalFunction("stand.lib.missing")
fun missingModel(x: Int): Int = 0
SRC

# Loaded only by the vacuousSpec build: the postcondition contradicts itself.
cat >"$consumer/specs/VacuousSpecs.kt" <<'SRC'
package stand.specs.vacuous

import org.jetbrains.kotlin.formver.plugin.*

@ExternalFunction("stand.lib.thrice")
@Pure fun thriceModel(x: Int): Int = 0

@SpecOf("thriceModel")
fun thriceSpec(x: Int) {
    postconditions<Int> { result -> result > x; result < x }
}
SRC

cat >"$consumer/build.gradle" <<'BUILD'
plugins {
    id "org.jetbrains.kotlin.jvm" version "2.3.0"
    id "org.jetbrains.kotlin.formver" version "0.1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven { url System.getProperty("formver.repo") }
}

dependencies {
    implementation project(":lib")
    implementation project(":vlib")
}

for (extra in ["badReference", "badClaim"]) {
    if (hasProperty(extra)) {
        sourceSets.main.kotlin.srcDir("src/$extra/kotlin")
    }
}

formver {
    externalSpec("specs/CounterSpecs.kt")
    externalSpec("specs/VerifiedSpecs.kt")
    if (project.hasProperty("badBinding")) {
        externalSpec("specs/BadBinding.kt")
    }
    if (project.hasProperty("vacuousSpec")) {
        externalSpec("specs/VacuousSpecs.kt")
    }
}
BUILD

# Every claim here holds only through the specs.
cat >"$consumer/src/main/kotlin/Main.kt" <<'SRC'
import org.jetbrains.kotlin.formver.plugin.*
import stand.lib.*
import kotlin.math.abs

fun answer(x: Int): Int {
    preconditions { x < 2147483647 }
    return x + 1
}

fun counting(counter: @Unique @Borrowed Counter) {
    verify(counter.value() >= 0)
    val before = counter.value()
    if (before <= 100) {
        val result = counter.advance(3)
        verify(result == before + 3)
        verify(counter.value() == result)
    }
}

fun creating() {
    val counter = create(2)
    verify(counter.value() == 2)
    val result = counter.advance(3)
    verify(result == 5)
    verify(counter.value() == 5)
}

fun doubling() {
    verify(twice(4) == 8)
}

fun absolute() {
    verify(abs(-3) == 3)
}

class Element(val number: Int)

fun stdlibModels(element: Element) {
    val singleton = listOf(7)
    verify(singleton.size == 1 && singleton[0] == 7 && singleton.indexOf(7) == 0)
    val list = mutableListOf<Element>()
    list.add(element)
    verify(list[0] == element && list[0].number == element.number)
    val set = mutableSetOf<Int>()
    set.add(4)
    val duplicate = set.add(4)
    verify(!duplicate && set.size == 1)
    val map = mutableMapOf<Int, Element>()
    map.put(2, element)
    verify(map.containsKey(2) && map[2] == element)
    val text = "hello".substring(1, 4)
    verify(text.length == 3 && text[0] == 'e')
}

fun halving(box: @Unique @Borrowed stand.vlib.Box) {
    verify(stand.vlib.half(8) <= 8 && box.size == 1)
}
SRC
cat >"$consumer/src/badReference/kotlin/BadReference.kt" <<'SRC'
fun badReference(x: Int): Int = stand.specs.twiceModel(x)
SRC
cat >"$consumer/src/badClaim/kotlin/BadClaim.kt" <<'SRC'
import org.jetbrains.kotlin.formver.plugin.*
import stand.lib.*
import kotlin.math.abs

fun wrongDouble() {
    verify(twice(4) == 9)
}

fun unguardedAbs(x: Int): Int = abs(x)

fun negativeCreate(): Int = create(-1).value()

fun wrongListElement() {
    val list = listOf(7)
    verify(list[0] == 8)
}

@Deprecated("replaced")
fun retired(): Int = 0

fun callsRetired(): Int = retired()
SRC

# The consumer is not part of this build, so the repository's own gradle
# invocation does not apply; the wrapper is reused for its Gradle version.
build_consumer() {
    "$ROOT/gradlew" --no-daemon -p "$consumer" -Dformver.repo="file://$repo" "$@" compileKotlin
}

log="$work/consumer.log"
if ! build_consumer >"$log" 2>&1; then
    cat "$log" >&2
    echo "check-gradle-consumer.sh: consumer build failed" >&2
    exit 1
fi
if grep -q "^w: .*Main.kt" "$log"; then
    grep "^w: .*Main.kt" "$log" >&2
    echo "check-gradle-consumer.sh: the consumer's claims did not verify" >&2
    exit 1
fi

classes="$consumer/build/classes/kotlin/main"
if [[ ! -f "$classes/MainKt.class" ]]; then
    echo "check-gradle-consumer.sh: consumer build produced no MainKt.class" >&2
    exit 1
fi
# Spec packages, files and declarations, both the consumer's and the bundled.
spec_names=(-e stand/specs -e stand.specs -e CounterSpecs -e CounterModel
    -e createModel -e createSpec -e twiceModel -e twiceSpec -e formver/external -e IntAbs -e absModel -e absSpecification
    -e VerifiedSpecs -e halfModel -e halfSpec -e BoxModel)
leaked="$(find "$classes" -type f -exec grep -l "${spec_names[@]}" {} + || true)"
if [[ -n "$leaked" ]]; then
    echo "check-gradle-consumer.sh: an external spec reached the compiled output:" >&2
    echo "$leaked" >&2
    exit 1
fi

# A default build shows the trust summary, and the report it points to lists
# what each declaration trusted.
report="$consumer/build/formver/compileKotlin/trust-report.txt"
if ! grep -q "^SnaKt verified .* Details: $report$" "$log"; then
    cat "$log" >&2
    echo "check-gradle-consumer.sh: the build did not show the trust summary" >&2
    exit 1
fi
for entry in "user spec: stand.lib.twice(Int): Int" "bundled spec: kotlin.math.abs(Int): Int" \
    "rebinds verified: stand.vlib.half(Int): Int" "rebinds verified: stand.vlib.Box"; do
    if ! grep -qF "    $entry" "$report"; then
        cat "$report" >&2
        echo "check-gradle-consumer.sh: the trust report does not list $entry" >&2
        exit 1
    fi
done

# A spec binding to a declaration that the library verified is a warning on
# the model, and the binding still applies.
for warning in "5:.*stand.vlib.half," "14:.*stand.vlib.Box," "16:.*stand.vlib.Box.size,"; do
    line="${warning%%:*}"
    if ! grep -q "^w: .*VerifiedSpecs.kt:$warning" "$log"; then
        cat "$log" >&2
        echo "check-gradle-consumer.sh: the binding on VerifiedSpecs.kt line $line to a verified declaration was not reported" >&2
        exit 1
    fi
done

# The spec was analysed: the reference resolves and is then rejected, rather
# than failing to resolve.
bad_log="$work/consumer-bad-reference.log"
if build_consumer -PbadReference >"$bad_log" 2>&1; then
    echo "check-gradle-consumer.sh: a reference to the external spec compiled" >&2
    exit 1
fi
if ! grep -q "^e: .*BadReference.kt.*'stand.specs.twiceModel'" "$bad_log"; then
    cat "$bad_log" >&2
    echo "check-gradle-consumer.sh: the consumer with a spec reference failed for another reason" >&2
    exit 1
fi

# Each binding to a missing library declaration is an error on its model.
binding_log="$work/consumer-bad-binding.log"
if build_consumer -PbadBinding >"$binding_log" 2>&1; then
    echo "check-gradle-consumer.sh: a spec binding to a missing library declaration compiled" >&2
    exit 1
fi
for line in 5 8; do
    if ! grep -q "^e: .*BadBinding.kt:$line:" "$binding_log"; then
        cat "$binding_log" >&2
        echo "check-gradle-consumer.sh: the binding on BadBinding.kt line $line was not rejected" >&2
        exit 1
    fi
done

# A contradictory spec is an error on its model, though no code calls it.
vacuous_log="$work/consumer-vacuous-spec.log"
if build_consumer -PvacuousSpec >"$vacuous_log" 2>&1; then
    echo "check-gradle-consumer.sh: a contradictory spec compiled" >&2
    exit 1
fi
if ! grep -q "^e: .*VacuousSpecs.kt:5:.*stand.specs.vacuous.thriceModel is contradictory" "$vacuous_log"; then
    cat "$vacuous_log" >&2
    echo "check-gradle-consumer.sh: the contradictory spec failed for another reason" >&2
    exit 1
fi

# Each false claim fails verification on its own line: a stand-in library
# postcondition, the bundled precondition of abs, and a stand-in library
# precondition.
claim_log="$work/consumer-bad-claim.log"
if build_consumer -PbadClaim >"$claim_log" 2>&1; then
    echo "check-gradle-consumer.sh: the consumer with false claims compiled" >&2
    exit 1
fi
for line in 6 9 11; do
    if ! grep -q "^e: .*BadClaim.kt:$line:" "$claim_log"; then
        cat "$claim_log" >&2
        echo "check-gradle-consumer.sh: the false claim on BadClaim.kt line $line verified" >&2
        exit 1
    fi
done
# Warnings are reported next to the errors.
if ! grep -q "^w: .*BadClaim.kt:21:" "$claim_log"; then
    cat "$claim_log" >&2
    echo "check-gradle-consumer.sh: a failing build dropped its warnings" >&2
    exit 1
fi
# A multiplatform consumer: the bundled specs must reach code in the common
# fragment, the platform one and the platform test compilation, so each
# function verifies only through the collection templates.
kmp="$work/kmp"
mkdir -p "$kmp/src/commonMain/kotlin" "$kmp/src/jvmMain/kotlin" "$kmp/src/jvmTest/kotlin" "$kmp/gradle"
cp gradle/gradle-daemon-jvm.properties "$kmp/gradle/"
cp "$consumer/gradle.properties" "$kmp/"
cat >"$kmp/settings.gradle" <<SETTINGS
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
rootProject.name = "kmp"
SETTINGS
cat >"$kmp/build.gradle" <<'BUILD'
plugins {
    id "org.jetbrains.kotlin.multiplatform" version "2.3.0"
    id "org.jetbrains.kotlin.formver" version "0.1.0-SNAPSHOT"
}

repositories {
    mavenCentral()
    maven { url System.getProperty("formver.repo") }
}

kotlin {
    jvm()
    explicitApi()
    compilerOptions {
        allWarningsAsErrors = true
        extraWarnings = true
    }
}
BUILD
for fragment in common jvm; do
    cat >"$kmp/src/${fragment}Main/kotlin/${fragment}.kt" <<SRC
import org.jetbrains.kotlin.formver.plugin.*

public fun ${fragment}Positive(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
}
SRC
done
cat >"$kmp/src/jvmTest/kotlin/jvmTest.kt" <<'SRC'
import org.jetbrains.kotlin.formver.plugin.*

fun jvmTestPositive(values: @Unique @Borrowed List<Int>) {
    preconditions { forAll<Int> { i -> (0 <= i && i < values.size) implies (values[i] > 0) } }
    values.forEach { verify(it > 0) }
}

fun jvmTestFiltered(values: @Unique @Borrowed List<Int>) {
    val positive = values.filter { it > 0 }
    positive.forEach { verify(it > 0) }
}
SRC
kmp_log="$work/kmp.log"
if ! "$ROOT/gradlew" --no-daemon -p "$kmp" -Dformver.repo="file://$repo" \
    compileKotlinJvm compileTestKotlinJvm >"$kmp_log" 2>&1; then
    cat "$kmp_log" >&2
    echo "check-gradle-consumer.sh: multiplatform consumer build failed" >&2
    exit 1
fi
if grep -q "^w: .*\(common\|jvm\|jvmTest\).kt" "$kmp_log"; then
    grep "^w: " "$kmp_log" >&2
    echo "check-gradle-consumer.sh: the multiplatform consumer's claims did not verify" >&2
    exit 1
fi
kmp_leaked="$(find "$kmp/build/classes" -type f -exec grep -l "${spec_names[@]}" {} + || true)"
if [[ -n "$kmp_leaked" ]]; then
    echo "check-gradle-consumer.sh: a bundled spec reached the multiplatform compiled output:" >&2
    echo "$kmp_leaked" >&2
    exit 1
fi

# Editing a spec re-verifies its callers: the consumer builds clean, then the
# spec of twice changes so that Main.kt's claim about it no longer holds.
rebuild_log="$work/consumer-rebuild.log"
if ! build_consumer >"$rebuild_log" 2>&1; then
    cat "$rebuild_log" >&2
    echo "check-gradle-consumer.sh: the consumer did not build again" >&2
    exit 1
fi
sed -i 's/result -> result == x \* 2 }/result -> result == x * 2 + 1 }/' "$consumer/specs/CounterSpecs.kt"
if ! grep -q 'result == x \* 2 + 1' "$consumer/specs/CounterSpecs.kt"; then
    echo "check-gradle-consumer.sh: the spec edit did not apply" >&2
    exit 1
fi
edited_log="$work/consumer-edited-spec.log"
if build_consumer >"$edited_log" 2>&1; then
    echo "check-gradle-consumer.sh: the consumer stayed verified after its spec changed" >&2
    exit 1
fi
if ! grep -q "^e: .*Main.kt:29:" "$edited_log"; then
    cat "$edited_log" >&2
    echo "check-gradle-consumer.sh: the consumer with an edited spec failed for another reason" >&2
    exit 1
fi

echo "check-gradle-consumer.sh: consumer build passed"
