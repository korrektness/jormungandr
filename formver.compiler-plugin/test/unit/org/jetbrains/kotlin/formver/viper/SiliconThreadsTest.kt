package org.jetbrains.kotlin.formver.viper

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertEquals

class SiliconThreadsTest {
    @Test
    fun `threads started under a stopped mark stop logging and other threads keep logging`() {
        // SiliconThreads initializes logback, which a direct call to getLogger may race with in a parallel test.
        val stopped = SiliconThreads()
        val logger = org.slf4j.LoggerFactory.getLogger("${javaClass.name}.threads") as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        val proceed = CountDownLatch(1)
        fun startUnder(threads: SiliconThreads, message: String): Thread = threads.run {
            Thread {
                proceed.await()
                logger.error(message)
            }.apply { start() }
        }
        val stoppedThread = startUnder(stopped, "stopped backend")
        val runningThread = startUnder(SiliconThreads(), "running backend")
        stopped.stop()
        proceed.countDown()
        stoppedThread.join()
        runningThread.join()
        logger.error("caller")
        logger.detachAppender(appender)
        assertEquals(listOf("running backend", "caller"), appender.list.map { it.message }.sortedBy { it != "running backend" })
    }
}
