package com.blockforge.engine.runtime

import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.SoundPool
import java.io.File
import java.io.InputStream

/**
 * Where the runtime gets sprite and audio bytes from. The editor plays straight out of the
 * project folder on disk; an exported APK plays out of `assets/res/`. Same engine, two providers.
 */
interface ResourceProvider {
    fun openStream(fileName: String): InputStream?
    fun exists(fileName: String): Boolean
    fun loadSound(pool: SoundPool, fileName: String): Int
    fun setMusicSource(player: MediaPlayer, fileName: String): Boolean
}

class FileResourceProvider(private val dir: File) : ResourceProvider {

    override fun openStream(fileName: String): InputStream? {
        val f = File(dir, fileName)
        return if (f.isFile) f.inputStream() else null
    }

    override fun exists(fileName: String): Boolean = File(dir, fileName).isFile

    override fun loadSound(pool: SoundPool, fileName: String): Int {
        val f = File(dir, fileName)
        if (!f.isFile) return 0
        return pool.load(f.absolutePath, 1)
    }

    override fun setMusicSource(player: MediaPlayer, fileName: String): Boolean {
        val f = File(dir, fileName)
        if (!f.isFile) return false
        player.setDataSource(f.absolutePath)
        return true
    }
}

class AssetResourceProvider(
    private val assets: AssetManager,
    private val base: String = "res"
) : ResourceProvider {

    private fun path(fileName: String) = if (base.isEmpty()) fileName else "$base/$fileName"

    override fun openStream(fileName: String): InputStream? =
        runCatching { assets.open(path(fileName)) }.getOrNull()

    override fun exists(fileName: String): Boolean =
        runCatching { assets.open(path(fileName)).close(); true }.getOrDefault(false)

    override fun loadSound(pool: SoundPool, fileName: String): Int {
        val afd: AssetFileDescriptor = runCatching { assets.openFd(path(fileName)) }.getOrNull() ?: return 0
        return afd.use { pool.load(it, 1) }
    }

    override fun setMusicSource(player: MediaPlayer, fileName: String): Boolean {
        val afd = runCatching { assets.openFd(path(fileName)) }.getOrNull() ?: return false
        afd.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        return true
    }
}

/**
 * Decoded-bitmap cache. Sprites are decoded once and shared by every actor using them; a scene with
 * two hundred bullets holds exactly one bullet bitmap.
 */
class BitmapCache(private val provider: ResourceProvider) {

    private val cache = HashMap<String, Bitmap?>()

    fun get(fileName: String?): Bitmap? {
        if (fileName.isNullOrEmpty()) return null
        return cache.getOrPut(fileName) {
            provider.openStream(fileName)?.use { stream ->
                runCatching {
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                }.getOrNull()
            }
        }
    }

    fun clear() {
        cache.values.forEach { it?.recycle() }
        cache.clear()
    }
}
