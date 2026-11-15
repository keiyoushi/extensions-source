package keiyoushi.lib.runtime

import java.io.IOException

/** The Keiyoushi Runtime is missing or too old. The app shows the message as is. */
class RuntimeUnavailableException(message: String) : IOException(message)
