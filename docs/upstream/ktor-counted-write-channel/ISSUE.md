# Draft YouTrack issue (project KTOR, subsystem Core / IO)

**Title:** `CountedByteWriteChannel.totalBytesWritten` wraps negative after 2 GiB

**Ktor version:** 3.0.0 through 3.6.0 and current `main`.

**Problem.** `ByteWriteChannel.counted()` returns a `CountedByteWriteChannel` whose `totalBytesWritten: Long` reports the bytes written through it. It has no KDoc; the `Long` type and the read-side counterpart, `CountedByteReadChannel.totalBytesRead`, which counts in `Long`, both state that counts beyond `Int` are expected. The write side keeps the flushed count in an `Int` (`private var flushedCount = 0`) and computes `(flushedCount + writeBuffer.size - initial).toLong()`, so the count wraps once 2^31 bytes have been flushed through one channel.

**Reproducer (JVM, JUnit 4, `-Xmx512m`, about a second of runtime):**

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

**Actual:** `java.lang.AssertionError: expected:<2147483648> but was:<-2147483648>`.

**Expected:** `2147483648`.

**Fix.** Count in `Long`:

```kotlin
private var flushedCount = 0L
public val totalBytesWritten: Long get() = flushedCount + writeBuffer.size - initial
```

The patch leaves `flushedCount += writeBuffer.size` in `flush()` as it is.
