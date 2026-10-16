package org.spellingcreator.board.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale

/** A system voice the partner can pick in settings. */
data class VoiceOption(
    val id: String,
    val name: String,
    val quality: String,
    val language: String,
)

/**
 * Says letters with as little delay as possible.
 *
 * Live text to speech takes long enough to start that a speller presses the letter again. So each
 * short phrase is synthesized to a file once, loaded into a [SoundPool], and played from memory on
 * later presses. Until a clip is ready, the live engine is used so nothing is ever silent.
 *
 * The voice matters for single letters: low quality voices blur Z and C, B and P. With no voice
 * chosen, the best installed voice for the current language is used.
 */
class LetterVoice(
    context: Context,
) {
    // Bump the folder name whenever the way clips are rendered changes, so stale clips are not reused.
    private val cacheDir = File(context.cacheDir, "letter-voice-v3").apply { mkdirs() }
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
    private var requestedVoiceId: String? = null
    private var engineReady = false
    private var currentStream = 0

    private val engine: TextToSpeech =
        TextToSpeech(context) { status ->
            engineReady = status == TextToSpeech.SUCCESS
            if (engineReady) {
                engine.language = Locale.getDefault()
                engine.setOnUtteranceProgressListener(progressListener)
                applyVoice()
                prepare(pendingPhrases)
            } else {
                Log.w(TAG, "Text to speech is unavailable; letters will be silent")
            }
        }

    private val main = Handler(Looper.getMainLooper())

    /** Whether live speech (a word, a sentence, a lesson step) is being spoken. Letter clips are too short to count. */
    var speaking: Boolean by mutableStateOf(false)
        private set

    private val progressListener =
        object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                if (utteranceId == LIVE_ID) main.post { speaking = true }
            }

            override fun onDone(utteranceId: String) {
                if (utteranceId == LIVE_ID) {
                    main.post { speaking = false }
                    return
                }
                val phrase = utteranceId.removePrefix(CLIP_PREFIX)
                if (phrase == utteranceId) return
                rendering.remove(phrase)
                val file = fileFor(phrase)
                WavSilence.prepend(file, ONSET_SILENCE_MILLIS)
                clipIds[phrase] = soundPool.load(file.path, 1)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                if (utteranceId == LIVE_ID) {
                    main.post { speaking = false }
                    return
                }
                rendering.remove(utteranceId.removePrefix(CLIP_PREFIX))
            }
        }

    /** Stops whatever is being said, live speech and clips alike. */
    fun stop() {
        if (engineReady) engine.stop()
        soundPool.stop(currentStream)
        speaking = false
    }

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedClips += sampleId
        }
    }

    /** Installed voices for the current language, best first. Empty until the engine is ready. */
    fun availableVoices(): List<VoiceOption> =
        rankedVoices().map { voice ->
            VoiceOption(
                id = voice.name,
                name = displayName(voice),
                quality = qualityLabel(voice.quality),
                language = voice.locale.toLanguageTag(),
            )
        }

    /** Switches voice, or back to the best installed one with null. Clips are rendered again for it. */
    fun select(voiceId: String?) {
        if (voiceId == requestedVoiceId && engineReady) return
        requestedVoiceId = voiceId
        if (!engineReady) return
        applyVoice()
        clipIds.clear()
        loadedClips.clear()
        prepare(pendingPhrases)
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
            engine.setSpeechRate(if (phrase.length == 1) LETTER_RATE else 1f)
            engine.synthesizeToFile(speakable(phrase), Bundle(), file, CLIP_PREFIX + phrase)
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
        engine.setSpeechRate(if (text.length == 1) LETTER_RATE else 1f)
        // A moment of silence first wakes the audio output, so the first consonant is not lost.
        engine.playSilentUtterance(ONSET_SILENCE_MILLIS.toLong(), queueMode, "silence")
        engine.speak(speakable(text), TextToSpeech.QUEUE_ADD, null, LIVE_ID)
    }

    fun release() {
        engine.stop()
        engine.shutdown()
        soundPool.release()
    }

    /**
     * A single character is spoken as a letter name, so pairs like Z and C keep their difference.
     * That is one SSML tag, the same for every letter, and only Google's engine is known to honor
     * it; other engines get the plain character so they never read the tag aloud.
     */
    private fun speakable(phrase: String): CharSequence {
        if (phrase.length != 1 || engine.defaultEngine != GOOGLE_ENGINE) return phrase
        val escaped = phrase.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<speak><say-as interpret-as=\"characters\">$escaped</say-as></speak>"
    }

    private fun applyVoice() {
        val voices = rankedVoices()
        val chosen = voices.firstOrNull { it.name == requestedVoiceId } ?: voices.firstOrNull() ?: return
        engine.voice = chosen
    }

    /**
     * Installed, offline voices for the current language, highest quality first, then lowest
     * latency. Falls back to every installed voice if none match the language.
     */
    private fun rankedVoices(): List<Voice> {
        if (!engineReady) return emptyList()
        val all =
            runCatching { engine.voices }
                .getOrNull()
                .orEmpty()
                .filter { voice ->
                    !voice.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features
                }
        val language = Locale.getDefault().language
        val matching = all.filter { it.locale.language == language }
        val candidates = matching.ifEmpty { all }
        val current = Locale.getDefault()
        // The engine's own default breaks ties, so the automatic choice is the voice the system would use.
        val systemDefault = runCatching { engine.defaultVoice?.name }.getOrNull()
        return candidates.sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenByDescending { it.locale == current }
                .thenByDescending { it.name == systemDefault }
                .thenBy { it.latency }
                .thenBy { it.name },
        )
    }

    private fun displayName(voice: Voice): String {
        // Google's names look like "en-us-x-tpf-local"; show the language and the engine's short tag.
        val tag =
            voice.name
                .substringAfter("-x-", voice.name)
                .removeSuffix("-local")
                .removeSuffix("-network")
        return "${voice.locale.displayName} ($tag)"
    }

    private fun qualityLabel(quality: Int): String =
        when {
            quality >= Voice.QUALITY_VERY_HIGH -> "Very high"
            quality >= Voice.QUALITY_HIGH -> "High"
            quality >= Voice.QUALITY_NORMAL -> "Normal"
            else -> "Low"
        }

    private fun fileFor(phrase: String): File {
        val safeName = phrase.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        val voiceKey = (engine.voice?.name ?: "default").hashCode()
        return File(cacheDir, "$safeName-${phrase.hashCode()}-$voiceKey.wav")
    }

    private companion object {
        const val TAG = "LetterVoice"
        const val CLIP_PREFIX = "clip:"
        const val LIVE_ID = "live"
        const val GOOGLE_ENGINE = "com.google.android.tts"

        /** Letters are said a little slower than running speech so they are easier to tell apart. */
        const val LETTER_RATE = 0.85f

        /**
         * Silence at the start of every sound. The audio output wakes up over the first few tens of
         * milliseconds, and anything spoken in that window is lost: "boo" arrives as "oo".
         */
        const val ONSET_SILENCE_MILLIS = 60
    }
}
