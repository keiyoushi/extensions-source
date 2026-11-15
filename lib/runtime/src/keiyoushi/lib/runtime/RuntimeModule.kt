package keiyoushi.lib.runtime

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * A module of the Keiyoushi Runtime extension, needed at API level [api] or higher. Declare it with
 * `keiyoushiRuntime { requires("<module>", api = N) }` and use the generated `RuntimeModules.<module>`.
 */
class RuntimeModule(val name: String, val api: Int) {
    /** The runtime registration this module last resolved against, with its contracts' methods */
    private class Resolved(val registration: Map<*, *>, val entry: Any) {
        val methods = ConcurrentHashMap<Class<*>, Map<Method, Method>>()
    }

    @Volatile
    private var resolved: Resolved? = null
    private val proxies = ConcurrentHashMap<Class<*>, Any>()

    /**
     * [T], implemented by the runtime. Calls go to the newest runtime loaded in this process, so the
     * result can be kept. Exceptions from the runtime reach the caller unwrapped.
     *
     * @throws RuntimeUnavailableException when the runtime is missing, too old, or no longer serves
     *   [api]: here, or on a later call if the runtime changes
     */
    inline fun <reified T : RuntimeContract> bind(): T = bind(T::class.java)

    fun <T : RuntimeContract> bind(contract: Class<T>): T {
        require(contract.isInterface) { "${contract.name} must be an interface" }
        resolve().methodsFor(contract)
        return contract.cast(proxies.getOrPut(contract) { newProxy(contract) })
    }

    /**
     * The error for a [feature] of this module that the installed runtime doesn't know yet, such
     * as a catalog entry a contract function reported back as unknown.
     */
    fun needsUpdate(feature: String): RuntimeUnavailableException {
        val installed = Registration.current()?.get(Registration.VERSION_NAME) ?: "none"
        return RuntimeUnavailableException("Update Keiyoushi Runtime (installed $installed; this source needs $name: $feature).")
    }

    private fun newProxy(contract: Class<*>): Any {
        val handler = InvocationHandler { proxy, method, args ->
            when {
                method.declaringClass == Any::class.java -> when (method.name) {
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    else -> "$this as ${contract.simpleName}"
                }
                else -> {
                    val resolved = resolve()
                    val target = resolved.methodsFor(contract).getValue(method)
                    try {
                        target.invoke(resolved.entry, *args.orEmpty())
                    } catch (e: InvocationTargetException) {
                        throw e.cause ?: e
                    }
                }
            }
        }
        return Proxy.newProxyInstance(contract.classLoader, arrayOf(contract), handler)
    }

    private fun resolve(): Resolved {
        val registration = Registration.current()
            ?: throw RuntimeUnavailableException("Keiyoushi Runtime isn't installed or didn't load. Check it in Browse → Extensions.")
        resolved?.takeIf { it.registration === registration }?.let { return it }

        val served = registration.module(Registration.APIS) as? Int
        val entry = registration.module(Registration.ENTRIES)
        if (served == null || served < api || entry == null) {
            throw RuntimeUnavailableException(
                "Update Keiyoushi Runtime (installed ${registration.versionName}; this source needs $name $api).",
            )
        }
        val minApi = registration.module(Registration.MIN_APIS) as? Int ?: 1
        if (api < minApi) {
            throw RuntimeUnavailableException(
                "Update this extension: Keiyoushi Runtime ${registration.versionName} no longer serves $name $api.",
            )
        }
        return Resolved(registration, entry).also { resolved = it }
    }

    private fun Map<*, *>.module(key: String): Any? = (get(key) as? Map<*, *>)?.get(name)

    private val Map<*, *>.versionName get() = get(Registration.VERSION_NAME)

    /**
     * Each of [contract]'s methods with the entry's method of the same name, parameter types and
     * return type. Shared types are the same classes on both sides, so a type bundled in the
     * extension never matches.
     */
    private fun Resolved.methodsFor(contract: Class<*>): Map<Method, Method> = methods.getOrPut(contract) {
        // Static methods are Kotlin's default-argument helpers; they call the abstract method
        contract.methods.filterNot { Modifier.isStatic(it.modifiers) }.associateWith { method ->
            val where = "${contract.name}.${method.name}"
            check(!method.isDefault) { "$where: runtime contracts hold only abstract functions" }
            // A proxy wraps checked exceptions its method doesn't declare
            check(method.exceptionTypes.any { it.isAssignableFrom(Exception::class.java) }) {
                "$where: annotate runtime contract functions with @Throws(Exception::class)"
            }
            // Not getMethod: an override can add a bridge with a narrower return type
            val target = entry.javaClass.methods.firstOrNull {
                it.name == method.name &&
                    it.returnType == method.returnType &&
                    it.parameterTypes.contentEquals(method.parameterTypes)
            }
            if (target == null) {
                val types = method.parameterTypes + method.returnType
                val bundled = types.firstOrNull { it.classLoader === contract.classLoader && it.classLoader !== entry.javaClass.classLoader }
                check(bundled == null) {
                    "$where uses ${bundled!!.name}, which each extension bundles separately; use a type the app loads"
                }
                throw RuntimeUnavailableException(
                    "Keiyoushi Runtime ${registration.versionName} doesn't match this extension. Update both.",
                )
            }
            target.apply { isAccessible = true }
        }
    }

    override fun toString(): String = "RuntimeModule($name, api = $api)"
}
