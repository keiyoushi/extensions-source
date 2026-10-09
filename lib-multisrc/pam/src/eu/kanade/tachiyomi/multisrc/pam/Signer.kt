package eu.kanade.tachiyomi.multisrc.pam

import com.dylibso.chicory.runtime.HostFunction
import com.dylibso.chicory.runtime.ImportValues
import com.dylibso.chicory.runtime.Instance
import com.dylibso.chicory.runtime.Memory
import com.dylibso.chicory.wasm.types.FunctionImport
import java.io.IOException

/**
 * One instance of the site's reader signer. `ecdhInit` keeps the session's shared secret inside
 * the instance, so a key derivation has to run on the instance that did the key exchange.
 */
internal class Signer(private val reader: ReaderModule) {

    private val instance: Instance = Instance.builder(reader.module)
        .withImportValues(ImportValues.builder().addFunction(*hostFunctions()).build())
        .build()
        .also { it.export(reader.export("ctors")).apply() }

    private val memory = instance.memory()

    private fun hostFunctions(): Array<HostFunction> {
        val types = reader.module.typeSection()
        val imports = reader.module.importSection()
        return (0 until imports.importCount())
            .map { imports.getImport(it) }
            .map { import ->
                if (import !is FunctionImport) throw IOException("Unsupported reader import ${import.module()}.${import.name()}")
                val type = types.getType(import.typeIndex())
                val name = import.module() to import.name()
                val unmask = reader.unmaskImports[name]
                when {
                    name == reader.resizeImport -> HostFunction(import.module(), import.name(), type) { instance, args ->
                        val memory = instance.memory()
                        val wanted = args[0].toInt().toLong() and 0xFFFFFFFFL
                        val missing = wanted - memory.pages().toLong() * PAGE_SIZE
                        val grown = missing <= 0 || memory.grow(((missing + PAGE_SIZE - 1) / PAGE_SIZE).toInt()) >= 0
                        longArrayOf(if (grown) 1 else 0)
                    }
                    unmask != null -> HostFunction(import.module(), import.name(), type) { instance, args ->
                        instance.memory().unmask(args[0].toInt(), unmask)
                        null
                    }
                    // A host function this theme does not know would be stubbed wrongly; refuse the build instead.
                    else -> throw IOException("Unsupported reader import ${import.module()}.${import.name()}")
                }
            }
            .toTypedArray()
    }

    private fun Memory.unmask(ptr: Int, unmask: Unmask) {
        repeat(unmask.passes) {
            val block = readBytes(ptr, UNMASK_SIZE)
            val out = ByteArray(UNMASK_SIZE)
            val order = if (unmask.ascending) 0 until UNMASK_SIZE else UNMASK_SIZE - 1 downTo 0
            var running = unmask.seed
            for (i in order) {
                val o = block[unmask.permutation[i]].toInt() and 0xFF
                val step = when (unmask.mode[i]) {
                    0 -> ((o xor unmask.xor[i]) + unmask.add[i]) and 0xFF
                    1 -> ((o + unmask.add[i]) and 0xFF) xor unmask.xor[i]
                    else -> (((o shl unmask.rotate[i]) or (o shr (8 - unmask.rotate[i]))) and 0xFF) xor unmask.xor[i]
                }
                running = if (unmask.foldRotate < 0) {
                    running xor step
                } else {
                    (step + ((running shl unmask.foldRotate) or (running shr (8 - unmask.foldRotate))).and(0xFF)) and 0xFF
                }
                out[i] = running.toByte()
            }
            write(ptr, out)
        }
    }

    fun signAttestation(challenge: String, payload: String): String {
        val c = challenge.toByteArray()
        val p = payload.toByteArray()
        val out = alloc(64)
        call("signAttestation", put(c), c.size, put(p), p.size, out)
        return String(memory.readBytes(out, 64))
    }

    fun signManifest(token: String, version: Int, uid: String, ts: Long, nonce: String): String {
        val t = token.toByteArray()
        val u = uid.toByteArray()
        val n = nonce.toByteArray()
        val out = alloc(64)
        call("signManifest", put(t), t.size, version, put(u), u.size, ts.toDouble(), put(n), n.size, out)
        return String(memory.readBytes(out, 64))
    }

    /** `ecdhInit` then `kdfRot`: unmasks the manifest hint into the chapter's page key. */
    fun deriveContentKey(privateKey: ByteArray, serverPubkey: ByteArray, uid: String, keyVersion: Int, hint: ByteArray): ByteArray {
        call("ecdhInit", put(privateKey), privateKey.size, put(serverPubkey), serverPubkey.size, alloc(32))

        val u = uid.toByteArray()
        val out = alloc(32)
        call("kdfRot", put(u), u.size, keyVersion, put(hint), hint.size, out)
        return memory.readBytes(out, 32)
    }

    // An instance only serves one chapter, so its allocations are never freed.
    private fun alloc(size: Int): Int = instance.export(reader.export("malloc")).apply(size.toLong())[0].toInt()

    private fun put(bytes: ByteArray): Int = alloc(bytes.size).also { memory.write(it, bytes) }

    /** Pointers and lengths are passed separately, as in the site's glue. */
    private fun call(export: String, vararg args: Number) {
        val raw = LongArray(args.size) { i -> args[i].let { if (it is Double) it.toRawBits() else it.toLong() } }
        val result = instance.export(reader.export(export)).apply(*raw).firstOrNull() ?: 0L
        if (result == 0L) throw IOException("Reader $export failed")
    }
}

private const val PAGE_SIZE = 65536L
