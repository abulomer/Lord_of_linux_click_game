package sene1453studio.lord.of.linux

import android.content.Intent
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import java.io.File
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_AURA
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_BG_PALETTE
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_EFFECTS
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_INNER
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_LOGO
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_MUSIC
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_OUTER
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_SOUNDS
import sene1453studio.lord.of.linux.GameManager.Companion.CAT_STRIKES

class MarketActivity : AppCompatActivity() {

    private lateinit var gameManager: GameManager
    private lateinit var soundManager: SoundManager
    private lateinit var assetManager: AssetManager
    private lateinit var rvMarket: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var spendableText: TextView
    private lateinit var categoryTabs: LinearLayout
    private lateinit var adapter: MarketAdapter

    private var currentCategory = CAT_OUTER
    private var pendingImportCategory: String? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val category = pendingImportCategory ?: return@let
                val imported = assetManager.importAsset(uri, category)
                if (imported != null) {
                    val finalUri = Uri.fromFile(imported).toString()
                    val name = when(category) {
                        CAT_LOGO -> getString(R.string.item_logo_custom)
                        CAT_MUSIC -> getString(R.string.item_music_custom)
                        else -> getString(R.string.item_sound_custom)
                    }
                    val newId = gameManager.addCustomAsset(name, finalUri, category)

                    applyEquip(newId, category)

                    Toast.makeText(this, getString(R.string.msg_asset_imported), Toast.LENGTH_SHORT).show()
                    loadCategory(currentCategory)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_market)

        gameManager = GameManager.getInstance(this)
        soundManager = SoundManager.getInstance(this)
        assetManager = AssetManager.getInstance(this)

        spendableText = findViewById(R.id.spendableText)
        findViewById<TextView>(R.id.clicksLabel).text = getString(R.string.clicks)
        findViewById<TextView>(R.id.titleMarket).text = getString(R.string.title_market)
        categoryTabs = findViewById(R.id.categoryTabs)
        rvMarket = findViewById(R.id.marketRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        setupTabs()

        adapter = MarketAdapter { item -> onItemSelected(item) }
        rvMarket.layoutManager = LinearLayoutManager(this)
        rvMarket.adapter = adapter

        loadCategory(currentCategory)
        updateSpendable()
    }

