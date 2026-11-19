package com.skylegends.game.fx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF

/**
 * Bitmap sprite cache for ships (Kenney "Space Shooter Redux", CC0 — see assets/sprites/CREDITS.txt).
 * Loaded once from assets at GameView construction; entities draw through [drawCentered] so
 * they never touch Context. Missing sprites return null and callers fall back to vector art.
 */
object Sprites {
    private val cache = HashMap<String, Bitmap>()
    private val dst = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private var assets: android.content.res.AssetManager? = null
    private var bgIndex = -1
    private var bgBitmap: Bitmap? = null

    /**
     * The painted backdrop for campaign sector [index] (0-based), decoded on demand. Only one is
     * kept in memory at a time (~4 MB at 1024²) instead of all 16 (~64 MB).
     */
    fun background(index: Int): Bitmap? {
        if (index == bgIndex) return bgBitmap
        val am = assets ?: return null
        val name = "backgrounds/bg_sector_%02d.webp".format(index)
        val bmp = try { am.open(name).use { BitmapFactory.decodeStream(it) } } catch (e: java.io.IOException) { null }
        // No recycle(): the render thread may still be mid-frame on the old bitmap (configure runs
        // on the touch thread). Dropping the reference lets GC reclaim it safely.
        bgBitmap = bmp
        bgIndex = index
        return bmp
    }

    /** Audiowide (headings/numbers) + Rajdhani Bold (captions), both SIL OFL 1.1. Null until [load]. */
    var fontBold: android.graphics.Typeface? = null; private set
    var fontThin: android.graphics.Typeface? = null; private set

    fun load(context: Context) {
        assets = context.assets
        if (fontBold == null) {
            fontBold = try { android.graphics.Typeface.createFromAsset(context.assets, "fonts/Audiowide-Regular.ttf") } catch (e: RuntimeException) { null }
            fontThin = try { android.graphics.Typeface.createFromAsset(context.assets, "fonts/Rajdhani-Bold.ttf") } catch (e: RuntimeException) { null }
        }
        if (cache.isNotEmpty()) return
        val am = context.assets
        val files = am.list("sprites") ?: return
        for (f in files) {
            if (!f.endsWith(".png")) continue
            am.open("sprites/$f").use { s -> BitmapFactory.decodeStream(s)?.let { cache[f.removeSuffix(".png")] = it } }
        }
    }

    fun get(name: String): Bitmap? = cache[name]

    /** Player aircraft id -> sprite. */
    fun forAircraft(id: String): Bitmap? = get(
        when (id) {
            "vanguard" -> "playerShip1_blue"
            "nova" -> "playerShip3_green"
            "titan" -> "playerShip2_blue"
            "raptor" -> "playerShip3_red"
            "vortex" -> "playerShip1_green"
            "colossus" -> "playerShip2_orange"
            else -> "playerShip1_blue"
        }
    )

    /** Enemy spec id -> sprite. */
    fun forEnemy(id: String): Bitmap? = get(
        when (id) {
            "scout" -> "enemyBlue3"
            "weaver" -> "enemyBlue2"
            "dart" -> "enemyRed1"
            "kamikaze" -> "enemyRed4"
            "gunship" -> "enemyBlack1"
            "turret" -> "ufoBlue"
            else -> "enemyBlack1"
        }
    )

    /** Player shots read blue/green, enemy shots red — instant friend/foe at a glance. */
    fun forBullet(fromPlayer: Boolean, glowColor: Int): Bitmap? {
        if (!fromPlayer) return get("laserRed01")
        val greenish = Color.green(glowColor) > Color.red(glowColor) && Color.green(glowColor) > Color.blue(glowColor)
        return get(if (greenish) "laserGreen11" else "laserBlue01")
    }

    /** Power-up kind name -> icon sprite. */
    fun forPowerUp(kindName: String): Bitmap? = get(
        when (kindName) {
            "WEAPON_MG" -> "powerupYellow_bolt"
            "WEAPON_SPREAD" -> "powerupGreen_star"
            "WEAPON_PLASMA" -> "powerupRed_star"
            "SHIELD" -> "powerupBlue_shield"
            else -> "pill_red"
        }
    )

    /**
     * Draws [bmp] centred on the canvas origin, [width] wide with aspect preserved.
     * [flash] in 0..1 tints the sprite's opaque pixels white (hit flash).
     */
    fun drawCentered(canvas: Canvas, bmp: Bitmap, width: Float, flash: Float = 0f) {
        val h = width * bmp.height / bmp.width
        dst.set(-width / 2f, -h / 2f, width / 2f, h / 2f)
        canvas.drawBitmap(bmp, null, dst, paint)
        if (flash > 0f) {
            paint.colorFilter = PorterDuffColorFilter(
                Color.argb((flash * 220f).toInt().coerceIn(0, 255), 255, 255, 255), PorterDuff.Mode.SRC_ATOP
            )
            canvas.drawBitmap(bmp, null, dst, paint)
            paint.colorFilter = null
        }
    }
}
