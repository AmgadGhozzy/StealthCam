package com.venom.stealthcam.core.extension

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

// ── Permission helpers ────────────────────────────────────────────────────────

fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

fun Context.hasAllRecordingPermissions(): Boolean =
    hasCameraPermission() && hasAudioPermission()

// ── System services ───────────────────────────────────────────────────────────

/**
 * Returns the system [CameraManager]. Throws if the service is unavailable,
 * which should never happen on a device that declares the camera feature.
 */
fun Context.getCameraManager(): CameraManager =
    getSystemService<CameraManager>()
        ?: error("CameraManager is not available on this device")

fun Context.getPowerManager(): PowerManager =
    getSystemService<PowerManager>()
        ?: error("PowerManager is not available on this device")

// ── Storage Access Framework ──────────────────────────────────────────────────

/**
 * Checks whether the app still holds a persistable read+write URI permission
 * for the given [treeUri]. SAF permissions can be revoked by the user or the OS,
 * so this must be verified before every write.
 */
fun Context.hasUriPermission(treeUri: Uri): Boolean {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    return contentResolver.persistedUriPermissions.any { permission ->
        permission.uri == treeUri &&
            permission.isReadPermission &&
            permission.isWritePermission
    }
}
