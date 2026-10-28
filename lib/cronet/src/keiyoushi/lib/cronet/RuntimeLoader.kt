package keiyoushi.lib.cronet

import dalvik.system.DexClassLoader
import keiyoushi.utils.applicationContext
import keiyoushi.utils.sha256
import java.io.File

/** One runtime per app process, shared by every extension: Play services' Cronet serves one class loader */
internal object RuntimeLoader {
    // Bump when CronetRuntime.server(Context) changes
    private const val KEY = "keiyoushi.lib.cronet.v1"
    private const val ASSET = "assets/cronet-runtime.apk"
    private const val RUNTIME = "keiyoushi.lib.cronetruntime.CronetRuntime"

    fun load(): Class<*> {
        val properties = System.getProperties()
        (properties[KEY] as? ClassLoader)?.let { return it.loadClass(RUNTIME) }
        val runtime = newLoader().loadClass(RUNTIME)
        val loader = properties.putIfAbsent(KEY, runtime.classLoader) as? ClassLoader ?: return runtime
        return loader.loadClass(RUNTIME)
    }

    private fun newLoader(): ClassLoader {
        val bytes = javaClass.classLoader!!.getResourceAsStream(ASSET)!!.use { it.readBytes() }
        val hash = bytes.sha256().toHexString()
        val dir = File(applicationContext.codeCacheDir, "keiyoushi-cronet").apply { mkdirs() }
        val file = File(dir, "$hash.apk")
        if (!file.exists() || file.readBytes().sha256().toHexString() != hash) {
            val tmp = File.createTempFile(hash, null, dir)
            try {
                tmp.writeBytes(bytes)
                // Android 14 only loads read-only dex files
                check(tmp.setReadOnly() && tmp.renameTo(file))
            } finally {
                tmp.delete()
            }
        }
        return DexClassLoader(file.path, dir.path, null, applicationContext.classLoader)
    }
}
