# Ktor census: arithmetic, index and string-bound obligations

This note explains every SnaKt verification failure in the Ktor 3.6.0
census that is an obligation of Ktor's own code:
`INTEGER_OVERFLOW`, `POSSIBLE_INDEX_OUT_OF_BOUND` and `DIVISION_BY_ZERO`, and
the string bounds of `s[i]` and `substring` (see [String bounds](#string-bounds)). Use
it to tell which failures are real defects in Ktor, which are SnaKt
limitations, and which hold only by a fact the code never states.

Evidence labels: (M) measured by a SnaKt run or a unit test; (R) classified
by reading the pinned source and its callers.

## Scope

The census runs SnaKt over Ktor without annotations and records every
diagnostic it gives:

- Target: ktorio/ktor tag `3.6.0`, 122 JVM modules (the `ktor-*` projects with a `compileKotlinJvm` or `compileKotlin` task).
- Ktor's own Gradle build compiled each module once, and a Gradle init script
  recorded each compile task's compiler arguments.
- Each module was then recompiled with the kotlinc 2.3.0 distribution and a
  SnaKt plugin build from a development branch that is not on main. The
  classpath is Ktor's own build output plus the SnaKt annotations jar, and
  Ktor's compiler plugins are replaced by the kotlinc distribution's jars.
  Every function body is a verification target, with a 60 s timeout per
  declaration. The compiler output of each module is one log file.

For one module, with `MODULE.args` holding its recorded arguments (`-d`
pointed at a scratch directory, the annotations jar appended to
`-classpath`):

```sh
kotlinc -J-Xmx4g -J-Xss30m @MODULE.args \
    -Xplugin=SNAKT_PLUGIN_JAR,KOTLINC_SERIALIZATION_PLUGIN_JAR \
    -P plugin:org.jetbrains.kotlin.formver:verification_targets_selection=all_targets \
    -P plugin:org.jetbrains.kotlin.formver:verification_timeout=60 \
    -P plugin:org.jetbrains.kotlin.formver:verification_workers=0 \
    > MODULE.log 2>&1
```

The 30 MB thread stack is the size the plugin's own tests give the verifier.

25 of the 122 modules have arithmetic or index failures: ktor-client-core,
ktor-client-tests, ktor-compiler-plugin, ktor-http, ktor-http-cio, ktor-io,
ktor-network, ktor-network-tls, ktor-network-tls-certificates,
ktor-openapi-schema, ktor-resources, ktor-server-auth, ktor-server-call-id,
ktor-server-core, ktor-server-cors, ktor-server-di, ktor-server-netty,
ktor-server-partial-content, ktor-server-rate-limit, ktor-server-sessions,
ktor-server-status-pages, ktor-server-test-base, ktor-server-test-suites,
ktor-utils and ktor-websockets.

A row is one distinct diagnostic: a source position and the check that failed
there. One expression can fail two checks (`*` and `+` in `31 * h + x`, or
both bounds of an index). The tables name each row's file and enclosing
declaration; a count after the check merges rows of one declaration with the
same check, class and reason. This command lists the rows from the census logs
in `LOG_DIR`:

```sh
grep -h -E 'error: \[(INTEGER_OVERFLOW|POSSIBLE_INDEX_OUT_OF_BOUND|DIVISION_BY_ZERO)\]' \
    LOG_DIR/*.log | sort -u
```


## Classes

- **A** The arithmetic wraps on purpose: hash codes, bit packing, `toByte` of
  128..255.
- **B** It holds by an invariant the code maintains but does not state. The
  reason names the invariant. The class column gives its cause:
  - `range` is a range guard (`x in a..b` or a `for` range) that the census
    plugin build does not understand;
  - `field` is a `var` field read twice, where SnaKt does not carry the value
    from the check to the use;
  - `unstated` is an invariant the code does not state.
- **C** It holds only under a precondition on a public parameter that the
  documentation does not state. C applies when only a caller that misuses the
  parameter can break the precondition. Examples: an `(offset, length)` pair
  outside the array, an offset not taken from `offsets()`, a clock that does
  not return epoch milliseconds.
- **D** It can fail for values the documentation allows: a caller using the
  parameter as intended can make it fail.
  - **D1** is plain API use.
  - **D2** is TLS code or code that handles network input. D2 rows are classified
    by reading only. This document gives their counts and leaves the rows out.

## Counts

| Module | A | B | C | D1 | D2 | Total |
|:--|--:|--:|--:|--:|--:|--:|
| ktor-client-core | 6 | 1 | 1 | 1 | 0 | 9 |
| ktor-client-tests | 1 | 0 | 0 | 0 | 0 | 1 |
| ktor-compiler-plugin | 0 | 1 | 0 | 0 | 0 | 1 |
| ktor-http | 10 | 25 | 1 | 2 | 0 | 38 |
| ktor-http-cio | 10 | 43 | 4 | 13 | 6 | 76 |
| ktor-io | 9 | 38 | 22 | 6 | 0 | 75 |
| ktor-network | 0 | 4 | 2 | 0 | 0 | 6 |
| ktor-network-tls | 4 | 47 | 0 | 0 | 2 | 53 |
| ktor-network-tls-certificates | 5 | 3 | 0 | 0 | 0 | 8 |
| ktor-openapi-schema | 0 | 4 | 0 | 2 | 0 | 6 |
| ktor-resources | 0 | 1 | 0 | 0 | 0 | 1 |
| ktor-server-auth | 22 | 0 | 0 | 0 | 0 | 22 |
| ktor-server-call-id | 0 | 2 | 0 | 0 | 0 | 2 |
| ktor-server-core | 0 | 22 | 1 | 4 | 0 | 27 |
| ktor-server-cors | 0 | 1 | 0 | 0 | 0 | 1 |
| ktor-server-di | 2 | 1 | 0 | 0 | 0 | 3 |
| ktor-server-netty | 0 | 2 | 0 | 0 | 0 | 2 |
| ktor-server-partial-content | 0 | 3 | 0 | 0 | 0 | 3 |
| ktor-server-rate-limit | 0 | 0 | 3 | 2 | 0 | 5 |
| ktor-server-sessions | 0 | 2 | 1 | 2 | 0 | 5 |
| ktor-server-status-pages | 0 | 1 | 0 | 0 | 0 | 1 |
| ktor-server-test-base | 0 | 14 | 0 | 0 | 2 | 16 |
| ktor-server-test-suites | 0 | 2 | 0 | 0 | 3 | 5 |
| ktor-utils | 15 | 40 | 6 | 7 | 0 | 68 |
| ktor-websockets | 0 | 9 | 0 | 1 | 3 | 13 |
| **Total** | **84** | **266** | **41** | **40** | **16** | **447** |

The 266 B rows by cause: 27 `range`, 14 `field`, 225 `unstated`.

## D1: defects in plain API use

The 40 arithmetic and index D1 rows and the string-bound D1 row come
from the defects below.

| Defect | Rows | Evidence |
|:--|:--|:-:|
| Client `WebSockets`: `pingInterval = Duration.INFINITE` gives `timeoutMillis == -2`. | `WebSockets.kt` `WebSockets.convertSessionToDefault` | M |
| `ContentDisposition.withParameter("filename*", v)`: if `v` is 238,609,295 or more three-byte UTF-8 characters, `percentEncode` computes a negative array size. | `Codecs.kt` `String.percentEncode` | R |
| The deprecated `HttpHeadersMap.find` does not advance its probe. `find("b")` on headers `a`, `b` returns -1. For an absent name with slot 0 occupied, it loops forever and `nextIndex` wraps. | `HttpHeadersMap.kt` `HttpHeadersMap.find` | M |
| `HttpHeadersMap.put` has no count limit. From about 2.0e8 headers (about 13 GB of index arrays) `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so slot offsets leave `Int`; `headerCapacity * 2` itself overflows after about 1.6e9. | `HttpHeadersMap.kt` `HttpHeadersMap.get`, `HttpHeadersMap.put`, `HttpHeadersMap.resize`, `HttpHeadersMap.headerHasName` | R |
| `Sink.size`, `Source.readAvailable(Buffer)`, `ByteReadChannel.availableForRead` and `readFully` truncate a `Long` byte count to `Int`, so they are wrong for buffers of 2^31 bytes or more. | `BytePacketBuilder.kt` `Sink.size`, `ByteReadPacket.kt` `Source.readAvailable`, `ByteReadChannelOperations.kt` `ByteReadChannel.availableForRead`, `ByteReadChannel.readFully` | R |
| `CountedByteWriteChannel` keeps `flushedCount` in an `Int`, so `totalBytesWritten` is wrong after 2^31 bytes. | `CountedByteWriteChannel.kt` `CountedByteWriteChannel.totalBytesWritten`, `CountedByteWriteChannel.flush` | M |
| `GenericElementMapDecoder` computes `entries.size * 2`, which overflows for an OpenAPI map element with 2^30 or more entries. | `GenericElement.kt` `GenericElementMapDecoder.decodeElementIndex`, `GenericElementMapDecoder.decodeCollectionSize` | R |
| `ktor-utils` `hex(bytes)`: for 2^30 or more bytes, `bytes.size * 2` is negative. | `Crypto.kt` `hex` | R |
| `GMTDate.plus(Duration.INFINITE)` and `minus(Duration.INFINITE)` wrap the timestamp. | `Date.kt` `GMTDate.plus`, `GMTDate.minus` | M |
| `sha1` and `sha256`: `pos + 64` wraps for arrays of 2^31 − 63 bytes or more. | `HashFunction.kt` `Sha1.update`, `Sha256.update` | R |
| `GMTDate(timestamp)` truncates the day count to `Int`, so dates past about 1.86e17 ms are wrong. | `DateJvm.kt` `timestampToGMTDate` | M |
| Server config decoders compute `keys.size * 2`, which overflows for 2^30 or more keys under one path. | `MapConfigDecoder.kt` `SubMapConfigDecoder.decodeElementIndex`, `HoconDecoder.kt` `HoconMapDecoder.decodeElementIndex` | R |
| HOCON `Byte` and `Short` properties are narrowed with `toByte()` and `toShort()` without a range check. | `HoconDecoder.kt` `HoconDecoder.decodeByte`, `HoconDecoder.decodeShort` | R |
| Session cookies with `maxAge = Duration.INFINITE`: `maxAge * 1000L` (`maxAge` holds `maxAgeInSeconds`) overflows when `expires` is computed. | `SessionTransportCookie.kt` `SessionTransportCookie.send` | M |
| Session `CacheStorage` with `idleTimeout = Long.MAX_VALUE`: `lastAccess + idleTimeout` wraps. | `CacheJvm.kt` `KeyState.timeToWait` | R |
| Rate limiter with `refillPeriod = Duration.INFINITE`: `lastRefillTimeMillis + refillPeriod` wraps. | `DefaultRateLimiter.kt` `DefaultRateLimiter.tryConsume` | M |
| `RateLimiter.Unlimited` reports `refillAtTimeMillis = Long.MAX_VALUE`, and the default response header computation adds 999 to it. | `RateLimitConfig.kt` `formatHeaderSeconds` | M |
| `CloseReason(code, message)` counts UTF-8 bytes in an `Int`, so a message of 715,827,883 to 1,431,655,765 three-byte characters passes the 123-byte check. | `ControlFrames.kt` `String.utf8Size` | R |
| String bound: the `Char` property decoder of the HOCON config takes the first character of the configured string without checking that it is non-empty. | `HoconDecoder.kt` `HoconDecoder.decodeChar` | R |

Defects that a test could show only with more than 1 GB of heap are
classified by reading. The `CacheStorage` defect is also by reading: it shows
only when the storage's background thread evicts an entry, and that thread
reads the wall clock directly.

Three of the defects with a unit test have an upstream package (issue text, test,
suggested patch, verifier diagnostic), prepared and not submitted:
[ping interval](../upstream/ktor-websocket-ping-interval/REPORT.md),
[`HttpHeadersMap.find`](../upstream/ktor-headers-map-find/REPORT.md),
[`CountedByteWriteChannel`](../upstream/ktor-counted-write-channel/REPORT.md).

### Unit tests

Each test calls the public API with a value the documentation allows and
asserts the correct result. Each test was compiled against the 3.6.0 jars
from the census build, or for the rate limiter against its 3.6.0 source, and
run with JUnit 4 under a 512 MB heap.

`PingIntervalOverflowTest` fails with `AssertionError: timeoutMillis = -2`:

```kotlin
private class RawSession(override val coroutineContext: CoroutineContext) : WebSocketSession {
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE
    override val incoming: ReceiveChannel<Frame> = Channel()
    override val outgoing: SendChannel<Frame> = Channel(Channel.UNLIMITED)
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override suspend fun flush() {}
    @Deprecated("Use cancel() instead.", level = DeprecationLevel.ERROR)
    override fun terminate() {}
}

private class RawWebSocketEngine : HttpClientEngineBase("raw-ws") {
    override val config: HttpClientEngineConfig = HttpClientEngineConfig()
    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(WebSocketCapability)

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = currentCoroutineContext()
        return HttpResponseData(
            HttpStatusCode.SwitchingProtocols, GMTDate(), Headers.Empty, HttpProtocolVersion.HTTP_1_1,
            RawSession(callContext), callContext
        )
    }
}

class PingIntervalOverflowTest {
    @Test
    fun infinitePingIntervalGivesNonNegativeTimeout() = runBlocking {
        val client = HttpClient(RawWebSocketEngine()) {
            install(WebSockets) { pingInterval = Duration.INFINITE }
        }
        val session = client.webSocketSession("ws://localhost/")
        val timeout = session.timeoutMillis
        session.cancel()
        client.close()
        assertTrue(timeout > 0, "timeoutMillis = $timeout")
    }
}
```

`HeadersFindTest` fails with `AssertionError: expected:<1> but was:<-1>`:

```kotlin
class HeadersFindTest {
    @Suppress("DEPRECATION")
    @Test
    fun findsSecondHeader() = runBlocking {
        val headers = parseHeaders(ByteReadChannel("a: 1\r\nb: 2\r\n\r\n"))
        assertEquals("2", headers["b"].toString())
        assertEquals(1, headers.find("b"))
    }
}
```

`CountedTotalBytesOverflowTest` writes 2^31 bytes in 1 MiB chunks to a
channel that a second coroutine discards, and fails with
`AssertionError: expected:<2147483648> but was:<-2147483648>`:

```kotlin
class CountedTotalBytesOverflowTest {
    @Test
    fun totalBytesWrittenCountsTwoGibibytes() = runBlocking {
        val channel = ByteChannel()
        val drain = launch(Dispatchers.Default) { channel.discard() }
        val counted = (channel as ByteWriteChannel).counted()
        val chunk = ByteArray(1 shl 20)
        repeat(2048) {
            counted.writeFully(chunk)
            counted.flush()
        }
        val total = counted.totalBytesWritten
        counted.flushAndClose()
        drain.join()
        assertEquals(1L shl 31, total)
    }
}
```

`GMTDateInfiniteDurationTest` fails with
`AssertionError: GMTDate(1000) + INFINITE gave timestamp -9223372036854774809`:

```kotlin
class GMTDateInfiniteDurationTest {
    @Test
    fun plusInfiniteIsNotEarlier() {
        val d = GMTDate(1_000L)
        val r = d + Duration.INFINITE
        assertTrue("GMTDate(1000) + INFINITE gave timestamp ${r.timestamp}", r.timestamp >= d.timestamp)
    }

    @Test
    fun minusInfiniteIsNotLater() {
        val d = GMTDate(-1_000L)
        val r = d - Duration.INFINITE
        assertTrue("GMTDate(-1000) - INFINITE gave timestamp ${r.timestamp}", r.timestamp <= d.timestamp)
    }
}
```

`GMTDateLargeTimestampTest` compares with `java.time` and fails with
`expected:<6339717-9-13 1> but was:<-5419504-8-23 4>` for `2e17` and
`expected:<292278994-8-17 7> but was:<-1701532-4-6 5>` for `Long.MAX_VALUE`:

```kotlin
class GMTDateLargeTimestampTest {
    private fun check(ts: Long) {
        val g = GMTDate(ts)
        val j = Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC)
        assertEquals("GMTDate($ts)", "${j.year}-${j.monthValue}-${j.dayOfMonth} ${j.dayOfWeek.value}",
            "${g.year}-${g.month.ordinal + 1}-${g.dayOfMonth} ${g.dayOfWeek.ordinal + 1}")
    }

    @Test fun twoTimesTenToSeventeen() = check(200_000_000_000_000_000L)
    @Test fun longMax() = check(Long.MAX_VALUE)
}
```

`SessionCookieInfiniteMaxAgeTest` runs an in-process `testApplication`; the
session cookie's `Expires` is one second before the request:

```kotlin
class SessionCookieInfiniteMaxAgeTest {
    @Test
    fun infiniteMaxAgeCookieDoesNotExpireInThePast() = testApplication {
        install(Sessions) {
            cookie<String>("S") {
                cookie.maxAge = Duration.INFINITE
                serializer = object : SessionSerializer<String> {
                    override fun serialize(session: String) = session
                    override fun deserialize(text: String) = text
                }
            }
        }
        routing { get("/") { call.sessions.set("v"); call.respondText("ok") } }
        val before = GMTDate()
        val header = client.get("/").headers[HttpHeaders.SetCookie]!!
        val expires = parseServerSetCookieHeader(header).expires
        assertTrue("Set-Cookie: $header (request at ${before.timestamp})",
            expires == null || expires.timestamp > before.timestamp)
    }
}
```

`RateLimitInfiniteRefillTest` fails with `refillAtTimeMillis =
-9223372036854774809` for `refillPeriod = Duration.INFINITE`, and with
`X-RateLimit-Reset: -9223372036854774` for `RateLimiter.Unlimited`. The
rate-limit module has no jar in the census build, so its 3.6.0 source was
compiled for this test:

```kotlin
class RateLimitInfiniteRefillTest {
    @Test
    fun infiniteRefillPeriodGivesNonNegativeRefillTime() = runBlocking {
        val limiter = RateLimiter.default(limit = 1, refillPeriod = Duration.INFINITE, clock = { 1_000L })
        val state = limiter.tryConsume(1) as RateLimiter.State.Available
        assertTrue("refillAtTimeMillis = ${state.refillAtTimeMillis}", state.refillAtTimeMillis >= 1_000L)
    }