    private fun setupTabs() {
        val categories = listOf(
            CAT_OUTER to getString(R.string.tab_outer),
            CAT_INNER to getString(R.string.tab_inner),
            CAT_AURA to getString(R.string.tab_aura),
            CAT_SOUNDS to getString(R.string.tab_sounds),
            CAT_MUSIC to getString(R.string.tab_music),
            CAT_STRIKES to getString(R.string.tab_strikes),
            CAT_EFFECTS to getString(R.string.tab_effects),
            CAT_LOGO to getString(R.string.tab_logo),
            CAT_BG_PALETTE to getString(R.string.tab_bg),
            GameManager.CAT_CUSTOMS to getString(R.string.tab_customs)
        )

        val normalTextColor = ContextCompat.getColor(this, R.color.game_text_primary)

        categoryTabs.removeAllViews()
        categories.forEach { (cat, name) ->
            val btn = Button(this).apply {
                text = name
                setBackgroundResource(R.drawable.tab_bg)
                setTextColor(normalTextColor)
                textSize = 10f
                setPadding(16, 0, 16, 0)
                setOnClickListener { loadCategory(cat) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 4, 0) }
            }
            categoryTabs.addView(btn)
        }
    }

    private val allCategories = listOf(CAT_OUTER, CAT_INNER, CAT_AURA, CAT_SOUNDS, CAT_MUSIC, CAT_STRIKES, CAT_EFFECTS, CAT_LOGO, CAT_BG_PALETTE, GameManager.CAT_CUSTOMS)

    private fun loadCategory(category: String) {
        currentCategory = category
        val items = GameManager.getItemsByCategory(category, gameManager)
        adapter.setItems(items)

        if (category == GameManager.CAT_CUSTOMS && items.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            rvMarket.visibility = View.GONE
        } else {
            emptyStateText.visibility = View.GONE
            rvMarket.visibility = View.VISIBLE
        }

        soundManager.releasePreviewOnly()

        val normalTextColor = ContextCompat.getColor(this, R.color.game_text_primary)
        val activeTextColor = ContextCompat.getColor(this, R.color.game_accent_gold)

        for (i in 0 until categoryTabs.childCount) {
            val child = categoryTabs.getChildAt(i) as Button
            child.setTextColor(if (i == allCategories.indexOf(category)) activeTextColor else normalTextColor)
        }
    }

    private fun updateSpendable() {
        spendableText.text = GameManager.formatNumber(gameManager.spendableClicks)
    }

    private fun refreshUI() {
        adapter.notifyDataSetChanged()
        updateSpendable()
    }

    private fun onItemSelected(item: MarketItem) {

        if (item.id == "logo_custom" || item.id == "sound_custom" || item.id == "music_custom") {
            if (!gameManager.isPurchased(item.id)) {
                if (gameManager.purchase(item.id)) {
                    refreshUI()
                    startImport(item.category)
                } else {
                    Toast.makeText(this, getString(R.string.msg_not_enough), Toast.LENGTH_SHORT).show()
                }
            } else {
                startImport(item.category)
            }
            return
        }

        if (!gameManager.isPurchased(item.id)) {
            if (gameManager.rebirthCount < item.minRebirth) {
                Toast.makeText(this, getString(R.string.msg_rebirth_required, item.minRebirth), Toast.LENGTH_SHORT).show()
                return
            }
            if (gameManager.purchase(item.id)) {
                Toast.makeText(this, getString(R.string.msg_purchased, getString(item.nameRes)), Toast.LENGTH_SHORT).show()
                applyEquip(item.id, item.category)
                refreshUI()
            } else {
                Toast.makeText(this, getString(R.string.msg_not_enough), Toast.LENGTH_SHORT).show()
            }
        } else {
            applyEquip(item.id, item.category)
            refreshUI()
        }
    }

    private fun startImport(category: String) {
        pendingImportCategory = category
        val type = if (category == CAT_LOGO) "image/*" else "audio/*"
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = type
        }
        importLauncher.launch(intent)
    }

    private fun applyEquip(itemId: String, category: String) {
        when (category) {
            CAT_OUTER -> gameManager.activeOuterColor = itemId
            CAT_INNER -> gameManager.activeInnerColor = itemId
            CAT_AURA -> gameManager.activeAura = itemId
            CAT_LOGO -> gameManager.activeLogo = itemId
            CAT_BG_PALETTE -> gameManager.activeBackgroundPalette = itemId
            CAT_EFFECTS -> gameManager.activeEffect = itemId
            CAT_STRIKES -> gameManager.activeStrike = itemId
            CAT_SOUNDS -> {
                gameManager.activeClickSound = itemId
                soundManager.loadClickSound(itemId, gameManager)
            }
            CAT_MUSIC -> {
                gameManager.activeMusic = itemId
                soundManager.setupBackgroundMusic(gameManager)
            }
        }
    }

    private fun showEditCustomDialog(item: MarketItem) {
        val isDynamic = item.id.startsWith("dyn_custom_")
        if (!isDynamic) return

        val currentName = item.customName ?: "Asset"
        val editText = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_edit_custom))
            .setView(editText)
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    gameManager.setCustomName(item.id, newName)
                    loadCategory(currentCategory)
                }
            }
            .setNeutralButton(getString(R.string.btn_delete)) { _, _ ->
                val uri = gameManager.getDynamicUri(item.id)
                assetManager.deleteAssetFile(uri)
                gameManager.deleteDynamicAsset(item.id)

                Toast.makeText(this, getString(R.string.msg_custom_cleared), Toast.LENGTH_SHORT).show()
                if (item.category == CAT_MUSIC) soundManager.setupBackgroundMusic(gameManager)
                loadCategory(currentCategory)
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    inner class MarketAdapter(private val onClick: (MarketItem) -> Unit) : RecyclerView.Adapter<MarketAdapter.ViewHolder>() {
        private var items = listOf<MarketItem>()

        fun setItems(newItems: List<MarketItem>) { items = newItems; notifyDataSetChanged() }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_market, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isPurchased = gameManager.isPurchased(item.id)
            val isDynamic = item.id.startsWith("dyn_custom_")
            val isActive = when (item.category) {
                CAT_OUTER -> gameManager.activeOuterColor == item.id
                CAT_INNER -> gameManager.activeInnerColor == item.id
                CAT_AURA -> gameManager.activeAura == item.id
                CAT_LOGO -> gameManager.activeLogo == item.id
                CAT_SOUNDS -> gameManager.activeClickSound == item.id
                CAT_MUSIC -> gameManager.activeMusic == item.id
                CAT_BG_PALETTE -> gameManager.activeBackgroundPalette == item.id
                CAT_EFFECTS -> gameManager.activeEffect == item.id
                CAT_STRIKES -> gameManager.activeStrike == item.id
                else -> false
            }

            val baseName = item.customName ?: gameManager.getCustomName(item.id) ?: getString(item.nameRes)

            if (currentCategory == GameManager.CAT_CUSTOMS) {
                val typeStr = when (item.category) {
                    CAT_LOGO -> getString(R.string.type_logo)
                    CAT_MUSIC -> getString(R.string.type_music)
                    CAT_SOUNDS -> getString(R.string.type_sound)
                    else -> ""
                }
                holder.name.text = getString(R.string.item_name_with_type, baseName, typeStr)
            } else {
                holder.name.text = baseName
            }
            holder.desc.text = if (gameManager.rebirthCount < item.minRebirth) {
                getString(R.string.msg_rebirth_required, item.minRebirth)
            } else {
                if (item.id.contains("custom")) getString(R.string.item_sound_custom_desc) else getString(item.descRes)
            }
            holder.multiplier.text = getString(R.string.multiplier, item.multiplier.toString())
            holder.multiplier.visibility = if (item.multiplier > 1.0f) View.VISIBLE else View.GONE

            holder.itemPreview.setImageDrawable(null)
            holder.itemPreview.setBackgroundResource(R.drawable.tab_bg)

            when (item.category) {
                CAT_OUTER, CAT_INNER -> {
                    if (item.colorValue != 0) holder.itemPreview.background = ColorDrawable(item.colorValue)
                }
                CAT_AURA -> {
                    if (item.auraColors != null && item.auraColors.isNotEmpty()) {
                        val gd = GradientDrawable(GradientDrawable.Orientation.TL_BR, item.auraColors)
                        gd.shape = GradientDrawable.OVAL
                        gd.gradientType = GradientDrawable.RADIAL_GRADIENT
                        gd.gradientRadius = 50f
                        holder.itemPreview.background = gd
                    }
                }
                CAT_LOGO -> {
                    when {
                        item.id == "logo_windows" -> holder.itemPreview.setImageResource(R.drawable.windowslogo)
                        item.id == "logo_linux" -> holder.itemPreview.setImageResource(R.drawable.linuxlogo)
                        item.id == "logo_custom" || isDynamic -> {
                            val customUri = gameManager.getDynamicUri(item.id)
                            if (customUri != null) {
                                try {
                                    val uri = Uri.parse(customUri)
                                    val source = if (uri.scheme == "file") {
                                        ImageDecoder.createSource(File(uri.path!!))
                                    } else {
                                        ImageDecoder.createSource(contentResolver, uri)
                                    }
                                    val drawable = ImageDecoder.decodeDrawable(source)
                                    holder.itemPreview.setImageDrawable(drawable)
                                    if (drawable is AnimatedImageDrawable) {
                                        drawable.start()
                                    }
                                } catch (e: Exception) {
                                    holder.itemPreview.setImageURI(Uri.parse(customUri))
                                }
                            } else {
                                holder.itemPreview.setImageResource(android.R.drawable.ic_menu_gallery)
                            }
                        }
                        else -> holder.itemPreview.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                CAT_SOUNDS -> {
                    holder.itemPreview.setImageResource(R.drawable.ic_waveform)
                    holder.itemPreview.setBackgroundResource(R.drawable.tab_bg)
                }
                CAT_MUSIC -> {
                    holder.itemPreview.setImageResource(R.drawable.ic_music_note)
                    holder.itemPreview.setBackgroundResource(R.drawable.tab_bg)
                }
                CAT_STRIKES -> {
                    holder.itemPreview.setImageResource(android.R.drawable.ic_menu_edit)
                }
                CAT_EFFECTS -> {
                    holder.itemPreview.setImageResource(android.R.drawable.ic_menu_view)
                }
                CAT_BG_PALETTE -> {
                    if (item.paletteColors != null && item.paletteColors.isNotEmpty()) {
                        val gd = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, item.paletteColors)
                        gd.cornerRadius = 8f
                        holder.itemPreview.background = gd
                    }
                }
            }

            when {
                isActive -> {
                    holder.btnAction.text = getString(R.string.btn_owned)
                    holder.btnAction.setBackgroundColor(ContextCompat.getColor(this@MarketActivity, R.color.game_btn_active))
                }
                isPurchased -> {
                    holder.btnAction.text = if (item.id.contains("custom") && !isDynamic) getString(R.string.btn_add) else getString(R.string.btn_equip)
                    holder.btnAction.setBackgroundColor(ContextCompat.getColor(this@MarketActivity, R.color.game_btn_owned))
                }
                else -> {
                    holder.btnAction.text = GameManager.formatNumber(item.price)
                    holder.btnAction.setBackgroundColor(ContextCompat.getColor(this@MarketActivity, R.color.game_btn_buy))
                }
            }

            holder.btnAction.setOnClickListener { onClick(item) }

            if (isDynamic) {
                holder.btnEdit.visibility = View.VISIBLE
                holder.btnEdit.setOnClickListener { showEditCustomDialog(item) }
            } else {
                holder.btnEdit.visibility = View.GONE
            }

            holder.itemView.setOnClickListener {
                if (item.category == CAT_SOUNDS || item.category == CAT_MUSIC) {
                    val customUri = if (isDynamic) gameManager.getDynamicUri(item.id) else null
                    if (item.id.contains("custom") && !isDynamic) {

                    } else {
                        soundManager.previewMarketSound(item.id, customUri)
                    }
                }
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.itemName)
            val desc: TextView = view.findViewById(R.id.itemDesc)
            val multiplier: TextView = view.findViewById(R.id.itemMultiplier)
            val itemPreview: ImageView = view.findViewById(R.id.itemPreview)
            val btnAction: Button = view.findViewById(R.id.btnAction)
            val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        }
    }

    override fun onDestroy() {
        soundManager.releasePreviewOnly()
        super.onDestroy()
    }
}

