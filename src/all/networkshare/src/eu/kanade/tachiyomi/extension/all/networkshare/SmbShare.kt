package eu.kanade.tachiyomi.extension.all.networkshare

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import keiyoushi.zip.Entry
import keiyoushi.zip.MAX_EOCD_SEARCH
import keiyoushi.zip.ZipDirectory
import keiyoushi.zip.findEocd
import keiyoushi.zip.parseCentralDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.min

class SmbShare(
    private val host: String,
    private val port: Int,
    private val shareName: String,
    private val root: List<String>,
    private val auth: AuthenticationContext,
) : Share {
    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withSoTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Shares that require encryption refuse unencrypted sessions; servers without SMB 3 still connect
            .withEncryptData(true)
            .build(),
    )

    private val mutex = Mutex()

    @Volatile
    private var diskShare: DiskShare? = null

    override suspend fun list(path: String): List<RemoteFile> = withShare { share ->
        share.list(smbPath(path))
            // System entries: Windows keeps them in every shared drive's root
            .filter { it.fileName != "." && it.fileName != ".." && !it.fileAttributes.isSet(FileAttributes.FILE_ATTRIBUTE_SYSTEM) }
            .map {
                RemoteFile(
                    name = it.fileName,
                    isDirectory = it.fileAttributes.isSet(FileAttributes.FILE_ATTRIBUTE_DIRECTORY),
                    size = it.endOfFile,
                    lastModified = it.lastWriteTime.toEpochMillis(),
                )
            }
    }

    override suspend fun read(path: String, range: LongRange?): BufferedSource = withShare { share ->
        val file = share.open(path)
        val size = file.size
        SmbSource(share, file, range?.first ?: 0, min(size, range?.let { it.last + 1 } ?: size)).buffer()
    }

    override suspend fun zipDirectory(path: String): ZipDirectory = withShare { share ->
        share.open(path).use { file ->
            val size = file.size
            val tailStart = size - min(size, MAX_EOCD_SEARCH.toLong())
            val tail = share.readFully(file, tailStart, (size - tailStart).toInt())
            val eocd = findEocd(tail)
            // An archive can follow other data (self-extracting archives); its offsets count from its own start
            val zipStart = tailStart + eocd.recordOffset - eocd.cdSize - eocd.cdOffset
            val cdInTail = eocd.recordOffset - eocd.cdSize
            val cd = if (cdInTail >= 0) {
                tail.copyOfRange(cdInTail.toInt(), (cdInTail + eocd.cdSize).toInt())
            } else {
                share.readFully(file, zipStart + eocd.cdOffset, eocd.cdSize.toInt())
            }
            val entries = parseCentralDirectory(cd).map {
                Entry(it.name, it.method, it.compressedSize, zipStart + it.localHeaderOffset, it.localHeaderAllowance)
            }
            ZipDirectory(entries, zipStart + eocd.cdOffset)
        }
    }

    override fun url(path: String): String = (listOf("smb://$host${if (port == DEFAULT_PORT) "" else ":$port"}", shareName) + root + segments(path))
        .joinToString("/")

    private suspend fun <T> withShare(block: (DiskShare) -> T): T = withContext(Dispatchers.IO) {
        translated {
            val share = connected()
            try {
                block(share)
            } catch (e: SMBApiException) {
                throw e
            } catch (e: SMBRuntimeException) {
                drop(share)
                // A connection the server has since closed (a NAS that slept, another network) fails at once
                if (e.isTimeout) throw e
                block(connected())
            }
        }
    }

    private suspend fun connected(): DiskShare = mutex.withLock {
        diskShare?.takeIf { it.isConnected } ?: connect().also { diskShare = it }
    }

    private fun connect(): DiskShare {
        val connection = try {
            client.connect(host, port)
        } catch (e: TransportException) {
            throw e
        } catch (e: IOException) {
            throw IOException("Can't connect to $host: ${e.message}", e)
        }
        return connection.authenticate(auth).connectShare(shareName) as DiskShare
    }

    /** Closes [share]'s connection, which smbj keeps using after a request times out. */
    private fun drop(share: DiskShare) {
        if (diskShare === share) diskShare = null
        runCatching { share.treeConnect.session.connection.close(true) }
    }

    private inline fun <T> translated(block: () -> T): T = try {
        block()
    } catch (e: SMBApiException) {
        throw e.toIOException()
    } catch (e: SMBRuntimeException) {
        throw IOException(if (e.isTimeout) "No answer from $host" else "Lost the connection to $host", e)
    } catch (e: TransportException) {
        throw IOException(if (e.isTimeout) "No answer from $host" else "Can't connect to $host", e)
    }

    private fun Long.isSet(attribute: FileAttributes) = EnumWithValue.EnumUtils.isSet(this, attribute)

    private val Throwable.isTimeout get() = generateSequence(this) { it.cause }.any { it is TimeoutException }

    private fun DiskShare.open(path: String): File = openFile(
        smbPath(path),
        EnumSet.of(AccessMask.GENERIC_READ),
        null,
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        null,
    )

    private fun smbPath(path: String) = (root + segments(path)).joinToString("\\")

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun SMBApiException.toIOException() = when (status) {
        NtStatus.STATUS_LOGON_FAILURE -> IOException("Wrong username or password")
        NtStatus.STATUS_ACCESS_DENIED -> IOException("Access denied; check the username and the share's permissions")
        NtStatus.STATUS_BAD_NETWORK_NAME -> IOException("No share named \"$shareName\" on $host")
        NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND -> IOException("Not found on the share")
        else -> IOException(message, this)
    }

    private val File.size: Long
        get() = getFileInformation(FileStandardInformation::class.java).endOfFile

    private fun DiskShare.readFully(file: File, offset: Long, length: Int): ByteArray = SmbSource(this, file, offset, offset + length).buffer().use {
        it.readByteArray()
    }

    /** Reads [file] from [position] up to [end] (exclusive) in large requests, since each read is a round trip. */
    private inner class SmbSource(
        private val share: DiskShare,
        private val file: File,
        private var position: Long,
        private val end: Long,
    ) : Source {
        private val chunk = ByteArray(min(CHUNK_SIZE.toLong(), end - position).coerceAtLeast(0).toInt())
        private val buffer = Buffer()

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (buffer.exhausted()) {
                if (position >= end) return -1
                val read = try {
                    translated { file.read(chunk, position, 0, min(chunk.size.toLong(), end - position).toInt()) }
                } catch (e: IOException) {
                    if (e.cause !is SMBApiException) drop(share)
                    throw e
                }
                if (read <= 0) return -1
                buffer.write(chunk, 0, read)
                position += read
            }
            return buffer.read(sink, byteCount)
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = file.close()
    }

    companion object {
        const val DEFAULT_PORT = 445
        private const val TIMEOUT_SECONDS = 20L
        private const val CHUNK_SIZE = 1 shl 20
    }
}
