package com.venom.stealthcam.data.recording

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import com.venom.stealthcam.domain.model.RecordingConfig
import com.venom.stealthcam.domain.model.VideoCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages the full Camera2 + MediaRecorder lifecycle for a single recording session.
 *
 * Threading model:
 * — A dedicated [HandlerThread] ("StealthCam-Camera") is started at the beginning of
 *   [start] and stopped at the end of [stop]. All Camera2 callbacks run on this thread.
 * — [start] is a suspend function that must be called from an IO-capable coroutine.
 * — [stop] dispatches blocking cleanup to [Dispatchers.IO] internally.
 *
 * Resource release order (must be respected to avoid Camera HAL errors):
 *   1. MediaRecorder.stop() — flush + close the output file
 *   2. CameraCaptureSession.close() — stop frame production
 *   3. CameraDevice.close() — release the physical camera
 *   4. MediaRecorder.release() — free native resources
 *   5. HandlerThread.quitSafely() — drain remaining messages, then stop
 */
@Singleton
class RecordingEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cameraManager: CameraManager
) {
    @Volatile private var cameraDevice: CameraDevice? = null
    @Volatile private var captureSession: CameraCaptureSession? = null
    @Volatile private var mediaRecorder: MediaRecorder? = null
    @Volatile private var handlerThread: HandlerThread? = null
    @Volatile private var cameraHandler: Handler? = null

    private val mutex = Mutex()

    /**
     * Opens the camera, creates a capture session, configures [MediaRecorder], and
     * starts the encoder.
     *
     * @param config          Fully resolved recording parameters.
     * @param onAsyncError    Called on the camera handler thread when Camera2 or MediaRecorder
     *                        signals a hardware error after [start] has already returned.
     * @param onMaxLimitReached Called when [RecordingConfig.maxDurationMs] or
     *                          [RecordingConfig.maxFileSizeBytes] is reached.
     *
     * @throws Exception if any step of the setup sequence fails.
     */
    suspend fun start(
        config: RecordingConfig,
        onAsyncError: (Throwable) -> Unit,
        onMaxLimitReached: () -> Unit
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
        // ── 1. Start the camera handler thread ───────────────────────────────
        val thread = HandlerThread("StealthCam-Camera").also {
            it.start()
            handlerThread = it
        }
        val handler = Handler(thread.looper).also { cameraHandler = it }
        val executor = Executor { runnable -> handler.post(runnable) }

        // ── 2. Build and prepare MediaRecorder — must happen before session ──
        val recorder = buildMediaRecorder(config, onAsyncError, onMaxLimitReached)
            .also { mediaRecorder = it }
        recorder.prepare()
        val recorderSurface = recorder.surface

        // ── 3. Open the physical camera device (with timeout) ───────────────────────────────
        val camera = kotlinx.coroutines.withTimeout(5000L) {
            openCamera(config.cameraId, handler, onAsyncError)
        }.also { cameraDevice = it }

        // ── 4. Create a capture session (with timeout) ──
        val session = kotlinx.coroutines.withTimeout(5000L) {
            createCaptureSession(camera, recorderSurface, executor)
        }.also { captureSession = it }

        // ── 5. Build the capture request with optional stabilisation ─────────
        val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(recorderSurface)
            if (config.enableElectronicStabilization) {
                set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }
            if (config.enableOpticalStabilization) {
                set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON
                )
            }
            // Continuous auto-focus for video — ignored on cameras that don't support it
            set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            )
        }
        session.setRepeatingRequest(requestBuilder.build(), null, handler)

        // ── 6. Begin encoding ────────────────────────────────────────────────
        recorder.start()
        }
    }

    /**
     * Stops encoding, releases all Camera2 and MediaRecorder resources in the
     * correct order, and shuts down the handler thread.
     * Safe to call even if [start] was never called or already failed.
     */
    suspend fun stop() = mutex.withLock {
        withContext(Dispatchers.IO) {
        try {
            mediaRecorder?.stop()
        } catch (_: RuntimeException) {
            // stop() throws RuntimeException if no frames were recorded
            // (e.g. the caller stops before the first I-frame). Swallow safely.
        }
        captureSession?.close()
        cameraDevice?.close()
        mediaRecorder?.release()
        handlerThread?.quitSafely()

        captureSession = null
        cameraDevice = null
        mediaRecorder = null
        handlerThread = null
        cameraHandler = null
        }
    }

    // ── Private: Camera2 suspend bridges ────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        cameraId: String,
        handler: Handler,
        onAsyncError: (Throwable) -> Unit
    ): CameraDevice = suspendCancellableCoroutine { continuation ->

        val stateCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                continuation.resume(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                val error = IllegalStateException(
                    "Camera $cameraId was disconnected before recording could start"
                )
                if (continuation.isActive) continuation.resumeWithException(error)
                else onAsyncError(error)
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                val ex = CameraAccessException(
                    CameraAccessException.CAMERA_ERROR,
                    "Camera $cameraId hardware error — code $error"
                )
                if (continuation.isActive) continuation.resumeWithException(ex)
                else onAsyncError(ex)
            }
        }

        continuation.invokeOnCancellation {
            // Coroutine cancelled before onOpened fired — release partial state
            cameraDevice?.close()
            cameraDevice = null
        }

        cameraManager.openCamera(cameraId, stateCallback, handler)
    }

    private suspend fun createCaptureSession(
        camera: CameraDevice,
        surface: android.view.Surface,
        executor: Executor
    ): CameraCaptureSession = suspendCancellableCoroutine { continuation ->

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                continuation.resume(session)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                continuation.resumeWithException(
                    IllegalStateException(
                        "CaptureSession configuration failed for camera ${camera.id}"
                    )
                )
            }
        }

        val outputConfig = OutputConfiguration(surface)
        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            listOf(outputConfig),
            executor,
            stateCallback
        )
        camera.createCaptureSession(sessionConfig)
    }

    // ── Private: MediaRecorder factory ───────────────────────────────────────

    private fun buildMediaRecorder(
        config: RecordingConfig,
        onAsyncError: (Throwable) -> Unit,
        onMaxLimitReached: () -> Unit
    ): MediaRecorder = MediaRecorder(context).apply {

        // Audio must be set before video source
        if (config.isAudioEnabled) {
            setAudioSource(MediaRecorder.AudioSource.MIC)
        }
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(config.outputFilePath)

        // Embed the rotation in the MP4 container so players display it correctly.
        // Must be called after setOutputFormat() and before prepare().
        setOrientationHint(config.orientationHint)

        // Video encoding parameters
        setVideoEncodingBitRate(config.videoBitrateBps)
        setVideoFrameRate(config.frameRate)
        setVideoSize(config.resolution.width, config.resolution.height)
        setVideoEncoder(config.videoCodec.toMediaRecorderEncoder())

        // Audio encoding parameters (AAC-LC at 128 kbps, 44.1 kHz)
        if (config.isAudioEnabled) {
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setAudioChannels(1) // Mono — sufficient for stealth recording
        }

        // Optional limits
        config.maxDurationMs?.let { setMaxDuration(it.toInt()) }
        config.maxFileSizeBytes?.let { setMaxFileSize(it) }

        // Asynchronous error and info listeners
        setOnErrorListener { _, what, extra ->
            onAsyncError(
                RuntimeException("MediaRecorder hardware error: what=$what extra=$extra")
            )
        }
        setOnInfoListener { _, what, _ ->
            when (what) {
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED,
                MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> onMaxLimitReached()
            }
        }
    }

    // ── Domain → MediaRecorder mapping ───────────────────────────────────────

    private fun VideoCodec.toMediaRecorderEncoder(): Int = when (this) {
        VideoCodec.H264 -> MediaRecorder.VideoEncoder.H264
        VideoCodec.H265 -> MediaRecorder.VideoEncoder.HEVC
    }
}
