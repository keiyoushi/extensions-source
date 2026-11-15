package keiyoushi.lib.runtime

import java.util.concurrent.ConcurrentHashMap

/** The Keiyoushi Runtime's side: only the runtime extension calls this */
object RuntimeHost {
    private const val STATE_KEY = "keiyoushi.runtime.state"

    /**
     * Makes this runtime's modules available to other extensions, unless a runtime with the same or
     * a higher [versionCode] is already registered in this process. The app constructs the factory
     * again on some update checks; those calls change nothing.
     */
    fun register(
        versionCode: Int,
        versionName: String,
        apis: Map<String, Int>,
        minApis: Map<String, Int>,
        entries: Map<String, Any>,
    ): Boolean = Registration.publish(
        versionCode,
        mapOf(
            Registration.VERSION_CODE to versionCode,
            Registration.VERSION_NAME to versionName,
            Registration.APIS to apis.toMap(),
            Registration.MIN_APIS to minApis.toMap(),
            Registration.ENTRIES to entries.toMap(),
        ),
    )

    /**
     * Process-wide state for the runtime's modules. It outlives the runtime version that wrote it:
     * an update replaces the runtime in a running app, and the new version reads what the old one
     * left. Keys are `<module>.<name>`, and values are app-loaded types only, since the next runtime
     * version can't use this version's classes. Available from the start, also while `Entry`
     * objects initialise.
     */
    val state: ConcurrentHashMap<String, Any>
        get() {
            val properties = System.getProperties()
            @Suppress("UNCHECKED_CAST")
            return synchronized(properties) {
                properties.getOrPut(STATE_KEY) { ConcurrentHashMap<String, Any>() }
            } as ConcurrentHashMap<String, Any>
        }
}
