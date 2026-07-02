package com.skylegends.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * All game audio, synthesized procedurally at startup — no bundled asset files. Each SFX is
 * generated as a PCM waveform, wrapped in a WAV container written to the cache dir, and
 * loaded into a [SoundPool] for low-latency polyphonic playback.
 *
 * Music is **adaptive and layered**: three identical-length, identical-tempo stem loops
 * (base pulse, combat groove, boss intensity) play simultaneously on their own looping
 * [MediaPlayer]s at all times; [updateMusicMix] crossfades their volumes toward the current
 * situation (calm menu = base only, active combat = base+combat, boss fight = all three).
 * Because the stems share one arrangement, layering them never sounds like a track switch.
 *
 * Generation runs on a background thread so it never blocks the UI/first frame.
 */
class SoundManager(private val context: Context) {

    private val sampleRate = 22050
    private val soundPool: SoundPool
    private val ids = HashMap<String, Int>()
    private val lastPlay = HashMap<String, Long>()
    private var ready = false

    private var musicBase: MediaPlayer? = null
    private var musicCombat: MediaPlayer? = null
    private var musicBoss: MediaPlayer? = null
    private var combatVol = 0f
    private var bossVol = 0f

    var sfxEnabled = true
    var musicEnabled = true

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attrs).build()
        Thread { generateAll() }.apply { isDaemon = true; start() }
    }

    // ---------------- Generation ----------------

    private fun generateAll() {
        try {
            loadSfx("shoot", genShoot())
            loadSfx("hit", genHit())
            loadSfx("explosion", genExplosion())
            loadSfx("coin", genCoin())
            loadSfx("powerup", genPowerup())
            loadSfx("hurt", genHurt())
            loadSfx("boss", genBoss())
            loadSfx("click", genClick())
            loadSfx("victory", genVictory())
            loadSfx("defeat", genDefeat())
            loadSfx("ability", genAbility())
            loadSfx("heartbeat", genHeartbeat())
            loadSfx("missile", genMissile())
            musicBase = prepareLoop("music_base", genMusicBase())
            musicCombat = prepareLoop("music_combat", genMusicCombat())
            musicBoss = prepareLoop("music_boss", genMusicBoss())
            musicBase?.setVolume(0.42f, 0.42f)
            musicCombat?.setVolume(0f, 0f)
            musicBoss?.setVolume(0f, 0f)
            if (musicEnabled) { musicBase?.start(); musicCombat?.start(); musicBoss?.start() }
            ready = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSfx(name: String, data: FloatArray) {
        val file = writeWav(name, data)
        ids[name] = soundPool.load(file.absolutePath, 1)
    }

    // ---------------- Playback ----------------

    private fun play(name: String, volume: Float = 1f, rate: Float = 1f, minGapMs: Long = 0L) {
        if (!sfxEnabled || !ready) return
        if (minGapMs > 0) {
            val now = SystemClock.uptimeMillis()
            val last = lastPlay[name] ?: 0L
            if (now - last < minGapMs) return
            lastPlay[name] = now
        }
        val id = ids[name] ?: return
        soundPool.play(id, volume, volume, 1, 0, rate)
    }

    fun shoot() = play("shoot", 0.35f, rate = 0.95f + Random.nextFloat() * 0.1f, minGapMs = 45)
    fun hit() = play("hit", 0.5f, minGapMs = 40)
    fun explosion(big: Boolean = false) = play("explosion", if (big) 1f else 0.7f, rate = if (big) 0.8f else 1f, minGapMs = 30)
    fun coin() = play("coin", 0.4f, minGapMs = 40)
    fun powerup() = play("powerup", 0.8f)
    fun hurt() = play("hurt", 0.8f, minGapMs = 120)
    fun boss() = play("boss", 1f)
    fun click() = play("click", 0.7f)
    fun victory() = play("victory", 1f)
    fun defeat() = play("defeat", 1f)
    fun ability() = play("ability", 0.75f)
    fun missile() = play("missile", 0.8f, minGapMs = 150)
    fun heartbeat() = play("heartbeat", 0.55f, minGapMs = 250)

    // ---------------- Adaptive music ----------------

    private fun prepareLoop(name: String, data: FloatArray): MediaPlayer? {
        val file = writeWav(name, data)
        return try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = true
                prepare()
            }
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    /**
     * Crossfades the combat/boss music layers toward [combatTarget]/[bossTarget] (each 0..1).
     * Call once per game update tick; the base pulse layer always plays, gently ducking
     * while the boss layer is prominent so the boss motif reads clearly.
     */
    fun updateMusicMix(dt: Float, combatTarget: Float, bossTarget: Float) {
        if (!ready) return
        val a = 1f - exp(-2.2 * dt).toFloat()
        combatVol += (combatTarget.coerceIn(0f, 1f) - combatVol) * a
        bossVol += (bossTarget.coerceIn(0f, 1f) - bossVol) * a
        if (!musicEnabled) return
        runCatching {
            val baseLevel = (0.42f - 0.12f * bossVol).coerceAtLeast(0f)
            musicBase?.setVolume(baseLevel, baseLevel)
            val combatLevel = combatVol * 0.38f
            musicCombat?.setVolume(combatLevel, combatLevel)
            val bossLevel = bossVol * 0.42f
            musicBoss?.setVolume(bossLevel, bossLevel)
        }
    }

    fun startMusic() {
        if (!musicEnabled) return
        runCatching {
            musicBase?.takeIf { !it.isPlaying }?.start()
            musicCombat?.takeIf { !it.isPlaying }?.start()
            musicBoss?.takeIf { !it.isPlaying }?.start()
        }
    }

    fun pauseMusic() {
        runCatching {
            musicBase?.takeIf { it.isPlaying }?.pause()
            musicCombat?.takeIf { it.isPlaying }?.pause()
            musicBoss?.takeIf { it.isPlaying }?.pause()
        }
    }

    fun enableMusic(on: Boolean) {
        musicEnabled = on
        if (on) startMusic() else pauseMusic()
    }
    fun enableSfx(on: Boolean) { sfxEnabled = on }

    fun release() {
        runCatching { musicBase?.release(); musicCombat?.release(); musicBoss?.release() }
        musicBase = null; musicCombat = null; musicBoss = null
        soundPool.release()
    }

    // ---------------- Synthesis primitives ----------------

    private fun buf(seconds: Float) = FloatArray((seconds * sampleRate).toInt())

    /** Adds a pitch-swept oscillator with an exponential-decay envelope into [out]. */
    private fun osc(out: FloatArray, start: Int, dur: Float, f0: Float, f1: Float, vol: Float, decay: Float, wave: Int) {
        val n = (dur * sampleRate).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i / sampleRate.toFloat()
            val f = f0 + (f1 - f0) * (i / n.toFloat())
            phase += 2.0 * Math.PI * f / sampleRate
            val s = when (wave) {
                0 -> sin(phase)
                1 -> if (sin(phase) >= 0) 1.0 else -1.0            // square
                else -> (2.0 * (phase / (2 * Math.PI) % 1.0) - 1.0) // saw
            }
            val env = exp(-decay * t)
            out[idx] += (s * vol * env).toFloat()
        }
    }

    private fun noiseBurst(out: FloatArray, start: Int, dur: Float, vol: Float, decay: Float, lowpass: Float = 0f) {
        val n = (dur * sampleRate).toInt()
        var prev = 0f
        for (i in 0 until n) {
            val idx = start + i
            if (idx >= out.size) break
            val t = i / sampleRate.toFloat()
            var s = Random.nextFloat() * 2f - 1f
            if (lowpass > 0f) { s = prev + (s - prev) * lowpass; prev = s }
            out[idx] += s * vol * exp(-decay * t).toFloat()
        }
    }

    // ---------------- SFX voices ----------------

    private fun genShoot(): FloatArray = buf(0.11f).also { osc(it, 0, 0.11f, 1150f, 380f, 0.5f, 28f, 0) }

    private fun genHit(): FloatArray = buf(0.06f).also {
        noiseBurst(it, 0, 0.06f, 0.5f, 70f); osc(it, 0, 0.05f, 1600f, 900f, 0.3f, 60f, 0)
    }

    private fun genExplosion(): FloatArray = buf(0.55f).also {
        noiseBurst(it, 0, 0.55f, 0.6f, 7f, lowpass = 0.35f)
        osc(it, 0, 0.4f, 90f, 40f, 0.6f, 6f, 0)
    }

    private fun genCoin(): FloatArray = buf(0.16f).also {
        osc(it, 0, 0.06f, 988f, 988f, 0.4f, 12f, 0)
        osc(it, (0.06f * sampleRate).toInt(), 0.10f, 1319f, 1319f, 0.4f, 10f, 0)
    }

    private fun genPowerup(): FloatArray = buf(0.30f).also {
        val notes = floatArrayOf(523f, 659f, 784f, 1047f)
        notes.forEachIndexed { i, f -> osc(it, (i * 0.06f * sampleRate).toInt(), 0.09f, f, f, 0.32f, 8f, 0) }
    }

    private fun genHurt(): FloatArray = buf(0.24f).also {
        osc(it, 0, 0.24f, 180f, 70f, 0.4f, 8f, 1)
        noiseBurst(it, 0, 0.1f, 0.3f, 20f)
    }

    private fun genBoss(): FloatArray = buf(0.9f).also {
        // Alternating two-tone siren.
        for (k in 0 until 3) {
            val s = (k * 0.3f * sampleRate).toInt()
            osc(it, s, 0.15f, 440f, 440f, 0.3f, 3f, 1)
            osc(it, s + (0.15f * sampleRate).toInt(), 0.15f, 300f, 300f, 0.3f, 3f, 1)
        }
    }

    private fun genClick(): FloatArray = buf(0.05f).also { osc(it, 0, 0.05f, 720f, 520f, 0.4f, 40f, 0) }

    private fun genVictory(): FloatArray = buf(0.6f).also {
        val notes = floatArrayOf(523f, 659f, 784f, 1047f, 1319f)
        notes.forEachIndexed { i, f -> osc(it, (i * 0.1f * sampleRate).toInt(), 0.16f, f, f, 0.3f, 5f, 0) }
    }

    private fun genDefeat(): FloatArray = buf(0.7f).also {
        val notes = floatArrayOf(440f, 392f, 330f, 262f)
        notes.forEachIndexed { i, f -> osc(it, (i * 0.16f * sampleRate).toInt(), 0.22f, f, f, 0.3f, 5f, 1) }
    }

    private fun genAbility(): FloatArray = buf(0.32f).also {
        osc(it, 0, 0.32f, 260f, 980f, 0.4f, 4f, 1)
        osc(it, 0, 0.20f, 780f, 1400f, 0.2f, 8f, 0)
    }

    private fun genHeartbeat(): FloatArray = buf(0.16f).also {
        osc(it, 0, 0.09f, 90f, 45f, 0.55f, 22f, 0)
        noiseBurst(it, 0, 0.03f, 0.15f, 90f)
    }

    private fun genMissile(): FloatArray = buf(0.4f).also {
        noiseBurst(it, 0, 0.18f, 0.5f, 12f, lowpass = 0.4f)          // ignition whoosh
        osc(it, 0, 0.35f, 140f, 60f, 0.55f, 5f, 0)                    // low launch thump
        osc(it, 0, 0.08f, 900f, 300f, 0.2f, 30f, 0)                   // brief crack
    }

    // ---------------- Adaptive music stems ----------------
    // All three share the exact same 16-step / 8s-at-120bpm arrangement window, so they
    // stay bar-aligned across loops no matter which combination is audible.

    private val musicSteps = 16
    private val musicBeat = 0.5f
    private val musicBass = floatArrayOf(110f, 110f, 131f, 110f, 98f, 98f, 131f, 98f, 87f, 87f, 110f, 87f, 98f, 98f, 123f, 98f)
    private val musicArp = floatArrayOf(440f, 523f, 659f, 523f)

    /** Always-on layer: kick + bass. Alone, this is the moody menu/ambient pulse. */
    private fun genMusicBase(): FloatArray = buf(musicBeat * musicSteps).also { out ->
        for (s in 0 until musicSteps) {
            val start = (s * musicBeat * sampleRate).toInt()
            osc(out, start, 0.18f, 120f, 45f, 0.5f, 16f, 0)                          // kick
            osc(out, start, musicBeat, musicBass[s], musicBass[s], 0.16f, 2.2f, 1)   // bass
        }
    }

    /** Combat layer: arp + hats. Fades in once enemies are on screen. */
    private fun genMusicCombat(): FloatArray = buf(musicBeat * musicSteps).also { out ->
        for (s in 0 until musicSteps) {
            val start = (s * musicBeat * sampleRate).toInt()
            osc(out, start, 0.22f, musicArp[s % musicArp.size], musicArp[s % musicArp.size], 0.07f, 5f, 2)
            osc(out, start + (0.25f * sampleRate).toInt(), 0.22f, musicArp[(s + 2) % musicArp.size], musicArp[(s + 2) % musicArp.size], 0.07f, 5f, 2)
            noiseBurst(out, start + (0.25f * sampleRate).toInt(), 0.05f, 0.05f, 60f, lowpass = 0.9f)
        }
    }

    /** Boss layer: octave-up stabs + doubled hats. Fades in only while a boss is active. */
    private fun genMusicBoss(): FloatArray = buf(musicBeat * musicSteps).also { out ->
        for (s in 0 until musicSteps) {
            val start = (s * musicBeat * sampleRate).toInt()
            val hi = musicArp[(s + 1) % musicArp.size] * 2f
            osc(out, start, 0.14f, hi, hi, 0.09f, 10f, 1)
            osc(out, start + (0.125f * sampleRate).toInt(), 0.10f, hi, hi, 0.06f, 14f, 1)
            osc(out, start + (0.375f * sampleRate).toInt(), 0.10f, hi, hi, 0.06f, 14f, 1)
            noiseBurst(out, start + (0.125f * sampleRate).toInt(), 0.04f, 0.05f, 70f, lowpass = 0.8f)
            noiseBurst(out, start + (0.375f * sampleRate).toInt(), 0.04f, 0.05f, 70f, lowpass = 0.8f)
        }
    }

    // ---------------- WAV writer ----------------

    private fun writeWav(name: String, samples: FloatArray): File {
        // Normalize to avoid clipping while keeping headroom.
        var peak = 0f
        for (v in samples) { val a = if (v < 0) -v else v; if (a > peak) peak = a }
        val gain = if (peak > 0.0001f) (0.92f / peak).coerceAtMost(1.6f) else 1f

        val pcm = ByteArray(samples.size * 2)
        var j = 0
        for (v in samples) {
            val s = (v * gain * 32767f).toInt().coerceIn(-32768, 32767)
            pcm[j++] = (s and 0xFF).toByte()
            pcm[j++] = ((s shr 8) and 0xFF).toByte()
        }

        val file = File(context.cacheDir, "$name.wav")
        FileOutputStream(file).use { fos ->
            val totalDataLen = 36 + pcm.size
            val byteRate = sampleRate * 2
            val header = ByteArray(44)
            fun wStr(off: Int, s: String) { for (k in s.indices) header[off + k] = s[k].code.toByte() }
            fun w32(off: Int, v: Int) {
                header[off] = (v and 0xFF).toByte(); header[off + 1] = ((v shr 8) and 0xFF).toByte()
                header[off + 2] = ((v shr 16) and 0xFF).toByte(); header[off + 3] = ((v shr 24) and 0xFF).toByte()
            }
            fun w16(off: Int, v: Int) { header[off] = (v and 0xFF).toByte(); header[off + 1] = ((v shr 8) and 0xFF).toByte() }
            wStr(0, "RIFF"); w32(4, totalDataLen); wStr(8, "WAVE"); wStr(12, "fmt ")
            w32(16, 16); w16(20, 1); w16(22, 1); w32(24, sampleRate); w32(28, byteRate)
            w16(32, 2); w16(34, 16); wStr(36, "data"); w32(40, pcm.size)
            fos.write(header); fos.write(pcm)
        }
        return file
    }
}