    @Test
    fun unlimitedGivesNonNegativeResetHeader() = testApplication {
        install(RateLimit) { register { rateLimiter { _, _ -> RateLimiter.Unlimited } } }
        routing { rateLimit { get("/") { call.respondText("ok") } } }
        val reset = client.get("/").headers["X-RateLimit-Reset"]!!
        assertTrue("X-RateLimit-Reset: $reset", reset.toLong() >= 0)
    }
}
```

## Undocumented preconditions (C)

| Precondition | Rows |
|:--|:--|
| `AcceptAllCookiesStorage(clock)`: `clock` returns epoch milliseconds, or at least stays below `Long.MAX_VALUE - 2^31 * 1000`. | `AcceptAllCookiesStorage.kt` `AcceptAllCookiesStorage.Cookie.maxAgeOrExpires` |
| `HttpHeadersMap.nameAtOffset` and `valueAtOffset`: the offset comes from `offsets()`. | `HttpHeadersMap.kt` `HttpHeadersMap.nameAtOffset`, `HttpHeadersMap.valueAtOffset` |
| `HttpStatusCode(value, …)`: `value` is a status code, so `compareTo`'s subtraction cannot overflow. | `HttpStatusCode.kt` `HttpStatusCode.compareTo` |
| ktor-io `(array, offset, length)` APIs: `offset + length <= array.size`, unchecked before the sum. | `ByteChannelCtor.kt` `ByteReadChannel`, `BytePacketBuilder.kt` `Sink.writeFully`, `ByteReadPacket.kt` `ByteReadPacket`, `Source.readFully`, `Input.kt` `Input.readAvailable`, `Strings.kt` `String`, `ByteReadChannelOperations.kt` `ByteReadChannel.readFully` |
| `CharsetEncoder.encode(input, fromIndex, toIndex, dst)`: indices inside `input`. | `Encoding.kt` `CharsetEncoder.encodeArrayImpl`, `CharArraySequence.kt` `CharArraySequence.get` |
| `ByteArray.storeIntAt(index, value)`: `0 <= index <= size - 4`. | `Memory.kt` `ByteArray.storeIntAt` |
| `Long.toIntOrFail` (`@PublishedApi`, no callers): the value is at least `Int.MIN_VALUE`. | `Numbers.kt` `Long.toIntOrFail` |
| A channel's write buffer holds fewer than 2^31 unflushed bytes. Channel writers flush at `CHANNEL_MAX_SIZE`, and `writeBuffer` is `@InternalAPI`. | `ByteReadChannelOperations.kt` `Sink.size (inlined getter)`, `ByteWriteChannel.availableForWrite`, `ByteWriteChannelOperations.kt` `ByteWriteChannel.write` |
| The `java.io` `(b, off, len)` contract, which the overrides do not check. | `Blocking.kt` `ByteChannelInputStream.read`, `ByteChannelOutputStream.write` |
| `InterestSuspensionsMap.removeSuspension(interestOrdinal)`: the ordinal is below `SelectInterest.AllInterests.size`; the KDoc names it an ordinal without stating the bound. | `InterestSuspensionsMap.kt` `InterestSuspensionsMap.removeSuspension` |
| `ByteArray.readShort(offset)` (`@InternalAPI`): `0 <= offset <= size - 2`. | `Bytes.kt` `ByteArray.readShort` |
| `GMTDate.plus(milliseconds)` and `minus(milliseconds)`: the result fits in a `Long`. | `Date.kt` `GMTDate.plus`, `GMTDate.minus` |
| `Calendar.toDate(timestamp)`: `timestamp` is at least the zone offset away from the `Long` limits. | `DateJvm.kt` `Calendar.toDate` |
| `ResponseCookies.append(maxAge = …)`: `maxAge` is at least `Int.MIN_VALUE`. | `ResponseCookies.kt` `ResponseCookies.append` |
| `CacheStorage(delegate, idleTimeout)`: `idleTimeout` is not near `Long.MIN_VALUE`. | `CacheJvm.kt` `KeyState.timeToWait` |
| `rateLimiter(limit, refillPeriod, clock)` and `requestWeight`: non-negative limit and weight, `refillPeriod` not near `-Duration.INFINITE`, `clock` returns epoch milliseconds. | `DefaultRateLimiter.kt` `DefaultRateLimiter.tryConsume`, `DefaultRateLimiter.timeToWaitMillis` |
| `MessageLengthLimitingLogger(maxLength, minLength)`: both are non-negative. | `LoggerJvm.kt` `MessageLengthLimitingLogger.logLong` |
| `parseQueryString(query, startIndex, …)`: `startIndex >= 0`. | `Query.kt` `ParametersBuilder.parse` |
| `CharsetEncoder.encodeToByteArray(input, fromIndex, toIndex)`: `0 <= fromIndex <= toIndex <= input.length`. | `CharsetJVM.kt` `CharsetEncoder.encodeToByteArray` |

## B: invariants as contracts

Three representative B invariants were added as SnaKt contracts to scratch
copies of the failing bodies. Each copy was then run with the census plugin
build (`verification_targets_selection=all_targets`) (M).

1. **Helper postcondition.** `Certificates.kt` `Sink.writeDerInt` relies on `derLength()`
   returning 0..5. The scratch copy keeps `derLength` and `writeDerInt`
   verbatim, apart from `writeByte` becoming a list append, and adds:

   ```kotlin
   fun Int.derLength(): Int {
       preconditions { this >= 0 }
       postconditions<Int> { r -> 0 <= r && r <= 5 }
       // body as upstream, with this loop invariant:
       //   (byteCount == 1 && mask == 0x7f) || (byteCount == 2 && mask == 0x3fff) ||
       //   (byteCount == 3 && mask == 0x1fffff) || (byteCount == 4 && mask == 0xfffffff) ||
       //   (byteCount == 5 && mask == -1)
   }
   ```

   `writeDerInt` verifies in 13 s. `derLength` verifies in 73 s, but only
   with a 300 s timeout: it times out at the census's 60 s.

2. **Class invariant.** Two lookups in `HttpHeadersMap.get` divide by
   `headerCapacity`. They rely on `size > 0` implying `headerCapacity > 0`. The
   scratch copy keeps the slot arithmetic of `get` and states the invariant as
   a class law:

   ```kotlin
   @Law @Pure @Unique fun capacityCoversSize(): Boolean = size >= 0 && (size == 0 || headerCapacity > 0)
   ```

   Both `hash % headerCapacity` and `(headerIndex + 1) % headerCapacity`
   verify.

3. **Caller precondition.** `ChunkedTransferEncoding.kt` `ByteWriteChannel.writeChunk` computes
   `endIndex - startIndex`. The `ByteReadChannel.read` callback is its only
   caller, and it passes an ordered range. The scratch copy drops the I/O calls
   and adds
   `preconditions { 0 <= startIndex && startIndex <= endIndex && endIndex <= memory.size }`.
   It verifies.


## SnaKt limitations behind B rows

A probe file run with the census plugin build shows that these guards fail
although they are in the code (M):

| Cause | Fails | Verifies |
|:--|:--|:--|
| `range` | `if (c in 'A'.code..'Z'.code) 'a'.code + (c - 'A'.code)`, `require(u in 0..7); u.toByte()` | the same with `>=` and `<=` |
| `field` | `if (sentCount >= max) throw …; sentCount++` on a class field | — |

SnaKt on main converts `Int` and `Long` range membership to comparisons
(R); the census plugin build does not (M). Two
`range` rows are `for` ranges rather than `in` guards (`Query.kt` `ParametersBuilder.parse`,
`ByteChannelScanner.kt` `ByteChannelScanner.buildPartialMatchTable`) (R).

One `toInt` row of `ByteReadChannelOperations.kt` is reported on an import line. Its check
is the inlined `Sink.size` getter, so the diagnostic position of an inlined
property getter points at the wrong place (R).

## Rows

### ktor-client-core

Paths under `ktor-client/ktor-client-core/common/src/io/ktor/client/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `plugins/cache/storage/HttpCacheStorage.kt` | `CachedResponseData.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `plugins/cache/storage/HttpCacheStorage.kt` | `CachedResponseData.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `plugins/cookies/AcceptAllCookiesStorage.kt` | `AcceptAllCookiesStorage.Cookie.maxAgeOrExpires` | `+ (Long)` | C | `createdAt + maxAge * 1000L` takes `createdAt` from the public constructor parameter `clock`, whose KDoc does not say it returns epoch milliseconds; a clock returning more than `Long.MAX_VALUE - 2^31 * 1000` overflows. |
| `plugins/HttpSend.kt` | `HttpSend.DefaultSender.execute` | `++` | B (field) | The guard in this declaration throws unless `sentCount < maxSendCount`, so the field stays at most `maxSendCount`; SnaKt re-reads the field after the check. |
| `plugins/HttpTimeout.kt` | `HttpTimeoutConfig.hashCode` | `*` | A | `hashCode` with `31 * result`. |
| `plugins/HttpTimeout.kt` | `HttpTimeoutConfig.hashCode` | `+` | A | `hashCode` with `31 * result`. |
| `plugins/HttpTimeout.kt` | `HttpTimeoutConfig.hashCode` | `*` | A | `hashCode` with `31 * result`. |
| `plugins/HttpTimeout.kt` | `HttpTimeoutConfig.hashCode` | `+` | A | `hashCode` with `31 * result`. |
| `plugins/websocket/WebSockets.kt` | `WebSockets.convertSessionToDefault` | `* (Long)` | D1 | `pingInterval = Duration.INFINITE` sets `pingIntervalMillis = Long.MAX_VALUE`, and `pingIntervalMillis * 2` makes the session's `timeoutMillis` -2 (see [Unit tests](#unit-tests)). |

### ktor-client-tests

Paths under `ktor-client/ktor-client-tests/common/src/io/ktor/client/tests/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `utils/Generators.kt` | `makeArray` | `toByte` | A | `makeArray` fills a test array with `index.toByte()`, wrapping every 256 bytes. |

### ktor-compiler-plugin

Paths under `ktor-compiler-plugin/src/io/ktor/openapi/fir/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `OpenApiCommentParser.kt` | `CommentLine.startIndex` | `+` | B (unstated) | `indexOfOrNull` returns an index below `rawText.length`, so adding 1 fits. |

### ktor-http

