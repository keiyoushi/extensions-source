package keiyoushi.webview.internal

/**
 * The state the extensions' copies of core share for [WebViewProxy]. Every extension carries its own copy of core, in
 * its own classloader, and can be updated or uninstalled while the app runs, so all of a process's copies find one
 * proxy here: a `ConcurrentHashMap<String, Any>` in `System.getProperties()` under [PROPERTY], whose monitor is the
 * lock for every change to it. Its values are JDK and OkHttp types only, OkHttp being the app's and so shared.
 * `System.getProperties()` is meant for `String` values; this non-`String` entry is a permanent part of the protocol,
 * as every released copy finds the others by it, so it stays under [PROPERTY] for good.
 *
 * | Key           | Type                                            | Written by                                   | Since |
 * |---------------|-------------------------------------------------|----------------------------------------------|-------|
 * | `routes`      | `CopyOnWriteArrayList<Map<String, Any>>`        | holders add and remove their own; the        | 1     |
 * |               |                                                 | serving copy removes lapsed ones             |       |
 * | `keyPair`     | `java.security.KeyPair` (EC P-256)              | the first copy to need it; never replaced    | 1     |
 * | `certificate` | `java.security.cert.X509Certificate`            | same; self-signed for `keyPair`              | 1     |
 * | `realm`       | `String`                                        | same; the 407's Basic realm                  | 1     |
 * | `username`    | `String`                                        | same                                         | 1     |
 * | `password`    | `String`                                        | same                                         | 1     |
 * | `listener`    | `java.net.ServerSocket` on 127.0.0.1, with an   | the copy that starts the proxy; closed and   | 1     |
 * |               | SO_TIMEOUT; accept only holding its monitor     | removed by whichever copy stops it           |       |
 * | `server`      | `Map<String, Any>`, a server record             | the serving copy; replaced on a takeover     | 1     |
 *
 * A route record, immutable: `hosts` (`Set<String>`: `example.com` or `*.example.com`, lowercase), `send`
 * (`java.util.function.Function<okhttp3.Request, okhttp3.Response>`, run by whichever copy serves), `expiresAt`
 * (`java.util.concurrent.atomic.AtomicLong`, a [System.nanoTime] deadline its holder moves on). Since 1.
 *
 * A server record, immutable: `version` (`Int`, [VERSION] of its copy), `build` (`Long`, [CoreBuild.STAMP] of its copy),
 * `retire` (`Runnable`: stop accepting, stop re-applying WebView's override on device proxy changes, end each
 * connection after its current exchange, close what's left after a grace; returns at once and never throws). Since 1.
 *
 * Rules, so any two released copies work together:
 * - Keys and record fields are only added. One never changes type or meaning and stays written while any released
 *   copy may read it. Readers ignore keys they don't know and skip a record they can't read.
 * - [VERSION] and `build` only decide who serves: opening or renewing a route, a copy whose version is higher than the
 *   server's, or equal with a newer build, starts its own server on the same `listener` and retires the other. An
 *   identical build never takes over, and the newer one strictly wins, so two copies can't take turns. It is never a
 *   reason to refuse. A newer build of core reaches every run as soon as one updated extension does, an extension
 *   updated in place included; bump [VERSION] with a change to this layout.
 * - A server feature a route may rely on goes into the server record as a new field, such as a `capabilities` set of
 *   names; a record without it offers none.
 * - A change that can't be made this way goes under a new property, `keiyoushi.webview-proxy-2`, with its own layout.
 *   A copy that knows it also serves this map, taking over its server like any newer copy and routing its records on
 *   the same `listener`, so older copies' runs keep working beside it.
 *
 * Tests drive other builds' copies through this map and [WebViewProxy]'s public entry points (`open`, `renew`,
 * `close`, `version`, `build`, `retireGraceMillis`) and [WebViewOverride]'s fields; keep those names (see the proxy's
 * compatibility tests).
 */
internal object ProxyProtocol {
    const val PROPERTY = "keiyoushi.webview-proxy"

    /** This copy's protocol version. */
    const val VERSION = 1

    const val ROUTES = "routes"
    const val KEY_PAIR = "keyPair"
    const val CERTIFICATE = "certificate"
    const val REALM = "realm"
    const val USERNAME = "username"
    const val PASSWORD = "password"
    const val LISTENER = "listener"
    const val SERVER = "server"

    const val HOSTS = "hosts"
    const val SEND = "send"
    const val EXPIRES_AT = "expiresAt"

    const val SERVER_VERSION = "version"
    const val SERVER_BUILD = "build"
    const val RETIRE = "retire"
}
