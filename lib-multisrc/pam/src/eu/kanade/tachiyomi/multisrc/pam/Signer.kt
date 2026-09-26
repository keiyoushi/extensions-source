package eu.kanade.tachiyomi.multisrc.pam

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.wasm.Parser
import com.dylibso.chicory.wasm.types.FunctionType
import com.dylibso.chicory.wasm.types.ValType

class Signer(wasm: ByteArray) {

    private val instance = Instance.builder(Parser.parse(wasm))
        .withImportValues(
            ImportValues.builder()
                .addFunction(
                    // emscripten_resize_heap; the signer never outgrows its initial memory.
                    HostFunction(
                        RESIZE_HEAP_MODULE,
                        RESIZE_HEAP_NAME,
                        FunctionType.of(listOf(ValType.I32), listOf(ValType.I32)),
                    ) { _, _ -> longArrayOf(0) },
                )
                .build(),
        )
        .build()
        .also { it.export(CTORS).apply() }

    private val memory = instance.memory()

    /** A block of the signer's heap, as the pointer and length pair its exports expect. */
    class Buf(val ptr: Int, val size: Int)

    /** Runs [block], freeing every [Buf] it allocated once it returns. */
    fun <T> use(block: Session.() -> T): T {
        val session = Session()
        try {
            return session.block()
        } finally {
            session.release()
        }
    }

    inner class Session internal constructor() {

        private val allocated = ArrayList<Int>()

        fun alloc(size: Int): Buf {
            val ptr = instance.export(MALLOC).apply(size.toLong())[0].toInt()
            allocated += ptr
            return Buf(ptr, size)
        }

        fun write(bytes: ByteArray): Buf = alloc(bytes.size).also { memory.write(it.ptr, bytes) }

        fun write(text: String): Buf = write(text.toByteArray())

        fun read(buf: Buf): ByteArray = memory.readBytes(buf.ptr, buf.size)

        fun readText(buf: Buf): String = String(read(buf))

        /**
         * Calls an export by its name in the module. [Buf] arguments pass their pointer, so
         * lengths have to be passed explicitly, like the reader's own glue code does.
         */
        fun call(export: String, vararg args: Any): Long {
            val raw = LongArray(args.size) { i ->
                when (val arg = args[i]) {
                    is Buf -> arg.ptr.toLong()
                    is Double -> arg.toRawBits()
                    is Number -> arg.toLong()
                    else -> throw IllegalArgumentException("unsupported argument: $arg")
                }
            }
            return instance.export(export).apply(*raw).firstOrNull() ?: 0L
        }

        internal fun release() {
            val free = instance.export(FREE)
            allocated.forEach { free.apply(it.toLong()) }
            allocated.clear()
        }
    }

    private companion object {
        const val RESIZE_HEAP_MODULE = "a"
        const val RESIZE_HEAP_NAME = "a"
        const val CTORS = "c"
        const val MALLOC = "i"
        const val FREE = "e"
    }
}
