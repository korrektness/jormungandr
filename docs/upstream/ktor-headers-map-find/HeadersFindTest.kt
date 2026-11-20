import io.ktor.http.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class HeadersFindTest {
    @Suppress("DEPRECATION")
    @Test
    fun findsSecondHeader() = runBlocking {
        val headers = parseHeaders(ByteReadChannel("a: 1\r\nb: 2\r\n\r\n"))
        assertEquals("2", headers["b"].toString())
        assertEquals(1, headers.find("b"))
    }
}
