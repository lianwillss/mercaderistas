package com.rutamercaderistas.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.net.ServerSocket
import kotlin.concurrent.thread

class HttpDownloaderTest {

    @Test
    fun `downloadBytes returns response body`() = runTest {
        LocalHttpServer(response(200, "OK", "hello")).use { server ->
            val result = downloadBytes(server.url)

            assertTrue(result.isSuccess)
            assertArrayEquals("hello".toByteArray(), result.getOrThrow())
        }
    }

    @Test
    fun `downloadBytes returns explicit failure for non successful status`() = runTest {
        LocalHttpServer(response(503, "Service Unavailable", "unavailable")).use { server ->
            val result = downloadBytes(server.url)

            assertTrue(result.isFailure)
            assertEquals(503, (result.exceptionOrNull() as HttpStatusException).statusCode)
        }
    }

    @Test
    fun `downloadBytes enforces size limit when content length is unknown`() = runTest {
        val chunkedResponse = "HTTP/1.1 200 OK\r\n" +
            "Transfer-Encoding: chunked\r\n" +
            "Connection: close\r\n\r\n" +
            "6\r\n123456\r\n0\r\n\r\n"
        LocalHttpServer(chunkedResponse).use { server ->
            val result = downloadBytes(server.url, maxBytes = 5)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is DownloadSizeLimitException)
        }
    }

    @Test
    fun `downloadBytes follows redirects`() = runTest {
        val redirect = "HTTP/1.1 302 Found\r\n" +
            "Location: /content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        LocalHttpServer(redirect, response(200, "OK", "data")).use { server ->
            val result = downloadBytes(server.url, maxRedirects = 1)

            assertTrue(result.isSuccess)
            assertArrayEquals("data".toByteArray(), result.getOrThrow())
        }
    }

    @Test
    fun `downloadBytes fails for invalid URL`() = runTest {
        assertTrue(downloadBytes("not a URL").isFailure)
        assertTrue(downloadBytes("").isFailure)
    }

    private class LocalHttpServer(vararg responses: String) : Closeable {
        private val server = ServerSocket(0)
        private val serverThread = thread(isDaemon = true) {
            responses.forEach { response ->
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    socket.getOutputStream().apply {
                        write(response.toByteArray())
                        flush()
                    }
                }
            }
        }

        val url: String = "http://127.0.0.1:${server.localPort}/"

        override fun close() {
            server.close()
            serverThread.join(1_000)
        }
    }

    private companion object {
        fun response(status: Int, reason: String, body: String): String =
            "HTTP/1.1 $status $reason\r\n" +
                "Content-Length: ${body.toByteArray().size}\r\n" +
                "Connection: close\r\n\r\n$body"
    }

    @Test
    fun `sha256Hex matches known vectors`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray()),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `sha256Hex is stable and sensitive to content`() {
        val a = sha256Hex("rutero-v1".toByteArray())
        val b = sha256Hex("rutero-v1".toByteArray())
        val c = sha256Hex("rutero-v2".toByteArray())
        assertNotNull(a)
        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(64, a!!.length)
    }
}