Paths under `ktor-http/common/src/io/ktor/http/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `*` | A | `MaxAge.hashCode` with `31 * result`. |
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `+` | A | `MaxAge.hashCode` with `31 * result`. |
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `*` | A | `MaxAge.hashCode` with `31 * result`. |
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `+` | A | `MaxAge.hashCode` with `31 * result`. |
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `*` | A | `MaxAge.hashCode` with `31 * result`. |
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `+` | A | `MaxAge.hashCode` with `31 * result`. |
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `*` | A | `MaxAge.hashCode` with `31 * result`. |
| `CacheControl.kt` | `CacheControl.MaxAge.hashCode` | `+` | A | `MaxAge.hashCode` with `31 * result`. |
| `Codecs.kt` | `String.percentEncode` | `+` | D1 | `percentEncode` sizes its output as `rawCount + 3 * encodedBytes`, which is negative for `ContentDisposition.withParameter("filename*", "中".repeat(238_609_295))`. |
| `Codecs.kt` | `String.percentEncode` | `*` | D1 | As the `+` row of this declaration. |
| `Codecs.kt` | `String.percentEncode` | `index < size` | B (unstated) | `resultSize` counts exactly 1 slot per allowed byte and 3 per other byte, so `writeIndex` never passes it (given `rawCount + 3 * encodedBytes` does not overflow). |
| `Codecs.kt` | `String.percentEncode` | `index >= 0` | B (unstated) | `writeIndex` starts at 0 and only increases. |
| `Codecs.kt` | `String.percentEncode` | `++` | B (unstated) | `writeIndex <= resultSize`, an array size. |
| `Codecs.kt` | `String.percentEncode` | `index < size` | B (unstated) | Each encoded byte was counted as 3 slots in `resultSize`. |
| `Codecs.kt` | `String.percentEncode` | `index >= 0` | B (unstated) | `writeIndex` starts at 0 and only increases. |
| `Codecs.kt` | `String.percentEncode` | `++` | B (unstated) | `writeIndex <= resultSize`. |
| `Codecs.kt` | `String.percentEncode` | `index < size` | B (unstated) | Second of the 3 slots counted per encoded byte. |
| `Codecs.kt` | `String.percentEncode` | `++` | B (unstated) | `writeIndex <= resultSize`. |
| `Codecs.kt` | `String.percentEncode` | `index < size` | B (unstated) | Third of the 3 slots counted per encoded byte. |
| `Codecs.kt` | `String.percentEncode` | `++` | B (unstated) | `writeIndex <= resultSize`. |
| `Codecs.kt` | `hexDigitToChar` | `Char +` | B (range) | Guarded by `digit in 0..9`; the census plugin build ignores `in`-range guards. |
| `Codecs.kt` | `hexDigitToChar` | `Char +` | B (unstated) | The private `hexDigitToChar` receives nibbles only (`code shr 4`, `code and 0xf`), so `digit` is 10..15 here. |
| `Codecs.kt` | `hexDigitToChar` | `Char -` | B (unstated) | As above; `'A' + digit - 10` stays in `'A'..'F'`. |
| `ContentDisposition.kt` | `ContentDisposition.hashCode` | `*` | A | `hashCode` with `31 *`. |
| `ContentDisposition.kt` | `ContentDisposition.hashCode` | `+` | A | `hashCode` with `31 *`. |
| `CookieUtils.kt` | `StringLexer.accept` | `++` | B (field) | Runs only after `test()` confirmed `index < source.length`; SnaKt re-reads the `var` field after the check. |
| `CookieUtils.kt` | `StringLexer.acceptWhile` | `++` | B (field) | As `StringLexer.accept`. |
| `FileContentType.kt` | `ContentType.Companion.fromFilePath` | `+` | B (unstated) | `lastIndexOfAny` returns at most `path.lastIndex`. |
| `FileContentType.kt` | `ContentType.Companion.fromFilePath` | `+` | B (unstated) | `indexOf` result checked against -1 is at most `path.lastIndex`. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValueQuoted` | `-` | B (unstated) | The only caller passes `start = position + 1 >= 1`. |
| `HttpHeaderValueParser.kt` | `String.nextIsDelimiterOrEnd` | `+` | B (unstated) | The only caller passes `position <= value.lastIndex`. |
| `HttpStatusCode.kt` | `HttpStatusCode.compareTo` | `-` | C | `compareTo` is `value - other.value`; the public constructor accepts any `Int` and its KDoc does not limit it, so `HttpStatusCode(Int.MIN_VALUE, "")` compared with code 1 overflows and orders wrongly. |
| `parsing/regex/RegexParserGenerator.kt` | `GrammarRegex.groupsCount` | `+` | B (unstated) | `groupsCount` is bounded by the node count of the finite built-in grammars. |
| `Query.kt` | `ParametersBuilder.parse` | `+` | B (range) | `index` ranges over `startIndex..query.lastIndex` (a `for` range SnaKt does not bound). |
| `Query.kt` | `ParametersBuilder.appendParam` | `+` | B (unstated) | `equalIndex` is a real index of `=`, at most `query.lastIndex`. |
| `URLParser.kt` | `URLBuilder.parseMailto` | `+` | B (unstated) | `indexOf("@")` result checked against -1. |
| `URLParser.kt` | `count` | `+` | B (unstated) | The loop guard keeps `startIndex + result < endIndex <= urlString.length`. |
| `URLParser.kt` | `count` | `+` | B (unstated) | Same expression already bounded by the loop guard on the `+` row of this declaration. |

### ktor-http-cio

