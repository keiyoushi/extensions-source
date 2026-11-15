package keiyoushi.lib.runtime

/**
 * Marks the interface a lib calls a Keiyoushi Runtime module through. The runtime's
 * `keiyoushi.runtime.<module>.Entry` object implements it, and [RuntimeModule.bind] connects the two.
 *
 * Contracts hold only abstract functions (`suspend` is fine), each annotated
 * `@Throws(Exception::class)` so exceptions reach the caller unchanged. Parameter and return types
 * are types the app loads for every extension: Android, Java, Kotlin, coroutines, OkHttp, Jsoup.
 */
interface RuntimeContract
