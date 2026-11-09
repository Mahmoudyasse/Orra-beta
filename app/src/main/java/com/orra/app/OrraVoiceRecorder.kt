package com.orra.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.audiofx.AcousticEchoCanceler
import android.os.Handler
import android.os.Looper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class OrraVoiceRecorder(private val outputDir: File) {
    companion object {
        const val RATE = 16000
        private fun writeIntLE(out: FileOutputStream, value: Int) {
            out.write(value and 255); out.write((value shr 8) and 255)
            out.write((value shr 16) and 255); out.write((value shr 24) and 255)
        }
        private fun writeShortLE(out: FileOutputStream, value: Int) {
            out.write(value and 255); out.write((value shr 8) and 255)
        }
    }

    @Volatile private var recording = false
    @Volatile private var paused = false
    private var audioRecord: AudioRecord? = null
    private var noise: NoiseSuppressor? = null
    private var echo: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null
    private val main = Handler(Looper.getMainLooper())

    fun noiseSuppressionAvailable() = NoiseSuppressor.isAvailable()

    fun start(onComplete: (File) -> Unit, onError: (String) -> Unit) {
        if (recording) return
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { onError("Audio format is not supported on this device."); return }
        fun tryOpen(source: Int): AudioRecord? = runCatching {
            AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 4096))
        }.getOrNull()?.let { candidate ->
            if (candidate.state == AudioRecord.STATE_INITIALIZED) candidate else { candidate.release(); null }
        }
        val rec = tryOpen(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            ?: tryOpen(MediaRecorder.AudioSource.MIC)
            ?: run { onError("Microphone could not be initialized."); return }
        audioRecord = rec
        noise = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        echo = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        agc = if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
        try { rec.startRecording() } catch (_: Exception) {
            release(); onError("Could not start microphone recording."); return
        }
        paused = false
        recording = true
        Thread {
            val raw = File(outputDir, "orra_voice_" + System.currentTimeMillis() + ".pcm")
            val wav = File(outputDir, raw.nameWithoutExtension + ".wav")
            var size = 0
            try {
                BufferedOutputStream(FileOutputStream(raw)).use { stream ->
                    val buffer = ByteArray(4096)
                    while (recording) {
                        val n = rec.read(buffer, 0, buffer.size)
                        if (n > 0) { if (!paused) { stream.write(buffer, 0, n); size += n } }
                        else if (n < 0) break
                    }
                }
                if (size > 0) {
                    FileOutputStream(wav).use { out ->
                        out.write("RIFF".toByteArray(Charsets.US_ASCII))
                        writeIntLE(out, 36 + size)
                        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                        writeIntLE(out, 16); writeShortLE(out, 1); writeShortLE(out, 1)
                        writeIntLE(out, RATE); writeIntLE(out, RATE * 2)
                        writeShortLE(out, 2); writeShortLE(out, 16)
                        out.write("data".toByteArray(Charsets.US_ASCII)); writeIntLE(out, size)
                        raw.inputStream().use { it.copyTo(out) }
                    }
                    raw.delete()
                    main.post { onComplete(wav) }
                } else {
                    raw.delete()
                    main.post { onError("No audio was captured. Check microphone access.") }
                }
            } catch (_: Exception) {
                raw.delete()
                main.post { onError("Recording could not be saved.") }
            } finally { release() }
        }.apply { name = "OrraVoiceRecorder"; start() }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    fun stop() {
        recording = false
        runCatching { audioRecord?.stop() }
    }

    private fun release() {
        runCatching { noise?.release() }; runCatching { echo?.release() }
        runCatching { agc?.release() }; runCatching { audioRecord?.release() }
        noise = null; echo = null; agc = null; audioRecord = null
    }
}
