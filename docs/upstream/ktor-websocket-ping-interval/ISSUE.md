# Draft YouTrack issue (project KTOR, subsystem Client. WebSockets)

**Title:** Client WebSockets: `pingInterval = Duration.INFINITE` makes the session's `timeoutMillis` negative (-2)

**Ktor version:** 3.6.0 and current `main`. JVM; the code is in common source.

**Problem.** `WebSockets.Config.pingInterval` accepts any `Duration?` ("Sets interval of sending [FrameType.PING] messages. Use `null` to disable ping."). The plugin stores `pingInterval.inWholeMilliseconds` and gives each session `timeoutMillis = pingIntervalMillis * 2` (`WebSockets.convertSessionToDefault`). For `Duration.INFINITE`, `inWholeMilliseconds` is `Long.MAX_VALUE`, and the product wraps to -2. The same happens for any `pingIntervalMillis` above `Long.MAX_VALUE / 2`.

`DefaultWebSocketSession.timeoutMillis` is documented as "a timeout to wait for pong reply to ping". The session reports -2 ms, which is not a timeout.

**Reproducer (JVM, JUnit 4).** The engine below answers every request with a raw `WebSocketSession`; no network is involved.

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

**Actual:** `java.lang.AssertionError: timeoutMillis = -2`.

**Expected:** a positive timeout. An infinite interval should give an effectively infinite timeout.

**Impact.** Low. With an infinite interval the pinger waits `Long.MAX_VALUE` ms before its first ping, so the negative timeout is not used for a ping. It is visible through `timeoutMillis` and `timeout`, and it would close the session with "Ping timeout" at the first ping if the application later sets a finite `pingIntervalMillis` on the session without also setting `timeoutMillis`. That last point comes from reading the code and was not run.

**Fix.** Saturate the doubling:

```kotlin
val timeoutMillis = pingIntervalMillis.coerceAtMost(Long.MAX_VALUE / 2) * 2
```

Intervals up to `Long.MAX_VALUE / 2` ms are unaffected by the fix.