Paths under `ktor-http/ktor-http-cio/common/src/io/ktor/http/cio/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `ChunkedTransferEncoding.kt` | `decodeChunked` | `+ (Long)` | B (unstated) | Each added `chunkSize` was copied from the input before `skipCrLf` (which throws at end of input), so the total is at most the bytes received; the total is never read. |
| `ChunkedTransferEncoding.kt` | `ByteWriteChannel.writeChunk` | `-` | B (unstated) | The only caller is the `ByteReadChannel.read` callback, which passes `startIndex <= endIndex` (see [B: invariants as contracts](#b-invariants-as-contracts)). |
| `ConnectionOptions.kt` | `ConnectionOptions.hashCode` | `*` | A | `hashCode` with `31 * result`. |
| `ConnectionOptions.kt` | `ConnectionOptions.hashCode` | `+` | A | `hashCode` with `31 * result`. |
| `ConnectionOptions.kt` | `ConnectionOptions.hashCode` | `*` | A | `hashCode` with `31 * result`. |
| `ConnectionOptions.kt` | `ConnectionOptions.hashCode` | `+` | A | `hashCode` with `31 * result`. |
| `ConnectionOptions.kt` | `ConnectionOptions.hashCode` | `*` | A | `hashCode` with `31 * result`. |
| `ConnectionOptions.kt` | `ConnectionOptions.hashCode` | `+` | A | `hashCode` with `31 * result`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.find` | `++` | D1 | The deprecated public `find` sets `offset = (offset / HEADER_SIZE) % headerCapacity`, which does not advance the probe: `find("b")` on headers `a`, `b` returns -1 (see [Unit tests](#unit-tests)), and for an absent name with slot 0 occupied the loop never ends and `nextIndex` wraps. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.find` | `%` | B (unstated) | `size > 0` implies `headerCapacity >= 128`; `find` returns early when `size == 0`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.get` | `%` | B (unstated) | `size > 0` implies `headerCapacity >= 128` (see [B: invariants as contracts](#b-invariants-as-contracts)). |
| `HttpHeadersMap.kt` | `HttpHeadersMap.get` | `*` | D1 | The public `put` has no count limit: from about 2.0e8 headers `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so the slot offset `6 * headerIndex` and the joined array offsets leave `Int`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.get` | `%` | B (unstated) | `size > 0` implies `headerCapacity >= 128`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.put` | `%` | B (unstated) | After the `thresholdReached()` check, `size < 0.75 * headerCapacity`, so `headerCapacity > 0`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.put` | `*` | D1 | The public `put` has no count limit: from about 2.0e8 headers `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so the slot offset `6 * headerIndex` and the joined array offsets leave `Int`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.put` | `%` | B (unstated) | `headerCapacity > 0` after the threshold check. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.put` | `+` | D1 | The public `put` has no count limit: from about 2.0e8 headers `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so the slot offset `6 * headerIndex` and the joined array offsets leave `Int`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.put` | `*` | D1 | The public `put` has no count limit: from about 2.0e8 headers `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so the slot offset `6 * headerIndex` and the joined array offsets leave `Int`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.put` | `+` | D1 | The public `put` has no count limit: from about 2.0e8 headers `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so the slot offset `6 * headerIndex` and the joined array offsets leave `Int`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.put` | `++` | B (unstated) | `size < 0.75 * headerCapacity` after the threshold check. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.resize` | `*` | D1 | The public `put` adds headers without a count limit; `headerCapacity * 2` itself overflows after about 1.6e9 calls, after the slot offsets at `HttpHeadersMap.get` and `HttpHeadersMap.put` already have (about 2.0e8 headers). |
| `HttpHeadersMap.kt` | `HttpHeadersMap.resize` | `*` | B (unstated) | `headerCapacity == 128 * arraysCount()`, so doubling the array count overflows only after `headerCapacity` already has. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.resize` | `+` (4) | D1 | The public `put` has no count limit: from about 2.0e8 headers `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so the slot offset `6 * headerIndex` and the joined array offsets leave `Int`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.headerHasName` | `+` (2) | D1 | The public `put` has no count limit: from about 2.0e8 headers `headerCapacity` exceeds `Int.MAX_VALUE / 6`, so the slot offset `6 * headerIndex` and the joined array offsets leave `Int`. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.nameAtOffset` | `+` | C | The public `nameAtOffset(headerOffset)` has no KDoc; it is correct only for offsets from `offsets()`, and `headerOffset > Int.MAX_VALUE - 1` overflows. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.nameAtOffset` | `+` | C | As the `+` row of this declaration (`headerOffset > Int.MAX_VALUE - 2`). |
| `HttpHeadersMap.kt` | `HttpHeadersMap.valueAtOffset` | `+` | C | The public `valueAtOffset(headerOffset)` has no KDoc; it is correct only for offsets from `offsets()`, and `headerOffset > Int.MAX_VALUE - 3` overflows. |
| `HttpHeadersMap.kt` | `HttpHeadersMap.valueAtOffset` | `+` | C | As the `+` row of this declaration (`headerOffset > Int.MAX_VALUE - 4`). |
| `HttpParser.kt` | `parseHeaders` | `-` | B (unstated) | `0 <= range.start <= range.end == builder.length`. |
| `HttpParser.kt` | `parseUri` | `-` | B (unstated) | `findSpaceOrEnd` returns an index at or after `range.start`. |
| `HttpParser.kt` | `parseHeaderValue` | `+` | B (unstated) | `valueLastIndex < end <= builder.length`. |
| `internals/AsciiCharTree.kt` | `AsciiCharTree.search` | `index` | B (unstated) | Reached only after the `index` row of this declaration indexed with a code below 256, and lowercasing a Latin-1 character stays below 256. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.getImpl` | `index < size` | B (unstated) | Callers pass `0 <= index < length`, and every buffer has the pool's array length. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.getImpl` | `index >= 0` | B (unstated) | Callers pass `0 <= index < length`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.getImpl` | `%` | B (unstated) | Pool arrays are non-empty (`CHAR_BUFFER_ARRAY_LENGTH`). |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `index < size` | B (unstated) | After `nonFullBuffer()`, `0 < remaining <= current.size`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `index >= 0` | B (unstated) | After `nonFullBuffer()`, `0 < remaining <= current.size`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `-` | B (unstated) | `0 < remaining <= current.size`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `-` | B (unstated) | `remaining >= 1` after `nonFullBuffer()`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `-` | B (unstated) | `0 <= remaining <= buffer.size`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `-` | B (unstated) | The only caller (ktor-io line reading) passes `0 <= startIndex <= endIndex <= value.length`, and `current < endIndex` in the loop. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `index < size` | B (unstated) | `offset + i < offset + remaining == buffer.size`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `index >= 0` | B (unstated) | `offset >= 0` and `i >= 0`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `+` | B (unstated) | `offset + i < buffer.size`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `+` | B (unstated) | `current + i < endIndex`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `+` | B (unstated) | `current + bytesToCopy <= endIndex`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `-` | B (unstated) | `bytesToCopy <= remaining`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.append` | `-` | B (unstated) | `startIndex <= endIndex` from the only caller. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.copy` | `-` | B (unstated) | `copy` is called with `startIndex <= endIndex`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.copy` | `-` | B (unstated) | `base <= startIndex`, both non-negative. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.copy` | `-` | B (unstated) | `base < endIndex`, both non-negative. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.copy` | `index` | B (unstated) | `innerIndex < min(endIndex - base, CHAR_BUFFER_ARRAY_LENGTH) <= buffer.size`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.SubSequenceImpl.length` | `-` | B (unstated) | `SubSequenceImpl` is built with `start <= end`. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.bufferForIndex` | `/` | B (unstated) | `current` is non-null and non-empty whenever `buffers` is non-null. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.rangeEqualsImpl` | `+` (2) | B (unstated) | Callers pass ranges inside both sequences. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.hashCodeImpl` | `*` | A | Polynomial string hash. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.hashCodeImpl` | `+` | A | Polynomial string hash. |
| `internals/CharArrayBuilder.kt` | `CharArrayBuilder.currentPosition` | `-` | B (unstated) | `0 <= remaining <= current.size`. |
| `internals/Chars.kt` | `CharSequence.hashCodeLowerCase` | `*` | A | Polynomial string hash. |
| `internals/Chars.kt` | `CharSequence.hashCodeLowerCase` | `+` | A | Polynomial string hash. |
| `internals/Chars.kt` | `CharSequence.equalsLowerCase` | `-` | B (unstated) | Called with `start <= end` from header slots. |
| `internals/Chars.kt` | `Int.toLowerCase` | `+` | B (range) | Guarded by `this in 'A'.code..'Z'.code`; the census plugin build ignores `in`-range guards. |
| `internals/Chars.kt` | `Int.toLowerCase` | `-` | B (range) | As the `+` row of this declaration. |
| `Multipart.kt` | `parsePartBodyImpl` | `+ (Long)` | B (unstated) | The sum counts bytes actually read: at most `contentLength <= limit` plus the boundary length. |
| `Multipart.kt` | `findBoundary` | `++` | B (unstated) | `paramNameCount <= i < contentType.length`. |

### ktor-io

Paths under `ktor-io/common/src/io/ktor/utils/io/` (`jvm/.../` is ktor-io/jvm/src/io/ktor/utils/io/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `bits/ByteOrder.kt` | `Short.highByte` | `toByte` | A | `highByte` bit extraction. |
| `bits/ByteOrder.kt` | `Short.lowByte` | `toByte` | A | `lowByte` bit extraction. |
| `bits/ByteOrder.kt` | `Int.highShort` | `toShort` | A | `highShort` bit extraction. |
| `bits/ByteOrder.kt` | `Int.lowShort` | `toShort` | A | `lowShort` bit extraction. |
| `bits/ByteOrder.kt` | `Long.highInt` | `toInt` | A | `highInt` bit extraction. |
| `bits/ByteOrder.kt` | `Long.lowInt` | `toInt` | A | `lowInt` bit extraction. |
| `ByteChannelCtor.kt` | `ByteReadChannel` | `+` | C | The public `ByteReadChannel(content, offset, length)` does not check its range; `offset = 1, length = Int.MAX_VALUE` wraps the end index (an exception follows either way). |
| `ByteChannelScanner.kt` | `ByteChannelScanner.advanceToNextPotentialMatch` | `+` | B (unstated) | `checkBounds(size)` just before keeps `bytesRead + size <= limit`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.advanceToNextPotentialMatch` | `+` | B (unstated) | `checkBounds(nextMatch)` precedes it and the read returns at most `nextMatch`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkFullMatch` | `index < size` | B (unstated) | `0 <= matchIndex < matchString.size`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkFullMatch` | `index >= 0` | B (field) | Loop guard `matchIndex > 0`; SnaKt re-reads the `var` field after the check. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkFullMatch` | `-` | B (field) | As above. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkFullMatch` | `-` | B (unstated) | Both indices lie in `[0, matchString.size)`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkFullMatch` | `+` | B (unstated) | `checkBounds(retained)` precedes it. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkFullMatch` | `++ (Long)` | B (unstated) | `bytesRead` counts bytes actually transferred, at most `limit`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkFullMatch` | `+` | B (unstated) | `matchIndex < matchString.size`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.checkBounds` | `+ (Long)` | B (unstated) | `bytesRead <= limit` plus a buffered byte count. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.findNext` | `+ (Long)` | B (unstated) | Adds a partial-match length below `matchString.size` to `bytesRead <= limit`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.buildPartialMatchTable` | `index < size` | B (unstated) | KMP table invariant `j <= i - 1 < size`. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.buildPartialMatchTable` | `+` | B (unstated) | As above. |
| `ByteChannelScanner.kt` | `ByteChannelScanner.buildPartialMatchTable` | `index < size` | B (range) | `i` ranges over `1 until matchString.size` (a `for` range SnaKt does not bound). |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readBuffer` | `- (Long)` | B (unstated) | `size = minOf(remaining, readBuffer.remaining)` under `remaining > 0`. |
| `ByteReadChannelOperations.kt` | `Sink.size (inlined getter)` | `toInt` | C | Reported on an import line; it is the inlined `Sink.size` used by `availableForWrite` (`ByteWriteChannel.availableForWrite`), wrong only when a write buffer holds 2^31 unflushed bytes, which channel writers flush long before. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.copyAndClose` | `+ (Long)` | B (unstated) | Sum of bytes actually transferred. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.copyTo` | `+ (Long)` | B (unstated) | Sum of bytes actually transferred. |
| `ByteReadChannelOperations.kt` | `ByteWriteChannel.availableForWrite` | `-` | C | As `Sink.size (inlined getter)` (`CHANNEL_MAX_SIZE - writeBuffer.size`). |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readTo` | `- (Long)` | B (unstated) | `byteCount = minOf(remaining, ...)` under `remaining > 0`. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readTo` | `- (Long)` | B (unstated) | `remaining` stays in `[0, limit]`. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.copyTo` | `- (Long)` | B (unstated) | `count = minOf(remaining, ...)` under `remaining > 0`. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.copyTo` | `- (Long)` | B (unstated) | `remaining` stays in `[0, limit]`. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readByteArray` | `-` | B (unstated) | The builder receives at most `count` bytes, so `0 <= size < count` in the loop. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readPacket` | `-` | B (unstated) | Loop guard `result.size < packet`. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readPacket` | `-` | B (unstated) | As the `-` row of this declaration. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.discard` | `- (Long)` | B (unstated) | `count = minOf(remaining, ...)` under `remaining > 0`. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.discard` | `- (Long)` | B (unstated) | `remaining` stays in `[0, max]`. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.availableForRead` | `toInt` | D1 | `availableForRead` truncates the buffered byte count; a channel over a buffer of 2^31 bytes or more reports a wrong or negative count. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readFully` | `-` | C | The public `readFully(out, start, end)` does not check `start`; `start = -2, end = Int.MAX_VALUE` overflows. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readFully` | `toInt` | D1 | `readBuffer.remaining.toInt()` truncates for a channel holding 2^31 bytes or more, making `count` negative or zero. |
| `ByteReadChannelOperations.kt` | `ByteReadChannel.readFully` | `+` | B (unstated) | `count <= end - offset`, so `offset + count <= end`. |
| `ByteWriteChannelOperations.kt` | `ByteWriteChannel.write` | `-` | C | `before` and `after` are truncated `Sink.size` values; overflows only when a write buffer crosses 2^31 unflushed bytes during one write, as the inlined `Sink.size` getter. |
| `charsets/Encoding.kt` | `CharsetEncoder.encodeArrayImpl` | `-` | C | The public `CharsetEncoder.encode(input, fromIndex, toIndex, dst)` does not check its indices; `fromIndex = -1, toIndex = Int.MAX_VALUE` overflows. |
| `charsets/Encoding.kt` | `CharsetEncoder.encodeToImpl` | `+` | B (unstated) | `encodeImpl` returns at most `toIndex - start` characters consumed. |
| `core/BufferAppend.kt` | `Buffer.writeBufferAppend` | `toInt` | B (unstated) | `byteCount = min(other.size, maxSize.toLong()) <= maxSize`, an `Int`. |
| `core/BytePacketBuilder.kt` | `Sink.size` | `toInt` | D1 | The public `Sink.size` truncates the buffered byte count; a `Buffer` holding 2^31 bytes or more reports a wrong or negative size. |
| `core/BytePacketBuilder.kt` | `Sink.writeFully` | `+` | C | The public `Sink.writeFully(buffer, offset, length)`: `offset = 1, length = Int.MAX_VALUE` wraps the end index. |
| `core/ByteReadPacket.kt` | `ByteReadPacket` | `+` | C | The public `ByteReadPacket(array, offset, length)`: same range overflow. |
| `core/ByteReadPacket.kt` | `Source.readAvailable` | `toInt` | D1 | The public `Source.readAvailable(out: Buffer)` returns the truncated byte count for a source holding 2^31 bytes or more. |
| `core/ByteReadPacket.kt` | `Source.readFully` | `+` | C | The public `Source.readFully(out, offset, length)`: same range overflow. |
| `core/Input.kt` | `Input.readAvailable` | `+` | C | The public `Input.readAvailable(buffer, offset, length)`: same range overflow. |
| `core/internal/CharArraySequence.kt` | `CharArraySequence.get` | `index < size` | C | Indices come from the unchecked public `CharsetEncoder.encode` (`Encoding.kt` `CharsetEncoder.encodeArrayImpl`); `toIndex > input.size` reads past the end. |
| `core/internal/CharArraySequence.kt` | `CharArraySequence.get` | `index >= 0` | C | As above with `fromIndex = -1`. |
| `core/internal/CharArraySequence.kt` | `CharArraySequence.get` | `+` | C | As above with `fromIndex` near `Int.MAX_VALUE`. |
| `core/internal/CharArraySequence.kt` | `CharArraySequence.toString` | `+` | B (unstated) | Construction keeps `offset + length == toIndex`, and `subSequence` stays inside it. |
| `core/internal/Numbers.kt` | `Long.toIntOrFail` | `toInt` | C | The `@PublishedApi` `Long.toIntOrFail` rejects only values at or above `Int.MAX_VALUE`; `-2^32` wraps to 0. It has no callers in Ktor. |
| `core/Memory.kt` | `ByteArray.storeIntAt` | `index < size` | C | The public `ByteArray.storeIntAt(index, value)` needs `0 <= index <= size - 4`, which is not documented. |
| `core/Memory.kt` | `ByteArray.storeIntAt` | `index >= 0` | C | As above. |
| `core/Memory.kt` | `ByteArray.storeIntAt` | `index < size` (3) | C | As above. |
| `core/Strings.kt` | `Source.readUtf8ExactCharacters` | `toChar` | B (unstated) | kotlinx-io code points are at most 0x10FFFF, so the high surrogate stays at most 0xDBFF. |
| `core/Strings.kt` | `Sink.writeText` | `-` | B (unstated) | `concatToString(fromIndex, toIndex)` on the line before already enforces the range. |
| `core/Strings.kt` | `String` | `+` | C | The deprecated public `String(bytes, offset, length, charset)`: same range overflow as `ByteChannelCtor.kt` `ByteReadChannel`. |
| `CountedByteReadChannel.kt` | `CountedByteReadChannel.transferFromDelegate` | `+ (Long)` | B (unstated) | Counts bytes actually appended. |
| `CountedByteReadChannel.kt` | `CountedByteReadChannel.updateConsumed` | `-` | B (unstated) | `initial >= buffer.size >= 0`. |
| `CountedByteReadChannel.kt` | `CountedByteReadChannel.updateConsumed` | `+ (Long)` | B (unstated) | Running total of bytes actually consumed. |
| `CountedByteWriteChannel.kt` | `CountedByteWriteChannel.totalBytesWritten` | `+` | D1 | `flushedCount` is an `Int`, so after 2^31 bytes through one counted channel `totalBytesWritten` is wrong. |
| `CountedByteWriteChannel.kt` | `CountedByteWriteChannel.totalBytesWritten` | `-` | B (unstated) | `initial <= writeBuffer.size`, reset after each flush. |
| `CountedByteWriteChannel.kt` | `CountedByteWriteChannel.flush` | `+` | D1 | As `CountedByteWriteChannel.totalBytesWritten` (`flushedCount += writeBuffer.size`). |
| `jvm/.../core/ByteReadPacketExtensions.jvm.kt` | `Source.readAvailable` | `-` | B (unstated) | `ByteBuffer.remaining()` only decreases. |
| `jvm/.../jvm/javaio/Blocking.kt` | `ByteChannelOutputStream.write` | `toByte` | A | `OutputStream.write(int)` keeps the low 8 bits by contract. |
| `jvm/.../jvm/javaio/Blocking.kt` | `ByteChannelOutputStream.write` | `+` | C | `OutputStream.write(b, off, len)` with `off = 1, len = Int.MAX_VALUE` wraps; the `java.io` contract states the bound but the override does not check it. |
| `jvm/.../jvm/javaio/Blocking.kt` | `ByteChannelInputStream.read` | `+` | C | `InputStream.read(b, off, len)` with `off = Int.MAX_VALUE, len = 1`; as `ByteChannelOutputStream.write`. |
| `jvm/.../pool/DefaultPool.kt` | `DefaultPool.popTop` | `toInt` | A | Low 32 bits of the packed version/index `Long`. |
| `jvm/.../pool/DefaultPool.kt` | `DefaultPool.popTop` | `index < size` | B (unstated) | `top` holds only indices from `tryPush`, in `[1, maxIndex]`; `next` has `maxIndex + 1` entries. |
| `jvm/.../pool/DefaultPool.kt` | `MAGIC` | `toInt` | A | `MAGIC` is the golden-ratio constant reinterpreted as a signed `Int`. |

### ktor-network

Paths under `ktor-network/jvm/src/io/ktor/network/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `selector/InterestSuspensionsMap.kt` | `InterestSuspensionsMap.removeSuspension` | `index < size` | C | The public `removeSuspension(interestOrdinal: Int)` indexes `updaters` with its parameter, which the KDoc calls an ordinal of `SelectInterest` without stating the bound `0 until SelectInterest.AllInterests.size`. |
| `selector/InterestSuspensionsMap.kt` | `InterestSuspensionsMap.removeSuspension` | `index >= 0` | C | As above, for the lower bound. |
| `selector/InterestSuspensionsMap.kt` | `InterestSuspensionsMap.updater` | `index < size` | B (unstated) | `updaters` is built from `SelectInterest.AllInterests`, which is `entries`, so every `interest.ordinal` is below its size; the code does not state this. |
| `selector/InterestSuspensionsMap.kt` | `InterestSuspensionsMap.updater` | `index >= 0` | B (unstated) | As above: an enum ordinal is not negative. |
| `selector/LockFreeMPSCQueue.kt` | `LockFreeMPSCQueueCore.mask` | `-` | B (unstated) | The private core is constructed only with `INITIAL_CAPACITY` (8) or a doubled capacity capped by `MAX_CAPACITY_MASK`, so `capacity - 1` cannot wrap; the constructor does not state `capacity >= 1`. |
| `selector/SelectorManagerSupport.kt` | `SelectorManagerSupport.applyInterest` | `+` | B (unstated) | `pending` is reset to the key count at every select and gains one per applied interest update in between, far fewer than 2^31; the code does not state a bound. |

### ktor-network-tls

Paths under `ktor-network/ktor-network-tls/jvm/src/io/ktor/network/tls/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `cipher/CBCCipher.kt` | `CBCCipher.validateMac` | `toShort` | B (unstated) | `readTLSRecord` rejects records over `MAX_TLS_FRAME_SIZE` (0x4800), so `macOffset` fits a Short. |
| `cipher/CBCCipher.kt` | `CBCCipher.validateMac` | `++ (Long)` | B (unstated) | Per-record sequence counter from 0; one increment per received record. |
| `cipher/CBCCipher.kt` | `CBCCipher.validateMac` | `+` | B (unstated) | `macOffset + 20 <= content.size - 1`; the MAC length comes from a built-in suite. |
| `cipher/CBCCipher.kt` | `CBCCipher.prepareMac` | `toShort` | B (unstated) | Encrypted outgoing records are at most one pool buffer (~4 KiB), Finished, or the close alert. |
| `cipher/CBCCipher.kt` | `CBCCipher.prepareMac` | `+ (Long)` | B (unstated) | Outgoing sequence counter from 0; one increment per record. |
| `cipher/CBCCipher.kt` | `CBCCipher.Sink.writePadding` | `%` | B (unstated) | CBC is used only for the built-in `AES/CBC/NoPadding` suites, block size 16. |
| `cipher/CBCCipher.kt` | `CBCCipher.Sink.writePadding` | `+` | B (unstated) | The buffer holds one small outgoing record. |
| `cipher/CBCCipher.kt` | `CBCCipher.Sink.writePadding` | `toByte` | B (unstated) | `16 - (x % 16)` is 1..16. |
| `cipher/CBCCipher.kt` | `CBCCipher.Sink.writePadding` | `-` | B (unstated) | `16 - (0..15)`. |
| `cipher/CBCCipher.kt` | `CBCCipher.validatePadding` | `index >= 0` | B (unstated) | The only caller already indexed `content[content.size - 1]` at `CBCCipher.decrypt`, so `content` is non-empty. |
| `cipher/Cipher.kt` | `ByteArray.set` | `+` | B (unstated) | Callers pass offset 0 or `fixedIvLength` (4); `idx` is 0..7. |
| `cipher/Cipher.kt` | `ByteArray.set` | `index < size` | B (unstated) | Callers write into 13-byte headers at 0 or a 12-byte IV at 4. |
| `cipher/Cipher.kt` | `ByteArray.set` | `index >= 0` | B (unstated) | `idx` is 0..7 and offsets are non-negative. |
| `cipher/Cipher.kt` | `ByteArray.set` | `+` | B (unstated) | The only callers pass offset 11. |
| `cipher/Cipher.kt` | `ByteArray.set` | `index < size` | B (unstated) | `11 + idx <= 12` inside 13-byte headers. |
| `cipher/Cipher.kt` | `ByteArray.set` | `index >= 0` | B (unstated) | Offset 11, `idx` 0..1. |
| `cipher/GCMCipher.kt` | `gcmEncryptCipher` | `*` | B (unstated) | `cipherTagSizeInBytes` is 16 for every built-in GCM suite. |
| `cipher/GCMCipher.kt` | `gcmEncryptCipher` | `toShort` | B (unstated) | Only reached from `encrypt()`, whose records are small (see `CBCCipher.kt` `CBCCipher.prepareMac`). |
| `Keys.kt` | `ByteArray.clientIV` | `*` | B (unstated) | The negotiated suite is always a built-in `SupportedSuites` entry, so sizes are small constants. |
| `Keys.kt` | `ByteArray.clientIV` | `*` | B (unstated) | Built-in suite constant. |
| `Keys.kt` | `ByteArray.clientIV` | `+` (2) | B (unstated) | Sum of built-in suite constants. |
| `Keys.kt` | `ByteArray.serverIV` | `*` (2) | B (unstated) | Built-in suite constant. |
| `Keys.kt` | `ByteArray.serverIV` | `+` (2) | B (unstated) | Sum of built-in suite constants. |
| `Keys.kt` | `ByteArray.serverIV` | `*` | B (unstated) | Built-in suite constant. |
| `Keys.kt` | `keyMaterial` | `*` | B (unstated) | The only caller passes the negotiated built-in suite's sizes. |
| `Keys.kt` | `keyMaterial` | `+` | B (unstated) | As above. |
| `Keys.kt` | `keyMaterial` | `*` (2) | B (unstated) | As above. |
| `Parser.kt` | `Source.readECPoint` | `+` | B (unstated) | The only caller passes `NamedCurve.fieldSize` (at most 571). |
| `Render.kt` | `Sink.writeEncryptedPreMasterSecret` | `toShort` | A | Guarded by `size <= 0xffff`; packs an unsigned 16-bit length. |
| `Render.kt` | `ByteWriteChannel.writeRecord` | `toByte` | B (unstated) | `TLSRecordType` codes are 0x14..0x17. |
| `Render.kt` | `ByteWriteChannel.writeRecord` | `toByte` | B (unstated) | `TLSVersion` codes are 0x0300..0x0303, so `shr 8` is 3. |
| `Render.kt` | `Sink.writeAligned` | `+` | B (unstated) | `fieldSize` is a `NamedCurve` value or the JDK key's field size (at most 571). |
| `Render.kt` | `Sink.writeAligned` | `-` | B (unstated) | `expectedSize <= 72` and `src.size - index` is small. |
| `Render.kt` | `ByteWriteChannel.writeRecord` | `toByte` | A | Low byte of the 16-bit version code. |
| `Render.kt` | `Sink.writeTripleByteLength` | `toByte` | A | Byte packing of a 24-bit length. |
| `Render.kt` | `Sink.writeTripleByteLength` | `toShort` | A | Unsigned 16-bit packing. |
| `TLSAlert.kt` | `TLSAlertLevel.byCode` | `index < size` | B (range) | `in 0..255` guard on a 256-entry array; the census plugin build ignores `in`-range guards. |
| `TLSAlert.kt` | `TLSAlertLevel.byCode` | `index >= 0` | B (range) | As above. |
| `TLSAlert.kt` | `TLSAlertType.byCode` | `index < size` | B (range) | As above. |
| `TLSAlert.kt` | `TLSAlertType.byCode` | `index >= 0` | B (range) | As above. |
| `TLSHandshakeType.kt` | `TLSHandshakeType.byCode` | `index < size` | B (range) | `in 0..0xff` guard on a 256-entry array. |
| `TLSHandshakeType.kt` | `TLSHandshakeType.byCode` | `index >= 0` | B (range) | As above. |
| `TLSHandshakeType.kt` | `ServerKeyExchangeType.byCode` | `index < size` | B (range) | `code in 0..0xff` guard on a 256-entry array. |
| `TLSHandshakeType.kt` | `ServerKeyExchangeType.byCode` | `index >= 0` | B (range) | As above. |
| `TLSRecordType.kt` | `TLSRecordType.byCode` | `index < size` | B (range) | `in 0..255` guard on a 256-entry array. |
| `TLSRecordType.kt` | `TLSRecordType.byCode` | `index >= 0` | B (range) | As above. |
| `TLSVersion.kt` | `TLSVersion.byCode` | `-` | B (range) | `in 0x0300..0x0303` guard makes the result 0..3. |

### ktor-network-tls-certificates

Paths under `ktor-network/ktor-network-tls/ktor-network-tls-certificates/jvm/src/io/ktor/network/tls/certificates/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `Certificates.kt` | `Sink.writeDerBitString` | `+` | B (unstated) | JVM arrays have fewer than `Int.MAX_VALUE` elements. |
| `Certificates.kt` | `Sink.writeDerBitString` | `toByte` | B (range) | Guarded by `require(unused in 0..7)`; the census plugin build ignores `in`-range guards. |
| `Certificates.kt` | `Sink.writeDerLength` | `toByte` | A | DER long-form length prefix `0x81` wraps to a negative byte on purpose. |
| `Certificates.kt` | `Sink.writeDerLength` | `toByte` | A | DER prefix `0x82`, as the `toByte` row of this declaration. |
| `Certificates.kt` | `Sink.writeDerLength` | `toByte` | A | DER prefix `0x83`, as the `toByte` row of this declaration. |
| `Certificates.kt` | `Sink.writeDerLength` | `toByte` | A | DER prefix `0x84`, as the `toByte` row of this declaration. |
| `Certificates.kt` | `Sink.writeDerInt` | `*` | B (unstated) | `derLength()` returns 0..5 for a non-negative `Int` (see [B: invariants as contracts](#b-invariants-as-contracts)). |
| `Certificates.kt` | `Sink.writeDerInt` | `toByte` | A | `part or 0x80` is 128..255 and wraps to a negative byte on purpose. |

### ktor-openapi-schema

Paths under `ktor-shared/ktor-openapi-schema/common/src/io/ktor/openapi/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `GenericElement.kt` | `GenericElementMapDecoder.getCurrentElement` | `-` | B (unstated) | `index` starts at 0 and only grows, so `index - 1` cannot wrap; the field carries no stated invariant. |
| `GenericElement.kt` | `GenericElementMapDecoder.decodeElementIndex` | `*` | D1 | A map element with 2^30 or more entries makes `entries.size * 2` overflow; nothing limits the entry count, and reaching it needs tens of GB of entries (by reading). |
| `GenericElement.kt` | `GenericElementMapDecoder.decodeElementIndex` | `+` | B (field) | `index++` runs only after `index < entries.size * 2`, so it stays below `Int.MAX_VALUE`; SnaKt does not carry the `var` field's checked value to the increment. |
| `GenericElement.kt` | `GenericElementMapDecoder.decodeCollectionSize` | `*` | D1 | Same defect as `GenericElementMapDecoder.decodeElementIndex` in `decodeCollectionSize` (by reading). |
| `GenericElement.kt` | `GenericElementListDecoder.decodeElementIndex` | `+` | B (field) | `index++` runs only after `index < elements.size`; SnaKt does not carry the `var` field's checked value to the increment. |
| `GenericElement.kt` | `GenericElementListDecoder.getCurrentElement` | `-` | B (unstated) | `index` starts at 0 and only grows, so `index - 1` cannot wrap; the field carries no stated invariant. |

### ktor-resources

Paths under `ktor-shared/ktor-resources/common/src/io/ktor/resources/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `serialization/Decoders.kt` | `ListLikeDecoder.decodeElementIndex` | `++` | B (unstated) | kotlinx.serialization stops calling `decodeElementIndex` after `DECODE_DONE`, so `currentIndex` never passes `elementsCount`, a list size. |

### ktor-server-auth

Paths under `ktor-server/ktor-server-plugins/ktor-server-auth/jvm/src/io/ktor/server/auth/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `*` | A | `hashCode` combines field hashes with `31 * result`. |
| `DigestCredential.kt` | `DigestCredential.hashCode` | `+` | A | `hashCode` combines field hashes with `31 * result`. |

### ktor-server-call-id

Paths under `ktor-server/ktor-server-plugins/ktor-server-call-id/common/src/io/ktor/server/plugins/callid/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `CallId.kt` | `Random.nextString` | `index < size` | B (unstated) | `Random.nextInt(dictionarySize)` returns a value in `0 until dictionarySize`, a library postcondition SnaKt does not know. |
| `CallId.kt` | `Random.nextString` | `index >= 0` | B (unstated) | As above, for the lower bound. |

### ktor-server-core

Paths under `ktor-server/ktor-server-core/common/src/io/ktor/server/` (`jvm/.../` is ktor-server/ktor-server-core/jvm/src/io/ktor/server/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `config/MapConfigDecoder.kt` | `SubMapConfigDecoder.currentKeyIndex` | `-` | B (unstated) | `index` starts at 0 and is only incremented, so `index - 1` cannot underflow. |
| `config/MapConfigDecoder.kt` | `SubMapConfigDecoder.currentKeyIndex` | `-` | B (unstated) | `index` starts at 0 and is only incremented, so `index - 2` cannot underflow. |
| `config/MapConfigDecoder.kt` | `SubMapConfigDecoder.decodeElementIndex` | `*` | D1 | `SubMapConfigDecoder` computes `keys.size * 2`, which overflows for a config map with 2^30 or more distinct keys under one path. |
| `config/MapConfigDecoder.kt` | `SubMapConfigDecoder.decodeElementIndex` | `+` | B (field) | `index++` runs only after `index < keys.size * 2`; SnaKt re-reads the `var` field after the check. |
| `config/MapConfigDecoder.kt` | `SubMapConfigDecoder.decodeString` | `-` | B (unstated) | `index` starts at 0 and is only incremented, so `index - 1` cannot underflow. |
| `config/MapConfigDecoder.kt` | `SubMapConfigDecoder.decodeString` | `-` | B (unstated) | `index` starts at 0 and is only incremented, so `index - 2` cannot underflow. |
| `config/MapConfigDecoder.kt` | `MapConfigDecoder.decodeElementIndex` | `+` | B (field) | `elementIndex++` runs only after `elementIndex < descriptor.elementsCount`; SnaKt re-reads the `var` field after the check. |
| `config/MapConfigDecoder.kt` | `ListMapConfigDecoder.decodeElementIndex` | `+` | B (field) | `index++` runs only after `index < size`; SnaKt re-reads the `var` field after the check. |
| `config/MapConfigDecoder.kt` | `ListMapConfigDecoder.decodeString` | `-` | B (unstated) | `index` starts at 0 and is only incremented, so `index - 1` cannot underflow. |
| `config/MapConfigDecoder.kt` | `ListMapConfigDecoder.beginStructure` | `-` | B (unstated) | `index` starts at 0 and is only incremented, so `index - 1` cannot underflow. |
| `engine/Long.kt` | `Long.toStringFast` | `index < size` | B (range) | The guard `this in 0..1023` keeps the index below the 1024-entry table. |
| `engine/Long.kt` | `Long.toStringFast` | `index >= 0` | B (range) | The guard `this in 0..1023` keeps the index non-negative. |
| `engine/Long.kt` | `Long.toStringFast` | `toInt` | B (range) | The guard `this in 0..1023` keeps the value within `Int`. |
| `response/ResponseCookies.kt` | `ResponseCookies.append` | `toInt` | C | `append(maxAge = …)` coerces only the upper bound, so a `maxAge` below `Int.MIN_VALUE` wraps in `toInt()`; the KDoc does not state that `maxAge` must not be negative. |
| `routing/RegexRouting.kt` | `PathSegmentRegexRouteSelector.countSegments` | `+` | B (unstated) | `count` counts characters of a string, so it is below `Int.MAX_VALUE` and adding 1 fits. |
| `routing/RoutingPathTree.kt` | `RoutingPathTree.captureTailcardSegments` | `+` | B (unstated) | `segEnd` is at most `end`, which the only caller passes as `path.length`, so adding 1 fits. |
| `routing/SegmentedPath.kt` | `SegmentedPath.forEachSegmentRange` | `+` | B (unstated) | `count` counts non-empty segments of `string`, at most its length. |
| `routing/SegmentedPath.kt` | `SegmentedPath.forEachSegmentRange` | `+` | B (unstated) | `next` is an `indexOf` result checked against -1, so it is below `string.length` and adding 1 fits. |
| `routing/SegmentedPath.kt` | `SegmentedPath.size` | `+` | B (unstated) | `count` counts segments of `string`, at most its length plus 1. |
| `jvm/.../config/HoconDecoder.kt` | `HoconListDecoder.decodeDouble` | `-` | B (unstated) | `elementIndex` starts at 0 and is only incremented, so `elementIndex - 1` cannot underflow. |
| `jvm/.../config/HoconDecoder.kt` | `HoconMapDecoder.decodeElementIndex` | `*` | D1 | `HoconMapDecoder` computes `keys.size * 2`, which overflows for a HOCON object with 2^30 or more keys. |
| `jvm/.../config/HoconDecoder.kt` | `HoconMapDecoder.decodeElementIndex` | `+` | B (field) | `elementIndex++` runs only after `elementIndex < keys.size * 2`; SnaKt re-reads the `var` field after the check. |
| `jvm/.../config/HoconDecoder.kt` | `HoconDecoder.decodeElementIndex` | `+` | B (field) | `elementIndex++` runs only after `elementIndex < descriptor.elementsCount`; SnaKt re-reads the `var` field after the check. |
| `jvm/.../config/HoconDecoder.kt` | `HoconDecoder.decodeByte` | `toByte` | D1 | `decodeByte` narrows the configured `Int` with `toByte()` without a range check, so a HOCON value outside -128..127 for a `Byte` property wraps silently. |
| `jvm/.../config/HoconDecoder.kt` | `HoconDecoder.decodeShort` | `toShort` | D1 | `decodeShort` narrows the configured `Int` with `toShort()` without a range check, so a HOCON value outside the `Short` range for a `Short` property wraps silently. |
| `jvm/.../config/HoconDecoder.kt` | `HoconListDecoder.currentElement` | `-` | B (unstated) | `elementIndex` starts at 0 and is only incremented, so `elementIndex - 1` cannot underflow. |
| `jvm/.../config/HoconDecoder.kt` | `HoconListDecoder.decodeElementIndex` | `+` | B (field) | `elementIndex++` runs only after `elementIndex < list.size`; SnaKt re-reads the `var` field after the check. |

### ktor-server-cors

Paths under `ktor-server/ktor-server-plugins/ktor-server-cors/common/src/io/ktor/server/plugins/cors/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `CORSUtils.kt` | `findPortDigitStartIndex` | `+` | B (unstated) | `portSeparatorIndex` is an `indexOf` result checked against -1, so it is below `origin.length` and adding 1 fits. |

### ktor-server-di

Paths under `ktor-server/ktor-server-plugins/ktor-server-di/common/src/io/ktor/server/plugins/di/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `DependencyInitializer.kt` | `DependencyInitializer.Implicit.hashCode` | `*` | A | `Implicit.hashCode` with `31 * result`. |
| `DependencyInitializer.kt` | `DependencyInitializer.Implicit.hashCode` | `+` | A | `Implicit.hashCode` with `31 * result`. |
| `DependencyKeyCovariance.kt` | `DependencyKeyCovarianceParser.skipWhitespace` | `++` | B (field) | `position < text.length` is checked just before; SnaKt re-reads the `var` field after the check. |

### ktor-server-netty

Paths under `ktor-server/ktor-server-netty/jvm/src/io/ktor/server/netty/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `cio/NettyHttpResponsePipeline.kt` | `NettyHttpResponsePipeline.respondWithSmallBody` | `+` | B (unstated) | The only caller passes `size` in 1..65536, and `start` is the writer index (0) of the freshly allocated buffer. |
| `http3/HmacQuicTokenHandler.kt` | `HmacQuicTokenHandler.maxTokenLength` | `+` | B (unstated) | Sum of the constants `TIMESTAMP_LENGTH` (8), `HMAC_LENGTH` (32) and Netty's `Quic.MAX_CONN_ID_LEN`. |

### ktor-server-partial-content

Paths under `ktor-server/ktor-server-plugins/ktor-server-partial-content/common/src/io/ktor/server/plugins/partialcontent/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `MultipleRangeWriter.kt` | `calculateHeadersLength` | `+` | B (unstated) | Sum of the lengths of the multipart boundary, the response content type, one `Content-Range` value and a fixed header part, all short strings. |
| `PartialOutgoingContent.kt` | `PartialOutgoingContent.Single.contentLength` | `+ (Long)` | B (unstated) | `Single` is built only by `processRange`, whose range comes from `RangesSpecifier.merge` or `mergeToSingle` with `0 <= first <= last < length`, so `last - first + 1 <= length`. |
| `PartialOutgoingContent.kt` | `PartialOutgoingContent.Single.contentLength` | `- (Long)` | B (unstated) | As the `+` row of this declaration; the merged range has `0 <= first <= last`. |

### ktor-server-rate-limit

Paths under `ktor-server/ktor-server-plugins/ktor-server-rate-limit/common/src/io/ktor/server/plugins/ratelimit/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `DefaultRateLimiter.kt` | `DefaultRateLimiter.tryConsume` | `-` | C | `current - tokens` overflows only for a negative `limit` or a negative weight from the public `requestWeight` block, and neither KDoc requires non-negative values. |
| `DefaultRateLimiter.kt` | `DefaultRateLimiter.tryConsume` | `+ (Long)` | D1 | `refillPeriod = Duration.INFINITE` (never refill) gives `inWholeMilliseconds == Long.MAX_VALUE`, so `lastRefillTimeMillis + Long.MAX_VALUE` wraps and `refillAtTimeMillis` is negative. |
| `DefaultRateLimiter.kt` | `DefaultRateLimiter.timeToWaitMillis` | `- (Long)` | C | The subtraction overflows only for a `refillPeriod` near `-Duration.INFINITE` or a `clock` jumping by about 2^63 ms; the public `rateLimiter(limit, refillPeriod, clock)` KDoc states neither bound. |
| `DefaultRateLimiter.kt` | `DefaultRateLimiter.timeToWaitMillis` | `- (Long)` | C | `clock() - lastRefillTimeMillis` takes both values from the public `clock` parameter, whose KDoc does not say it returns epoch milliseconds; values about 2^63 apart overflow. |
| `RateLimitConfig.kt` | `formatHeaderSeconds` | `+ (Long)` | D1 | `RateLimiter.Unlimited` reports `refillAtTimeMillis = Long.MAX_VALUE`, and the default `modifyResponse` passes it to `formatHeaderSeconds`, whose `milliseconds + 999` wraps, so `X-RateLimit-Reset` is negative. |

### ktor-server-sessions

Paths under `ktor-server/ktor-server-plugins/ktor-server-sessions/common/src/io/ktor/server/sessions/` (`jvm/.../` is ktor-server/ktor-server-plugins/ktor-server-sessions/jvm/src/io/ktor/server/sessions/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `serialization/ListLikeDecoder.kt` | `ListLikeDecoder.decodeElementIndex` | `+` | B (unstated) | `currentIndex` starts at -1 and stops at `items.size`, after which the decoder returns `DECODE_DONE` and is not called again. |
| `SessionTransportCookie.kt` | `SessionTransportCookie.send` | `* (Long)` | D1 | `CookieConfiguration.maxAge = Duration.INFINITE` sets `maxAgeInSeconds = Long.MAX_VALUE`, and `maxAge * 1000L` overflows when the session cookie's `expires` is computed. |
| `SessionTransportCookie.kt` | `SessionTransportCookie.send` | `toInt` | B (unstated) | The `maxAgeInSeconds` setter requires a non-negative value and `coerceAtMost(Int.MAX_VALUE)` bounds it above, so `toInt()` fits. |
| `jvm/.../CacheJvm.kt` | `KeyState.timeToWait` | `+ (Long)` | D1 | `CacheStorage(delegate, idleTimeout)` accepts any `Long`, and an `idleTimeout` such as `Long.MAX_VALUE` makes `lastAccess + timeout` wrap negative, so `timeToWait` is 0. |
| `jvm/.../CacheJvm.kt` | `KeyState.timeToWait` | `- (Long)` | C | Once the sum fits, subtracting the current time underflows only for an `idleTimeout` near `Long.MIN_VALUE`; the `CacheStorage` KDoc does not state that `idleTimeout` is non-negative. |

### ktor-server-status-pages

Paths under `ktor-server/ktor-server-plugins/ktor-server-status-pages/jvm/src/io/ktor/server/plugins/statuspages/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `StatusPagesUtilsJvm.kt` | `distance` | `++` | B (unstated) | `result` counts superclass steps from the exception class to a key it is an instance of (filtered at `StatusPages.kt`), so it is bounded by the class hierarchy depth. |

### ktor-server-test-base

Paths under `ktor-server/ktor-server-test-base/jvm/src/io/ktor/server/test/base/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.doWrite` | `--` | B (unstated) | `remaining` starts at 0 and only grows by a positive `qty` or is reset to 0, and this declaration returns when it is 0. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.findHttp` | `-` | B (unstated) | A `ByteBuffer` keeps `0 <= position <= limit`, so `limit - position` fits. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.findHttpSlow` | `-` | B (unstated) | `tokenSize` is at most 10 and `position` is a non-negative `ByteBuffer` position. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.findHttpSlow` | `index >= 0` | B (unstated) | `idx + offset` equals the running `tokenSize`, which is never negative. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.findHttpSlow` | `+` | B (unstated) | `idx + offset` equals the running `tokenSize`, which stays at most 10. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.findHttpSlow` | `++` | B (unstated) | `tokenSize` never exceeds 10: every increment that passes 8, 10 or 3 returns, and each new token resets it. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.skipSpaces` | `-` | B (unstated) | A `ByteBuffer` keeps `0 <= position <= limit`, so `limit - position` fits. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.skipSpacesSlow` | `++` | B (unstated) | As `HighLoadHttpGenerator.ClientState.findHttpSlow`; `tokenSize` is reset once it passes 10. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.skipSpacesSlow` | `-` | B (range) | `idx` ranges over `position until limit` of a `ByteBuffer`, so `0 < limit - idx <= limit`. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.parseCode` | `++` | B (unstated) | As `HighLoadHttpGenerator.ClientState.findHttpSlow`; `tokenSize` starts at 1 for a status code and the method returns once it passes 3. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.parseCode` | `*` | B (unstated) | The `++tokenSize > 3` check returns before a fourth digit, so `code` has at most three digits. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.parseCode` | `+` | B (unstated) | As the `*` row of this declaration; `code * 10 + n` is at most 999. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.gotStatus` | `index < size` | B (range) | `gotStatus` receives 200 or a code inside the `code in 100..999` guard at `HighLoadHttpGenerator.ClientState.parseCode`, and `codeCounts` has 1000 entries. |
| `HighLoadHttpGenerator.kt` | `HighLoadHttpGenerator.ClientState.gotStatus` | `index >= 0` | B (range) | As the `index < size` row of this declaration; the code is at least 100. |

### ktor-server-test-suites

Paths under `ktor-server/ktor-server-test-suites/jvm/src/io/ktor/server/testing/suites/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `Utils.kt` | `crcWithSize` | `+ (Long)` | B (unstated) | `count` grows by at most 65,536 per read of a test response, far below `Long.MAX_VALUE`. |
| `Utils.kt` | `BufferedReader.skipHttpResponseContent` | `toChar` | B (unstated) | `read()` returns -1 or a UTF-16 code unit, and `assertNotEquals(-1, ch)` throws on -1. |

### ktor-utils

Paths under `ktor-utils/common/src/io/ktor/util/` (`jvm/.../` is ktor-utils/jvm/src/io/ktor/util/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `Bytes.kt` | `ByteArray.readShort` | `index < size` | C | The `@InternalAPI` public `ByteArray.readShort(offset)` indexes with its parameter, and its KDoc does not require `0 <= offset <= size - 2`. |
| `Bytes.kt` | `ByteArray.readShort` | `index >= 0` | C | As the `index < size` row of this declaration, for the lower bound of `offset`. |
| `Bytes.kt` | `ByteArray.readShort` | `index < size` | C | As the `index < size` and `index >= 0` rows of this declaration; `offset + 1` is below `size` only if the caller keeps `offset <= size - 2`, which the KDoc does not state. |
| `Bytes.kt` | `ByteArray.readShort` | `toShort` | A | Packs two bytes into a 16-bit value of 0..65535 and wraps it to `Short` on purpose. |
| `CaseInsensitiveMap.kt` | `CaseInsensitiveMap.caseInsensitiveHashCode` | `*` | A | `caseInsensitiveHashCode` combines characters with `31 * h`. |
| `CaseInsensitiveMap.kt` | `CaseInsensitiveMap.caseInsensitiveHashCode` | `+` | A | `caseInsensitiveHashCode` combines characters with `31 * h`. |
| `Crypto.kt` | `hex` | `*` | D1 | The deprecated public `hex(bytes)` allocates `bytes.size * 2` characters, which is negative for an array of 2^30 bytes or more. |
| `Crypto.kt` | `hex` | `++` | B (unstated) | `resultIndex` advances by two per input byte, so it stays at most `result.size`. |
| `Crypto.kt` | `hex` | `index < size` | B (unstated) | `b` is masked to 0..255, so `b shr 4` is below 16, the size of the private `digits` table; neither range is stated. |
| `Crypto.kt` | `hex` | `index < size` | B (unstated) | `resultIndex` advances by two per input byte and `result` has `bytes.size * 2` slots. |
| `Crypto.kt` | `hex` | `index >= 0` | B (unstated) | `resultIndex` starts at 0 and is only incremented. |
| `Crypto.kt` | `hex` | `index < size` | B (unstated) | `b and 0x0f` is below 16, the size of the private `digits` table; neither range is stated. |
| `Crypto.kt` | `hex` | `index < size` | B (unstated) | As the `index < size` and `index >= 0` rows of this declaration; the second write of each byte lands at `2 * i + 1 < result.size`. |
| `date/Date.kt` | `GMTDate.plus` | `+ (Long)` | C | `GMTDate.plus(milliseconds)` needs `timestamp + milliseconds` to fit in a `Long`, and its KDoc does not bound the public `milliseconds` parameter. |
| `date/Date.kt` | `GMTDate.minus` | `- (Long)` | C | `GMTDate.minus(milliseconds)` needs `timestamp - milliseconds` to fit in a `Long`, and its KDoc does not bound the public `milliseconds` parameter. |
| `date/Date.kt` | `GMTDate.plus` | `+ (Long)` | D1 | `GMTDate.plus(Duration.INFINITE)` adds `Long.MAX_VALUE` milliseconds, which wraps for any date after the epoch. |
| `date/Date.kt` | `GMTDate.minus` | `- (Long)` | D1 | `GMTDate.minus(Duration.INFINITE)` subtracts `Long.MAX_VALUE` milliseconds, which wraps for any date before the epoch. |
| `date/GMTDateParser.kt` | `GMTDateParser.parse` | `+` | B (unstated) | `chunkStart` always equals `start`, so `chunkStart + index - start` is `index`, below `pattern.length`. |
| `date/GMTDateParser.kt` | `GMTDateParser.parse` | `-` | B (unstated) | `start` is an earlier value of `index`, so `index - start` is between 0 and `pattern.length`. |
| `HashFunction.kt` | `Sha256.update` | `+ (Long)` | B (unstated) | Each `Sha256` instance gets one `update` with one array, so `messageLength` is at most `Int.MAX_VALUE`. |
| `HashFunction.kt` | `Sha256.update` | `+` | B (unstated) | The only callers, `sha256` on posix and web, pass `offset = 0` and `length = input.size`. |
| `HashFunction.kt` | `Sha256.update` | `+` | B (unstated) | `unprocessedLimit` is below 64 and is 0 on the single `update` each instance gets, so this branch is not reached by the callers. |
| `HashFunction.kt` | `Sha256.update` | `+` | B (unstated) | `consumeByteCount <= length` here, so `pos + consumeByteCount <= offset + length`. |
| `HashFunction.kt` | `Sha256.update` | `+` | D1 | The public `sha256(bytes)` on posix and web steps `pos + 64` while `pos < limit`, which wraps for an array of 2^31 - 63 bytes or more. |
| `HashFunction.kt` | `Sha1.update` | `+ (Long)` | B (unstated) | Each `Sha1` instance gets one `update` with one array, so `messageLength` is at most `Int.MAX_VALUE`. |
| `HashFunction.kt` | `Sha1.update` | `+` | B (unstated) | The only callers, `sha1` on posix and web, pass `offset = 0` and `length = input.size`. |
| `HashFunction.kt` | `Sha1.update` | `+` | B (unstated) | `unprocessedLimit` is below 64 and is 0 on the single `update` each instance gets, so this branch is not reached by the callers. |
| `HashFunction.kt` | `Sha1.update` | `+` | B (unstated) | `consumeByteCount <= length` here, so `pos + consumeByteCount <= offset + length`. |
| `HashFunction.kt` | `Sha1.update` | `+` | D1 | The public `sha1(bytes)` on posix and web steps `pos + 64` while `pos < limit`, which wraps for an array of 2^31 - 63 bytes or more. |
| `pipeline/Pipeline.kt` | `Pipeline.insertPhaseAfter` | `+` | B (unstated) | `index` is a `findPhaseIndex` result other than -1, so it is below `phasesRaw.size`. |
| `pipeline/Pipeline.kt` | `Pipeline.insertPhaseAfter` | `+` | B (unstated) | `lastRelatedPhaseIndex` is `index` or a loop index, both below `phasesRaw.size`. |
| `pipeline/Pipeline.kt` | `Pipeline.intercept` | `++` | B (unstated) | `interceptorsQuantity` counts interceptors held in the phase lists; the code does not state a bound. |
| `pipeline/Pipeline.kt` | `Pipeline.intercept` | `++` | B (unstated) | As the `++` row of this declaration. |
| `pipeline/Pipeline.kt` | `Pipeline.fastPathMerge` | `+` | B (unstated) | Adds the interceptor count of another pipeline, also a count of held interceptors; the code does not state a bound. |
| `pipeline/SuspendFunctionGun.kt` | `SuspendFunctionGun.addContinuation` | `++` | B (unstated) | `addContinuation` runs once per `proceed` to the next interceptor and `suspensions` has `interceptors.size` slots, so the index stays below that size. |
| `pipeline/SuspendFunctionGun.kt` | `SuspendFunctionGun.addContinuation` | `index < size` | B (unstated) | As the `++` row of this declaration; at most `interceptors.size` continuations are saved at once. |
| `pipeline/SuspendFunctionGun.kt` | `SuspendFunctionGun.addContinuation` | `index >= 0` | B (unstated) | `lastSuspensionIndex` is at least -1 and is pre-incremented here. |
| `reflect/Type.kt` | `TypeInfo.hashCode` | `*` | A | `TypeInfo.hashCode` with `31 * hash`. |
| `reflect/Type.kt` | `TypeInfo.hashCode` | `+` | A | `TypeInfo.hashCode` with `31 * hash`. |
| `StringValues.kt` | `StringValuesSingleImpl.hashCode` | `*` | A | `hashCode` seeds with `31 * caseInsensitiveName.hashCode()`. |
| `StringValues.kt` | `StringValuesImpl.forEach` | `index < size` | B (unstated) | `i < entryCount`, and the constructor allocates `keyStorage` with `entryCount` slots; the class does not state this. |
| `StringValues.kt` | `StringValuesImpl.forEach` | `index < size` | B (unstated) | `i < entryCount`, and the constructor allocates `valueStorage` with `entryCount` slots; the class does not state this. |
| `StringValues.kt` | `StringValuesImpl.hashCode` | `*` | A | `hashCode` seeds with `31 * caseInsensitiveName.hashCode()`. |
| `StringValues.kt` | `StringValuesImpl.caseInsensitiveHashCode` | `*` | A | `caseInsensitiveHashCode` combines characters with `31 * h`. |
| `StringValues.kt` | `StringValuesImpl.caseInsensitiveHashCode` | `+` | A | `caseInsensitiveHashCode` combines characters with `31 * h`. |
| `StringValues.kt` | `entriesHashCode` | `*` | A | `entriesHashCode` combines with `seed * 31`. |
| `StringValues.kt` | `entriesHashCode` | `+` | A | `entriesHashCode` combines with `seed * 31`. |
| `Text.kt` | `toUpperCasePreservingASCII` | `Char -` | B (range) | The guard `ch in 'a'..'z'` keeps `ch - 32` within `Char`. |
| `Text.kt` | `CaseInsensitiveString.init` | `*` | A | `CaseInsensitiveString` hash code with `temp * 31`. |
| `Text.kt` | `CaseInsensitiveString.init` | `+` | A | `CaseInsensitiveString` hash code with `temp * 31`. |
| `Text.kt` | `String.chomp` | `+` | B (unstated) | `idx` is an `indexOf(separator)` result other than -1, so `idx + separator.length` is at most `length`. |
| `Text.kt` | `toLowerCasePreservingASCII` | `Char +` | B (range) | The guard `ch in 'A'..'Z'` keeps `ch + 32` within `Char`. |
| `jvm/.../CryptoJvm.kt` | `tryGenerateNonceFromChannel` | `-` | B (unstated) | `builder.length` is never negative and the builder never exceeds `length`, so `length - builder.length` cannot wrap. |
| `jvm/.../CryptoJvm.kt` | `generateNonceLong` | `-` | B (unstated) | The loop guard `length > acc.length` and a non-negative `acc.length` keep the difference in range. |
| `jvm/.../date/DateJvm.kt` | `Calendar.toDate` | `+` | B (unstated) | `ZONE_OFFSET` and `DST_OFFSET` are bounded by `Calendar` to a few hours in milliseconds. |
| `jvm/.../date/DateJvm.kt` | `Calendar.toDate` | `+` | B (unstated) | `DAY_OF_WEEK` is 1..7 by the `Calendar` contract. |
| `jvm/.../date/DateJvm.kt` | `Calendar.toDate` | `+ (Long)` | C | The public `Calendar.toDate(timestamp)` adds the zone offset to `timeInMillis`, which needs `timestamp` at least the offset away from the `Long` limits; the KDoc does not state this. |
| `jvm/.../date/DateJvm.kt` | `timestampToGMTDate` | `toInt` | B (unstated) | `secondOfDay` is a `floorMod` by 86400, so `secondOfDay / 3600` is 0..23. |
| `jvm/.../date/DateJvm.kt` | `timestampToGMTDate` | `toInt` | D1 | `GMTDate(timestamp)` truncates the day count to `Int`, so a timestamp beyond about 1.86e17 ms, `Long.MAX_VALUE` included, gives a wrong date. |
| `jvm/.../date/DateJvm.kt` | `timestampToGMTDate` | `+` | D1 | As the `toInt` row of this declaration; `days + 719468` wraps for day counts within 719468 of `Int.MAX_VALUE`, so the same timestamps give a wrong date. |
| `jvm/.../date/DateJvm.kt` | `timestampToGMTDate` | `+` | B (unstated) | `dayOfMonth` is 1..31 and the month table holds at most 335. |
| `jvm/.../date/DateJvm.kt` | `timestampToGMTDate` | `index < size` | B (unstated) | `monthIndex` is 0..11 by the civil-from-days algorithm (`mp` is 0..11); the code does not state it. |
| `jvm/.../Deflater.kt` | `GZIP_MAGIC` | `toShort` | A | `0x8b1f.toShort()` wraps the gzip magic to a `Short` on purpose. |
| `jvm/.../Deflater.kt` | `Deflater.deflateTo` | `+` | B (unstated) | `arrayOffset() + position()` is within the backing array by the `ByteBuffer` contract. |
| `jvm/.../Deflater.kt` | `Deflater.deflateTo` | `+` | B (unstated) | `written` is at most `remaining()`, so the new position is at most `limit()`. |
| `jvm/.../Deflater.kt` | `ByteWriteChannel.putGzipTrailer` | `toInt` | A | Takes the low 32 bits of the CRC32 value, which is unsigned 32-bit, on purpose. |
| `jvm/.../NIO.kt` | `ByteBuffer.moveTo` | `+` | B (unstated) | `size` is at most `remaining()`, so `position() + size` is at most `limit()`. |
| `jvm/.../Nonce.kt` | `generateNonceSynchronously` | `-` | B (unstated) | The loop guard `acc.length < length` and a non-negative `acc.length` keep the difference in range. |

### ktor-websockets

Paths under `ktor-shared/ktor-websockets/common/src/io/ktor/websocket/` (`jvm/.../` is ktor-shared/ktor-websockets/jvm/src/io/ktor/websocket/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `ControlFrames.kt` | `String.utf8Size` | `+` | D1 | `CloseReason(code, message)` counts UTF-8 bytes in an `Int`, so a message of 715,827,883 to 1,431,655,765 three-byte characters wraps `size` negative and passes the 123-byte check its KDoc promises. |
| `ControlFrames.kt` | `String.utf8Size` | `+` | B (unstated) | `index` is below `length`, and it advances by 2 only when `index + 1 < length`. |
| `ControlFrames.kt` | `String.utf8Truncate` | `+` | B (unstated) | `size` stays at most `maxSize` (123 from both callers) and `bytes` is at most 4. |
| `ControlFrames.kt` | `String.utf8Truncate` | `+` | B (unstated) | As `String.utf8Size`; `index` advances by 2 only when `index + 1 < length`. |
| `FrameType.kt` | `FrameType.get` | `index < size` | B (range) | The guard `opcode in 0..maxOpcode` keeps the index inside `byOpcodeArray`, whose size is `maxOpcode + 1`. |
| `FrameType.kt` | `FrameType.get` | `index >= 0` | B (range) | The guard `opcode in 0..maxOpcode` keeps the index non-negative. |
| `jvm/.../internals/BytePacketUtils.kt` | `Source.endsWith` | `- (Long)` | B (unstated) | `remaining` and `data.size` are both non-negative, so the difference fits in `Long`. |
| `jvm/.../internals/DeflaterUtils.kt` | `Sink.deflateTo` | `+` | B (unstated) | `buffer.clear()` sets the position to 0, and `deflate` returns at most `buffer.limit()`. |
| `jvm/.../Serializer.kt` | `Serializer.estimateFrameHeaderSize` | `+` | B (unstated) | The header size is 2, 4 or 10 and `maskSize` is 0 or 4. |
| `jvm/.../SimpleFrameCollector.kt` | `SimpleFrameCollector.handle` | `-` | B (unstated) | `moveTo` returns `min(remaining, …)`, so subtracting it keeps `remaining` between 0 and its old value. |

## String bounds

SnaKt models `s[i]` as `stringGet` and `substring` as `substringModel`. A row
here is a `VIPER_VERIFICATION_ERROR` whose message is "Precondition of function
stringGet might not hold" or "Precondition of function substringModel might not
hold", minus rows whose assertion is `isSubtype(typeOf(..), ..)`, which are
SnaKt's own type obligations:

```sh
grep -h 'VIPER_VERIFICATION_ERROR' LOG_DIR/*.log \
    | grep -E 'stringGet|substringModel' | grep -v isSubtype | sort -u
```

The classes are those above. The C and D1 rows also appear in the C and D1
tables. The D2 rows are counted and not listed.

| Module | A | B | C | D1 | D2 | Total |
|:--|--:|--:|--:|--:|--:|--:|
| ktor-client-logging | 0 | 1 | 2 | 0 | 0 | 3 |
| ktor-http | 0 | 44 | 1 | 0 | 0 | 45 |
| ktor-io | 0 | 0 | 3 | 0 | 0 | 3 |
| ktor-resources | 0 | 0 | 0 | 0 | 2 | 2 |
| ktor-server-core | 0 | 7 | 0 | 1 | 0 | 8 |
| ktor-server-cors | 0 | 1 | 0 | 0 | 0 | 1 |
| ktor-server-di | 0 | 6 | 0 | 0 | 0 | 6 |
| ktor-server-sessions | 0 | 0 | 0 | 0 | 2 | 2 |
| ktor-utils | 0 | 9 | 0 | 0 | 1 | 10 |
| ktor-websockets | 0 | 4 | 0 | 0 | 0 | 4 |
| **Total** | **0** | **72** | **6** | **1** | **5** | **84** |

The 72 B rows by cause: 2 `range`, 2 `field`, 68 `unstated`.

### String-bound rows

#### ktor-client-logging

Paths under `ktor-client/ktor-client-plugins/ktor-client-logging/common/src/io/ktor/client/plugins/logging/` (`jvm/.../` is ktor-client/ktor-client-plugins/ktor-client-logging/jvm/src/io/ktor/client/plugins/logging/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `jvm/.../LoggerJvm.kt` | `MessageLengthLimitingLogger.logLong` | `substringModel start <= end` | C | `message.substring(0, maxLength)` needs `maxLength >= 0`; the `MessageLengthLimitingLogger(maxLength)` constructor accepts any `Int` and its KDoc does not require a non-negative value. |
| `jvm/.../LoggerJvm.kt` | `MessageLengthLimitingLogger.logLong` | `substringModel end <= len` | B (unstated) | `lastIndex` is a `lastIndexOf` result of `msgSubstring`, so it is below its length. |
| `jvm/.../LoggerJvm.kt` | `MessageLengthLimitingLogger.logLong` | `substringModel start <= end` | C | `lastIndex >= minLength` excludes -1 only when `minLength >= 0`; the constructor accepts any `minLength` and its KDoc does not require a non-negative value. |

#### ktor-http

Paths under `ktor-http/common/src/io/ktor/http/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `auth/HttpAuthHeader.kt` | `nextChallengeIndex` | `stringGet i >= 0` | B (unstated) | Both callers pass an index from `skipSpaces`/`matchToken68`, which start at a non-negative index and only increase. |
| `auth/HttpAuthHeader.kt` | `nextChallengeIndex` | `stringGet i < len` | B (unstated) | `skipSpaces` and `matchToken68` return at most `headerValue.length`, and `index == headerValue.length` is excluded first. |
| `auth/HttpAuthHeader.kt` | `matchToken68` | `stringGet i >= 0` | B (unstated) | `index` comes from `skipSpaces(startIndex)` with a non-negative `startIndex` from every caller and only increases. |
| `auth/HttpAuthHeader.kt` | `matchToken68` | `stringGet i >= 0` | B (unstated) | As the `stringGet i >= 0` row of this declaration; `index` only increases from a non-negative start. |
| `auth/HttpAuthHeader.kt` | `String.skipDelimiter` | `stringGet i >= 0` | B (unstated) | `skipDelimiter` is called with a `matchParameter` result, which is at least its non-negative `startIndex`; `skipSpaces` only increases it. |
| `auth/HttpAuthHeader.kt` | `String.skipDelimiter` | `stringGet i < len` | B (unstated) | `matchParameter` returns at most `headerValue.length`, `skipSpaces` keeps it there, and `index == length` returns first. |
| `auth/HttpAuthHeader.kt` | `String.skipSpaces` | `stringGet i >= 0` | B (unstated) | Every `skipSpaces` caller passes a non-negative start index. |
| `CookieUtils.kt` | `StringLexer.test` | `stringGet i >= 0` | B (unstated) | `StringLexer.index` starts at 0 and is only incremented. |
| `CookieUtils.kt` | `StringLexer.test` | `stringGet i < len` | B (field) | `index < source.length` is checked just before; SnaKt re-reads the `var` field after the check. |
| `CookieUtils.kt` | `StringLexer.capture` | `substringModel end <= len` | B (unstated) | `index` is incremented only after `test()` confirmed `index < source.length`, so it never exceeds the length. |
| `CookieUtils.kt` | `StringLexer.capture` | `substringModel start >= 0` | B (unstated) | `start` is a snapshot of `index`, which starts at 0 and only increases. |
| `CookieUtils.kt` | `StringLexer.capture` | `substringModel start <= end` | B (unstated) | The block only increments `index`, so it is at least the earlier snapshot `start`. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValue` | `stringGet i >= 0` | B (unstated) | The only caller passes `position + 1` for a non-negative `position`. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValue` | `stringGet i < len` | B (unstated) | The caller's `position <= text.lastIndex` gives `start <= length`, and `start == length` returns first. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValue` | `stringGet i >= 0` | B (unstated) | `position` starts at the non-negative `start` and only increases. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValueQuoted` | `stringGet i >= 0` | B (unstated) | The only caller passes `position + 1` for a non-negative `position`, and `position` only increases. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValueQuoted` | `substringModel start >= 0` | B (unstated) | The only caller passes `start = position + 1 >= 1`. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValueQuoted` | `substringModel start <= end` | B (unstated) | `position` starts at `start` and only increases, so `start - 1 <= position`. |
| `HttpHeaderValueParser.kt` | `parseHeaderValueParameterValueQuoted` | `substringModel end <= len` | B (unstated) | The loop advances by 1 while `position <= lastIndex`, or by 2 only when `position < lastIndex`, so it exits at most at `length`. |
| `HttpHeaderValueParser.kt` | `String.nextIsDelimiterOrEnd` | `stringGet i >= 0` | B (unstated) | The only caller passes a non-negative `position`, so `start + 1 >= 1`. |
| `HttpHeaderValueParser.kt` | `String.nextIsDelimiterOrEnd` | `stringGet i < len` | B (unstated) | The only caller passes `start <= lastIndex`, so `position <= length` and `position == length` is excluded first. |
| `Query.kt` | `ParametersBuilder.parse` | `stringGet i >= 0` | C | The `for` range starts at the public `parseQueryString(startIndex)` argument, which the KDoc does not require to be non-negative. |
| `Query.kt` | `ParametersBuilder.appendParam` | `substringModel end <= len` | B (unstated) | `endIndex` is a loop index or `query.length`, and `trimEnd` returns at most `end`. |
| `Query.kt` | `ParametersBuilder.appendParam` | `substringModel start >= 0` | B (unstated) | `nameIndex` is `startIndex`, already used as an index at `ParametersBuilder.parse`, or a loop index plus 1; `trimStart` only increases it. |
| `Query.kt` | `ParametersBuilder.appendParam` | `substringModel start >= 0` | B (unstated) | As the `substringModel end <= len` and `substringModel start >= 0` rows of this declaration; `nameIndex` is non-negative once `ParametersBuilder.parse` has run and `trimStart` only increases it. |
| `Query.kt` | `ParametersBuilder.appendParam` | `substringModel end <= len` | B (unstated) | `equalIndex` is a real index of `=`, and `trimEnd` returns at most `end`. |
| `Query.kt` | `ParametersBuilder.appendParam` | `substringModel end <= len` | B (unstated) | `endIndex` is a loop index or `query.length`, and `trimEnd` returns at most `end`. |
| `Query.kt` | `ParametersBuilder.appendParam` | `substringModel start >= 0` | B (unstated) | `equalIndex` is a real index, so `equalIndex + 1 >= 1`, and `trimStart` only increases it. |
| `Query.kt` | `ParametersBuilder.appendParam` | `substringModel start <= end` | B (unstated) | `equalIndex < endIndex`, `trimStart` returns at most `endIndex`, and `trimEnd` returns at least its `start`. |
| `URLParser.kt` | `URLBuilder.parseMailto` | `substringModel end <= len` | B (unstated) | `delimiter` is an `indexOf` result checked against -1. |
| `URLParser.kt` | `URLBuilder.parseMailto` | `substringModel start >= 0` | B (unstated) | `takeFrom` returns early for blank strings, so `indexOfFirst` is non-negative and later steps only increase `startIndex`. |
| `URLParser.kt` | `URLBuilder.parseMailto` | `substringModel start <= end` | B (unstated) | `indexOf("@", startIndex)` returns at least `startIndex` when found. |
| `URLParser.kt` | `URLBuilder.parseMailto` | `substringModel end <= len` | B (unstated) | `endIndex` is `indexOfLast(...) + 1`, at most `urlString.length`. |
| `URLParser.kt` | `URLBuilder.parseMailto` | `substringModel start <= end` | B (unstated) | `@` is not whitespace, so its index is below `endIndex`. |
| `URLParser.kt` | `URLBuilder.parseFragment` | `stringGet i >= 0` | B (unstated) | Every caller passes a non-negative index derived from the non-blank start. |
| `URLParser.kt` | `URLBuilder.parseFragment` | `stringGet i < len` | B (unstated) | The guard `startIndex < endIndex` holds and `endIndex <= urlString.length`. |
| `URLParser.kt` | `URLBuilder.parseFragment` | `substringModel end <= len` | B (unstated) | `endIndex` is `indexOfLast(...) + 1`, at most `urlString.length`. |
| `URLParser.kt` | `findScheme` | `stringGet i >= 0` | B (unstated) | `takeFrom` returns early for blank strings, so `indexOfFirst` finds a non-negative index. |
| `URLParser.kt` | `findScheme` | `stringGet i < len` | B (unstated) | `indexOfFirst` found a character, so its index is below the length. |
| `URLParser.kt` | `findScheme` | `stringGet i >= 0` | B (unstated) | `current` starts at the non-negative `startIndex` and only increases. |
| `URLParser.kt` | `findScheme` | `stringGet i < len` | B (unstated) | The guard `current < endIndex` holds and `endIndex <= urlString.length`. |
| `URLParser.kt` | `count` | `stringGet i >= 0` | B (unstated) | `startIndex` is non-negative and `result` counts up from 0. |
| `URLParser.kt` | `count` | `stringGet i < len` | B (unstated) | The loop guard keeps `startIndex + result < endIndex <= urlString.length`. |
| `URLParser.kt` | `String.indexOfColonInHostPort` | `stringGet i >= 0` | B (range) | `index` ranges over `startIndex until endIndex` with a non-negative `startIndex` from every caller. |
| `URLParser.kt` | `String.indexOfColonInHostPort` | `stringGet i < len` | B (range) | `index` ranges below `endIndex`, which every caller keeps at most `length`. |

#### ktor-io

Paths under `ktor-io/common/src/io/ktor/utils/io/` (`jvm/.../` is ktor-io/jvm/src/io/ktor/utils/io/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `jvm/.../charsets/CharsetJVM.kt` | `CharsetEncoder.encodeToByteArray` | `substringModel start >= 0` | C | The public `CharsetEncoder.encodeToByteArray(input, fromIndex, toIndex)` does not check that its indices lie inside `input`. |
| `jvm/.../charsets/CharsetJVM.kt` | `CharsetEncoder.encodeToByteArray` | `substringModel start <= end` | C | As the `start >= 0` row of this declaration; `fromIndex <= toIndex` is unstated and unchecked. |
| `jvm/.../charsets/CharsetJVM.kt` | `CharsetEncoder.encodeToByteArray` | `substringModel end <= len` | C | As the `start >= 0` row of this declaration; `toIndex <= input.length` is unstated and unchecked. |

#### ktor-resources

Paths under `ktor-shared/ktor-resources/common/src/io/ktor/resources/`.

The rows of this module are class D2.

#### ktor-server-core

Paths under `ktor-server/ktor-server-core/common/src/io/ktor/server/` (`jvm/.../` is ktor-server/ktor-server-core/jvm/src/io/ktor/server/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `routing/RegexRouting.kt` | `PathSegmentRegexRouteSelector.countSegments` | `substringModel start <= end` | B (unstated) | Both callers pass `result.value.length` or that minus 1 after checking it is at least 1. |
| `routing/RegexRouting.kt` | `PathSegmentRegexRouteSelector.countSegments` | `substringModel end <= len` | B (unstated) | Both callers pass at most `result.value.length`. |
| `routing/RoutingPathTree.kt` | `RoutingPathTree.captureTailcardSegments` | `stringGet i >= 0` | B (unstated) | The only caller passes a non-negative segment start `i`, and `segEnd` only increases from it. |
| `routing/RoutingPathTree.kt` | `RoutingPathTree.captureTailcardSegments` | `stringGet i < len` | B (unstated) | The guard `segEnd < end` holds and the only caller passes `end = path.length`. |
| `routing/RoutingPathTree.kt` | `RoutingPathTree.captureTailcardSegments` | `substringModel end <= len` | B (unstated) | `segEnd` stops at `end = path.length` at most. |
| `routing/RoutingPathTree.kt` | `RoutingPathTree.captureTailcardSegments` | `substringModel start >= 0` | B (unstated) | `segStart` starts at the caller's non-negative `i` and only increases. |
| `routing/RoutingPathTree.kt` | `RoutingPathTree.captureTailcardSegments` | `substringModel start <= end` | B (unstated) | `segEnd` starts at `segStart` and only increases. |
| `jvm/.../config/HoconDecoder.kt` | `HoconDecoder.decodeChar` | `stringGet i < len` | D1 | `decodeChar` takes the first character of the configured string without checking that it is non-empty, so an empty HOCON value for a `Char` property fails with an index error. |

#### ktor-server-cors

Paths under `ktor-server/ktor-server-plugins/ktor-server-cors/common/src/io/ktor/server/plugins/cors/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `CORSUtils.kt` | `findPortDigitStartIndex` | `stringGet i >= 0` | B (unstated) | Both callers pass `indexOf("://") + 3` (at least 2), and `isValidOrigin` also requires the index to be positive. |

#### ktor-server-di

Paths under `ktor-server/ktor-server-plugins/ktor-server-di/common/src/io/ktor/server/plugins/di/`.

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `DependencyKeyCovariance.kt` | `DependencyKeyCovarianceParser.skipWhitespace` | `stringGet i >= 0` | B (unstated) | `position` starts at 0 and is only incremented. |
| `DependencyKeyCovariance.kt` | `DependencyKeyCovarianceParser.skipWhitespace` | `stringGet i < len` | B (field) | `position < text.length` is checked just before; SnaKt re-reads the `var` field after the check. |
| `DependencyKeyCovariance.kt` | `DependencyKeyCovarianceParser.peek` | `stringGet i >= 0` | B (unstated) | `position` starts at 0 and is only incremented. |
| `DependencyKeyCovariance.kt` | `DependencyKeyCovarianceParser.peek` | `stringGet i < len` | B (unstated) | Every `peek()` caller checks `position < text.length` first (`DependencyKeyCovarianceParser.parseMultiplication`, `DependencyKeyCovarianceParser.parsePrimary`). |
| `utils/ClasspathReference.kt` | `ClasspathReference.container` | `substringModel end <= len` | B (unstated) | `index` is a `lastIndexOf` result, below `value.length`. |
| `utils/ClasspathReference.kt` | `ClasspathReference.container` | `substringModel start <= end` | B (unstated) | `index == -1` returns first, so `index >= 0`. |

#### ktor-server-sessions

Paths under `ktor-server/ktor-server-plugins/ktor-server-sessions/common/src/io/ktor/server/sessions/` (`jvm/.../` is ktor-server/ktor-server-plugins/ktor-server-sessions/jvm/src/io/ktor/server/sessions/).

The rows of this module are class D2.

#### ktor-utils

Paths under `ktor-utils/common/src/io/ktor/util/` (`jvm/.../` is ktor-utils/jvm/src/io/ktor/util/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `date/GMTDateParser.kt` | `GMTDateParser.parse` | `stringGet i < len` | B (unstated) | The constructor checks `pattern.isNotEmpty()`, so index 0 is in range; the class does not state the invariant. |
| `date/GMTDateParser.kt` | `GMTDateParser.parse` | `stringGet i >= 0` | B (unstated) | `index` starts at 1 and is only incremented. |
| `date/GMTDateParser.kt` | `GMTDateParser.parse` | `substringModel start >= 0` | B (unstated) | `chunkStart` starts at 0 and only takes earlier `chunkEnd` values, which are non-negative. |
| `date/GMTDateParser.kt` | `GMTDateParser.parse` | `substringModel start <= end` | B (unstated) | `chunkEnd` is `chunkStart + index - start` with `index > start`. |
| `Text.kt` | `String.chomp` | `substringModel end <= len` | B (unstated) | `idx` is an `indexOf` result other than -1, so it is below `length`. |
| `Text.kt` | `String.chomp` | `substringModel start <= end` | B (unstated) | `idx == -1` is handled first, so `idx >= 0`. |
| `jvm/.../CryptoJvm.kt` | `generateNonceSuspend` | `substringModel start <= end` | B (unstated) | `tryGenerateNonceFromChannel` returns null only when `length == NONCE_SIZE_IN_CHARS` (32). |
| `jvm/.../CryptoJvm.kt` | `generateNonceSuspend` | `substringModel end <= len` | B (unstated) | `length` is 32 here and every channel nonce is a 32-character slice of the hex buffer. |
| `jvm/.../Path.kt` | `dropLeadingTopDirs` | `stringGet i >= 0` | B (unstated) | `startIndex` starts at 0 and is only incremented. |

#### ktor-websockets

Paths under `ktor-shared/ktor-websockets/common/src/io/ktor/websocket/` (`jvm/.../` is ktor-shared/ktor-websockets/jvm/src/io/ktor/websocket/).

| File | Declaration | Check | Class | Reason |
|:--|:--|:--|:-:|:--|
| `ControlFrames.kt` | `String.utf8ByteCountAt` | `stringGet i >= 0` | B (unstated) | Both callers pass `index`, which starts at 0 and only increases. |
| `ControlFrames.kt` | `String.utf8ByteCountAt` | `stringGet i < len` | B (unstated) | Both callers call it only while `index < length`. |
| `ControlFrames.kt` | `String.utf8Truncate` | `substringModel start <= end` | B (unstated) | `index` starts at 0 and only increases. |
| `ControlFrames.kt` | `String.utf8Truncate` | `substringModel end <= len` | B (unstated) | `index` advances by 2 only when `index + 1 < length`, so it never exceeds `length`. |
