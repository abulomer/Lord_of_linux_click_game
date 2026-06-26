package sene1453studio.lord.of.linux

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var gameManager: GameManager
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        gameManager = GameManager.getInstance(this)
        soundManager = SoundManager.getInstance(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        setupRebirth()
        setupSoundControls()
        setupCustomization()
    }

    private fun setupCustomization() {
        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val rgLanguage = findViewById<RadioGroup>(R.id.rgLanguage)
        val prefs = getSharedPreferences("GamePrefs", MODE_PRIVATE)

        val savedTheme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> rgTheme.check(R.id.rbThemeLight)
            AppCompatDelegate.MODE_NIGHT_YES -> rgTheme.check(R.id.rbThemeDark)
            else -> rgTheme.check(R.id.rbThemeSystem)
        }

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rbThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
            prefs.edit().putInt("theme", mode).apply()
        }

        val savedLang = prefs.getString("language_tag", "") ?: ""
        when (savedLang) {
            "tr" -> rgLanguage.check(R.id.rbLangTr)
            "en" -> rgLanguage.check(R.id.rbLangEn)
            else -> rgLanguage.check(R.id.rbLangSystem)
        }

        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val tag = when (checkedId) {
                R.id.rbLangEn -> "en"
                R.id.rbLangTr -> "tr"
                else -> ""
            }
            val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)

            prefs.edit().putString("language_tag", tag).apply()
        }
    }

    private fun setupRebirth() {
        val btnRebirth = findViewById<Button>(R.id.btnRebirth)
        val rebirthInfo = findViewById<TextView>(R.id.rebirthInfo)

        val currentRebirthMult = gameManager.rebirthMultiplier
        val nextRebirthMult = 1.0f + ((gameManager.rebirthCount + 1).coerceAtMost(30) * 0.5f)
        val requiredLevel = gameManager.rebirthLevelRequired

        if (gameManager.canRebirth) {
            btnRebirth.isEnabled = true
            btnRebirth.alpha = 1.0f
            rebirthInfo.text = getString(R.string.rebirth_info_ready, requiredLevel, currentRebirthMult.toString(), nextRebirthMult.toString())
        } else {
            btnRebirth.isEnabled = false
            btnRebirth.alpha = 0.5f
            rebirthInfo.text = getString(R.string.rebirth_info_not_ready, currentRebirthMult.toString(), nextRebirthMult.toString(), requiredLevel, gameManager.level)
        }

        btnRebirth.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_rebirth_title))
                .setMessage(getString(R.string.dialog_rebirth_msg))
                .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                    if (gameManager.doRebirth()) {
                        Toast.makeText(this, getString(R.string.msg_rebirth_done, gameManager.rebirthMultiplier.toString()), Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
                .setNegativeButton(getString(R.string.dialog_no), null)
                .show()
        }
    }

    private fun setupSoundControls() {
        val switchMusic = findViewById<Switch>(R.id.switchMusic)
        val switchSfx = findViewById<Switch>(R.id.switchSfx)
        val seekMusic = findViewById<SeekBar>(R.id.seekMusic)
        val seekClick = findViewById<SeekBar>(R.id.seekClick)
        val lblMusicVol = findViewById<TextView>(R.id.lblMusicVol)
        val lblClickVol = findViewById<TextView>(R.id.lblClickVol)

        switchMusic.isChecked = gameManager.isMusicEnabled
        switchSfx.isChecked = gameManager.isSfxEnabled

        fun updateMusicLabel(progress: Int) {
            lblMusicVol.text = getString(R.string.settings_music_vol) + " ($progress%)"
        }
        fun updateClickLabel(progress: Int) {
            lblClickVol.text = getString(R.string.settings_click_vol) + " ($progress%)"
        }

        switchMusic.setOnCheckedChangeListener { _, isChecked ->
            gameManager.isMusicEnabled = isChecked

            soundManager.toggleMusicWithSetup(isChecked, gameManager)
        }

        switchSfx.setOnCheckedChangeListener { _, isChecked ->
            gameManager.isSfxEnabled = isChecked

            if (isChecked) {
                soundManager.playClickSound(gameManager.activeClickSound, gameManager.clickVolume)
            }
        }

        seekMusic.progress = (gameManager.musicVolume * 100).toInt()
        updateMusicLabel(seekMusic.progress)
        seekMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                gameManager.musicVolume = p / 100f

                soundManager.setMusicVolume(gameManager.musicVolume)
                updateMusicLabel(p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        var lastPreviewTime = 0L
        seekClick.progress = (gameManager.clickVolume * 100).toInt()
        updateClickLabel(seekClick.progress)
        seekClick.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var lastStepValue = seekClick.progress / 10

            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                gameManager.clickVolume = p / 100f
                updateClickLabel(p)

                if (b) {

                    val currentStep = p / 10
                    if (currentStep != lastStepValue) {
                        lastStepValue = currentStep
                        val now = System.currentTimeMillis()
                        if (now - lastPreviewTime > 100) {
                            lastPreviewTime = now
                            soundManager.playClickSound(gameManager.activeClickSound, gameManager.clickVolume)
                        }
                    }
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {
                lastStepValue = (s?.progress ?: 0) / 10
            }
            override fun onStopTrackingTouch(s: SeekBar?) {

                soundManager.playClickSound(gameManager.activeClickSound, gameManager.clickVolume)
            }
        })
    }
}

