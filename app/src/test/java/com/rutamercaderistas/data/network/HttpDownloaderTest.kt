package com.rutamercaderistas.data.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpDownloaderTest {

    @Test
    fun `downloadBytes fails for invalid URL`() = runTest {
        val result = downloadBytes(url = "https://invalid.url.example")
        assertTrue(result.isFailure)
    }

    @Test
    fun `downloadBytes fails for empty URL`() = runTest {
        val result = downloadBytes(url = "")
        assertTrue(result.isFailure)
    }

    @Test
    fun `downloadBytes respects custom timeout`() = runTest {
        val result = downloadBytes(
            url = "https://httpbin.org/delay/5",
            connectTimeout = 1000,
            readTimeout = 1000,
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `downloadBytes returns data for valid URL`() = runTest {
        val result = downloadBytes(url = "https://www.google.com")
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }
}
