package com.example

import com.example.hardware.AcousticSignalQuality
import com.example.hardware.AudioRecorderManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorderManagerTest {

    @Test
    fun `buildWavBytes writes valid 16kHz 16-bit mono RIFF WAVE header`() {
        val pcm = ByteArray(3200) { (it % 127).toByte() } // 0.1s of 16kHz 16-bit mono
        val wav = AudioRecorderManager.buildWavBytes(pcm, sampleRate = 16_000)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

        val header = ByteBuffer.wrap(wav, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(pcm.size + 36, header.getInt(4))
        assertEquals(16, header.getInt(16)) // PCM chunk size
        assertEquals(1.toShort(), header.getShort(20)) // PCM format
        assertEquals(1.toShort(), header.getShort(22)) // Mono
        assertEquals(16_000, header.getInt(24)) // Sample rate
        assertEquals(32_000, header.getInt(28)) // Byte rate
        assertEquals(2.toShort(), header.getShort(32)) // Block align
        assertEquals(16.toShort(), header.getShort(34)) // Bits per sample
        assertEquals(pcm.size, header.getInt(40)) // Data chunk length
    }

    @Test
    fun `classifySignalQuality distinguishes quiet optimal and clipping audio`() {
        assertEquals(
            AcousticSignalQuality.TOO_QUIET,
            AudioRecorderManager.classifySignalQuality(rms = 120.0, peakAbs = 400)
        )
        assertEquals(
            AcousticSignalQuality.OPTIMAL,
            AudioRecorderManager.classifySignalQuality(rms = 2800.0, peakAbs = 14_000)
        )
        assertEquals(
            AcousticSignalQuality.CLIPPING,
            AudioRecorderManager.classifySignalQuality(rms = 18_000.0, peakAbs = 32_100)
        )
    }

    @Test
    fun `downsampleWaveform preserves peak spikes into fixed bar count`() {
        val raw = List(176) { idx -> if (idx == 88) 0.96f else 0.14f }
        val overview = AudioRecorderManager.downsampleWaveform(raw, targetCount = 44)

        assertEquals(44, overview.size)
        assertTrue("Expected peak spike preserved in middle bucket", overview[22] >= 0.95f)
        assertTrue("Expected baseline bars to stay low", overview.first() <= 0.16f)
    }

    @Test
    fun `computeApproxSplDb maps silent and loud rms within workshop range`() {
        val quietDb = AudioRecorderManager.computeApproxSplDb(10.0)
        val engineDb = AudioRecorderManager.computeApproxSplDb(5000.0)
        val loudDb = AudioRecorderManager.computeApproxSplDb(30_000.0)

        assertTrue(quietDb < engineDb)
        assertTrue(engineDb < loudDb)
        assertTrue(quietDb in 24..45)
        assertTrue(loudDb in 85..105)
    }
}
