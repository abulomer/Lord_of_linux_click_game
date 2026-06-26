package sene1453studio.lord.of.linux

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit
import kotlin.math.floor
import kotlin.math.pow

data class MarketItem(
    val id: String,
    val nameRes: Int,
    val price: Long,
    val multiplier: Float,
    val category: String,
    val colorValue: Int = 0,
    val auraColors: IntArray? = null,
    val paletteColors: IntArray? = null,
    val descRes: Int,
    val minRebirth: Int = 0,
    var customName: String? = null
) {
    override fun equals(other: Any?): Boolean = other is MarketItem && id == other.id
    override fun hashCode(): Int = id.hashCode()
}

class GameManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

    private val SAFE_LIMIT = Long.MAX_VALUE - 1000
    private val MAX_LEVEL = 1000
    private val MAX_REBIRTH = 30

    var spendableClicks: Long
        get() = prefs.getLong("spendable_clicks", 0L)
        set(v) = prefs.edit { putLong("spendable_clicks", v.coerceIn(0L, SAFE_LIMIT)) }

    var totalEarned: Long
        get() = prefs.getLong("total_earned", 0L)
        set(v) = prefs.edit { putLong("total_earned", v.coerceIn(0L, SAFE_LIMIT)) }

    var sessionClicks: Long = 0L

    var rebirthCount: Int
        get() = prefs.getInt("rebirth_count", 0)
        set(v) = prefs.edit { putInt("rebirth_count", v.coerceIn(0, MAX_REBIRTH)) }

    fun getPurchasedItems(): MutableSet<String> {
        return prefs.getStringSet("purchased_items", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

    private fun savePurchasedItems(items: Set<String>) {
        prefs.edit { putStringSet("purchased_items", items) }
    }

    fun isPurchased(itemId: String): Boolean {
        val freeItems = setOf("outer_white", "inner_white", "aura_none", "logo_linux", "sound_default", "bg_rainbow", "effect_none", "strike_tap", "music_default")
        if (itemId.endsWith("_default") || itemId in freeItems) return true
        return getPurchasedItems().contains(itemId)
    }

    fun purchase(itemId: String): Boolean {
        val item = allMarketItems.find { it.id == itemId } ?: return false
        if (isPurchased(itemId)) return false
        if (spendableClicks < item.price) return false
        if (rebirthCount < item.minRebirth) return false

        spendableClicks -= item.price
        val items = getPurchasedItems()
        items.add(itemId)
        savePurchasedItems(items)
        return true
    }

    var activeOuterColor: String
        get() = prefs.getString("active_outer", "outer_white") ?: "outer_white"
        set(v) = prefs.edit { putString("active_outer", v) }

    var activeInnerColor: String
        get() = prefs.getString("active_inner", "inner_white") ?: "inner_white"
        set(v) = prefs.edit { putString("active_inner", v) }

    var activeAura: String
        get() = prefs.getString("active_aura", "aura_none") ?: "aura_none"
        set(v) = prefs.edit { putString("active_aura", v) }

    var activeLogo: String
        get() = prefs.getString("active_logo", "logo_linux") ?: "logo_linux"
        set(v) = prefs.edit { putString("active_logo", v) }

    var activeClickSound: String
        get() = prefs.getString("active_click_sound", "sound_default") ?: "sound_default"
        set(v) = prefs.edit { putString("active_click_sound", v) }

    var activeBackgroundPalette: String
        get() = prefs.getString("active_bg_palette", "bg_rainbow") ?: "bg_rainbow"
        set(v) = prefs.edit { putString("active_bg_palette", v) }

    var activeMusic: String
        get() = prefs.getString("active_music", "music_default") ?: "music_default"
        set(v) = prefs.edit { putString("active_music", v) }

    var activeEffect: String
        get() = prefs.getString("active_effect", "effect_none") ?: "effect_none"
        set(v) = prefs.edit { putString("active_effect", v) }

    var activeStrike: String
        get() = prefs.getString("active_strike", "strike_tap") ?: "strike_tap"
        set(v) = prefs.edit { putString("active_strike", v) }

    var isMusicEnabled: Boolean
        get() = prefs.getBoolean("music_enabled", false)
        set(v) = prefs.edit { putBoolean("music_enabled", v) }

    var isSfxEnabled: Boolean
        get() = prefs.getBoolean("sfx_enabled", true)
        set(v) = prefs.edit { putBoolean("sfx_enabled", v) }

    var musicVolume: Float
        get() = prefs.getFloat("music_volume", 0.7f)
        set(v) = prefs.edit { putFloat("music_volume", v) }

    var clickVolume: Float
        get() = prefs.getFloat("click_volume", 1.0f)
        set(v) = prefs.edit { putFloat("click_volume", v) }

    fun getActiveAssetUri(category: String): String? {
        val activeId = when (category) {
            CAT_MUSIC -> activeMusic
            CAT_SOUNDS -> activeClickSound
            CAT_LOGO -> activeLogo
            else -> return null
        }
        return getDynamicUri(activeId)
    }

    fun getCustomName(id: String): String? = prefs.getString("custom_name_$id", null)
    fun setCustomName(id: String, name: String?) = prefs.edit {
        if (name == null) remove("custom_name_$id") else putString("custom_name_$id", name)
    }

    fun addCustomAsset(name: String, uri: String, category: String): String {
        val id = "dyn_custom_${System.currentTimeMillis()}"
        val assets = prefs.getStringSet("custom_asset_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        assets.add(id)

        val importedMultiplier = when(category) {
            CAT_LOGO -> 5.0f
            CAT_MUSIC -> 2.0f
            CAT_SOUNDS -> 1.0f
            else -> 1.0f
        }

        prefs.edit {
            putStringSet("custom_asset_ids", assets)
            putString("custom_uri_$id", uri)
            putString("custom_name_$id", name)
            putString("custom_cat_$id", category)
            putFloat("custom_mult_$id", importedMultiplier)
        }
        val purchased = getPurchasedItems()
        purchased.add(id)
        savePurchasedItems(purchased)
        return id
    }

    fun getDynamicAssets(): List<MarketItem> {
        val ids = prefs.getStringSet("custom_asset_ids", emptySet()) ?: emptySet()
        return ids.mapNotNull { id ->
            val cat = prefs.getString("custom_cat_$id", null) ?: return@mapNotNull null
            val name = prefs.getString("custom_name_$id", "Unnamed")
            val uri = prefs.getString("custom_uri_$id", null) ?: return@mapNotNull null
            val mult = prefs.getFloat("custom_mult_$id", 1.0f)

            MarketItem(
                id = id,
                nameRes = 0,
                price = 0L,
                multiplier = mult,
                category = cat,
                descRes = R.string.item_sound_custom_desc,
                customName = name
            )
        }
    }

    fun deleteDynamicAsset(id: String) {
        val assets = prefs.getStringSet("custom_asset_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        assets.remove(id)
        prefs.edit {
            putStringSet("custom_asset_ids", assets)
            remove("custom_uri_$id")
            remove("custom_name_$id")
            remove("custom_cat_$id")
        }
        val purchased = getPurchasedItems()
        purchased.remove(id)
        savePurchasedItems(purchased)
    }

    fun getDynamicUri(id: String): String? = prefs.getString("custom_uri_$id", null)

    val level: Int get() = calculateLevel(totalEarned)

    private fun calculateLevel(earned: Long): Int {
        var lvl = 1
        var cumulative = 0L
        while (lvl < MAX_LEVEL) {
            val required = clicksRequiredForLevel(lvl)
            if (SAFE_LIMIT - required < cumulative) break
            if (earned < cumulative + required) break
            cumulative += required
            lvl++
        }
        return lvl.coerceAtMost(MAX_LEVEL)
    }

    fun clicksRequiredForLevel(lvl: Int): Long {

        return floor(100.0 * 1.035.pow((lvl - 1).toDouble())).toLong().coerceAtLeast(1L)
    }

    fun cumulativeClicksForLevel(targetLevel: Int): Long {
        var total = 0L
        for (i in 1 until targetLevel.coerceAtMost(MAX_LEVEL + 1)) {
            val req = clicksRequiredForLevel(i)
            if (SAFE_LIMIT - req < total) return SAFE_LIMIT
            total += req
        }
        return total
    }

    val levelProgress: Float
        get() {
            if (level >= MAX_LEVEL) return 1f
            val currentLevelStart = cumulativeClicksForLevel(level)
            val nextLevelStart = cumulativeClicksForLevel(level + 1)
            val range = nextLevelStart - currentLevelStart
            if (range <= 0) return 1f
            return ((totalEarned - currentLevelStart).toFloat() / range).coerceIn(0f, 1f)
        }

    val clicksToNextLevel: Long
        get() {
            if (level >= MAX_LEVEL) return 0L
            val nextLevelStart = cumulativeClicksForLevel(level + 1)
            return (nextLevelStart - totalEarned).coerceAtLeast(0L)
        }

    val rebirthLevelRequired: Int get() = 25 + rebirthCount * 15

    val canRebirth: Boolean
        get() = level >= rebirthLevelRequired && rebirthCount < MAX_REBIRTH

    val rebirthMultiplier: Float
        get() {
            if (rebirthCount <= 0) return 1f

            return (2.0 * 1.65.pow((rebirthCount - 1).toDouble())).toFloat()
        }

    fun doRebirth(): Boolean {
        if (!canRebirth) return false
        rebirthCount++
        spendableClicks = 0L
        totalEarned = 0L
        sessionClicks = 0L
        return true
    }

    val outerColorMultiplier: Float
        get() = allMarketItems.find { it.id == activeOuterColor }?.multiplier ?: 1f

    val innerColorMultiplier: Float
        get() = allMarketItems.find { it.id == activeInnerColor }?.multiplier ?: 1f

    val auraMultiplier: Float
        get() = allMarketItems.find { it.id == activeAura }?.multiplier ?: 1f

    val logoMultiplier: Float
        get() {
            val activeId = activeLogo
            val dynamic = getDynamicAssets().find { it.id == activeId }
            if (dynamic != null) return dynamic.multiplier
            return logos.find { it.id == activeId }?.multiplier ?: 1f
        }

    val musicMultiplier: Float
        get() {
            val activeId = activeMusic
            val dynamic = getDynamicAssets().find { it.id == activeId }
            if (dynamic != null) return dynamic.multiplier
            return musicItems.find { it.id == activeId }?.multiplier ?: 1f
        }

    val bgPaletteMultiplier: Float
        get() = bgPalettes.find { it.id == activeBackgroundPalette }?.multiplier ?: 1f

    val effectMultiplier: Float
        get() = effects.find { it.id == activeEffect }?.multiplier ?: 1f

    val strikeMultiplier: Float
        get() = strikes.find { it.id == activeStrike }?.multiplier ?: 1f

    val totalMultiplier: Float
        get() = outerColorMultiplier * innerColorMultiplier * auraMultiplier *
                logoMultiplier * musicMultiplier * bgPaletteMultiplier *
                effectMultiplier * strikeMultiplier * rebirthMultiplier

    val clickValue: Long
        get() {
            val value = floor(1.0 * totalMultiplier).toLong()
            return value.coerceAtLeast(1L)
        }

    fun addClick(): Long {
        val value = clickValue
        spendableClicks = (spendableClicks + value).coerceAtMost(SAFE_LIMIT)
        totalEarned = (totalEarned + value).coerceAtMost(SAFE_LIMIT)
        sessionClicks += value
        return value
    }

    val activeOuterColorValue: Int
        get() = allMarketItems.find { it.id == activeOuterColor }?.colorValue ?: Color.WHITE

    val activeInnerColorValue: Int
        get() = allMarketItems.find { it.id == activeInnerColor }?.colorValue ?: Color.BLACK

    val activeAuraColors: IntArray?
        get() = allMarketItems.find { it.id == activeAura }?.auraColors

    val activePaletteColors: IntArray
        get() = allMarketItems.find { it.id == activeBackgroundPalette }?.paletteColors
            ?: bgPalettes[0].paletteColors!!

    companion object {
        private var instance: GameManager? = null

        fun getInstance(context: Context): GameManager {
            return instance ?: GameManager(context.applicationContext).also { instance = it }
        }

        fun formatNumber(n: Long): String {
            return when {
                n >= 1_000_000_000_000_000L -> String.format("%.2fQa", n / 1_000_000_000_000_000.0)
                n >= 1_000_000_000_000L -> String.format("%.2fT", n / 1_000_000_000_000.0)
                n >= 1_000_000_000L -> String.format("%.1fB", n / 1_000_000_000.0)
                n >= 1_000_000L -> String.format("%.1fM", n / 1_000_000.0)
                n >= 10_000L -> String.format("%.1fK", n / 1_000.0)
                else -> "%,d".format(n)
            }
        }

        const val CAT_SOUNDS = "sounds"
        const val CAT_OUTER = "outer_color"
        const val CAT_INNER = "inner_color"
        const val CAT_AURA = "aura"
        const val CAT_LOGO = "logo"
        const val CAT_MUSIC = "music"
        const val CAT_BG_PALETTE = "bg_palette"
        const val CAT_EFFECTS = "effects"
        const val CAT_STRIKES = "strikes"
        const val CAT_CUSTOMS = "customs"

        val outerColors = listOf(
            MarketItem("outer_white", R.string.item_white, 0, 1.0f, CAT_OUTER, Color.WHITE, descRes = R.string.item_white_desc, minRebirth = 0),
            MarketItem("outer_red", R.string.item_red, 100, 1.1f, CAT_OUTER, Color.RED, descRes = R.string.item_red_desc, minRebirth = 0),
            MarketItem("outer_blue", R.string.item_blue, 500, 1.2f, CAT_OUTER, Color.BLUE, descRes = R.string.item_blue_desc, minRebirth = 0),
            MarketItem("outer_green", R.string.item_green, 2000, 1.3f, CAT_OUTER, Color.GREEN, descRes = R.string.item_green_desc, minRebirth = 0),
            MarketItem("outer_yellow", R.string.item_yellow, 8000, 1.4f, CAT_OUTER, Color.parseColor("#FFD700"), descRes = R.string.item_yellow_desc, minRebirth = 0),
            MarketItem("outer_purple", R.string.item_purple, 25000, 1.6f, CAT_OUTER, Color.parseColor("#800080"), descRes = R.string.item_purple_desc, minRebirth = 1),
            MarketItem("outer_teal", R.string.item_teal, 100000, 1.8f, CAT_OUTER, Color.parseColor("#008080"), descRes = R.string.item_teal_desc, minRebirth = 1),
            MarketItem("outer_coral", R.string.item_coral, 500000, 2.0f, CAT_OUTER, Color.parseColor("#FF7F50"), descRes = R.string.item_coral_desc, minRebirth = 2),
            MarketItem("outer_crimson", R.string.item_crimson, 2000000, 2.3f, CAT_OUTER, Color.parseColor("#DC143C"), descRes = R.string.item_crimson_desc, minRebirth = 3),
            MarketItem("outer_gold", R.string.item_gold, 10000000, 2.6f, CAT_OUTER, Color.parseColor("#FFD700"), descRes = R.string.item_gold_desc, minRebirth = 4),
            MarketItem("outer_diamond", R.string.item_diamond, 50000000, 3.0f, CAT_OUTER, Color.parseColor("#B9F2FF"), descRes = R.string.item_diamond_desc, minRebirth = 5),
            MarketItem("outer_emerald", R.string.item_emerald, 250000000, 3.5f, CAT_OUTER, Color.parseColor("#50C878"), descRes = R.string.item_emerald_desc, minRebirth = 7),
            MarketItem("outer_ruby_rare", R.string.item_ruby_rare, 1000000000, 4.0f, CAT_OUTER, Color.parseColor("#E0115F"), descRes = R.string.item_ruby_rare_desc, minRebirth = 9),
            MarketItem("outer_obsidian", R.string.item_obsidian, 5000000000L, 4.5f, CAT_OUTER, Color.parseColor("#303030"), descRes = R.string.item_obsidian_desc, minRebirth = 11),
            MarketItem("outer_antimatter", R.string.item_antimatter, 25000000000L, 5.0f, CAT_OUTER, Color.parseColor("#FF00FF"), descRes = R.string.item_antimatter_desc, minRebirth = 13),
            MarketItem("outer_nebula", R.string.item_nebula, 100000000000L, 5.5f, CAT_OUTER, Color.parseColor("#7B68EE"), descRes = R.string.item_nebula_desc, minRebirth = 15),
            MarketItem("outer_void", R.string.item_void_outer, 500000000000L, 6.0f, CAT_OUTER, Color.parseColor("#1A1A2E"), descRes = R.string.item_void_outer_desc, minRebirth = 17),
            MarketItem("outer_absolute", R.string.item_absolute, 2000000000000L, 6.5f, CAT_OUTER, Color.parseColor("#0D0D0D"), descRes = R.string.item_absolute_desc, minRebirth = 19),
            MarketItem("outer_celestial", R.string.item_celestial_color, 10000000000000L, 7.0f, CAT_OUTER, Color.WHITE, descRes = R.string.item_celestial_color_desc, minRebirth = 20)
        ).sortedBy { it.price }

        val innerColors = listOf(
            MarketItem("inner_white", R.string.item_white, 0, 1.0f, CAT_INNER, Color.WHITE, descRes = R.string.item_white_desc, minRebirth = 0),
            MarketItem("inner_cyan", R.string.item_cyan, 200, 1.1f, CAT_INNER, Color.CYAN, descRes = R.string.item_cyan_desc, minRebirth = 0),
            MarketItem("inner_magenta", R.string.item_magenta, 1000, 1.2f, CAT_INNER, Color.MAGENTA, descRes = R.string.item_magenta_desc, minRebirth = 0),
            MarketItem("inner_orange", R.string.item_orange, 5000, 1.3f, CAT_INNER, Color.parseColor("#FFA500"), descRes = R.string.item_orange_desc, minRebirth = 0),
            MarketItem("inner_pink", R.string.item_pink, 20000, 1.4f, CAT_INNER, Color.parseColor("#FFC0CB"), descRes = R.string.item_pink_desc, minRebirth = 1),
            MarketItem("inner_lime", R.string.item_lime, 80000, 1.5f, CAT_INNER, Color.parseColor("#32CD32"), descRes = R.string.item_lime_desc, minRebirth = 1),
            MarketItem("inner_turquoise", R.string.item_turquoise, 300000, 1.7f, CAT_INNER, Color.parseColor("#40E0D0"), descRes = R.string.item_turquoise_desc, minRebirth = 2),
            MarketItem("inner_gold", R.string.item_gold, 1500000, 1.9f, CAT_INNER, Color.parseColor("#FFD700"), descRes = R.string.item_gold_desc, minRebirth = 3),
            MarketItem("inner_ruby", R.string.item_ruby, 8000000, 2.2f, CAT_INNER, Color.parseColor("#E0115F"), descRes = R.string.item_ruby_desc, minRebirth = 5),
            MarketItem("inner_sapphire", R.string.item_sapphire, 50000000, 2.5f, CAT_INNER, Color.parseColor("#0F52BA"), descRes = R.string.item_sapphire_desc, minRebirth = 7),
            MarketItem("inner_amethyst", R.string.item_amethyst, 300000000, 2.8f, CAT_INNER, Color.parseColor("#9966CC"), descRes = R.string.item_amethyst_desc, minRebirth = 9),
            MarketItem("inner_plasma", R.string.item_plasma, 2000000000L, 3.2f, CAT_INNER, Color.parseColor("#FF4500"), descRes = R.string.item_plasma_desc, minRebirth = 11),
            MarketItem("inner_neutron", R.string.item_neutron, 15000000000L, 3.5f, CAT_INNER, Color.parseColor("#C0C0C0"), descRes = R.string.item_neutron_desc, minRebirth = 13),
            MarketItem("inner_dark_matter", R.string.item_dark_matter, 80000000000L, 3.8f, CAT_INNER, Color.parseColor("#2C2C54"), descRes = R.string.item_dark_matter_desc, minRebirth = 15),
            MarketItem("inner_supernova", R.string.item_supernova, 500000000000L, 4.2f, CAT_INNER, Color.parseColor("#FF6347"), descRes = R.string.item_supernova_desc, minRebirth = 17),
            MarketItem("inner_singularity", R.string.item_singularity, 3000000000000L, 4.5f, CAT_INNER, Color.parseColor("#0A0A0A"), descRes = R.string.item_singularity_desc, minRebirth = 19),
            MarketItem("inner_infinity", R.string.item_infinity, 15000000000000L, 5.0f, CAT_INNER, Color.WHITE, descRes = R.string.item_infinity_desc, minRebirth = 20)
        ).sortedBy { it.price }

        val effects = listOf(
            MarketItem("effect_none", R.string.item_effect_none, 0, 1.0f, CAT_EFFECTS, descRes = R.string.item_effect_none_desc),
            MarketItem("effect_spark", R.string.item_effect_spark, 50000, 1.2f, CAT_EFFECTS, descRes = R.string.item_effect_spark_desc, minRebirth = 1),
            MarketItem("effect_star", R.string.item_effect_star, 1000000, 1.4f, CAT_EFFECTS, descRes = R.string.item_effect_star_desc, minRebirth = 3),
            MarketItem("effect_crack", R.string.item_effect_crack, 25000000, 1.6f, CAT_EFFECTS, descRes = R.string.item_effect_crack_desc, minRebirth = 7),
            MarketItem("effect_blood", R.string.item_effect_blood, 500000000, 1.8f, CAT_EFFECTS, descRes = R.string.item_effect_blood_desc, minRebirth = 12),
            MarketItem("effect_bomb", R.string.item_effect_bomb, 10000000000L, 2.0f, CAT_EFFECTS, descRes = R.string.item_effect_bomb_desc, minRebirth = 17)
        )

        val strikes = listOf(
            MarketItem("strike_tap", R.string.item_strike_tap, 0, 1.0f, CAT_STRIKES, descRes = R.string.item_strike_tap_desc),
            MarketItem("strike_punch", R.string.item_strike_punch, 100000, 1.2f, CAT_STRIKES, descRes = R.string.item_strike_punch_desc, minRebirth = 1),
            MarketItem("strike_slap", R.string.item_strike_slap, 2000000, 1.4f, CAT_STRIKES, descRes = R.string.item_strike_slap_desc, minRebirth = 4),
            MarketItem("strike_kick", R.string.item_strike_kick, 30000000, 1.6f, CAT_STRIKES, descRes = R.string.item_strike_kick_desc, minRebirth = 8),
            MarketItem("strike_sword", R.string.item_strike_sword, 500000000, 1.9f, CAT_STRIKES, descRes = R.string.item_strike_sword_desc, minRebirth = 12),
            MarketItem("strike_lightning", R.string.item_strike_lightning, 10000000000L, 2.2f, CAT_STRIKES, descRes = R.string.item_strike_lightning_desc, minRebirth = 16),
            MarketItem("strike_nuclear", R.string.item_strike_nuclear, 200000000000L, 2.5f, CAT_STRIKES, descRes = R.string.item_strike_nuclear_desc, minRebirth = 20)
        )

        val auras = listOf(
            MarketItem("aura_none", R.string.item_aura_none, 0, 1.0f, CAT_AURA, descRes = R.string.item_aura_none_desc, minRebirth = 0),
            MarketItem("aura_blue", R.string.item_aura_blue, 10000, 1.2f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#00008B"), Color.CYAN), descRes = R.string.item_aura_blue_desc, minRebirth = 1),
            MarketItem("aura_gold", R.string.item_aura_gold, 80000, 1.4f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#B8860B"), Color.YELLOW), descRes = R.string.item_aura_gold_desc, minRebirth = 2),
            MarketItem("aura_fire", R.string.item_aura_fire, 500000, 1.6f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#FF4500"), Color.parseColor("#FF8C00"), Color.YELLOW), descRes = R.string.item_aura_fire_desc, minRebirth = 4),
            MarketItem("aura_frost", R.string.item_aura_frost, 5000000, 1.8f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#E0FFFF"), Color.parseColor("#00CED1"), Color.parseColor("#4169E1")), descRes = R.string.item_aura_frost_desc, minRebirth = 6),
            MarketItem("aura_purple", R.string.item_aura_purple, 30000000, 2.0f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#4B0082"), Color.MAGENTA), descRes = R.string.item_aura_purple_desc, minRebirth = 8),
            MarketItem("aura_rainbow", R.string.item_aura_rainbow, 200000000, 2.3f, CAT_AURA, auraColors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA), descRes = R.string.item_aura_rainbow_desc, minRebirth = 10),
            MarketItem("aura_void", R.string.item_aura_void, 2000000000L, 2.5f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#0A0A0A"), Color.parseColor("#1A1A2E"), Color.parseColor("#16213E")), descRes = R.string.item_aura_void_desc, minRebirth = 12),
            MarketItem("aura_quantum", R.string.item_aura_quantum, 20000000000L, 2.8f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#00FFFF"), Color.parseColor("#FF00FF"), Color.parseColor("#FFFF00")), descRes = R.string.item_aura_quantum_desc, minRebirth = 14),
            MarketItem("aura_cosmic", R.string.item_aura_cosmic, 200000000000L, 3.0f, CAT_AURA, auraColors = intArrayOf(Color.parseColor("#191970"), Color.parseColor("#4B0082"), Color.parseColor("#8A2BE2"), Color.parseColor("#DA70D6")), descRes = R.string.item_aura_cosmic_desc, minRebirth = 17),
            MarketItem("aura_singularity", R.string.item_aura_singularity, 5000000000000L, 3.5f, CAT_AURA, auraColors = intArrayOf(Color.YELLOW, Color.WHITE, Color.CYAN, Color.WHITE, Color.YELLOW), descRes = R.string.item_aura_singularity_desc, minRebirth = 20)
        ).sortedBy { it.price }

        val bgPalettes = listOf(
            MarketItem("bg_rainbow", R.string.item_bg_rainbow, 0, 1.0f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#FF6B6B"), Color.parseColor("#4ECDC4"), Color.parseColor("#45B7D1")), descRes = R.string.item_bg_rainbow_desc, minRebirth = 0),
            MarketItem("bg_ocean", R.string.item_bg_ocean, 5000, 1.1f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#0077B6"), Color.parseColor("#00B4D8"), Color.parseColor("#90E0EF")), descRes = R.string.item_bg_ocean_desc, minRebirth = 1),
            MarketItem("bg_sunset", R.string.item_bg_sunset, 50000, 1.2f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#FF6B35"), Color.parseColor("#F7931E"), Color.parseColor("#FCCD04")), descRes = R.string.item_bg_sunset_desc, minRebirth = 2),
            MarketItem("bg_forest", R.string.item_bg_forest, 300000, 1.3f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#2D6A4F"), Color.parseColor("#40916C"), Color.parseColor("#95D5B2")), descRes = R.string.item_bg_forest_desc, minRebirth = 4),
            MarketItem("bg_galaxy", R.string.item_bg_galaxy, 3000000, 1.4f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#7400B8"), Color.parseColor("#6930C3"), Color.parseColor("#5390D9")), descRes = R.string.item_bg_galaxy_desc, minRebirth = 6),
            MarketItem("bg_neon", R.string.item_bg_neon, 25000000, 1.5f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#FF00FF"), Color.parseColor("#00FFFF"), Color.parseColor("#39FF14")), descRes = R.string.item_bg_neon_desc, minRebirth = 9),
            MarketItem("bg_elite", R.string.item_bg_elite, 500000000, 1.7f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#C0C0C0"), Color.parseColor("#E5E4E2"), Color.parseColor("#D4AF37")), descRes = R.string.item_bg_elite_desc, minRebirth = 12),
            MarketItem("bg_abyss", R.string.item_bg_abyss, 10000000000L, 1.9f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.parseColor("#0D0D0D"), Color.parseColor("#1A1A2E"), Color.parseColor("#16213E")), descRes = R.string.item_bg_abyss_desc, minRebirth = 16),
            MarketItem("bg_apex", R.string.item_bg_apex, 500000000000L, 2.2f, CAT_BG_PALETTE,
                paletteColors = intArrayOf(Color.WHITE, Color.parseColor("#F0F8FF"), Color.parseColor("#E6E6FA")), descRes = R.string.item_bg_apex_desc, minRebirth = 20)
        ).sortedBy { it.price }

        val sounds = listOf(
            MarketItem("sound_default", R.string.item_sound_default, 0, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_default_desc, minRebirth = 0),
            MarketItem("sound_mechanical", R.string.item_sound_mech, 500, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_mech_desc, minRebirth = 0),
            MarketItem("sound_boing", R.string.item_sound_boing, 2000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_boing_desc, minRebirth = 0),
            MarketItem("sound_laser", R.string.item_sound_laser, 10000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_laser_desc, minRebirth = 0),
            MarketItem("sound_coin", R.string.item_sound_coin, 50000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_coin_desc, minRebirth = 0),
            MarketItem("sound_bubble", R.string.item_sound_bubble, 200000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_bubble_desc, minRebirth = 1),
            MarketItem("sound_sword", R.string.item_sound_sword, 1000000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_sword_desc, minRebirth = 2),
            MarketItem("sound_magic", R.string.item_sound_magic, 5000000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_magic_desc, minRebirth = 4),
            MarketItem("sound_thunder", R.string.item_sound_thunder, 25000000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_thunder_desc, minRebirth = 7),
            MarketItem("sound_custom", R.string.item_sound_custom, 500000000, 1.0f, CAT_SOUNDS, descRes = R.string.item_sound_custom_desc, minRebirth = 5)
        ).sortedBy { it.price }

        val logos = listOf(
            MarketItem("logo_linux", R.string.item_logo_linux, 0, 1.0f, CAT_LOGO, descRes = R.string.item_logo_linux_desc, minRebirth = 0),
            MarketItem("logo_windows", R.string.item_logo_windows, 100000, 1.3f, CAT_LOGO, descRes = R.string.item_logo_windows_desc, minRebirth = 1),
            MarketItem("logo_custom", R.string.item_logo_custom, 10000000, 1.5f, CAT_LOGO, descRes = R.string.item_logo_custom_desc, minRebirth = 3)
        ).sortedBy { it.price }

        val musicItems = listOf(
            MarketItem("music_default", R.string.item_music_default, 0, 1.0f, CAT_MUSIC, descRes = R.string.item_music_default_desc, minRebirth = 0),
            MarketItem("music_epic", R.string.item_music_epic, 500000, 1.2f, CAT_MUSIC, descRes = R.string.item_music_epic_desc, minRebirth = 2),
            MarketItem("music_chill", R.string.item_music_chill, 5000000, 1.4f, CAT_MUSIC, descRes = R.string.item_music_chill_desc, minRebirth = 5),
            MarketItem("music_custom", R.string.item_music_custom, 50000000, 1.5f, CAT_MUSIC, descRes = R.string.item_music_custom_desc, minRebirth = 8)
        ).sortedBy { it.price }

        val allMarketItems: List<MarketItem> = outerColors + innerColors + sounds + auras + logos + musicItems + bgPalettes + effects + strikes

        fun getItemsByCategory(category: String, gm: GameManager): List<MarketItem> {
            val dynamicAssets = gm.getDynamicAssets()

            if (category == CAT_CUSTOMS) {
                return dynamicAssets
            }

            val baseItems = when (category) {
                CAT_SOUNDS -> sounds
                CAT_OUTER -> outerColors
                CAT_INNER -> innerColors
                CAT_AURA -> auras
                CAT_LOGO -> logos
                CAT_MUSIC -> musicItems
                CAT_BG_PALETTE -> bgPalettes
                CAT_EFFECTS -> effects
                CAT_STRIKES -> strikes
                else -> emptyList()
            }

            val filteredDynamic = dynamicAssets.filter { it.category == category }

            val triggerIds = setOf("sound_custom", "logo_custom", "music_custom")
            val (triggers, normalBase) = baseItems.partition { it.id in triggerIds }

            return normalBase + filteredDynamic + triggers
        }
    }
}

