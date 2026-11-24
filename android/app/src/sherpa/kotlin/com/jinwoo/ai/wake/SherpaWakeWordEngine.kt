package com.jinwoo.ai.wake

// Compiled ONLY when app/libs/sherpa-onnx.aar exists (see app/build.gradle.kts and docs/WAKE_WORD_SETUP.md).
// IMPORTANT: written against the sherpa-onnx Kotlin API from memory of its Android keyword-spotting example. It could not
// be compiled in the build sandbox. If a class/parameter name differs in your AAR version, adjust here (one small file);
// compare with the SherpaOnnxKws example in the official sherpa-onnx repository.
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

class SherpaWakeWordEngine(
    encoder: String, decoder: String, joiner: String, tokens: String, keywords: String, sensitivity: Float,
) : WakeWordEngine {
    private val spotter: KeywordSpotter
    private val stream: OnlineStream

    init {
        // sensitivity 0..1 -> threshold 0.5..0.1 (lower threshold = triggers more easily)
        val threshold = (0.5f - 0.4f * sensitivity.coerceIn(0f, 1f)).coerceIn(0.05f, 0.5f)
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(encoder = encoder, decoder = decoder, joiner = joiner),
                tokens = tokens, numThreads = 2, provider = "cpu",
            ),
            keywordsFile = keywords, keywordsScore = 1.5f, keywordsThreshold = threshold,
        )
        spotter = KeywordSpotter(config = config)
        stream = spotter.createStream()
    }

    override fun process(pcm: ShortArray, length: Int): Boolean {
        val samples = FloatArray(length) { pcm[it] / 32768f }
        stream.acceptWaveform(samples, 16000)
        var hit = false
        while (spotter.isReady(stream)) {
            spotter.decode(stream)
            if (spotter.getResult(stream).keyword.isNotBlank()) { hit = true; spotter.reset(stream) }
        }
        return hit
    }

    override fun close() { stream.release(); spotter.release() }
}
