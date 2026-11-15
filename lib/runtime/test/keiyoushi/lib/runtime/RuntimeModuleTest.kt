package keiyoushi.lib.runtime

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class RuntimeModuleTest {
    interface Demo : RuntimeContract {
        @Throws(Exception::class)
        fun echo(value: String, suffix: String = ""): String

        @Throws(Exception::class)
        suspend fun later(value: String): String

        @Throws(Exception::class)
        fun fail(checked: Boolean)
    }

    interface DemoV2 : RuntimeContract {
        @Throws(Exception::class)
        fun added(): Int
    }

    interface Undeclared : RuntimeContract {
        fun echo(value: String, suffix: String): String
    }

    object EntryV1 : Demo {
        override fun echo(value: String, suffix: String) = "v1:$value$suffix"
        override suspend fun later(value: String): String {
            delay(1)
            return "later:$value"
        }
        override fun fail(checked: Boolean) = throw if (checked) IOException("HTTP 503") else IllegalArgumentException("from entry")
    }

    object EntryV2 : Demo, DemoV2 {
        override fun echo(value: String, suffix: String) = "v2:$value$suffix"
        override suspend fun later(value: String) = "v2later:$value"
        override fun fail(checked: Boolean) = Unit
        override fun added() = 2
    }

    @Before
    @After
    fun clear() {
        System.getProperties().remove(Registration.KEY)
        System.getProperties().remove("keiyoushi.runtime.state")
    }

    private fun register(versionCode: Int, api: Int = 1, minApi: Int = 1, entry: Any = EntryV1) = RuntimeHost.register(
        versionCode = versionCode,
        versionName = "1.6.$versionCode",
        apis = mapOf("demo" to api),
        minApis = mapOf("demo" to minApi),
        entries = mapOf("demo" to entry),
    )

    private fun unavailable(block: () -> Unit) = assertThrows(RuntimeUnavailableException::class.java, block).message

    @Test
    fun missingRuntimeSaysInstall() {
        assertEquals(
            "Keiyoushi Runtime isn't installed or didn't load. Check it in Browse → Extensions.",
            unavailable { RuntimeModule("demo", 1).bind<Demo>() },
        )
    }

    @Test
    fun olderApiSaysUpdateRuntime() {
        register(1, api = 1)
        assertEquals(
            "Update Keiyoushi Runtime (installed 1.6.1; this source needs demo 2).",
            unavailable { RuntimeModule("demo", 2).bind<Demo>() },
        )
        unavailable { RuntimeModule("other", 1).bind<Demo>() }
    }

    @Test
    fun belowMinApiSaysUpdateExtension() {
        register(1, api = 3, minApi = 2)
        assertEquals(
            "Update this extension: Keiyoushi Runtime 1.6.1 no longer serves demo 1.",
            unavailable { RuntimeModule("demo", 1).bind<Demo>() },
        )
        RuntimeModule("demo", 2).bind<Demo>()
    }

    @Test
    fun runtimeMissingAContractFunctionSaysUpdateBoth() {
        register(1)
        assertEquals(
            "Keiyoushi Runtime 1.6.1 doesn't match this extension. Update both.",
            unavailable { RuntimeModule("demo", 1).bind<DemoV2>() },
        )
    }

    @Test
    fun unknownFeatureSaysUpdateRuntime() {
        register(1)
        assertEquals(
            "Update Keiyoushi Runtime (installed 1.6.1; this source needs demo: patch x).",
            RuntimeModule("demo", 1).needsUpdate("patch x").message,
        )
    }

    @Test
    fun contractWithoutThrowsIsRejected() {
        register(1)
        val e = assertThrows(IllegalStateException::class.java) { RuntimeModule("demo", 1).bind<Undeclared>() }
        assertTrue(e.message!!.endsWith("annotate runtime contract functions with @Throws(Exception::class)"))
    }

    @Test
    fun callsThroughTheContract() {
        register(1)
        val module = RuntimeModule("demo", 1)
        val demo = module.bind<Demo>()
        assertEquals("v1:x", demo.echo("x"))
        assertEquals("v1:x!", demo.echo("x", "!"))
        assertEquals("later:y", runBlocking { demo.later("y") })
        assertSame(demo, module.bind<Demo>())
        assertEquals(demo, demo)
        assertNotEquals(demo, Any())
        assertTrue(demo.toString().startsWith("RuntimeModule(demo"))
    }

    @Test
    fun exceptionsReachTheCallerUnchanged() {
        register(1)
        val demo = RuntimeModule("demo", 1).bind<Demo>()
        assertEquals("HTTP 503", assertThrows(IOException::class.java) { demo.fail(checked = true) }.message)
        assertEquals("from entry", assertThrows(IllegalArgumentException::class.java) { demo.fail(checked = false) }.message)
    }

    @Test
    fun keptProxyFollowsUpdatesAndReportsLostRuntimes() {
        assertTrue(register(2))
        val first = Registration.current()
        assertFalse(register(2, entry = EntryV2))
        assertFalse(register(1, entry = EntryV2))
        assertSame(first, Registration.current())

        val demo = RuntimeModule("demo", 1).bind<Demo>()
        assertEquals("v1:x", demo.echo("x"))
        assertTrue(register(3, entry = EntryV2))
        assertEquals("v2:x", demo.echo("x"))

        assertTrue(register(4, minApi = 2, api = 2, entry = EntryV2))
        unavailable { demo.echo("x") }
    }

    @Test
    fun stateExistsBeforeRegistrationAndSurvivesUpdates() {
        RuntimeHost.state["demo.applied"] = true
        register(1)
        register(2, entry = EntryV2)
        assertEquals(true, RuntimeHost.state["demo.applied"])
    }
}
