package com.example.hardware

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

enum class AcousticSignalQuality {
    IDLE,
    TOO_QUIET,
    OPTIMAL,
    CLIPPING
}

class AudioRecorderManager {

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val maxDurationMs = 20_000L

    private val recorderLock = Any()
    private val playbackLock = Any()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val pcmBuffer = ByteArrayOutputStream()
    private val capturedEnvelope = mutableListOf<Float>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _elapsedMillis = MutableStateFlow(0L)
    val elapsedMillis: StateFlow<Long> = _elapsedMillis.asStateFlow()

    private val _recordedDurationMs = MutableStateFlow(0L)
    val recordedDurationMs: StateFlow<Long> = _recordedDurationMs.asStateFlow()

    private val _currentDb = MutableStateFlow(0)
    val currentDb: StateFlow<Int> = _currentDb.asStateFlow()

    private val _signalQuality = MutableStateFlow(AcousticSignalQuality.IDLE)
    val signalQuality: StateFlow<AcousticSignalQuality> = _signalQuality.asStateFlow()

    private val _waveformSamples = MutableStateFlow(List(WAVEFORM_BAR_COUNT) { IDLE_BAR_LEVEL })
    val waveformSamples: StateFlow<List<Float>> = _waveformSamples.asStateFlow()

    private val _recordedWavBytes = MutableStateFlow<ByteArray?>(null)
    val recordedWavBytes: StateFlow<ByteArray?> = _recordedWavBytes.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAutoStopped: (ByteArray) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isRecording.value) return
        stopPlayback()

        val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufSize <= 0) {
            onError("Audio hardware buffer unavailable on this device.")
            return
        }

        val bufferSize = maxOf(minBufSize * 2, 4096)
        val recorder = createPreferredAudioRecord(bufferSize, onError) ?: return

        synchronized(recorderLock) {
            pcmBuffer.reset()
            capturedEnvelope.clear()
            _recordedWavBytes.value = null
            _recordedDurationMs.value = 0L
            _elapsedSeconds.value = 0
            _elapsedMillis.value = 0L
            _currentDb.value = 0
            _signalQuality.value = AcousticSignalQuality.TOO_QUIET
            _waveformSamples.value = List(WAVEFORM_BAR_COUNT) { IDLE_BAR_LEVEL }
            audioRecord = recorder

            try {
                recorder.startRecording()
            } catch (e: Exception) {
                recorder.release()
                audioRecord = null
                onError(e.localizedMessage ?: "Failed to start audio capture.")
                return
            }
            _isRecording.value = true
        }

        val startTimeMs = System.currentTimeMillis()

        recordingJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ShortArray(1024)
            val byteBuffer = ByteArray(readBuffer.size * 2)

            while (isActive && _isRecording.value) {
                val activeRecorder = synchronized(recorderLock) { audioRecord } ?: break
                val readCount = try {
                    activeRecorder.read(readBuffer, 0, readBuffer.size)
                } catch (_: Exception) {
                    break
                }
                if (readCount > 0) {
                    var sumSquares = 0.0
                    var peakAbs = 0
                    for (i in 0 until readCount) {
                        val sample = readBuffer[i]
                        val sampleInt = sample.toInt()
                        val absVal = abs(sampleInt)
                        if (absVal > peakAbs) peakAbs = absVal
                        sumSquares += sample.toDouble() * sample.toDouble()
                        byteBuffer[i * 2] = (sampleInt and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sampleInt shr 8) and 0xFF).toByte()
                    }
                    synchronized(recorderLock) {
                        pcmBuffer.write(byteBuffer, 0, readCount * 2)
                    }

                    val rms = sqrt(sumSquares / readCount)
                    val normalized = (rms / 8500.0).toFloat().coerceIn(0.06f, 1.0f)
                    val approxSplDb = computeApproxSplDb(rms)
                    val quality = classifySignalQuality(rms = rms, peakAbs = peakAbs)

                    synchronized(recorderLock) {
                        capturedEnvelope.add(normalized)
                    }

                    _currentDb.value = approxSplDb
                    _signalQuality.value = quality
                    _waveformSamples.value = (_waveformSamples.value.drop(1) + normalized)
                }

                val elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtMost(maxDurationMs)
                _elapsedMillis.value = elapsedMs
                _elapsedSeconds.value = (elapsedMs / 1000L).toInt()
                if (elapsedMs >= maxDurationMs) {
                    val wav = stopInternal()
                    if (wav != null) {
                        withContext(Dispatchers.Main) {
                            onAutoStopped(wav)
                        }
                    }
                    break
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createPreferredAudioRecord(
        bufferSize: Int,
        onError: (String) -> Unit
    ): AudioRecord? {
        val preferredSources = buildList {
            // Prefer UNPROCESSED on API 24+ so Android voice noise-suppression doesn't
            // strip out continuous engine hums, bearing whines, or steady pipe hiss.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(MediaRecorder.AudioSource.UNPROCESSED)
            }
            add(MediaRecorder.AudioSource.MIC)
        }

        var lastError: String? = null
        for (source in preferredSources) {
            val candidate = try {
                AudioRecord(source, sampleRate, channelConfig, audioFormat, bufferSize)
            } catch (e: Exception) {
                lastError = e.localizedMessage
                null
            }
            if (candidate != null && candidate.state == AudioRecord.STATE_INITIALIZED) {
                return candidate
            }
            candidate?.release()
        }

        onError(lastError ?: "Microphone failed to initialize.")
        return null
    }

    fun stopRecording(): ByteArray? {
        recordingJob?.cancel()
        recordingJob = null
        return stopInternal()
    }

    fun startPlayback(
        scope: CoroutineScope,
        onError: (String) -> Unit = {}
    ) {
        if (_isRecording.value) return
        val wav = _recordedWavBytes.value
        if (wav == null || wav.size <= WAV_HEADER_SIZE) {
            onError("No captured audio clip available to preview.")
            return
        }

        stopPlayback()

        val pcmData = wav.copyOfRange(WAV_HEADER_SIZE, wav.size)
        val minOutBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            audioFormat
        ).coerceAtLeast(4096)

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minOutBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Audio playback unavailable on this device.")
            return
        }

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            onError("Audio speaker failed to initialize.")
            return
        }

        synchronized(playbackLock) {
            audioTrack = track
            _isPlaying.value = true
            _playbackProgress.value = 0f
            try {
                track.play()
            } catch (e: Exception) {
                track.release()
                audioTrack = null
                _isPlaying.value = false
                onError(e.localizedMessage ?: "Failed to start audio preview.")
                return
            }
        }

        playbackJob = scope.launch(Dispatchers.IO) {
            val chunkSize = 2048
            var offset = 0
            try {
                while (isActive && _isPlaying.value && offset < pcmData.size) {
                    val activeTrack = synchronized(playbackLock) { audioTrack } ?: break
                    val toWrite = minOf(chunkSize, pcmData.size - offset)
                    val written = activeTrack.write(pcmData, offset, toWrite)
                    if (written <= 0) break
                    offset += written
                    _playbackProgress.value = (offset.toFloat() / pcmData.size.toFloat()).coerceIn(0f, 1f)
                }
            } catch (_: Exception) {
            } finally {
                stopPlaybackInternal()
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        stopPlaybackInternal()
    }

    fun clearRecording() {
        stopPlayback()
        if (_isRecording.value) stopRecording()
        synchronized(recorderLock) {
            pcmBuffer.reset()
            capturedEnvelope.clear()
        }
        _recordedWavBytes.value = null
        _recordedDurationMs.value = 0L
        _elapsedSeconds.value = 0
        _elapsedMillis.value = 0L
        _currentDb.value = 0
        _signalQuality.value = AcousticSignalQuality.IDLE
        _waveformSamples.value = List(WAVEFORM_BAR_COUNT) { IDLE_BAR_LEVEL }
        _playbackProgress.value = 0f
    }

    fun release() {
        stopPlayback()
        if (_isRecording.value) {
            stopRecording()
        }
    }

    private fun stopInternal(): ByteArray? = synchronized(recorderLock) {
        _isRecording.value = false
        val recorder = audioRecord
        audioRecord = null
        try {
            if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        val rawPcm = pcmBuffer.toByteArray()
        if (rawPcm.isEmpty()) {
            _signalQuality.value = AcousticSignalQuality.IDLE
            return null
        }

        val durationMs = (rawPcm.size * 1000L) / (sampleRate * 2L)
        _recordedDurationMs.value = durationMs
        _elapsedMillis.value = durationMs
        _elapsedSeconds.value = (durationMs / 1000L).toInt()
        _waveformSamples.value = downsampleWaveform(capturedEnvelope, WAVEFORM_BAR_COUNT)
        _signalQuality.value = AcousticSignalQuality.IDLE

        val wav = buildWavBytes(rawPcm, sampleRate)
        _recordedWavBytes.value = wav
        wav
    }

    private fun stopPlaybackInternal() = synchronized(playbackLock) {
        _isPlaying.value = false
        _playbackProgress.value = 0f
        val track = audioTrack
        audioTrack = null
        try {
            if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
                track.stop()
            }
        } catch (_: Exception) {
        }
        try {
            track?.release()
        } catch (_: Exception) {
        }
    }

    companion object {
        const val WAVEFORM_BAR_COUNT = 44
        const val IDLE_BAR_LEVEL = 0.08f
        const val WAV_HEADER_SIZE = 44

        internal fun computeApproxSplDb(rms: Double): Int {
            val normalizedRms = (rms / 32768.0).coerceAtLeast(0.0003)
            val dbfs = 20.0 * log10(normalizedRms)
            return (dbfs + 96.0).toInt().coerceIn(24, 105)
        }

        internal fun classifySignalQuality(rms: Double, peakAbs: Int): AcousticSignalQuality {
            return when {
                peakAbs >= 31_000 -> AcousticSignalQuality.CLIPPING
                rms < 240.0 -> AcousticSignalQuality.TOO_QUIET
                else -> AcousticSignalQuality.OPTIMAL
            }
        }

        internal fun downsampleWaveform(
            samples: List<Float>,
            targetCount: Int = WAVEFORM_BAR_COUNT
        ): List<Float> {
            if (targetCount <= 0) return emptyList()
            if (samples.isEmpty()) return List(targetCount) { IDLE_BAR_LEVEL }
            if (samples.size <= targetCount) {
                val padded = MutableList(targetCount) { IDLE_BAR_LEVEL }
                val step = targetCount.toFloat() / samples.size.toFloat()
                samples.forEachIndexed { idx, value ->
                    val start = (idx * step).toInt().coerceIn(0, targetCount - 1)
                    val end = ((idx + 1) * step).toInt().coerceIn(start + 1, targetCount)
                    for (slot in start until end) {
                        padded[slot] = value.coerceIn(0.06f, 1.0f)
                    }
                }
                return padded
            }

            return List(targetCount) { bucket ->
                val start = (bucket * samples.size) / targetCount
                val end = (((bucket + 1) * samples.size) / targetCount).coerceAtLeast(start + 1)
                var maxVal = IDLE_BAR_LEVEL
                for (i in start until minOf(end, samples.size)) {
                    if (samples[i] > maxVal) maxVal = samples[i]
                }
                maxVal.coerceIn(0.06f, 1.0f)
            }
        }

        internal fun buildWavBytes(pcmData: ByteArray, sampleRate: Int = 16_000): ByteArray {
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * 2 // 16-bit mono

            val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(totalDataLen)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16) // PCM chunk size
                putShort(1.toShort()) // AudioFormat = 1 (PCM)
                putShort(1.toShort()) // Mono
                putInt(sampleRate)
                putInt(byteRate)
                putShort(2.toShort()) // Block align
                putShort(16.toShort()) // Bits per sample
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(pcmData.size)
            }.array()

            return header + pcmData
        }
    }
}
