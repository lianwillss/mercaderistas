package com.rutamercaderistas.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `tag v12_01 decodifica a 12001`() {
        assertEquals(12001, UpdateChecker.tagToVersionCode("v12.01"))
    }

    @Test
    fun `tag sin parche usa minor 0`() {
        assertEquals(12000, UpdateChecker.tagToVersionCode("v12"))
    }

    @Test
    fun `tag invalido devuelve null`() {
        assertNull(UpdateChecker.tagToVersionCode("release-final"))
    }

    @Test
    fun `instalado 11101 ofrece 12001 una sola vez`() {
        val remote = UpdateChecker.tagToVersionCode("v12.01")!!
        // App vieja (12.01 con código roto 11101): hay update
        assertTrue(remote > 11101)
        // App ya instalada (12.01 con código 12001): NO hay update
        assertTrue(remote <= 12001)
    }

    @Test
    fun `misma version no ofrece update`() {
        val remote = UpdateChecker.tagToVersionCode("v11.95")!!
        assertTrue(remote <= 11095)
    }

    @Test
    fun `comparacion semantica parte por parte`() {
        assertTrue(UpdateChecker.isRemoteNewer("v12.02", 12001))
        assertFalse(UpdateChecker.isRemoteNewer("v12.02", 12002))
        assertFalse(UpdateChecker.isRemoteNewer("v12.01", 12002))
        assertFalse(UpdateChecker.isRemoteNewer("release-final", 12002))
    }

    @Test
    fun `parche de tres partes decide`() {
        assertTrue(UpdateChecker.isRemoteNewer("v12.02.1", 12002))
        assertFalse(UpdateChecker.isRemoteNewer("v12.02.0", 12002))
    }

    @Test
    fun `minor grande no rompe la comparacion`() {
        assertTrue(UpdateChecker.isRemoteNewer("v11.100", 11099))
        assertFalse(UpdateChecker.isRemoteNewer("v11.100", 11100))
    }

    @Test
    fun `decodeVersionCode mantiene esquema`() {
        assertEquals(listOf(12, 2), UpdateChecker.decodeVersionCode(12002))
    }
}
