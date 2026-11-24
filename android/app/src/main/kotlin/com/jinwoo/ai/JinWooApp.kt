package com.jinwoo.ai

import android.app.Application
import androidx.room.Room
import com.jinwoo.ai.actions.ActionRegistry
import com.jinwoo.ai.data.AppDatabase
import com.jinwoo.ai.data.SecureStore
import com.jinwoo.ai.data.SettingsRepository
import com.jinwoo.ai.net.BackendClient
import com.jinwoo.ai.voice.ActivationSound
import com.jinwoo.ai.voice.SpeechInput
import com.jinwoo.ai.voice.VoiceOutput
import com.jinwoo.ai.wake.WakeBus
import kotlinx.coroutines.flow.MutableSharedFlow

/** Manual dependency container (no DI framework needed for an app this size). */
class AppContainer(app: Application) {
    val settings = SettingsRepository(app)
    val secure = SecureStore(app)
    val backend = BackendClient(settings, secure)
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "jinwoo.db").build()
    val voice = VoiceOutput(app, settings, backend)
    val speechInput = SpeechInput(app)
    val sound = ActivationSound(app, settings)
    val wakeBus = WakeBus()
    val actions = ActionRegistry.default()
    val historyCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Set by MainActivity.onStart/onStop. Used so background wake detections post a notification instead. */
    @Volatile var appInForeground: Boolean = false
}

class JinWooApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
