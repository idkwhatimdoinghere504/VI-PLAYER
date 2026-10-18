package com.viplayer.playback

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqBand(
    val index: Int,
    val centerHz: Int,
    val levelMb: Int,
    val minMb: Int,
    val maxMb: Int
)

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val presetIndex: Int = -1
)

/**
 * Process-wide equalizer bound to the ExoPlayer audio session.
 *
 * The playback service and the UI share a process, so a singleton keeps the
 * effect and its controller in sync. Every platform call is guarded: audio
 * effect support varies a lot between devices and audio HALs, and a missing
 * effect must never take the player down with it.
 */
object EqualizerController {

    private var fx: Equalizer? = null
    private var sessionId: Int = 0

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    val attached: Boolean get() = fx != null

    /** Bind to an audio session. Safe to call repeatedly with the same id. */
    fun attach(id: Int) {
        if (id <= 0) return
        if (fx != null && sessionId == id) return
        release()
        sessionId = id
        fx = runCatching { Equalizer(0, id) }.getOrNull()
        publish()
    }

    fun setEnabled(enabled: Boolean) {
        val eq = fx ?: return
        runCatching { eq.enabled = enabled }
        publish()
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        val eq = fx ?: return
        runCatching { eq.setBandLevel(band.toShort(), levelMb.toShort()) }
        publish()
    }

    fun usePreset(index: Int) {
        val eq = fx ?: return
        runCatching { eq.usePreset(index.toShort()) }
        publish()
    }

    fun flatten() {
        val eq = fx ?: return
        runCatching {
            for (b in 0 until eq.numberOfBands.toInt()) {
                eq.setBandLevel(b.toShort(), 0)
            }
        }
        publish()
    }

    fun release() {
        fx?.let { runCatching { it.release() } }
        fx = null
        sessionId = 0
        _state.value = EqState()
    }

    private fun publish() {
        val eq = fx
        if (eq == null) {
            _state.value = EqState()
            return
        }
        _state.value = runCatching {
            val bandCount = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val bands = (0 until bandCount).map { b ->
                EqBand(
                    index = b,
                    centerHz = eq.getCenterFreq(b.toShort()) / 1000,
                    levelMb = eq.getBandLevel(b.toShort()).toInt(),
                    minMb = range[0].toInt(),
                    maxMb = range[1].toInt()
                )
            }
            val presetCount = eq.numberOfPresets.toInt()
            val presets = (0 until presetCount).map { eq.getPresetName(it.toShort()) }
            EqState(
                available = true,
                enabled = eq.enabled,
                bands = bands,
                presets = presets,
                presetIndex = eq.currentPreset.toInt()
            )
        }.getOrElse { EqState(available = true) }
    }
}
