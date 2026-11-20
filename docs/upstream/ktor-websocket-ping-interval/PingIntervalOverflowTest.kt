import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

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

    @OptIn(io.ktor.utils.io.InternalAPI::class)
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
