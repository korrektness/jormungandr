// LOCALITY_CHECK_ONLY

class Handlers {
    var onClose: (() -> Unit)? = null
    internal var filter: ((String) -> Boolean)? = null

    fun reset() {
        filter = null
    }
}

fun configure(f: ((String) -> Unit)? = null) {}

fun `pass null`() {
    configure(null)
}

fun `return null`(b: Boolean): ((String) -> Unit)? {
    if (b) return null
    return { _ -> }
}

fun `throw in place of a function`(x: Throwable): (String) -> Unit = throw x
