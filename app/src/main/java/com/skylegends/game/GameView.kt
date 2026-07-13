package com.skylegends.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.skylegends.game.aircraft.AircraftCatalog
import com.skylegends.game.audio.SoundManager
import com.skylegends.game.effects.Background
import com.skylegends.game.effects.ParticleSystem
import com.skylegends.game.enemies.EnemyCatalog
import com.skylegends.game.entities.AbilityContext
import com.skylegends.game.entities.Boss
import com.skylegends.game.entities.BulletSpawner
import com.skylegends.game.entities.Bullet
import com.skylegends.game.entities.Coin
import com.skylegends.game.entities.Enemy
import com.skylegends.game.entities.Player
import com.skylegends.game.entities.PowerUp
import com.skylegends.game.entities.PowerUpKind
import com.skylegends.game.level.LevelDirector
import com.skylegends.game.level.LevelLibrary
import com.skylegends.game.ui.Hud
import com.skylegends.game.ui.UiLayout
import com.skylegends.game.upgrade.UpgradeCatalog
import com.skylegends.game.upgrade.Upgrades
import com.skylegends.game.utils.Constants
import com.skylegends.game.utils.Pool
import com.skylegends.game.utils.SaveManager
import com.skylegends.game.weapons.WeaponCatalog
import kotlin.random.Random

