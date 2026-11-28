package eu.kanade.tachiyomi.extension.en.comix

import app.cash.quickjs.QuickJs
import app.cash.quickjs.QuickJsException

object ApicoreKey {

    fun read(source: String): SiteKey? {
        val script = source
            .replace(IMPORT) { "const ${it.groupValues[1]}=globalThis.__n,${it.groupValues[2]}=globalThis.__r,${it.groupValues[3]}=globalThis.__t;" }
            .replace(EXPORT, "")
        val result = QuickJs.create().use { engine ->
            engine.evaluate(PRELUDE)
            try {
                engine.evaluate(script)
            } catch (_: QuickJsException) {
            }
            engine.evaluate(FIND_KEY) as? String
        } ?: return null
        val parts = result.split(',')
        return SiteKey.of(parts.first(), parts.drop(1).map { it.toInt().toByte() }.toByteArray())
    }

    private val IMPORT = Regex("""import\{n as ([\w$]+),r as ([\w$]+),t as ([\w$]+)\}from"[^"]*";?""")
    private val EXPORT = Regex("""export\{[^}]*\};?""")

    private const val PRELUDE = """
        globalThis.__t = (fn) => { let m; return () => (m || (fn((m = { exports: {} }).exports, m), fn = null), m.exports); };
        globalThis.__n = (a, b) => { let c = {}; for (var d in a) Object.defineProperty(c, d, { get: a[d], enumerable: true }); return b || Object.defineProperty(c, Symbol.toStringTag, { value: 'Module' }), c; };
        globalThis.__r = (a, b, c) => (c = a != null ? Object.create(Object.getPrototypeOf(a)) : {},
            ((t, e) => { if (e && typeof e === 'object' || typeof e === 'function') for (const k of Object.getOwnPropertyNames(e)) if (!Object.prototype.hasOwnProperty.call(t, k) && k !== 'default') Object.defineProperty(t, k, { get: () => e[k], enumerable: true }); return t; })(
                Object.defineProperty(c, 'default', { value: a, enumerable: true }), a));
    """

    private const val FIND_KEY = """
        (() => {
            for (const name of Object.getOwnPropertyNames(globalThis)) {
                let value;
                try { value = globalThis[name]; } catch (e) { continue; }
                if (!value || typeof value !== 'object' || typeof value.kid !== 'string' || typeof value.material !== 'function') continue;
                const material = value.material();
                if (material && material.length === 1088) return value.kid + ',' + Array.from(material).join(',');
            }
            return null;
        })()
    """
}
