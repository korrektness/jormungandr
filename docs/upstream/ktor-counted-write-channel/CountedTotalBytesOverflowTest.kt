import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals

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