/**
 * The game's beating heart: owns the surface, the fixed-timestep thread, all entity
 * collections, the state machine, input, collision resolution and the render pipeline.
 * Implements [LevelDirector.Context] so the data-driven director can spawn into it.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, LevelDirector.Context {

    private var thread: GameThread? = null
    private val save = SaveManager(context)
    private val sound = SoundManager(context)
    private val upgrades = Upgrades(save)

    // World.
    private val background = Background()
    private val particles = ParticleSystem()
    private val camera = com.skylegends.game.fx.Camera()
    private val player = Player()
    private val hud = Hud()
    private var hangarIndex = 0

    private val bulletPool = Pool(Constants.MAX_BULLETS) { Bullet() }
    private val bullets = ArrayList<Bullet>(Constants.MAX_BULLETS)
    private val coinPool = Pool(256) { Coin() }
    private val coins = ArrayList<Coin>(256)
    private val enemies = ArrayList<Enemy>(64)
    private val powerups = ArrayList<PowerUp>(16)
    private var boss: Boss? = null

    private var director = LevelDirector(LevelLibrary.level(1))
    private var state = GameState.MENU

    // Session stats.
    private var score = 0
    private var coinCount = 0
    private var comboCount = 0
    private var comboTimer = 0f
    private var multiplier = 1f
    private var menuTime = 0f
    private var bossWarning = 0f
    private var victoryTimer = -1f
    private var defeatTimer = -1f
    private var bossDeathHandled = false
    private var playerDeathHandled = false
    private var newBest = false
    private var currentSectorIndex = 0
    private var resultSubtitle = ""
    private var retryLabel = "RETRY"
    private var heartbeatTimer = 0f

    // Screen transform (set in surfaceChanged).
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    // Bullet spawners: one stamps player ownership, the other enemy ownership.
    private val playerSpawner = BulletSpawner { x, y, vx, vy, r, dmg, core, glow ->
        spawnBullet(x, y, vx, vy, r, dmg, true, core, glow)
    }
    private val enemySpawner = BulletSpawner { x, y, vx, vy, r, dmg, core, glow ->
        spawnBullet(x, y, vx, vy, r, dmg, false, core, glow)
    }
    private val missileSpawner = BulletSpawner { x, y, vx, vy, r, dmg, core, glow ->
        spawnBullet(x, y, vx, vy, r, dmg, true, core, glow, isMissile = true)
    }

    // Siege Burst reaches outside the Player — damages everything currently on screen.
    private val abilityContext = AbilityContext { damage, ox, oy ->
        for (e in enemies) if (e.active) { e.hit(damage); if (e.hp <= 0f) killEnemy(e) }
        boss?.let { b -> if (b.active && !b.invulnerable) b.hit(damage) }
        particles.explosion(ox, oy, 3f, Color.rgb(200, 160, 255))
        camera.addTrauma(0.8f); camera.addFlash(0.6f, 200, 160, 255)
        sound.explosion(big = true)
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        sound.musicEnabled = save.musicOn
        sound.sfxEnabled = save.sfxOn
        syncMenuBackground()
        syncMenuShip()
    }

    /** Menu shows the *next* sector's theme, so progression reads even before a run starts. */
    private fun syncMenuBackground() {
        background.configure(save.campaignProgress.coerceIn(0, LevelLibrary.levels.size - 1))
    }

    /** Puts the currently-selected aircraft on display for the menu's hangar-bay showcase —
     * top shmups (Sky Force Reloaded and peers) foreground the ship on the title screen
     * rather than reusing the gameplay view verbatim. Re-synced whenever selection could
     * have changed (Hangar) so it's never stale. */
    private fun syncMenuShip() {
        val spec = AircraftCatalog.byId(save.selectedAircraft)
        player.configure(spec, spec.maxHp, spec.maxShield, 1f, 1f)
        player.spawnAtStart()
        player.firing = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sound.release()
    }

    // ---------------- Surface lifecycle ----------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = GameThread(holder, this).also { it.startThread() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        scale = minOf(width / Constants.GAME_WIDTH, height / Constants.GAME_HEIGHT)
        offsetX = (width - Constants.GAME_WIDTH * scale) / 2f
        offsetY = (height - Constants.GAME_HEIGHT * scale) / 2f
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.stopThread(); thread = null
    }

    fun resume() {
        if (thread == null && holder.surface.isValid) {
            thread = GameThread(holder, this).also { it.startThread() }
        }
        sound.startMusic()
    }

    fun pause() {
        if (state == GameState.PLAYING) state = GameState.PAUSED
        thread?.stopThread(); thread = null
        sound.pauseMusic()
    }

    // ---------------- Game flow ----------------

    private fun startGame() {
        score = 0; coinCount = 0; comboCount = 0; comboTimer = 0f; multiplier = 1f
        victoryTimer = -1f; defeatTimer = -1f; bossDeathHandled = false; playerDeathHandled = false
        newBest = false; heartbeatTimer = 0f
        bullets.forEach { bulletPool.release(it) }; bullets.clear()
        coins.forEach { coinPool.release(it) }; coins.clear()
        enemies.clear(); powerups.clear()
        boss = null
        particles.clear()
        camera.reset()
        // Apply selected aircraft + permanent upgrade bonuses for this run.
        val spec = AircraftCatalog.byId(save.selectedAircraft)
        player.configure(
            spec,
            maxHp = spec.maxHp + upgrades.armorBonus,
            maxShield = spec.maxShield + upgrades.shieldBonus,
            damageMult = upgrades.damageMult,
            fireRateMult = upgrades.fireRateMult
        )
        player.spawnAtStart()
        currentSectorIndex = save.campaignProgress.coerceIn(0, LevelLibrary.levels.size - 1)
        background.configure(currentSectorIndex)
        director = LevelDirector(LevelLibrary.level(currentSectorIndex + 1))
        bossWarning = 0f
        state = GameState.PLAYING
        sound.startMusic()
    }

    private fun winGame() {
        state = GameState.VICTORY
        finishRun()
        val isFinalSector = currentSectorIndex >= LevelLibrary.levels.size - 1
        if (!isFinalSector) {
            save.campaignProgress = maxOf(save.campaignProgress, currentSectorIndex + 1)
            resultSubtitle = "SECTOR ${currentSectorIndex + 1} CLEARED"
            retryLabel = "NEXT SECTOR"
        } else {
            resultSubtitle = "CAMPAIGN COMPLETE!"
            retryLabel = "PLAY AGAIN"
        }
        sound.victory()
    }

    private fun loseGame() {
        state = GameState.DEFEAT
        finishRun()
        resultSubtitle = LevelLibrary.level(currentSectorIndex + 1).name
        retryLabel = "RETRY"
        sound.defeat()
    }

    private fun finishRun() {
        save.addCoins(Math.round(coinCount * upgrades.coinMult))
        if (score > save.bestScore) { save.bestScore = score; newBest = true }
    }

    // ---------------- Update ----------------

    fun update(dt: Float) {
        camera.update(dt)
        // The menu is a calm "hangar bay" showroom, not the same fast-scrolling gameplay
        // scene — everything drifts at a fraction of gameplay speed here.
        background.update(if (state == GameState.MENU) dt * 0.3f else dt)

        // Adaptive music: layers crossfade toward the current situation every frame.
        val combatTarget = if (state == GameState.PLAYING && enemies.isNotEmpty()) 1f else 0f
        val bossTarget = if (state == GameState.PLAYING && boss?.active == true) 1f else 0f
        sound.updateMusicMix(dt, combatTarget, bossTarget)

        if (state == GameState.MENU) { menuTime += dt; player.update(dt); return }
        if (state != GameState.PLAYING) { particles.update(dt); return }

        if (bossWarning > 0f) bossWarning -= dt

        // Hitstop freezes gameplay but not FX for that punchy impact beat.
        val frozen = camera.consumeHitstop(dt)
        particles.update(dt)
        if (frozen) return

        // Player.
        if (player.alive) {
            player.update(dt)
            if (player.tryFire(playerSpawner)) {
                particles.muzzleFlash(player.pos.x, player.pos.y - Constants.PLAYER_HEIGHT / 2f, player.weapon.muzzleColor)
                sound.shoot()
            }
            if (player.tryFireWingCannon(playerSpawner)) {
                val wing = player.spec.wingWeapon!!
                val wingY = player.pos.y - Constants.PLAYER_HEIGHT * 0.15f
                for (port in wing.portsFor(1)) particles.muzzleFlash(player.pos.x + port.offsetX, wingY, wing.muzzleColor)
            }
            if (player.tryFireMissile(missileSpawner)) {
                sound.missile()
                val missile = player.spec.missileWeapon!!
                val noseY = player.pos.y + Constants.PLAYER_HEIGHT * 0.1f
                for (port in missile.portsFor(1)) particles.muzzleFlash(player.pos.x + port.offsetX, noseY, missile.muzzleColor)
            }
            // Low-health heartbeat cue — faster as hull nears zero.
            val hpFrac = player.hp / player.maxHp
            if (hpFrac < 0.3f) {
                heartbeatTimer -= dt
                if (heartbeatTimer <= 0f) {
                    sound.heartbeat()
                    val severity = (1f - hpFrac / 0.3f).coerceIn(0f, 1f)
                    heartbeatTimer = 0.9f - 0.55f * severity
                }
            } else heartbeatTimer = 0f
        }

        // Director drives spawns / boss.
        director.update(dt, this)

        // Enemies.
        var i = 0
        while (i < enemies.size) {
            val e = enemies[i]
            e.tick(dt, player.pos.x, player.pos.y, enemySpawner)
            if (!e.active) enemies.removeAt(i) else i++
        }

        // Boss.
        boss?.let { b ->
            b.tick(dt, player.pos.x, player.pos.y, enemySpawner)
            if (b.justChangedPhase) {
                b.justChangedPhase = false
                camera.addTrauma(0.6f); camera.addFlash(0.5f, 255, 120, 60)
                sound.explosion(big = true)
            }
        }

        // Pickups.
        i = 0
        while (i < coins.size) {
            val c = coins[i]
            c.tickToward(dt, player.pos.x, player.pos.y)
            if (!c.active) { coinPool.release(c); coins.removeAt(i) } else i++
        }
        i = 0
        while (i < powerups.size) {
            val p = powerups[i]; p.update(dt)
            if (!p.active) powerups.removeAt(i) else i++
        }

        // Bullets — integrate motion (also self-deactivate when off-screen) before collisions.
        for (b in bullets) if (b.active) {
            b.update(dt)
            if (b.isMissile && Random.nextFloat() < 0.6f) particles.smokeTrail(b.pos.x, b.pos.y)
        }

        resolveCollisions()

        // Combo decay.
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) { comboCount = 0; multiplier = 1f }
        }

        // Cull spent bullets.
        i = 0
        while (i < bullets.size) {
            val b = bullets[i]
            if (!b.active) { bulletPool.release(b); bullets.removeAt(i) } else i++
        }

        // Win/lose sequencing (with a short cinematic beat).
        boss?.let { b ->
            if (!b.active && !bossDeathHandled) {
                bossDeathHandled = true
                repeat(5) {
                    particles.explosion(
                        b.pos.x + (Random.nextFloat() - 0.5f) * 160f,
                        b.pos.y + (Random.nextFloat() - 0.5f) * 120f,
                        2.6f
                    )
                }
                camera.addTrauma(1f); camera.addFlash(0.8f); camera.addHitstop(0.12f)
                sound.explosion(big = true)
                coinCount += 50
                victoryTimer = 1.7f
            }
        }
        if (victoryTimer > 0f) { victoryTimer -= dt; if (victoryTimer <= 0f) winGame() }

        if (!player.alive && !playerDeathHandled) {
            playerDeathHandled = true
            particles.explosion(player.pos.x, player.pos.y, 2.2f, Color.rgb(120, 200, 255))
            camera.addTrauma(0.9f); camera.addFlash(0.7f); camera.addHitstop(0.1f)
            defeatTimer = 1.4f
        }
        if (defeatTimer > 0f) { defeatTimer -= dt; if (defeatTimer <= 0f) loseGame() }
    }

    private fun resolveCollisions() {
        for (b in bullets) {
            if (!b.active) continue
            if (b.fromPlayer) {
                var consumed = false
                for (e in enemies) {
                    if (e.active && b.overlaps(e)) {
                        e.hit(b.damage)
                        particles.hitSparks(b.pos.x, b.pos.y, b.glowColor)
                        b.active = false
                        if (e.hp <= 0f) killEnemy(e) else sound.hit()
                        consumed = true
                        break
                    }
                }
                if (!consumed) {
                    boss?.let { bs ->
                        if (bs.active && !bs.invulnerable && b.overlaps(bs)) {
                            bs.hit(b.damage)
                            particles.hitSparks(b.pos.x, b.pos.y, b.glowColor)
                            b.active = false
                        }
                    }
                }
            } else {
                if (player.alive && !player.invulnerable && b.overlaps(player)) {
                    if (player.takeDamage(b.damage)) {
                        particles.hitSparks(player.pos.x, player.pos.y, Color.rgb(120, 200, 255))
                        camera.addTrauma(0.3f); camera.addFlash(0.25f, 255, 80, 80)
                        sound.hurt()
                    }
                    b.active = false
                }
            }
        }

        // Enemy contact (ram) damage.
        if (player.alive && !player.invulnerable) {
            for (e in enemies) {
                if (e.active && e.overlaps(player)) {
                    player.takeDamage(e.spec.contactDamage)
                    camera.addTrauma(0.35f); camera.addFlash(0.3f, 255, 80, 80)
                    sound.hurt()
                    killEnemy(e)
                    break
                }
            }
            boss?.let { bs ->
                if (bs.active && !bs.invulnerable && bs.overlaps(player)) {
                    player.takeDamage(24f)
                    camera.addTrauma(0.4f)
                }
            }
        }

        // Pickups.
        for (c in coins) {
            if (c.active && c.overlaps(player)) { coinCount += c.value; c.active = false; sound.coin() }
        }
        for (p in powerups) {
            if (p.active && p.overlaps(player)) { applyPowerUp(p.kind); p.active = false; camera.addFlash(0.2f, 120, 220, 255); sound.powerup() }
        }
    }

    private fun killEnemy(e: Enemy) {
        val scale = (e.spec.width / 48f).coerceIn(0.8f, 2.2f)
        particles.explosion(e.pos.x, e.pos.y, scale, e.spec.bodyColor)
        camera.addTrauma(0.18f * scale)
        if (scale > 1.4f) camera.addHitstop(0.04f)
        sound.explosion(big = scale > 1.4f)
        score += (e.spec.score * multiplier).toInt()
        registerCombo()
        repeat(e.spec.coinValue) {
            val c = coinPool.obtain() ?: return@repeat
            c.configure(
                e.pos.x + (Random.nextFloat() - 0.5f) * 30f,
                e.pos.y + (Random.nextFloat() - 0.5f) * 30f,
                1, magnetRange()
            )
            coins.add(c)
        }
        maybeDropPowerUp(e)
        e.active = false
    }

    private fun magnetRange() = upgrades.magnetRange

    private fun registerCombo() {
        comboCount++
        comboTimer = Constants.COMBO_WINDOW
        multiplier = (1f + comboCount * 0.1f).coerceAtMost(5f)
    }

    private fun maybeDropPowerUp(e: Enemy) {
        val chance = if (e.spec.id == "gunship" || e.spec.id == "turret") 0.7f else 0.09f
        if (Random.nextFloat() > chance) return
        // Bias toward what the player needs.
        val kind = when {
            player.hp < player.maxHp * 0.4f && Random.nextFloat() < 0.5f -> PowerUpKind.HEALTH
            player.shield <= 0f && Random.nextFloat() < 0.35f -> PowerUpKind.SHIELD
            else -> listOf(
                PowerUpKind.WEAPON_MG, PowerUpKind.WEAPON_SPREAD, PowerUpKind.WEAPON_PLASMA,
                PowerUpKind.SHIELD, PowerUpKind.HEALTH
            ).random()
        }
        val p = PowerUp(kind); p.spawn(e.pos.x, e.pos.y)
        powerups.add(p)
    }

    private fun applyPowerUp(kind: PowerUpKind) {
        when (kind) {
            PowerUpKind.WEAPON_MG -> player.equip(WeaponCatalog.MACHINE_GUN)
            PowerUpKind.WEAPON_SPREAD -> player.equip(WeaponCatalog.SPREAD)
            PowerUpKind.WEAPON_PLASMA -> player.equip(WeaponCatalog.PLASMA)
            PowerUpKind.SHIELD -> player.addShield(player.maxShield)
            PowerUpKind.HEALTH -> player.heal(30f)
        }
    }

    private fun spawnBullet(
        x: Float, y: Float, vx: Float, vy: Float, r: Float, dmg: Float, fromPlayer: Boolean,
        core: Int, glow: Int, isMissile: Boolean = false
    ) {
        val b = bulletPool.obtain() ?: return
        b.configure(x, y, vx, vy, r, dmg, fromPlayer, core, glow, isMissile)
        bullets.add(b)
    }

    // ---------------- LevelDirector.Context ----------------

    override fun spawnEnemy(specId: String, x: Float, y: Float) {
        val e = Enemy(EnemyCatalog.byId(specId)); e.spawn(x, y); enemies.add(e)
    }

    override fun aliveEnemyCount(): Int = enemies.size

    override fun spawnBoss(spec: com.skylegends.game.bosses.BossSpec) {
        boss = Boss(spec).also { it.spawn() }
        bossWarning = 1.8f
        sound.boss()
    }

    override fun bossActive(): Boolean = boss?.active == true

    // ---------------- Render ----------------

    fun render(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, Constants.GAME_WIDTH, Constants.GAME_HEIGHT)

        // World (under screen-shake).
        canvas.save()
        canvas.translate(camera.offsetX, camera.offsetY)
        background.render(canvas)
        for (c in coins) c.render(canvas)
        for (p in powerups) p.render(canvas)
        for (e in enemies) e.render(canvas)
        boss?.render(canvas)
        if (state == GameState.MENU) {
            // Large hero showcase of the selected ship — the menu is a hangar bay, not a
            // second copy of the gameplay view.
            canvas.save()
            canvas.scale(1.7f, 1.7f, player.pos.x, player.pos.y)
            player.render(canvas)
            canvas.restore()
        } else if (state == GameState.PLAYING || state == GameState.PAUSED || victoryTimer > 0f || defeatTimer > 0f) {
            if (player.alive) player.render(canvas)
        }
        for (b in bullets) b.render(canvas)
        particles.render(canvas)
        canvas.restore()

        // HUD + overlays (not shaken).
        when (state) {
            GameState.MENU -> {
                val nextSector = save.campaignProgress.coerceIn(0, LevelLibrary.levels.size - 1)
                hud.drawMenu(
                    canvas, save.bestScore, save.coins, save.musicOn, save.sfxOn,
                    nextSector + 1, LevelLibrary.levels.size, LevelLibrary.level(nextSector + 1).name, menuTime
                )
            }
            GameState.HANGAR -> {
                val spec = AircraftCatalog.all[hangarIndex]
                hud.drawHangar(
                    canvas, spec, save.isUnlocked(spec.id), save.selectedAircraft == spec.id,
                    save.coins, hangarIndex, AircraftCatalog.all.size
                )
            }
            GameState.UPGRADES -> {
                val rows = UpgradeCatalog.all.map { def ->
                    val cost = upgrades.costFor(def)
                    Hud.UpgradeRow(
                        def.displayName, def.symbol, upgrades.level(def), def.maxLevel,
                        if (cost < 0) 0 else cost, upgrades.isMaxed(def), cost in 0..save.coins
                    )
                }
                hud.drawUpgrades(canvas, rows, save.coins)
            }
            GameState.PLAYING -> drawInGameHud(canvas)
            GameState.PAUSED -> { drawInGameHud(canvas); hud.drawPause(canvas) }
            GameState.VICTORY -> hud.drawResult(canvas, true, score, coinCount, save.bestScore, newBest, resultSubtitle, retryLabel)
            GameState.DEFEAT -> hud.drawResult(canvas, false, score, coinCount, save.bestScore, newBest, resultSubtitle, retryLabel)
        }

        if (camera.flash > 0f) hud.drawFlash(canvas, camera.flash, camera.flashR, camera.flashG, camera.flashB)
        canvas.restore()
    }

    private fun drawInGameHud(canvas: Canvas) {
        // Low-health vignette.
        val lowThresh = player.maxHp * 0.35f
        if (player.hp in 0.01f..lowThresh) {
            val intensity = 1f - player.hp / lowThresh
            hud.drawLowHealthVignette(canvas, intensity * (0.6f + 0.4f * kotlin.math.sin(System.nanoTime() / 1.2e8).toFloat()))
        }
        hud.drawPlaying(
            canvas, score, coinCount, player.hp, player.maxHp, player.shield, player.maxShield,
            player.weapon.displayName, player.weaponLevel, multiplier,
            player.spec.abilitySymbol, player.abilityCooldownFraction, player.abilityReady, player.overdriveActive
        )
        boss?.let { b -> if (b.active) hud.drawBossBar(canvas, b.displayName, b.hp / b.maxHp) }
        if (bossWarning > 0f) hud.drawBossWarning(canvas, 1f - bossWarning / 1.8f)
    }

    // ---------------- Input ----------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val vx = (event.x - offsetX) / scale
        val vy = (event.y - offsetY) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onDown(vx, vy)
            MotionEvent.ACTION_MOVE -> if (state == GameState.PLAYING) player.setTarget(vx, vy)
        }
        return true
    }

    private fun onDown(vx: Float, vy: Float) {
        when (state) {
            GameState.MENU -> onMenuDown(vx, vy)
            GameState.HANGAR -> onHangarDown(vx, vy)
            GameState.UPGRADES -> onUpgradesDown(vx, vy)
            GameState.PLAYING -> {
                if (UiLayout.contains(UiLayout.pauseButton, vx, vy)) state = GameState.PAUSED
                else if (UiLayout.contains(UiLayout.abilityButton, vx, vy)) {
                    if (player.tryActivateAbility(abilityContext)) sound.ability() else sound.hurt()
                } else player.setTarget(vx, vy)
            }
            GameState.PAUSED -> {
                if (UiLayout.contains(UiLayout.resumeButton, vx, vy)) state = GameState.PLAYING
                else if (UiLayout.contains(UiLayout.restartButton, vx, vy)) startGame()
            }
            GameState.VICTORY, GameState.DEFEAT -> {
                if (UiLayout.contains(UiLayout.retryButton, vx, vy)) { sound.click(); startGame() }
                else if (UiLayout.contains(UiLayout.menuButton, vx, vy)) {
                    sound.click(); state = GameState.MENU; menuTime = 0f; syncMenuBackground(); syncMenuShip()
                }
            }
        }
    }

    private fun onMenuDown(vx: Float, vy: Float) {
        when {
            UiLayout.contains(UiLayout.playButton, vx, vy) -> { sound.click(); startGame() }
            UiLayout.contains(UiLayout.hangarButton, vx, vy) -> {
                sound.click()
                hangarIndex = AircraftCatalog.all.indexOfFirst { it.id == save.selectedAircraft }.coerceAtLeast(0)
                state = GameState.HANGAR
            }
            UiLayout.contains(UiLayout.upgradeButton, vx, vy) -> { sound.click(); state = GameState.UPGRADES }
            UiLayout.contains(UiLayout.musicToggle, vx, vy) -> {
                save.musicOn = !save.musicOn; sound.enableMusic(save.musicOn); sound.click()
            }
            UiLayout.contains(UiLayout.sfxToggle, vx, vy) -> {
                save.sfxOn = !save.sfxOn; sound.enableSfx(save.sfxOn); sound.click()
            }
        }
    }

    private fun onHangarDown(vx: Float, vy: Float) {
        val n = AircraftCatalog.all.size
        val spec = AircraftCatalog.all[hangarIndex]
        when {
            UiLayout.contains(UiLayout.backButton, vx, vy) -> { sound.click(); state = GameState.MENU; syncMenuShip() }
            UiLayout.contains(UiLayout.hangarPrev, vx, vy) -> { sound.click(); hangarIndex = (hangarIndex - 1 + n) % n }
            UiLayout.contains(UiLayout.hangarNext, vx, vy) -> { sound.click(); hangarIndex = (hangarIndex + 1) % n }
            UiLayout.contains(UiLayout.hangarAction, vx, vy) -> {
                when {
                    save.selectedAircraft == spec.id -> { /* already selected */ }
                    save.isUnlocked(spec.id) -> { save.selectedAircraft = spec.id; sound.click() }
                    save.spend(spec.cost) -> { save.unlock(spec.id); save.selectedAircraft = spec.id; sound.powerup() }
                    else -> sound.hurt() // can't afford
                }
            }
        }
    }

    private fun onUpgradesDown(vx: Float, vy: Float) {
        if (UiLayout.contains(UiLayout.backButton, vx, vy)) { sound.click(); state = GameState.MENU; return }
        UpgradeCatalog.all.forEachIndexed { i, def ->
            if (UiLayout.contains(UiLayout.upgradeBuy(i), vx, vy)) {
                if (upgrades.buy(def)) sound.powerup() else sound.hurt()
            }
        }
    }
}
