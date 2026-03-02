package com.hrcoach.service.audio

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

object EarconWaveforms {
    const val SAMPLE_RATE_HZ = 44_100

    private const val ATTACK_MS = 10
    private const val DECAY_MS = 20
    private const val RELEASE_MS = 30
    private const val SUSTAIN_LEVEL = 0.7f
    private const val MAX_AMPLITUDE = Short.MAX_VALUE * 0.7f

    private const val C4 = 261.63
    private const val E4 = 329.63
    private const val G4 = 392.00
    private const val C5 = 523.25
    private const val E5 = 659.25
    private const val G5 = 783.99

    fun risingArpeggio(): ShortArray = concat(
        generateTone(C5, 100),
        silence(50),
        generateTone(E5, 100),
        silence(50),
        generateTone(G5, 100)
    )

    fun fallingArpeggio(): ShortArray = concat(
        generateTone(G4, 140),
        silence(40),
        generateTone(E4, 140),
        silence(40),
        generateTone(C4, 140)
    )

    fun warmChime(): ShortArray = mix(
        generateTone(C5, 300),
        generateTone(E5, 300)
    )

    fun doubleTap(): ShortArray = concat(
        generateTone(800.0, 60),
        silence(130),
        generateTone(800.0, 60)
    )

    fun ascendingFifth(): ShortArray = concat(
        generateTone(C5, 80),
        silence(40),
        generateTone(G5, 80)
    )

    fun sosClicks(): ShortArray = concat(
        generateTone(1000.0, 80),
        silence(40),
        generateTone(1000.0, 80),
        silence(40),
        generateTone(1000.0, 80),
        silence(200),
        generateTone(1000.0, 80),
        silence(40),
        generateTone(1000.0, 80),
        silence(40),
        generateTone(1000.0, 80)
    )

    fun brightPing(): ShortArray = generateTone(1200.0, 150)

    fun generateTone(freqHz: Double, durationMs: Int): ShortArray {
        val totalSamples = samplesFor(durationMs)
        val envelopeParts = envelopeSegments(totalSamples)
        val samples = ShortArray(totalSamples)

        for (index in 0 until totalSamples) {
            val envelope = envelopeAt(index, totalSamples, envelopeParts)
            val raw = sin(2.0 * PI * freqHz * index / SAMPLE_RATE_HZ) * MAX_AMPLITUDE * envelope
            samples[index] = raw.roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return samples
    }

    fun samplesFor(ms: Int): Int {
        return ((ms / 1000f) * SAMPLE_RATE_HZ).roundToInt().coerceAtLeast(1)
    }

    private fun silence(durationMs: Int): ShortArray = ShortArray(samplesFor(durationMs))

    private fun concat(vararg parts: ShortArray): ShortArray {
        val totalSize = parts.sumOf { it.size }
        val out = ShortArray(totalSize)
        var cursor = 0
        parts.forEach { part ->
            part.copyInto(out, destinationOffset = cursor)
            cursor += part.size
        }
        return out
    }

    private fun mix(vararg parts: ShortArray): ShortArray {
        val maxSize = parts.maxOfOrNull { it.size } ?: return ShortArray(0)
        val out = ShortArray(maxSize)

        for (index in 0 until maxSize) {
            var sum = 0f
            var contributors = 0
            parts.forEach { part ->
                if (index < part.size) {
                    sum += part[index].toFloat()
                    contributors += 1
                }
            }
            val normalized = if (contributors > 0) sum / contributors else 0f
            out[index] = normalized.roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    private data class EnvelopeSegments(
        val attack: Int,
        val decay: Int,
        val sustain: Int,
        val release: Int
    )

    private fun envelopeSegments(totalSamples: Int): EnvelopeSegments {
        val nominalAttack = samplesFor(ATTACK_MS)
        val nominalDecay = samplesFor(DECAY_MS)
        val nominalRelease = samplesFor(RELEASE_MS)
        val nominalTotal = nominalAttack + nominalDecay + nominalRelease

        val scale = if (nominalTotal > totalSamples) {
            totalSamples.toFloat() / nominalTotal.toFloat()
        } else {
            1f
        }

        val attack = (nominalAttack * scale).roundToInt()
        val decay = (nominalDecay * scale).roundToInt()
        val release = (nominalRelease * scale).roundToInt()
        val sustain = (totalSamples - attack - decay - release).coerceAtLeast(0)

        return EnvelopeSegments(
            attack = attack,
            decay = decay,
            sustain = sustain,
            release = release
        )
    }

    private fun envelopeAt(index: Int, totalSamples: Int, segments: EnvelopeSegments): Float {
        val attackEnd = segments.attack
        val decayEnd = attackEnd + segments.decay
        val sustainEnd = decayEnd + segments.sustain

        return when {
            index < attackEnd && segments.attack > 0 -> {
                index.toFloat() / segments.attack.toFloat()
            }

            index < decayEnd && segments.decay > 0 -> {
                val t = (index - attackEnd).toFloat() / segments.decay.toFloat()
                1f - ((1f - SUSTAIN_LEVEL) * t)
            }

            index < sustainEnd -> {
                SUSTAIN_LEVEL
            }

            segments.release > 0 -> {
                val t = ((index - sustainEnd).toFloat() / segments.release.toFloat()).coerceIn(0f, 1f)
                (SUSTAIN_LEVEL * (1f - t)).coerceAtLeast(0f)
            }

            else -> {
                if (index == totalSamples - 1) 0f else SUSTAIN_LEVEL
            }
        }
    }
}
