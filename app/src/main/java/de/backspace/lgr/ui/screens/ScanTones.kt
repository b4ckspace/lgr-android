// SPDX-FileCopyrightText: 2026 Andreas Uhsemann
// SPDX-License-Identifier: Apache-2.0

package de.backspace.lgr.ui.screens

import android.media.AudioManager
import android.media.ToneGenerator
import de.backspace.lgr.viewmodel.ScanResult
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Shared, queued audio feedback for all barcode scanners.
 *
 * Two problems are solved here:
 *
 * 1. A freshly constructed [ToneGenerator] allocates a cold `AudioTrack` whose first tone is
 *    routinely clipped while the audio output warms up. We keep a single instance alive for the
 *    app's lifetime ([warmUp] is called at app start) and only ever call `startTone` on it.
 *
 * 2. `startTone` is asynchronous; firing the next tone before the previous one has actually finished
 *    cuts it off (choppy beeps). So every tone goes through a single-threaded **queue**: one worker
 *    plays a tone, sleeps for its full duration *and* its trailing pause, and only then plays the
 *    next. Tones therefore never overlap and a given incident always sounds identical.
 *
 * Scanners call [ack] the instant a barcode is decoded (before any network lookup) so the
 * acknowledge beep is immediate, then enqueue the result-specific tones via [resultFollowUp] /
 * [notFound] / [rising]; the queue keeps them correctly ordered behind the ack.
 */
object ScanTones {
    private const val MAX_VOLUME = 100

    // Tone durations and the silence that follows each (ms).
    private const val ACK_MS = 120
    private const val ACK_PAUSE = 80
    private const val FOLLOW_MS = 90
    private const val FOLLOW_PAUSE = 80
    private const val NACK_MS = 300
    private const val RISING_MS = 80
    private const val RISING_PAUSE = 40

    /** One queued tone: which `ToneGenerator` tone, how long to play it, how long to stay silent after. */
    private data class Tone(val type: Int, val durationMs: Int, val pauseMs: Int)

    private val queue = LinkedBlockingQueue<Tone>()

    @Volatile
    private var generator: ToneGenerator? = null

    @Volatile
    private var unavailable = false

    private var worker: Thread? = null

    /** Create the generator and start the playback worker ahead of the first scan. */
    fun warmUp() {
        ensureStarted()
    }

    @Synchronized
    private fun ensureStarted() {
        if (worker != null || unavailable) return
        val g = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, MAX_VOLUME) }.getOrNull()
        if (g == null) {
            unavailable = true
            return
        }
        generator = g
        worker = thread(isDaemon = true, name = "ScanTones") {
            while (true) {
                val tone = try {
                    queue.take()
                } catch (_: InterruptedException) {
                    continue
                }
                runCatching {
                    g.startTone(tone.type, tone.durationMs)
                    // Wait out the tone itself plus its trailing pause before touching the
                    // generator again, so the next tone can never clip this one.
                    Thread.sleep((tone.durationMs + tone.pauseMs).toLong())
                }
            }
        }
    }

    private fun enqueue(vararg tones: Tone) {
        ensureStarted()
        if (unavailable) return
        for (t in tones) queue.offer(t)
    }

    /** Immediate acknowledge beep — a barcode was decoded. Enqueue this before any lookup. */
    fun ack() = enqueue(Tone(ToneGenerator.TONE_PROP_BEEP, ACK_MS, ACK_PAUSE))

    /** Descending "burp" — the scanned barcode is unknown / not found. */
    fun notFound() = enqueue(Tone(ToneGenerator.TONE_PROP_NACK, NACK_MS, 0))

    /**
     * Result feedback enqueued *after* the [ack] beep once the lookup completes: nothing for a new
     * item, one extra beep for an item already expected here, two for a duplicate already scanned in
     * this session, and a burp for an unknown barcode.
     */
    fun resultFollowUp(result: ScanResult) {
        when (result) {
            ScanResult.FOUND_NEW -> {} // the acknowledge beep already conveyed it
            ScanResult.FOUND_EXISTING ->
                enqueue(Tone(ToneGenerator.TONE_DTMF_A, FOLLOW_MS, 0))
            ScanResult.DUPLICATE ->
                enqueue(
                    Tone(ToneGenerator.TONE_DTMF_A, FOLLOW_MS, FOLLOW_PAUSE),
                    Tone(ToneGenerator.TONE_DTMF_A, FOLLOW_MS, 0),
                )
            ScanResult.NOT_FOUND ->
                enqueue(Tone(ToneGenerator.TONE_PROP_NACK, NACK_MS, 0))
        }
    }

    /** Rising three-step tone — an invalid action (e.g. scanning the container's own code). */
    fun rising() = enqueue(
        Tone(ToneGenerator.TONE_DTMF_1, RISING_MS, RISING_PAUSE),
        Tone(ToneGenerator.TONE_DTMF_A, RISING_MS, RISING_PAUSE),
        Tone(ToneGenerator.TONE_DTMF_D, RISING_MS, 0),
    )
}
