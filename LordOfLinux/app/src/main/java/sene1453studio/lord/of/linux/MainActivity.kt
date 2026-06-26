package sene1453studio.lord.of.linux

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import java.io.File
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import sene1453studio.lord.of.linux.databinding.ActivitiyMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivitiyMainBinding
    private lateinit var gameManager: GameManager
    private lateinit var soundManager: SoundManager

    private var auraAnimator: ValueAnimator? = null
    private var discoAnimator: ValueAnimator? = null
    private val MAX_POPUPS = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitiyMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gameManager = GameManager.getInstance(this)
        soundManager = SoundManager.getInstance(this)

        soundManager.generateBuiltInSounds()
        soundManager.setupBackgroundMusic(gameManager)

        setupLogoTouch()
        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        binding.btnMarket.setOnClickListener {
            startActivity(android.content.Intent(this, MarketActivity::class.java))
        }

        binding.gameArea.clipChildren = false
        binding.gameArea.clipToPadding = false
        binding.mainLayout.clipChildren = false
        binding.mainLayout.clipToPadding = false

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        soundManager.loadClickSound(gameManager.activeClickSound, gameManager)

        binding.root.post {
            if (gameManager.isMusicEnabled && !soundManager.isMusicPlaying) {
                soundManager.setupBackgroundMusic(gameManager)
            } else if (!gameManager.isMusicEnabled) {
                soundManager.toggleMusic(false)
            }

            updateUI()
            setupAuraEffect()
            updateLogo()
            setupDiscoBackground()
        }
    }

    private fun updateUI() {
        binding.balanceText.text = getString(R.string.balance, GameManager.formatNumber(gameManager.spendableClicks))
        binding.sessionText.text = getString(R.string.session, GameManager.formatNumber(gameManager.sessionClicks))
        binding.totalEarnedText.text = getString(R.string.total_earned, GameManager.formatNumber(gameManager.totalEarned))

        val rebirthStatus = if (gameManager.canRebirth) getString(R.string.rebirth_ready_status) else ""
        binding.rebirthText.text = getString(R.string.rebirth_bonus_label, String.format("%.2f", gameManager.rebirthMultiplier), rebirthStatus)

        binding.levelText.text = getString(R.string.level, gameManager.level)
        binding.nextLevelText.text = getString(R.string.next, GameManager.formatNumber(gameManager.clicksToNextLevel))
        binding.levelProgress.progress = (gameManager.levelProgress * 10000).toInt()
        binding.clickValueText.text = getString(R.string.per_click, gameManager.clickValue)
    }

    private fun setupDiscoBackground() {
        discoAnimator?.cancel()
        val paletteColors = gameManager.activePaletteColors
        if (paletteColors.isEmpty()) return

        var currentIndex = 0
        discoAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            val evaluator = ArgbEvaluator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val fromColor = paletteColors[currentIndex % paletteColors.size]
                val toColor = paletteColors[(currentIndex + 1) % paletteColors.size]
                binding.mainLayout.setBackgroundColor(evaluator.evaluate(fraction, fromColor, toColor) as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    currentIndex = (currentIndex + 1) % paletteColors.size
                }
            })
            start()
        }
    }

    private fun setupAuraEffect() {
        auraAnimator?.cancel()
        val auraColors = gameManager.activeAuraColors
        if (auraColors == null || auraColors.isEmpty()) {
            binding.auraEffect.background = null
            return
        }
        val gd = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.TRANSPARENT, auraColors[0], Color.TRANSPARENT))
        gd.shape = GradientDrawable.OVAL
        gd.gradientType = GradientDrawable.RADIAL_GRADIENT
        gd.gradientRadius = 500f
        binding.auraEffect.background = gd

        auraAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val idx = (fraction * (auraColors.size - 1)).toInt().coerceIn(0, auraColors.size - 1)
                gd.colors = intArrayOf(Color.TRANSPARENT, auraColors[idx], Color.TRANSPARENT)
                binding.auraEffect.alpha = 0.5f + (0.5f * fraction)
                binding.auraEffect.scaleX = 0.9f + (0.2f * fraction)
                binding.auraEffect.scaleY = 0.9f + (0.2f * fraction)
            }
            start()
        }
    }

    private fun updateLogo() {
        val activeLogoId = gameManager.activeLogo
        val customUri = gameManager.getActiveAssetUri(GameManager.CAT_LOGO)

        when {
            activeLogoId == "logo_windows" -> binding.mainLogo.setImageResource(R.drawable.windowslogo)
            customUri != null -> {
                try {
                    val uri = Uri.parse(customUri)
                    val source = if (uri.scheme == "file") {
                        ImageDecoder.createSource(File(uri.path!!))
                    } else {
                        ImageDecoder.createSource(contentResolver, uri)
                    }
                    val drawable = ImageDecoder.decodeDrawable(source)
                    binding.mainLogo.setImageDrawable(drawable)
                    if (drawable is AnimatedImageDrawable) {
                        drawable.start()
                    }
                } catch (e: Exception) {
                    try {
                        binding.mainLogo.setImageURI(Uri.parse(customUri))
                    } catch (_: Exception) {
                        binding.mainLogo.setImageResource(R.drawable.linuxlogo)
                    }
                }
            }
            else -> binding.mainLogo.setImageResource(R.drawable.linuxlogo)
        }
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLogoTouch() {

        binding.mainLogo.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
            }
            false
        }

        binding.mainLogo.setOnClickListener { view ->
            handleLogoClick(view, lastTouchX, lastTouchY)
        }
    }

    private fun handleLogoClick(view: View, touchScreenX: Float, touchScreenY: Float) {

        breakCount++
        if (breakCount >= BREAK_LIMIT) {
            triggerBreakEffect()
            breakCount = 0
        }

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f, 0.9f, 1.1f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.8f, 1.2f, 0.95f, 1f),
                ObjectAnimator.ofFloat(view, "rotation", 0f, 10f, -10f, 5f, -5f, 0f)
            )
            duration = 300
            interpolator = OvershootInterpolator(1.5f)
            start()
        }

        if (gameManager.isSfxEnabled) {
            soundManager.playClickSound(gameManager.activeClickSound, gameManager.clickVolume)

            if (gameManager.activeStrike != "strike_tap") {
                soundManager.playClickSound(gameManager.activeStrike, gameManager.clickVolume)
            }
            if (gameManager.activeEffect != "effect_none") {
                soundManager.playClickSound(gameManager.activeEffect, gameManager.clickVolume)
            }
        }

        val addedValue = gameManager.addClick()
        animatePlusOne(addedValue, touchScreenX, touchScreenY)
        showVfxEffect(touchScreenX, touchScreenY)
        updateUI()
    }

    private fun triggerBreakEffect() {

        ObjectAnimator.ofFloat(binding.mainLogo, "alpha", 1f, 0f, 1f).apply {
            duration = 400
            start()
        }

        ObjectAnimator.ofFloat(binding.gameArea, "translationX", 0f, 20f, -20f, 10f, -10f, 0f).apply {
            duration = 300
            start()
        }

        if (gameManager.isSfxEnabled) {
            soundManager.playClickSound("effect_crack", gameManager.clickVolume)
        }
    }

    private fun showVfxEffect(x: Float, y: Float) {
        val effectId = gameManager.activeEffect
        if (effectId == "effect_none") return

        val emoji = when (effectId) {
            "effect_crack" -> "⚡"
            "effect_bomb" -> "💥"
            "effect_spark" -> "✨"
            "effect_blood" -> "🩸"
            "effect_star" -> "⭐"
            else -> ""
        }
        if (emoji.isEmpty()) return

        val vfxText = TextView(this).apply {
            text = emoji
            textSize = 40f
        }

        val containerLoc = IntArray(2)
        binding.popupContainer.getLocationOnScreen(containerLoc)
        vfxText.x = x - containerLoc[0] - 40f
        vfxText.y = y - containerLoc[1] - 40f
        binding.popupContainer.addView(vfxText)

        vfxText.animate()
            .scaleX(2f).scaleY(2f).alpha(0f)
            .setDuration(500)
            .withEndAction { binding.popupContainer.removeView(vfxText) }
            .start()
    }

    private var breakCount = 0
    private val BREAK_LIMIT = 20

    private fun animatePlusOne(value: Long, touchScreenX: Float, touchScreenY: Float) {
        while (binding.popupContainer.childCount >= MAX_POPUPS) {
            binding.popupContainer.removeViewAt(0)
        }

        val strikeEmoji = when (gameManager.activeStrike) {
            "strike_punch" -> "👊"
            "strike_slap" -> "✋"
            "strike_kick" -> "🦶"
            "strike_sword" -> "⚔️"
            "strike_lightning" -> "⚡"
            "strike_nuclear" -> "☢️"
            else -> ""
        }

        val popupText = TextView(this).apply {
            val formatted = GameManager.formatNumber(value)
            text = if (strikeEmoji.isNotEmpty()) "$strikeEmoji +$value" else "+$value"
            setTypeface(null, android.graphics.Typeface.BOLD)
            textSize = 36f
        }

        val innerC = gameManager.activeInnerColorValue
        val outerC = gameManager.activeOuterColorValue
        val textColor = when {
            innerC == Color.BLACK && outerC == Color.BLACK -> Color.WHITE
            innerC == Color.WHITE && outerC == Color.WHITE -> Color.parseColor("#FFD700")
            else -> innerC
        }
        val shadowColor = when {
            outerC == Color.BLACK -> Color.parseColor("#C8AA6E")
            outerC == Color.WHITE -> Color.parseColor("#1A1A2E")
            else -> outerC
        }
        popupText.setTextColor(textColor)
        popupText.setShadowLayer(12f, 2f, 2f, shadowColor)

        val containerLoc = IntArray(2)
        binding.popupContainer.getLocationOnScreen(containerLoc)
        val localX = touchScreenX - containerLoc[0]
        val localY = touchScreenY - containerLoc[1]
        val randomXOffset = ((Math.random() * 60) - 30).toFloat()

        popupText.x = localX - 30f + randomXOffset
        popupText.y = localY - 20f
        binding.popupContainer.addView(popupText)

        val floatUp = 300f + (Math.random() * 100f).toFloat()

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(popupText, "translationY", 0f, -floatUp),
                ObjectAnimator.ofFloat(popupText, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(popupText, "scaleX", 0.5f, 1.3f, 1.0f),
                ObjectAnimator.ofFloat(popupText, "scaleY", 0.5f, 1.3f, 1.0f)
            )
            duration = 1200
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.popupContainer.removeView(popupText)
                }
            })
            start()
        }
    }

    override fun onDestroy() {
        auraAnimator?.cancel()
        discoAnimator?.cancel()
        super.onDestroy()
    }
}

