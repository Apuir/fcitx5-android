package org.fcitx.fcitx5.android.link

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import org.fcitx.fcitx5.android.input.FcitxInputMethodService
import org.fcitx.fcitx5.android.R
import com.k2fsa.sherpa.onnx.*
import org.json.JSONObject
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object SherpaSpeechClient {

    private val recognizerRef = AtomicReference<OfflineRecognizer?>(null)
    private val streamRef = AtomicReference<AtomicReference<OfflineStream?>?>()
    // 采用双重引用或直接维持标准 AtomicReference
    private val currentStreamRef = AtomicReference<OfflineStream?>(null)

    // 使用 AtomicBoolean 确保多线程状态切换的可见性与原子性
    private val isHolding = AtomicBoolean(false)

    private var audioJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private var serviceRef: WeakReference<FcitxInputMethodService>? = null
    private val initLock = Any()  // 初始化锁
    private val audioLock = Any() // 音频处理与流释放锁

    private const val SAMPLE_RATE = 16000

    // 静音检测阈值（振幅比值 0.0f ~ 1.0f）
    private const val NOISE_THRESHOLD = 0.02f

    /**
     * 按需懒加载初始化推理引擎（运行在 IO 线程）
     */
    private fun initEngineIfNeeded(ctx: Context): Boolean {
        if (recognizerRef.get() != null) return true

        synchronized(initLock) {
            if (recognizerRef.get() != null) return true

            val appContext = ctx.applicationContext
            val voiceDir = File(appContext.getExternalFilesDir(null), "voice")
            val metaFile = File(voiceDir, "metadata.json")
            val tokensFile = File(voiceDir, "tokens.txt")

            var numThreads = 4
            var modelName = "model.int8.onnx"
            var language = "auto"
            var provider = "cpu"
            var decodingMethod = "greedy_search"

            if (metaFile.exists()) {
                try {
                    val jsonString = metaFile.readText(Charsets.UTF_8)
                    val json = JSONObject(jsonString)
                    numThreads = json.optInt("numThreads", numThreads)
                    modelName = json.optString("model", modelName)
                    language = json.optString("language", language)
                    provider = json.optString("provider", provider)
                    decodingMethod = json.optString("decodingMethod", decodingMethod)
                    Timber.i("⚙️ 成功从外部 JSON 载入配置")
                } catch (e: Exception) {
                    Timber.e(e, "⚠️ 外部 metadata.json 解析失败")
                }
            }

            val modelFile = File(voiceDir, modelName)
            if (!modelFile.exists() || !tokensFile.exists()) {
                Timber.e("❌ 初始化终止：未找到指定的模型文件或词表。")
                return false
            }

            return try {
                val senseVoiceConfig = OfflineSenseVoiceModelConfig(
                    model = modelFile.absolutePath,
                    language = language,
                    useInverseTextNormalization = true,
                    qnnConfig = QnnConfig()
                )

                val modelConfig = OfflineModelConfig(
                    tokens = tokensFile.absolutePath,
                    senseVoice = senseVoiceConfig,
                    debug = false,
                    numThreads = numThreads,
                    provider = provider
                )

                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = modelConfig,
                    decodingMethod = decodingMethod
                )

                recognizerRef.set(OfflineRecognizer(null, config))
                Timber.i("🚀 Sherpa-onnx 外置离线引擎初始化成功！")
                true
            } catch (e: Throwable) {
                Timber.e(e, "❌ 外置引擎模型初始化崩溃")
                false
            }
        }
    }

    /**
     * 按下语音键，启动会话
     */
    fun startHoldSession(service: FcitxInputMethodService) {
        if (!isHolding.compareAndSet(false, true)) return

        serviceRef = WeakReference(service)

        service.lifecycleScope.launch {
            // 切到 IO 线程检查/初始化引擎
            val initSuccess = withContext(Dispatchers.IO) { initEngineIfNeeded(service) }

            if (initSuccess) {
                val engine = recognizerRef.get()
                if (isHolding.get() && engine != null) {
                    try {
                        synchronized(audioLock) {
                            val newStream = engine.createStream()
                            currentStreamRef.set(newStream)
                        }
                        startAudioStreaming(service)
                    } catch (t: Throwable) {
                        Timber.e(t, "创建推理流失败")
                        cancelSession()
                    }
                }
            } else {
                toast(service, "语音识别本地组件未就绪")
                cancelSession()
            }
        }
    }

    /**
     * 松开语音键，结束当前会话并上屏
     */
    fun stopHoldSession() {
        if (!isHolding.compareAndSet(true, false)) return

        val job = audioJob
        audioJob = null

        serviceRef?.get()?.let { s ->
            s.lifecycleScope.launch {
                // 【核心死锁防护】通过 cancelAndJoin 等待音频协程完全退出，确保最后一次解码和资源释放单向顺序执行
                job?.cancelAndJoin()
                s.currentInputConnection?.finishComposingText()
                clearReferences()
            }
        } ?: run {
            job?.cancel()
            clearReferences()
        }
    }

    /**
     * 核心音频采集与流式投喂控制
     */
    private fun startAudioStreaming(service: FcitxInputMethodService) {
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            val intent = Intent(service, MicPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { service.startActivity(intent) }
            runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
            resetStateDirectly()
            return
        }

        audioJob = service.lifecycleScope.launch(Dispatchers.IO) {
            var rec: AudioRecord? = null
            try {
                val ch = AudioFormat.CHANNEL_IN_MONO
                val fmt = AudioFormat.ENCODING_PCM_16BIT
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, ch, fmt)

                // 每帧大小设为 80ms
                val chunkBytes = (SAMPLE_RATE * 80 / 1000) * 2
                val bufSize = minBuf.coerceAtLeast(chunkBytes * 2)

                rec = try {
                    AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, ch, fmt, bufSize)
                } catch (_: Throwable) {
                    AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, ch, fmt, bufSize)
                }

                audioRecord = rec
                rec.startRecording()

                val chunk = ByteArray(chunkBytes)
                var notifiedRecordingStarted = false
                var loopCounter = 0

                // --- 智能静音过滤状态机 ---
                var hasRealAudioEntered = false // 标记这轮长按中，用户是否开口说话了
                var continuousSilenceCount = 0  // 连续静音的帧数计数（1帧=80ms）

                // 尾音安全缓冲区：即使被判定为静音，只要之前说话过，就强制完整投喂此帧。
                // 10 帧 * 80ms = 800ms 的安全滑动窗缓冲，完美承接“了、啊、呢”等弱尾音
                val maxTailBufferFrames = 10

                // 只要协程处于活动状态且用户仍在长按，并且 AudioRecord 正在录音，就持续循环
                while (isActive && isHolding.get() && rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val n = try { rec.read(chunk, 0, chunk.size) } catch (_: Throwable) { -1 }
                    if (n < 0) break
                    if (n == 0) {
                        delay(10)
                        continue
                    }

                    if (!notifiedRecordingStarted) {
                        notifiedRecordingStarted = true
                        withContext(Dispatchers.Main) {
                            runCatching { VoiceOverlayUiBridge.onRecordingStarted?.invoke() }
                        }
                    }

                    val sampleCount = n / 2
                    val shortChunk = ShortArray(sampleCount)
                    ByteBuffer.wrap(chunk, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortChunk)

                    val amp = calculateAmplitude(chunk, n)
                    val floatChunk = FloatArray(sampleCount)

                    // 1. 振幅门限判断
                    val isCurrentFrameSpeech = amp >= NOISE_THRESHOLD

                    if (isCurrentFrameSpeech) {
                        continuousSilenceCount = 0
                        hasRealAudioEntered = true // 激活状态机
                    } else {
                        continuousSilenceCount++
                    }

                    // 【核心改动】不论当前帧是否是静音，都保留原始采样率缩放（不再强行抹成纯 0.0f）
                    // 确保隐藏在“静音”阈值之下的微弱语气尾音信号不被物理抹杀
                    for (i in 0 until sampleCount) {
                        floatChunk[i] = shortChunk[i] / 32768.0f
                    }

                    // 用于在锁外部承接识别结果的临时变量
                    var pendingText: String? = null

                    // 2. 【智能滑窗拦截】进入锁环境，只做纯 CPU/Native 运算，杜绝挂起
                    synchronized(audioLock) {
                        val engine = recognizerRef.get()
                        val stream = currentStreamRef.get()
                        if (engine != null && stream != null) {

                            if (hasRealAudioEntered) {
                                // 策略：只要曾经说过话，在刚转为静音的 800ms 缓冲区内，必须继续投喂！
                                if (continuousSilenceCount <= maxTailBufferFrames) {

                                    stream.acceptWaveform(floatChunk, SAMPLE_RATE)

                                    // 降低非流式（Offline）模型的中间层解码频率，每 8 轮（约 640ms）解码一次预览
                                    loopCounter++
                                    if (loopCounter % 8 == 0) {
                                        try {
                                            engine.decode(stream)
                                            val resultObj = engine.getResult(stream)
                                            if (resultObj.text.isNotBlank()) {
                                                pendingText = cleanSenseVoiceText(resultObj.text)
                                            }
                                        } catch (e: Throwable) {
                                            Timber.e(e, "流式运行中解码失败")
                                        }
                                    }
                                } else {
                                    // 超过 800ms 的绝对静音，视为用户彻底闭嘴，截断投喂，防止模型幻觉
                                }
                            }
                        }
                    } // 🔓 锁瞬间释放
                    // 3. 【安全锁外挂起】切换到主线程更新输入法框
                    if (!pendingText.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            serviceRef?.get()?.currentInputConnection?.setComposingText(pendingText, 1)
                        }
                    }

                    // 刷新 UI 波形振幅
                    withContext(Dispatchers.Main) {
                        runCatching { VoiceOverlayUiBridge.onAmplitude?.invoke(amp) }
                    }

                    delay(5)
                }

                // 4. 【最终解码上屏】当用户抬起手，只要期间确实说过话，才做最后全量解码
                var finalCleanText: String? = null

                synchronized(audioLock) {
                    val engine = recognizerRef.get()
                    val stream = currentStreamRef.get()
                    if (engine != null && stream != null && hasRealAudioEntered) {
                        try {
                            engine.decode(stream)
                            val finalResult = engine.getResult(stream)
                            if (finalResult.text.isNotBlank()) {
                                finalCleanText = cleanSenseVoiceText(finalResult.text)
                            }
                        } catch (e: Throwable) {
                            Timber.e(e, "松手后最终解码失败")
                        }
                    }
                } // 🔓 锁释放

                // 在锁外安全上屏最终全量文本，语气词一个都不会漏
                if (!finalCleanText.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        serviceRef?.get()?.currentInputConnection?.setComposingText(finalCleanText, 1)
                    }
                }

            } catch (t: Throwable) {
                Timber.e(t, "录音及核心推理链异常")
                withContext(Dispatchers.Main) {
                    serviceRef?.get()?.let { toast(it, it.getString(R.string.err_audio_record_failed)) }
                    runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
                }
            } finally {
                // 确保任何情况下都能彻底关闭和释放 AudioRecord 硬件资源
                try {
                    rec?.stop()
                    rec?.release()
                } catch (_: Throwable) {}
                if (audioRecord == rec) {
                    audioRecord = null
                }
                // 在协程自收尾阶段安全释放底层 C++ Stream
                synchronized(audioLock) {
                    try { currentStreamRef.get()?.release() } catch (_: Throwable) {}
                    currentStreamRef.set(null)
                }
            }
        }
    }

    fun isHolding(): Boolean = isHolding.get()

    private fun cancelSession() {
        isHolding.set(false)
        audioJob?.cancel()
        audioJob = null
        runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
        resetStateDirectly()
    }

    private fun resetStateDirectly() {
        isHolding.set(false)
        synchronized(audioLock) {
            try { currentStreamRef.get()?.release() } catch (_: Throwable) {}
            currentStreamRef.set(null)
        }
        clearReferences()
    }

    private fun clearReferences() {
        serviceRef?.clear()
        serviceRef = null
    }

    private fun calculateAmplitude(buffer: ByteArray, size: Int): Float {
        var max = 0
        for (i in 0 until size step 2) {
            if (i + 1 >= size) break
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            val absSample = kotlin.math.abs(sample)
            if (absSample > max) max = absSample
        }
        return max.toFloat() / 32768f
    }

    private fun cleanSenseVoiceText(rawText: String): String {
        return rawText.replace(Regex("<\\|.*?\\|>"), "").trim()
    }

    private fun toast(ctx: Context, msg: String) {
        ContextCompat.getMainExecutor(ctx).execute {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }
}