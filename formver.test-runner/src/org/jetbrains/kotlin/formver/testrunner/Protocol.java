package org.jetbrains.kotlin.formver.testrunner;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Messages between client and server: one {@link Properties} document each way. The client shuts down its output
 * after the request, so the server reads to the end of the stream; the server closes the connection after its
 * response. List values are newline-separated.
 */
final class Protocol {
    private Protocol() {}

    static Properties read(SocketChannel channel) throws IOException {
        InputStream in = Channels.newInputStream(channel);
        Properties message = new Properties();
        message.load(new StringReader(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
        return message;
    }

    /** Reads a message, closing the channel and throwing {@link TimeoutException} if none arrives within the bound. */
    static Properties read(SocketChannel channel, Duration bound) throws IOException, TimeoutException {
        return within(bound, () -> read(channel), channel);
    }

    /**
     * Runs a blocking read on its own thread and returns its result; if it has none within the bound, closes
     * {@code blocker}, which ends the read, and throws {@link TimeoutException}.
     */
    static <T> T within(Duration bound, Callable<T> read, Closeable blocker) throws IOException, TimeoutException {
        FutureTask<T> task = new FutureTask<>(read);
        Thread reader = new Thread(task, "test-runner-read");
        reader.setDaemon(true);
        reader.start();
        try {
            return task.get(bound.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            blocker.close();
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            blocker.close();
            throw new IOException("interrupted while waiting for the server", e);
        }
    }

    static void write(SocketChannel channel, Properties message) throws IOException {
        OutputStream out = Channels.newOutputStream(channel);
        message.store(out, null);
        out.flush();
    }

    static List<String> list(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String line : value.split("\n")) {
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    static String join(List<String> values) {
        return String.join("\n", values);
    }
}
