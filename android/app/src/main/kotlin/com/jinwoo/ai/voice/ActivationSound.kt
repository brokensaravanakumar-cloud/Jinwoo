package com.jinwoo.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.jinwoo.ai.R
import com.jinwoo.ai.data.SettingsRepository

/** Plays the original activation sound (res/raw/activation.wav) or the user's own imported audio file. */
class ActivationSound(private val ctx: Context, private val settings: SettingsRepository) {
    fun play(force: Boolean = false) {
        val s = settings.current
        if (!s.activationSound && !force) return
        if (s.customSoundUri.isNotBlank() && tryPlay(Uri.parse(s.customSoundUri), s.soundVolume)) return
        tryPlay(null, s.soundVolume)
    }

    private fun tryPlay(uri: Uri?, volume: Float): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            if (uri != null) mp.setDataSource(ctx, uri)
            else ctx.resources.openRawResourceFd(R.raw.activation).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            val v = volume.coerceIn(0f, 1f)
            mp.setVolume(v, v)
            mp.setOnCompletionListener { it.release() }
            mp.setOnErrorListener { m, _, _ -> m.release(); true }
            mp.prepare(); mp.start(); true
        } catch (ignored: Exception) { runCatching { mp.release() }; false }
    }
}
