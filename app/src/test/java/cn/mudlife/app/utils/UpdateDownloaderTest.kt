package cn.mudlife.app.utils

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UpdateDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val servers = mutableListOf<ServerSocket>()
    private val workers = Executors.newCachedThreadPool()
    private val client = OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build()
    private val downloader = UpdateDownloader(client)
    private fun apk(marker: Byte) = ByteArray(320 * 1024) { marker }.apply {
        this[0] = 0x50; this[1] = 0x4b; this[2] = 3; this[3] = 4
    }
    private fun endpoint(handler: (Socket) -> Unit): String {
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        servers += server
        workers.submit {
            while (!server.isClosed) try {
                val socket = server.accept()
                workers.submit {
                    socket.use {
                        try {
                            val reader = it.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { /* Read request headers. */ }
                            handler(it)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) { break }
        }
        return "http://127.0.0.1:${server.localPort}/apk"
    }
    private fun send(socket: Socket, data: ByteArray, chunked: Boolean = false) {
        val output = socket.getOutputStream()
        val length = if (chunked) "Transfer-Encoding: chunked" else "Content-Length: ${data.size}"
        output.write("HTTP/1.1 200 OK\r\n$length\r\nConnection: close\r\n\r\n".toByteArray())
        if (chunked) output.write("${data.size.toString(16)}\r\n".toByteArray())
        output.write(data)
        if (chunked) output.write("\r\n0\r\n\r\n".toByteArray())
        output.flush()
    }
    @After fun close() {
        servers.forEach { it.close() }; workers.shutdownNow()
        client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll()
    }

    @Test(timeout = 8000) fun slowFirstSourceDoesNotDelayFastSourceAndPrefixIsPreserved() = runBlocking {
        val release = CountDownLatch(1)
        val slow = endpoint { release.await(3, TimeUnit.SECONDS); send(it, apk(1)) }
        val expected = apk(2)
        val fast = endpoint { send(it, expected) }
        val target = temporary.root.resolve("update.apk")
        try {
            downloader.download(listOf(slow, fast), target, {}) { assertArrayEquals(expected, it.readBytes()) }
            assertArrayEquals(expected, target.readBytes())
            assertFalse(temporary.root.resolve("update.apk.part").exists())
        } finally { release.countDown() }
    }

    @Test(timeout = 8000) fun htmlReplyIsRejectedBeforeWritingFinalFile() = runBlocking {
        val bad = endpoint { send(it, "<html>rate limit</html>".toByteArray()) }
        val expected = apk(3)
        val good = endpoint { send(it, expected) }
        val target = temporary.root.resolve("update.apk")
        downloader.download(listOf(bad, good), target, {}) { assertArrayEquals(expected, it.readBytes()) }
        assertArrayEquals(expected, target.readBytes())
    }

    @Test(timeout = 8000) fun validationFailureReopensOtherCandidateWithoutKeepingBadApk() = runBlocking {
        val rejected = CountDownLatch(1)
        val bad = endpoint { send(it, apk(4)) }
        val expected = apk(5)
        val good = endpoint { rejected.await(2, TimeUnit.SECONDS); send(it, expected) }
        val target = temporary.root.resolve("update.apk")
        downloader.download(listOf(bad, good), target, {}) {
            if (it.readBytes()[4] == 4.toByte()) { rejected.countDown(); error("Wrong signing identity") }
            assertArrayEquals(expected, it.readBytes())
        }
        assertArrayEquals(expected, target.readBytes())
    }

    @Test(timeout = 8000) fun chunkedResponseReportsBytesWithoutFakeZeroPercent() = runBlocking {
        val expected = apk(6)
        val source = endpoint { send(it, expected, chunked = true) }
        val states = mutableListOf<UpdateDownloadProgress>()
        val target = temporary.root.resolve("update.apk")
        downloader.download(listOf(source), target, { states += it }) { assertArrayEquals(expected, it.readBytes()) }
        assertTrue(states.all { it.fraction == null })
        assertEquals(expected.size.toLong(), states.last().bytes)
        assertEquals(UpdateDownloadPhase.VERIFYING, states.last().phase)
    }

    @Test(timeout = 8000) fun cancellingSourceSelectionStopsAndLeavesNoPartialFile() = runBlocking {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val sources = List(2) { endpoint { entered.countDown(); release.await(3, TimeUnit.SECONDS); send(it, apk(7)) } }
        val target = temporary.root.resolve("update.apk")
        val job = launch(Dispatchers.IO) { downloader.download(sources, target, {}) {} }
        try {
            withContext(Dispatchers.IO) { assertTrue(entered.await(3, TimeUnit.SECONDS)) }
            withTimeout(2000) { job.cancelAndJoin() }
            assertFalse(target.exists()); assertFalse(temporary.root.resolve("update.apk.part").exists())
        } finally { release.countDown() }
    }

    @Test fun progressDescribesConnectingAndActualTransferWithoutAssumingContentLength() {
        assertNull(UpdateDownloadProgress().fraction)
        assertEquals("正在连接下载源…", UpdateDownloadProgress().description)
        val state = UpdateDownloadProgress(UpdateDownloadPhase.DOWNLOADING, 1048576, 4194304, 524288, "Gitee")
        assertEquals(.25f, state.fraction!!, .001f)
        assertEquals("Gitee · 1.0 MB / 4.0 MB · 512 KB/s", state.description)
        assertNull(state.copy(totalBytes = -1).fraction)
    }
}
