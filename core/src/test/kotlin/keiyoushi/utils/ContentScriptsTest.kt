package keiyoushi.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentScriptsTest {

    @Test
    fun messagesRoundTrip() {
        val message = encodeContentScriptMessage("page", """{"index":3,"urls":["a","b"]}""")
        assertEquals("page" to """{"index":3,"urls":["a","b"]}""", decodeContentScriptMessage(message))
    }

    @Test
    fun handshakeHasEmptyChannelAndNullData() {
        assertEquals("" to "null", decodeContentScriptMessage("""{"c":""}"""))
    }

    @Test
    fun scalarDataStaysJson() {
        assertEquals("n" to "3", decodeContentScriptMessage(encodeContentScriptMessage("n", "3")))
        assertEquals("s" to "\"x\"", decodeContentScriptMessage(encodeContentScriptMessage("s", "\"x\"")))
    }

    @Test
    fun malformedMessagesAreIgnored() {
        assertNull(decodeContentScriptMessage("not json"))
        assertNull(decodeContentScriptMessage("""["c"]"""))
        assertNull(decodeContentScriptMessage("""{"d":1}"""))
        assertNull(decodeContentScriptMessage("""{"c":{"x":1}}"""))
    }
}
