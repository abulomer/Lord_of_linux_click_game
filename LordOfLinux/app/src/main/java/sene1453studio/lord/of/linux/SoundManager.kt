package sene1453studio.lord.of.linux

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

class SoundManager private constructor(private val context: Context) {

    private val soundPool: SoundPool
    private var backgroundMusicPlayer: MediaPlayer? = null
    private val loadedSounds = mutableMapOf<String, Int>()
    private val soundRates = mutableMapOf<String, Float>()
    private val handler = Handler(Looper.getMainLooper())
    private var previewPlayer: MediaPlayer? = null
    private var savedMusicVolume: Float = 0.7f
    private var isDucking = false

    companion object {
        @Volatile
        private var instance: SoundManager? = null

        fun getInstance(context: Context): SoundManager {
            return instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
    }

    fun generateBuiltInSounds() {
        val dir = File(context.filesDir, "sounds/builtin")
        dir.mkdirs()

        val defs = mapOf(
            "sound_default" to SoundDef(800f, 800f, 80, WaveType.SQUARE),
            "sound_mechanical" to SoundDef(600f, 900f, 100, WaveType.SQUARE),
            "sound_boing" to SoundDef(200f, 800f, 200, WaveType.SINE),
            "sound_laser" to SoundDef(2000f, 200f, 150, WaveType.SINE),
            "sound_coin" to SoundDef(1000f, 1500f, 120, WaveType.DUAL_TONE),
            "sound_bubble" to SoundDef(300f, 1200f, 180, WaveType.SINE_WOBBLE),
            "sound_sword" to SoundDef(1500f, 100f, 120, WaveType.SAWTOOTH),
            "sound_magic" to SoundDef(600f, 1800f, 250, WaveType.HARMONIC),
            "sound_thunder" to SoundDef(60f, 30f, 300, WaveType.NOISE_RUMBLE),
            "sound_whoosh" to SoundDef(2500f, 500f, 200, WaveType.NOISE_SWEEP),
            "sound_ding" to SoundDef(2400f, 2400f, 400, WaveType.BELL),
            "effect_crack" to SoundDef(200f, 4000f, 300, WaveType.GLASS_SHATTER),
            "effect_bomb" to SoundDef(80f, 40f, 600, WaveType.EXPLOSION),
            "strike_punch" to SoundDef(150f, 100f, 150, WaveType.THUMP),
            "strike_slap" to SoundDef(1200f, 2000f, 100, WaveType.SNAP)
        )

        defs.forEach { (id, def) ->
            val file = File(dir, "$id.wav")
            if (!file.exists()) generateWav(file, def)
        }

        generateBuiltInMusic()
        loadAllBuiltInSounds()
    }

    private fun generateBuiltInMusic() {
        val dir = File(context.filesDir, "sounds/music")
        dir.mkdirs()

        val epicFile = File(dir, "music_epic.wav")
        if (!epicFile.exists()) generateMusicTrack(epicFile, MusicStyle.EPIC)

        val chillFile = File(dir, "music_chill.wav")
        if (!chillFile.exists()) generateMusicTrack(chillFile, MusicStyle.CHILL)
    }

    private fun loadAllBuiltInSounds() {
        val dir = File(context.filesDir, "sounds/builtin")
        if (!dir.exists()) return
        dir.listFiles { f -> f.extension == "wav" }?.forEach { file ->
            val id = file.nameWithoutExtension
            if (!loadedSounds.containsKey(id)) {
                val spId = soundPool.load(file.absolutePath, 1)
                loadedSounds[id] = spId
                soundRates[id] = 1f
            }
        }
    }

    fun loadClickSound(soundId: String, gameManager: GameManager) {
        if (loadedSounds.containsKey(soundId)) return

        val dynamicUri = gameManager.getDynamicUri(soundId)
        if (dynamicUri != null) {
            val path = Uri.parse(dynamicUri).path
            if (path != null) {
                val file = File(path)
                if (file.exists()) {
                    val spId = soundPool.load(path, 1)
                    loadedSounds[soundId] = spId
                    val durationMs = getAudioDuration(file)
                    val rate = if (durationMs > 1000) (durationMs / 1000f).coerceIn(1f, 2f) else 1f
                    soundRates[soundId] = rate
                }
            }
        }
    }

    fun playClickSound(soundId: String, volume: Float) {
        val spId = loadedSounds[soundId] ?: loadedSounds["sound_default"] ?: return
        val rate = soundRates[soundId] ?: 1f
        soundPool.play(spId, volume, volume, 1, 0, rate)
    }

    private val FADE_DURATION = 500L
    private var targetVolume = 0.7f

    private fun fadeInMusic(player: MediaPlayer?, finalVolume: Float, durationMs: Long = FADE_DURATION) {
        player ?: return
        targetVolume = finalVolume
        player.setVolume(0f, 0f)
        val steps = 20
        val stepDelay = durationMs / steps
        val volumeStep = finalVolume / steps

        for (i in 1..steps) {
            handler.postDelayed({
                try {
                    val vol = (volumeStep * i).coerceAtMost(finalVolume)
                    player.setVolume(vol, vol)
                } catch (_: Exception) {}
            }, stepDelay * i)
        }
    }

    private fun fadeOutMusic(player: MediaPlayer?, durationMs: Long = FADE_DURATION, onComplete: () -> Unit) {
        player ?: run { onComplete(); return }
        val currentVol = targetVolume
        val steps = 20
        val stepDelay = durationMs / steps
        val volumeStep = currentVol / steps

        for (i in 1..steps) {
            handler.postDelayed({
                try {
                    val vol = (currentVol - volumeStep * i).coerceAtLeast(0f)
                    player.setVolume(vol, vol)
                } catch (_: Exception) {}
            }, stepDelay * i)
        }
        handler.postDelayed({ onComplete() }, durationMs + 50)
    }

    fun setupBackgroundMusic(gameManager: GameManager) {
        if (!gameManager.isMusicEnabled) {
            releaseBackgroundMusic()
            return
        }
        val oldPlayer = backgroundMusicPlayer
        if (oldPlayer != null) {
            fadeOutMusic(oldPlayer) {
                releaseBackgroundMusic()
                createAndStartMusic(gameManager)
            }
        } else {
            createAndStartMusic(gameManager)
        }
    }

    private fun createAndStartMusic(gameManager: GameManager) {
        try {
            val musicId = gameManager.activeMusic
            val customUri = gameManager.getActiveAssetUri(GameManager.CAT_MUSIC)

            backgroundMusicPlayer = MediaPlayer().apply {
                when {
                    customUri != null -> {
                        setDataSource(context, Uri.parse(customUri))
                    }
                    musicId.startsWith("music_") -> {
                        val file = File(context.filesDir, "sounds/music/$musicId.wav")
                        if (file.exists()) {
                            setDataSource(file.absolutePath)
                        } else {
                            val resId = context.resources.getIdentifier("arkaplanses", "raw", context.packageName)
                            if (resId == 0) return
                            val afd = context.resources.openRawResourceFd(resId)
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                        }
                    }
                    else -> {
                        val resId = context.resources.getIdentifier("arkaplanses", "raw", context.packageName)
                        if (resId == 0) return
                        val afd = context.resources.openRawResourceFd(resId)
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                }
                isLooping = true
                setVolume(0f, 0f)
                prepareAsync()
                setOnPreparedListener { mp ->
                    if (gameManager.isMusicEnabled) {
                        mp.start()
                        fadeInMusic(mp, gameManager.musicVolume)
                    }
                }
                setOnErrorListener { _, _, _ -> true }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMusicVolume(volume: Float) {
        targetVolume = volume
        backgroundMusicPlayer?.setVolume(volume, volume)
    }

    fun toggleMusic(enabled: Boolean) {
        try {
            if (enabled) {
                backgroundMusicPlayer?.apply {
                    start()
                    fadeInMusic(this, targetVolume)
                }
            } else {
                val player = backgroundMusicPlayer
                if (player?.isPlaying == true) {
                    fadeOutMusic(player) {
                        try { player.pause() } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun toggleMusicWithSetup(enabled: Boolean, gameManager: GameManager) {
        if (enabled) {
            if (backgroundMusicPlayer == null) {
                setupBackgroundMusic(gameManager)
            } else {
                try {
                    backgroundMusicPlayer?.apply {
                        start()
                        fadeInMusic(this, gameManager.musicVolume)
                    }
                } catch (_: Exception) { setupBackgroundMusic(gameManager) }
            }
        } else {
            val player = backgroundMusicPlayer
            if (player?.isPlaying == true) {
                fadeOutMusic(player) {
                    try { player.pause() } catch (_: Exception) {}
                }
            }
        }
    }

    val isMusicPlaying: Boolean get() = try { backgroundMusicPlayer?.isPlaying == true } catch (_: Exception) { false }

    private fun isMusicPreview(soundId: String): Boolean {
        return soundId.startsWith("music_")
    }

    private fun duckBackgroundMusic() {
        if (!isDucking) {
            val player = backgroundMusicPlayer
            if (player?.isPlaying == true) {
                savedMusicVolume = targetVolume
                player.setVolume(targetVolume * 0.2f, targetVolume * 0.2f)
                isDucking = true
            }
        }
    }

    private fun unduckBackgroundMusic() {
        if (isDucking) {
            backgroundMusicPlayer?.setVolume(savedMusicVolume, savedMusicVolume)
            isDucking = false
        }
    }

    fun previewMarketSound(soundId: String, customUri: String? = null) {
        handler.removeCallbacksAndMessages("preview_tag")
        releasePreviewOnly()

        if (isMusicPreview(soundId)) {
            duckBackgroundMusic()
        }

        try {
            previewPlayer = MediaPlayer().apply {
                when {
                    customUri != null -> setDataSource(context, Uri.parse(customUri))
                    soundId.startsWith("sound_") -> {
                        val file = File(context.filesDir, "sounds/builtin/$soundId.wav")
                        if (file.exists()) setDataSource(file.absolutePath) else return
                    }
                    soundId.startsWith("music_") -> {
                        val file = File(context.filesDir, "sounds/music/$soundId.wav")
                        if (file.exists()) setDataSource(file.absolutePath)
                        else {
                            val resId = context.resources.getIdentifier("arkaplanses", "raw", context.packageName)
                            if (resId != 0) {
                                val afd = context.resources.openRawResourceFd(resId)
                                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                afd.close()
                            }
                        }
                    }
                    else -> return
                }

                setVolume(1.0f, 1.0f)
                setOnPreparedListener { mp ->
                    mp.start()
                    handler.postDelayed({
                        try { if (mp.isPlaying) mp.stop() } catch (_: Exception) {}
                        unduckBackgroundMusic()
                    }, 4000)
                }
                setOnCompletionListener { unduckBackgroundMusic() }
                setOnErrorListener { _, _, _ -> unduckBackgroundMusic(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            unduckBackgroundMusic()
        }
    }

    fun getAudioDuration(file: File): Long {
        if (!file.exists()) return 0L
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            retriever.release()
            d
        } catch (e: Exception) { 0L }
    }

    fun getAudioDurationFromUri(uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            retriever.release()
            d
        } catch (e: Exception) { 0L }
    }

    private enum class WaveType { SINE, SQUARE, DUAL_TONE, SINE_WOBBLE, SAWTOOTH, HARMONIC, NOISE_RUMBLE, NOISE_SWEEP, BELL, GLASS_SHATTER, EXPLOSION, THUMP, SNAP }
    private data class SoundDef(val startFreq: Float, val endFreq: Float, val durationMs: Int, val waveType: WaveType)
    private enum class MusicStyle { EPIC, CHILL }

    private fun generateWav(file: File, def: SoundDef) {
        val sampleRate = 44100
        val numSamples = (sampleRate * def.durationMs / 1000.0).toInt()
        val samples = ShortArray(numSamples)
        val random = java.util.Random(def.startFreq.toLong())

        var phase = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val freq = def.startFreq + (def.endFreq - def.startFreq) * progress

            phase += 2.0 * PI * freq / sampleRate
            if (phase > 2.0 * PI) phase -= 2.0 * PI

            val wave = when (def.waveType) {
                WaveType.SINE -> sin(phase)
                WaveType.SQUARE -> if (sin(phase) > 0) 0.5 else -0.5
                WaveType.DUAL_TONE -> (sin(phase) + sin(phase * 1.5)) * 0.5
                WaveType.SINE_WOBBLE -> sin(phase + sin(2.0 * PI * 8.0 * (i.toDouble()/sampleRate)) * 2.0)
                WaveType.SAWTOOTH -> {
                    var saw = 0.0
                    for (h in 1..8) saw += sin(phase * h) / h
                    saw * 0.4
                }
                WaveType.HARMONIC -> (sin(phase) * 0.5 + sin(phase * 2) * 0.2)
                WaveType.NOISE_RUMBLE -> (random.nextDouble() * 2.0 - 1.0) * 0.2 + sin(phase) * 0.4
                WaveType.NOISE_SWEEP -> (random.nextDouble() * 2.0 - 1.0) * 0.3 * sin(phase)
                WaveType.BELL -> sin(phase) * exp(-3.0 * progress)
                WaveType.GLASS_SHATTER -> (random.nextDouble() * 2.0 - 1.0) * 0.5 * exp(-10.0 * progress) + sin(phase * 4) * 0.1
                WaveType.EXPLOSION -> (random.nextDouble() * 2.0 - 1.0) * exp(-5.0 * progress)
                WaveType.THUMP -> sin(phase) * exp(-15.0 * progress)
                WaveType.SNAP -> (random.nextDouble() * 2.0 - 1.0) * 0.4 * exp(-20.0 * progress) + sin(phase) * 0.2
            }

            val envelope = when {
                progress < 0.05 -> progress / 0.05
                progress > 0.85 -> (1.0 - progress) / 0.15
                else -> 1.0
            }

            val sample = (wave * envelope * Short.MAX_VALUE * 0.4)
                .toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = sample.toShort()
        }

        writeWavFile(file, samples, sampleRate)
    }

    private fun generateMusicTrack(file: File, style: MusicStyle) {
        val sampleRate = 44100
        val durationSec = 8
        val numSamples = sampleRate * durationSec
        val samples = ShortArray(numSamples)

        when (style) {
            MusicStyle.EPIC -> {
                val notes = doubleArrayOf(110.0, 130.81, 146.83, 164.81, 146.83, 130.81, 110.0, 98.0)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples
                    val noteIndex = ((progress * notes.size * 2) % notes.size).toInt()
                    val freq = notes[noteIndex]
                    val bass = sin(2.0 * PI * freq * t) * 0.35
                    val chord = sin(2.0 * PI * freq * 2.0 * t) * 0.15 + sin(2.0 * PI * freq * 3.0 * t) * 0.08
                    val wave = (bass + chord) * 0.8
                    samples[i] = (wave * Short.MAX_VALUE * 0.55).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            MusicStyle.CHILL -> {
                val notes = doubleArrayOf(261.63, 293.66, 329.63, 349.23, 392.0, 349.23, 329.63, 293.66)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val progress = i.toDouble() / numSamples
                    val noteIndex = ((progress * notes.size) % notes.size).toInt()
                    val freq = notes[noteIndex]
                    val pad = sin(2.0 * PI * freq * 0.5 * t) * 0.25
                    val melody = sin(2.0 * PI * freq * t) * 0.12
                    val wave = (pad + melody) * 0.8
                    samples[i] = (wave * Short.MAX_VALUE * 0.45).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
        }

        writeWavFile(file, samples, sampleRate)
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort(it) }
        val data = buffer.array()
        val dataSize = data.size
        val fileSize = 36 + dataSize
        FileOutputStream(file).use { fos ->
            val dos = DataOutputStream(fos)
            dos.writeBytes("RIFF")
            dos.write(intToLEBytes(fileSize))
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            dos.write(intToLEBytes(16))
            dos.write(shortToLEBytes(1))
            dos.write(shortToLEBytes(1))
            dos.write(intToLEBytes(sampleRate))
            dos.write(intToLEBytes(sampleRate * 2))
            dos.write(shortToLEBytes(2))
            dos.write(shortToLEBytes(16))
            dos.writeBytes("data")
            dos.write(intToLEBytes(dataSize))
            dos.write(data)
            dos.flush()
        }
    }

    private fun intToLEBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun shortToLEBytes(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()

    fun releasePreviewOnly() {
        handler.removeCallbacksAndMessages(null)
        previewPlayer?.apply {
            try { if (isPlaying) stop() } catch (_: Exception) {}
            release()
        }
        previewPlayer = null
        unduckBackgroundMusic()
    }

    private fun releaseBackgroundMusic() {
        backgroundMusicPlayer?.apply {
            try { if (isPlaying) stop() } catch (_: Exception) {}
            release()
        }
        backgroundMusicPlayer = null
    }

    fun releaseMusic() {
        releasePreviewOnly()
        releaseBackgroundMusic()
    }

    fun release() {
        releaseMusic()
        soundPool.release()
        instance = null
    }
}

