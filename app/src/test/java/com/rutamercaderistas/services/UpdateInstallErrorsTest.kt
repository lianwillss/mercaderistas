package com.rutamercaderistas.services

import android.content.pm.PackageInstaller
import com.rutamercaderistas.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateInstallErrorsTest {

    @Test
    fun `cada fallo del instalador tiene su mensaje`() {
        assertEquals(
            R.string.update_instalacion_bloqueada,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE_BLOCKED),
        )
        assertEquals(
            R.string.update_instalacion_cancelada,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE_ABORTED),
        )
        assertEquals(
            R.string.update_instalacion_apk_invalido,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE_INVALID),
        )
        assertEquals(
            R.string.update_instalacion_conflicto,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE_CONFLICT),
        )
        assertEquals(
            R.string.update_instalacion_sin_espacio,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE_STORAGE),
        )
        assertEquals(
            R.string.update_instalacion_incompatible,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE),
        )
        assertEquals(
            R.string.update_instalacion_timeout,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE_TIMEOUT),
        )
    }

    @Test
    fun `fallo generico o desconocido usa el mensaje base`() {
        assertEquals(
            R.string.update_instalacion_error,
            UpdateInstallErrors.messageRes(PackageInstaller.STATUS_FAILURE),
        )
        assertEquals(
            R.string.update_instalacion_error,
            UpdateInstallErrors.messageRes(99),
        )
    }

    @Test
    fun `los codigos tienen nombre para el log`() {
        assertEquals(
            "STATUS_FAILURE_BLOCKED",
            UpdateInstallErrors.statusName(PackageInstaller.STATUS_FAILURE_BLOCKED),
        )
        assertEquals(
            "STATUS_FAILURE_CONFLICT",
            UpdateInstallErrors.statusName(PackageInstaller.STATUS_FAILURE_CONFLICT),
        )
        assertEquals("STATUS_FAILURE_TIMEOUT", UpdateInstallErrors.statusName(8))
        assertEquals("STATUS_DESCONOCIDO", UpdateInstallErrors.statusName(123))
    }
}
