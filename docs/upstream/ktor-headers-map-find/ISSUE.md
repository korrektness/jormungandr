# Draft YouTrack issue (project KTOR, subsystem Server. Engine. CIO / Client. Engine. CIO)

**Title:** `HttpHeadersMap.find` returns -1 for a header that is present, and never returns for some absent names

**Ktor version:** 3.1.2 through 3.6.0 and current `main`.

**Problem.** `HttpHeadersMap.find(name, fromIndex)` is public and deprecated with a warning ("Use getAll instead"). It has no KDoc; its contract is the one the indexed accessors use: return the first index `i >= fromIndex` with `nameAt(i) == name` (case-insensitive), or -1 if there is none. `HttpHeadersMapTest` in the Ktor repository uses it that way (`valueAt(find(name, from))`).

The loop starts at the offset of `fromIndex` and then sets `offset = (offset / HEADER_SIZE) % headerCapacity`. That turns an offset into a slot number and uses it as an offset again; it does not move to the next header. Results:

- If the header at `fromIndex` does not match, the next offset tried is unrelated to the next header, so a present header is reported as -1.
- If the first header sits in slot 0 and does not match, the offset stays 0 and the loop never ends (`nextIndex++` also wraps after 2^31 iterations).

**Reproducer (JVM, JUnit 4):**

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

**Actual:** `java.lang.AssertionError: expected:<1> but was:<-1>`. `headers["b"]` is `2`, so the header is present.

For the non-termination: parse one header whose name hashes to slot 0 (`h91`: its lower-case hash is 101760, a multiple of the initial capacity 128), then call `find("absent")`. The call had not returned after 3 s. With the fix below it returns -1.

**Expected:** `find("b") == 1` (with `nameAt(1) == "b"`), and `find` of an absent name returns -1.

**Fix.** Walk `offsets()`, which defines the indices `nameAt` and `valueAt` use:

```kotlin
offsets().drop(fromIndex).forEachIndexed { index, offset ->
    if (headerHasName(name, offset)) {
        return fromIndex + index
    }
}
return -1
```

With the fix, a negative `fromIndex` throws `IllegalArgumentException` (from `drop`), and a `fromIndex` at or past `size` returns -1. Removing the deprecated function would also resolve this.
