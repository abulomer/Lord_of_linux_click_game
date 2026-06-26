package sene1453studio.lord.of.linux

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class MainApplication : Application() {

    private var activityCount = 0

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

        val themeMode = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)

        val langTag = prefs.getString("language_tag", "") ?: ""
        if (langTag.isNotEmpty()) {
            val locales = LocaleListCompat.forLanguageTags(langTag)
            AppCompatDelegate.setApplicationLocales(locales)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                activityCount++
                if (activityCount == 1) {
                    val gameManager = GameManager.getInstance(this@MainApplication)
                    if (gameManager.isMusicEnabled) {
                        SoundManager.getInstance(this@MainApplication).toggleMusic(true)
                    }
                }
            }

            override fun onActivityStopped(activity: Activity) {
                activityCount--
                if (activityCount <= 0) {
                    activityCount = 0
                    SoundManager.getInstance(this@MainApplication).toggleMusic(false)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {

                if (activityCount == 0 && activity.isFinishing) {
                    SoundManager.getInstance(this@MainApplication).releaseMusic()
                }
            }
        })
    }
}

