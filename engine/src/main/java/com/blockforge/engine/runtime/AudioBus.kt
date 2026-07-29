package com.blockforge.engine.runtime

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Short sounds go through a [SoundPool] so they can overlap and retrigger without latency; the one
 * background track goes through a [MediaPlayer] so a long MP3 does not have to sit in memory.
 */
class AudioBus(private val provider: ResourceProvider) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = HashMap<String, Int>()
    private val durations = HashMap<String, Float>()
    private val ready = HashSet<Int>()
    private var music: MediaPlayer? = null
    private var musicFile: String? = null

    var volume: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
            music?.setVolume(field, field)
        }

    var muted: Boolean = false
        set(value) {
            field = value
            music?.setVolume(if (value) 0f else volume, if (value) 0f else volume)
        }

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) ready.add(sampleId)
        }
    }

    private fun soundId(fileName: String): Int = soundIds.getOrPut(fileName) {
        provider.loadSound(pool, fileName)
    }

    fun preload(fileNames: Collection<String>) {
        fileNames.forEach { soundId(it) }
    }

    fun playSfx(fileName: String?) {
        if (fileName.isNullOrEmpty() || muted) return
        val id = soundId(fileName)
        if (id == 0) return
        val v = volume
        pool.play(id, v, v, 1, 0, 1f)
    }

    /**
     * Duration in seconds, used by "mainkan efek sampai selesai". Probed once with a throwaway
     * player because SoundPool does not expose sample length.
     */
    fun durationOf(fileName: String?): Float {
        if (fileName.isNullOrEmpty()) return 0f
        return durations.getOrPut(fileName) {
            val probe = MediaPlayer()
            val seconds = runCatching {
                if (!provider.setMusicSource(probe, fileName)) return@runCatching 0f
                probe.prepare()
                probe.duration / 1000f
            }.getOrDefault(0f)
            runCatching { probe.release() }
            seconds
        }
    }

    fun playMusic(fileName: String?, loop: Boolean = true) {
        if (fileName.isNullOrEmpty()) return
        if (musicFile == fileName && music?.isPlaying == true) return
        stopMusic()
        val player = MediaPlayer()
        val ok = runCatching {
            if (!provider.setMusicSource(player, fileName)) return@runCatching false
            player.isLooping = loop
            val v = if (muted) 0f else volume
            player.setVolume(v, v)
            player.prepare()
            player.start()
            true
        }.getOrDefault(false)
        if (ok) {
            music = player
            musicFile = fileName
        } else {
            runCatching { player.release() }
        }
    }

    fun stopMusic() {
        music?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        music = null
        musicFile = null
    }

    fun pause() {
        runCatching { music?.takeIf { it.isPlaying }?.pause() }
        pool.autoPause()
    }

    fun resume() {
        runCatching { music?.start() }
        pool.autoResume()
    }

    fun release() {
        stopMusic()
        pool.release()
        soundIds.clear()
        durations.clear()
    }
}
