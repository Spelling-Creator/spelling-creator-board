package org.spellingcreator.board.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Says letters with as little delay as possible.
 *
 * Live text to speech takes long enough to start that a speller presses the letter again. So each
 * short phrase is synthesized to a file once, loaded into a [SoundPool], and played from memory on
 * later presses. Until a clip is ready, the live engine is used so nothing is ever silent.
 */
class LetterVoice(
    context: Context,
) {
    private val cacheDir = File(context.cacheDir, "letter-voice").apply { mkdirs() }
    private val soundPool =
        SoundPool
            .Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            ).build()

    private val clipIds = HashMap<String, Int>()
    private val loadedClips = HashSet<Int>()
    private val rendering = HashSet<String>()
    private var pendingPhrases: List<String> = emptyList()
    private var engineReady = false
    private var currentStream = 0

    private val engine: TextToSpeech =
        TextToSpeech(context) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            if (engineReady) {
                engine.language = Locale.getDefault()
                engine.setOnUtteranceProgressListener(progressListener)
                prepare(pendingPhrases)
            } else {
                Log.w(TAG, "Text to speech is unavailable; letters will be silent")
            }
        }

    private val progressListener =
        object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                val phrase = utteranceId.removePrefix(CLIP_PREFIX)
                if (phrase == utteranceId) return
                rendering.remove(phrase)
                val id = soundPool.load(fileFor(phrase).path, 1)
                clipIds[phrase] = id
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                rendering.remove(utteranceId.removePrefix(CLIP_PREFIX))
            }
        }

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedClips += sampleId
        }
    }

    /** Renders clips for these phrases in the background. Safe to call before the engine is ready. */
    fun prepare(phrases: List<String>) {
        pendingPhrases = phrases
        if (!engineReady) return
        for (phrase in phrases) {
            if (phrase in clipIds || phrase in rendering) continue
            val file = fileFor(phrase)
            if (file.exists() && file.length() > 0) {
                clipIds[phrase] = soundPool.load(file.path, 1)
                continue
            }
            rendering += phrase
            engine.synthesizeToFile(phrase, Bundle(), file, CLIP_PREFIX + phrase)
        }
    }

    fun say(
        text: String,
        interrupt: Boolean,
    ) {
        val clip = clipIds[text]
        if (clip != null && clip in loadedClips) {
            if (interrupt) {
                soundPool.stop(currentStream)
                engine.stop()
            }
            currentStream = soundPool.play(clip, 1f, 1f, 1, 0, 1f)
            return
        }
        if (!engineReady) return
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (interrupt) soundPool.stop(currentStream)
        engine.speak(text, queueMode, null, "live")
    }

    fun release() {
        engine.stop()
        engine.shutdown()
        soundPool.release()
    }

    private fun fileFor(phrase: String): File {
        val safeName = phrase.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        return File(cacheDir, "$safeName-${phrase.hashCode()}.wav")
    }

    private companion object {
        const val TAG = "LetterVoice"
        const val CLIP_PREFIX = "clip:"
    }
}
