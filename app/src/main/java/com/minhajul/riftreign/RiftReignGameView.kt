package com.minhajul.riftreign

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.MotionEvent
import android.view.KeyEvent
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class RiftReignGameView(
    context: Context,
    private val economy: EconomyStore,
    private val monetization: MonetizationUi,
    private val social: SocialUi
) : View(context), Choreographer.FrameCallback {
    private val world = GameWorld(Random(System.nanoTime()))
    private val prefs = context.getSharedPreferences("rift_reign", Context.MODE_PRIVATE)
    private var bestScore = prefs.getLong("best_score", 0L)
    private var bestWave = prefs.getInt("best_wave", 0)
    private var totalRuns = prefs.getInt("total_runs", 0)
    private var totalKills = prefs.getLong("total_kills", 0L)
    private var totalScore = prefs.getLong("total_score", 0L)

    private var soundEnabled = prefs.getBoolean("sound_enabled", true)
    private var vibrationEnabled = prefs.getBoolean("vibration_enabled", true)
    private var shakeEnabled = prefs.getBoolean("shake_enabled", true)
    private var controlHintsEnabled = prefs.getBoolean("control_hints_enabled", true)
    private var controlScaleIndex = prefs.getInt("control_scale", 1).coerceIn(0, 2)
    private var selectedDifficulty = runCatching {
        Difficulty.valueOf(prefs.getString("difficulty", Difficulty.STANDARD.name) ?: Difficulty.STANDARD.name)
    }.getOrDefault(Difficulty.STANDARD)
    private var tutorialSeen = prefs.getBoolean("tutorial_seen", false)

    private enum class MenuPage { HOME, HANGAR, SOCIAL, HOW_TO, SETTINGS, ABOUT, PRIVACY }
    private enum class HangarTab { SHIPS, WEAPONS, TARGETS, STORE }
    private enum class PausePage { MAIN, SETTINGS }
    private var menuPage = MenuPage.HOME
    private var hangarTab = HangarTab.SHIPS
    private var shopMessage = ""
    private var socialMessage = ""
    private var pendingCloudDelete = false
    private var pendingCosmeticPurchase = ""
    private var lastRunCrystalReward = 0
    private var pausePage = PausePage.MAIN
    private var tutorialTimer = 0f
    private var newBestThisRun = false
    private var running = false
    private var lastFrameNanos = 0L
    private var uiSeconds = 0f

    private var backgroundGradient: LinearGradient? = null
    private var vignetteGradient: RadialGradient? = null

    private var keyLeft = false
    private var keyRight = false
    private var keyUp = false
    private var keyDown = false
    private var keyAimLeft = false
    private var keyAimRight = false
    private var keyAimUp = false
    private var keyAimDown = false
    private var keyFire = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val path = Path()
    private val random = Random(421337)

    private data class Star(val nx: Float, val ny: Float, val size: Float, val layer: Float)
    private val stars = List(130) { Star(random.nextFloat(), random.nextFloat(), 1f + random.nextFloat() * 2.6f, 0.25f + random.nextFloat() * 0.75f) }

    private var leftPointer = -1
    private var rightPointer = -1
    private var leftStartX = 0f
    private var leftStartY = 0f
    private var leftNowX = 0f
    private var leftNowY = 0f
    private var rightStartX = 0f
    private var rightStartY = 0f
    private var rightNowX = 0f
    private var rightNowY = 0f
    private val controlScale get() = when (controlScaleIndex) { 0 -> 0.86f; 2 -> 1.18f; else -> 1.0f }
    private val joystickRadius get() = min(width, height) * 0.105f * controlScale

    private var lastDamageEvent = 0L
    private var lastBossEvent = 0L
    private var lastUpgradeEvent = 0L
    private var lastGameOverEvent = 0L
    private var tone: ToneGenerator? = null

    init {
        setBackgroundColor(Color.rgb(4, 6, 18))
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        keepScreenOn = true
        contentDescription = "RiftReign space survival shooter"
        world.setDifficulty(selectedDifficulty)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val safeW = w.coerceAtLeast(1).toFloat()
        val safeH = h.coerceAtLeast(1).toFloat()
        world.resize(safeW, safeH)
        backgroundGradient = LinearGradient(
            0f, 0f, safeW, safeH,
            intArrayOf(Color.rgb(3, 7, 20), Color.rgb(7, 10, 32), Color.rgb(14, 5, 27)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        vignetteGradient = RadialGradient(
            safeW * 0.5f, safeH * 0.48f, max(safeW, safeH) * 0.70f,
            intArrayOf(Color.TRANSPARENT, Color.argb(205, 0, 0, 8)),
            floatArrayOf(0.50f, 1f), Shader.TileMode.CLAMP
        )
    }

    fun onHostPause() {
        if (world.state == GameState.RUNNING) world.togglePause()
        stopLoop()
    }

    fun onHostResume() {
        requestFocus()
        startLoop()
    }

    fun onHostDestroy() {
        stopLoop()
        try { tone?.release() } catch (_: Exception) { }
        tone = null
    }

    fun onMonetizationChanged() {
        postInvalidateOnAnimation()
    }

    fun onSocialChanged() {
        postInvalidateOnAnimation()
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
        postInvalidateOnAnimation()
    }

    private fun stopLoop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
        lastFrameNanos = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        try {
            if (width > 1 && height > 1) {
                val dt = if (lastFrameNanos == 0L) 0f else
                    ((frameTimeNanos - lastFrameNanos) / 1_000_000_000.0)
                        .toFloat()
                        .coerceIn(0f, 0.033f)
                lastFrameNanos = frameTimeNanos
                uiSeconds += dt
                if (tutorialTimer > 0f) tutorialTimer = max(0f, tutorialTimer - dt)
                world.resize(width.toFloat(), height.toFloat())
                updateKeyboardInput()
                world.update(dt)
                handleFeedbackEvents()
                postInvalidateOnAnimation()
            } else {
                lastFrameNanos = frameTimeNanos
            }
        } catch (t: Throwable) {
            Log.e("RiftReign", "Game update failed; continuing", t)
        } finally {
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun handleFeedbackEvents() {
        if (world.damageEventId != lastDamageEvent) {
            lastDamageEvent = world.damageEventId
            playTone(ToneGenerator.TONE_PROP_NACK, 55)
            vibrate(28)
        }
        if (world.bossEventId != lastBossEvent) {
            lastBossEvent = world.bossEventId
            playTone(ToneGenerator.TONE_SUP_RADIO_ACK, 140)
            vibrate(45)
        }
        if (world.upgradeEventId != lastUpgradeEvent) {
            lastUpgradeEvent = world.upgradeEventId
            playTone(ToneGenerator.TONE_PROP_ACK, 80)
        }
        if (world.gameOverEventId != lastGameOverEvent) {
            lastGameOverEvent = world.gameOverEventId
            playTone(ToneGenerator.TONE_PROP_NACK, 180)
            vibrate(90)
            newBestThisRun = world.score > bestScore
            bestScore = max(bestScore, world.score)
            bestWave = max(bestWave, world.wave)
            totalRuns += 1
            totalKills += world.kills.toLong()
            totalScore += world.score
            lastRunCrystalReward = min(40, 4 + world.wave / 2 + world.kills / 20 + if (selectedDifficulty == Difficulty.OVERDRIVE) 2 else 0)
            economy.grantCrystals(lastRunCrystalReward)
            prefs.edit()
                .putLong("best_score", bestScore)
                .putInt("best_wave", bestWave)
                .putInt("total_runs", totalRuns)
                .putLong("total_kills", totalKills)
                .putLong("total_score", totalScore)
                .apply()
            social.onRunFinished(
                score = world.score,
                wave = world.wave,
                runKills = world.kills,
                totalRuns = totalRuns,
                totalKills = totalKills,
                overdrive = selectedDifficulty == Difficulty.OVERDRIVE,
                shipCount = economy.unlockedShipCount()
            )
            if (social.isConnected()) {
                social.saveCloud(exportCloudProgress()) { msg ->
                    socialMessage = msg
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        if (!soundEnabled) return
        try {
            var generator = tone
            if (generator == null) {
                generator = ToneGenerator(AudioManager.STREAM_MUSIC, 28)
                tone = generator
            }
            generator.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.w("RiftReign", "Tone playback unavailable", e)
        }
    }

    private fun vibrate(ms: Long) {
        if (!vibrationEnabled) return
        try {
            val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(ms, 70))
            else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ms)
            }
        } catch (_: Exception) { }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 1 || height <= 1) return
        try {
            canvas.drawColor(Color.rgb(4, 6, 18))
            val shake = if (shakeEnabled) world.screenShake else 0f
            if (shake > 0f) {
                canvas.save()
                canvas.translate((random.nextFloat() - 0.5f) * shake, (random.nextFloat() - 0.5f) * shake)
            }
            drawBackground(canvas)
            if (world.state != GameState.MENU) {
                drawParticles(canvas)
                drawPowerUps(canvas)
                drawBullets(canvas)
                drawEnemies(canvas)
                drawPlayer(canvas)
            }
            if (shake > 0f) canvas.restore()
            drawHud(canvas)
            drawControls(canvas)
            drawOverlay(canvas)
            if (world.state == GameState.RUNNING && tutorialTimer > 0f) drawTutorialHint(canvas)
        } catch (t: Throwable) {
            Log.e("RiftReign", "Game draw failed", t)
            canvas.drawColor(Color.rgb(4, 6, 18))
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 32f
            textPaint.color = Color.WHITE
            canvas.drawText("RIFTREIGN", width * 0.5f, height * 0.5f, textPaint)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        val cw = canvas.width.coerceAtLeast(1).toFloat()
        val ch = canvas.height.coerceAtLeast(1).toFloat()
        val t = uiSeconds

        paint.style = Paint.Style.FILL
        paint.shader = backgroundGradient
        canvas.drawRect(0f, 0f, cw, ch, paint)
        paint.shader = null

        // Procedural nebula glows: subtle enough to preserve gameplay contrast.
        paint.color = Color.argb(25, 0, 210, 255)
        canvas.drawCircle(cw * 0.23f + sin(t * 0.18f) * cw * 0.035f, ch * 0.28f, min(cw, ch) * 0.36f, paint)
        paint.color = Color.argb(20, 255, 50, 190)
        canvas.drawCircle(cw * 0.78f + cos(t * 0.15f) * cw * 0.03f, ch * 0.72f, min(cw, ch) * 0.34f, paint)

        for (s in stars) {
            val drift = ((t * 8f * s.layer) % ch)
            var y = s.ny * ch + drift
            if (y > ch) y -= ch
            val twinkle = 0.72f + 0.28f * sin(t * (1.2f + s.layer) + s.nx * 13f)
            val alpha = ((80f + 140f * s.layer) * twinkle).toInt().coerceIn(30, 220)
            paint.color = Color.argb(alpha, 145, 205, 255)
            canvas.drawCircle(s.nx * cw, y, s.size, paint)
        }

        strokePaint.strokeWidth = 1.1f
        strokePaint.color = Color.argb(28, 0, 220, 255)
        val gap = max(72f, min(cw, ch) * 0.085f)
        val scroll = ((t * 18f) % gap)
        var x = -gap + scroll
        while (x < cw + gap) { canvas.drawLine(x, 0f, x, ch, strokePaint); x += gap }
        var y = -gap + scroll
        while (y < ch + gap) { canvas.drawLine(0f, y, cw, y, strokePaint); y += gap }

        paint.shader = vignetteGradient
        canvas.drawRect(0f, 0f, cw, ch, paint)
        paint.shader = null
    }

    private fun drawPlayer(canvas: Canvas) {
        val p = world.player
        val angle = Math.toDegrees(atan2(world.aimY.toDouble(), world.aimX.toDouble())).toFloat()
        val blink = if (p.invulnerable > 0f && ((p.invulnerable * 20).toInt() % 2 == 0)) 90 else 255
        drawShipSkin(
            canvas,
            p.x,
            p.y,
            p.radius * 1.08f,
            CosmeticsCatalog.ship(economy.selectedShip),
            angle,
            blink
        )
        if (p.shieldCharges > 0) {
            strokePaint.color = Color.argb(150, 130, 120, 255)
            strokePaint.strokeWidth = 4f
            canvas.drawCircle(p.x, p.y, p.radius * 1.55f + sin(System.nanoTime() / 1e8).toFloat() * 2f, strokePaint)
        }
    }

    private fun drawEnemies(canvas: Canvas) {
        val theme = CosmeticsCatalog.target(economy.selectedTarget)
        for (e in world.enemies) {
            when (e.type) {
                EnemyType.CHASER -> drawEnemyDiamond(canvas, e, theme.chaser)
                EnemyType.SHOOTER -> drawEnemyShooter(canvas, e, theme.shooter, theme.trim)
                EnemyType.TANK -> drawEnemyTank(canvas, e, theme.tank, theme.trim)
                EnemyType.SPLITTER -> drawEnemyDiamond(canvas, e, theme.splitter)
                EnemyType.BOSS -> drawBoss(canvas, e, theme.boss, theme.trim)
            }
            if (e.hp < e.maxHp) drawEnemyHealth(canvas, e)
        }
    }

    private fun drawEnemyDiamond(canvas: Canvas, e: Enemy, color: Int) {
        canvas.save(); canvas.translate(e.x, e.y); canvas.rotate(e.phase * 90f)
        path.reset(); path.moveTo(0f, -e.radius); path.lineTo(e.radius, 0f); path.lineTo(0f, e.radius); path.lineTo(-e.radius, 0f); path.close()
        paint.color = color; paint.style = Paint.Style.FILL; canvas.drawPath(path, paint)
        strokePaint.color = Color.WHITE; strokePaint.alpha = 160; strokePaint.strokeWidth = 2.5f; canvas.drawPath(path, strokePaint); strokePaint.alpha = 255
        canvas.restore()
    }

    private fun drawEnemyShooter(canvas: Canvas, e: Enemy, body: Int, trim: Int) {
        paint.color = body
        paint.style = Paint.Style.FILL
        canvas.drawCircle(e.x, e.y, e.radius, paint)
        strokePaint.color = trim
        strokePaint.strokeWidth = 3f
        canvas.drawCircle(e.x, e.y, e.radius * 0.65f, strokePaint)
        val a = e.phase * 2.2f
        for (i in 0..2) {
            val ang = a + i * 2.094f
            canvas.drawLine(
                e.x + cos(ang) * e.radius * 0.65f,
                e.y + sin(ang) * e.radius * 0.65f,
                e.x + cos(ang) * e.radius * 1.25f,
                e.y + sin(ang) * e.radius * 1.25f,
                strokePaint
            )
        }
    }

    private fun drawEnemyTank(canvas: Canvas, e: Enemy, body: Int, trim: Int) {
        paint.color = body
        paint.style = Paint.Style.FILL
        val rect = RectF(e.x - e.radius, e.y - e.radius, e.x + e.radius, e.y + e.radius)
        canvas.save()
        canvas.rotate(e.phase * 22f, e.x, e.y)
        canvas.drawRoundRect(rect, e.radius * 0.25f, e.radius * 0.25f, paint)
        strokePaint.color = trim
        strokePaint.strokeWidth = 3f
        canvas.drawRoundRect(rect, e.radius * 0.25f, e.radius * 0.25f, strokePaint)
        canvas.restore()
    }

    private fun drawBoss(canvas: Canvas, e: Enemy, body: Int, trim: Int) {
        val pulse = 1f + sin(e.phase * 4f) * 0.06f
        paint.color = body
        paint.style = Paint.Style.FILL
        canvas.drawCircle(e.x, e.y, e.radius * 0.74f * pulse, paint)
        strokePaint.strokeWidth = 5f
        strokePaint.color = trim
        canvas.drawCircle(e.x, e.y, e.radius, strokePaint)
        canvas.save()
        canvas.rotate(e.phase * 48f, e.x, e.y)
        for (i in 0 until 8) {
            val a = (Math.PI * 2.0 * i / 8.0).toFloat()
            canvas.drawLine(
                e.x + cos(a) * e.radius * 0.72f,
                e.y + sin(a) * e.radius * 0.72f,
                e.x + cos(a) * e.radius * 1.18f,
                e.y + sin(a) * e.radius * 1.18f,
                strokePaint
            )
        }
        canvas.restore()
    }

    private fun drawEnemyHealth(canvas: Canvas, e: Enemy) {
        val w = e.radius * 1.7f; val h = 5f
        val x = e.x - w / 2f; val y = e.y - e.radius - 12f
        paint.color = Color.argb(120, 0, 0, 0); canvas.drawRect(x, y, x + w, y + h, paint)
        paint.color = Color.rgb(255, 80, 120); canvas.drawRect(x, y, x + w * (e.hp / e.maxHp).coerceIn(0f, 1f), y + h, paint)
    }

    private fun drawBullets(canvas: Canvas) {
        val weapon = CosmeticsCatalog.weapon(economy.selectedWeapon)
        val enemyTheme = CosmeticsCatalog.target(economy.selectedTarget)
        for (b in world.bullets) {
            val core = if (b.friendly) weapon.core else enemyTheme.shooter
            val glow = if (b.friendly) weapon.glow else Color.argb(60, Color.red(enemyTheme.boss), Color.green(enemyTheme.boss), Color.blue(enemyTheme.boss))
            val trail = if (b.friendly) weapon.trailScale else 1f
            val speed = kotlin.math.hypot(b.vx.toDouble(), b.vy.toDouble()).toFloat().coerceAtLeast(1f)
            val tx = b.x - b.vx / speed * b.radius * 4.5f * trail
            val ty = b.y - b.vy / speed * b.radius * 4.5f * trail
            strokePaint.strokeWidth = b.radius * (0.8f + 0.2f * trail)
            strokePaint.color = glow
            canvas.drawLine(tx, ty, b.x, b.y, strokePaint)
            paint.style = Paint.Style.FILL
            paint.color = glow
            canvas.drawCircle(b.x, b.y, b.radius * (1.9f + 0.35f * trail), paint)
            paint.color = core
            canvas.drawCircle(b.x, b.y, b.radius, paint)
        }
    }

    private fun drawPowerUps(canvas: Canvas) {
        val t = System.nanoTime() / 1_000_000_000.0
        for (p in world.powerUps) {
            val color = when (p.type) {
                PowerType.HEAL -> Color.rgb(70, 255, 130)
                PowerType.RAPID -> Color.rgb(255, 220, 60)
                PowerType.SHIELD -> Color.rgb(100, 120, 255)
                PowerType.NOVA -> Color.rgb(255, 70, 235)
            }
            val pulse = 1f + sin((t * 5.0 + p.x).toFloat()) * 0.12f
            paint.color = Color.argb(65, Color.red(color), Color.green(color), Color.blue(color)); canvas.drawCircle(p.x, p.y, p.radius * 1.65f * pulse, paint)
            paint.color = color; canvas.drawCircle(p.x, p.y, p.radius * pulse, paint)
            textPaint.textAlign = Paint.Align.CENTER; textPaint.textSize = p.radius * 1.2f; textPaint.color = Color.BLACK
            val symbol = when (p.type) { PowerType.HEAL -> "+"; PowerType.RAPID -> ">>"; PowerType.SHIELD -> "S"; PowerType.NOVA -> "N" }
            canvas.drawText(symbol, p.x, p.y + textPaint.textSize * 0.34f, textPaint)
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in world.particles) {
            val alpha = (255f * (p.life / p.maxLife).coerceIn(0f, 1f)).toInt()
            val base = when (p.colorTag) {
                1 -> Triple(255, 60, 120)
                2 -> Triple(185, 70, 255)
                3 -> Triple(255, 90, 45)
                4 -> Triple(60, 230, 255)
                else -> Triple(255, 70, 235)
            }
            paint.color = Color.argb(alpha, base.first, base.second, base.third)
            canvas.drawCircle(p.x, p.y, p.size * (p.life / p.maxLife + 0.25f), paint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        if (world.state == GameState.MENU) return
        val short = min(width, height).toFloat()
        val pad = short * 0.026f
        val hudText = max(19f, short * 0.030f)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = hudText
        textPaint.color = Color.WHITE
        canvas.drawText("SCORE ${compactNumber(world.score)}", pad, pad + hudText, textPaint)

        textPaint.textSize = hudText * 0.70f
        textPaint.color = Color.rgb(145, 215, 255)
        canvas.drawText(
            "WAVE ${world.wave}  LV ${world.level}  ${world.difficulty.label}",
            pad,
            pad + hudText * 1.78f,
            textPaint
        )

        val barW = min(width * 0.30f, short * 0.72f)
        val hpY = pad + hudText * 2.10f
        drawProgressBar(
            canvas,
            pad,
            hpY,
            barW,
            13f,
            (world.player.hp / world.player.maxHp).coerceIn(0f, 1f),
            Color.rgb(255, 62, 110)
        )
        drawProgressBar(
            canvas,
            pad,
            hpY + 20f,
            barW,
            7f,
            (world.xp.toFloat() / world.xpNeeded).coerceIn(0f, 1f),
            Color.rgb(65, 225, 255)
        )

        if (world.player.shieldCharges > 0 || world.player.rapidTimer > 0f) {
            textPaint.textSize = hudText * 0.62f
            textPaint.color = Color.rgb(222, 210, 255)
            val status = buildString {
                if (world.player.shieldCharges > 0) append("SHIELD x${world.player.shieldCharges}   ")
                if (world.player.rapidTimer > 0f) append("RAPID ${world.player.rapidTimer.toInt() + 1}s")
            }
            canvas.drawText(status, pad, hpY + 47f, textPaint)
        }

        // Threat/time chip on the top-right.
        val chipW = short * 0.36f
        val chipH = short * 0.072f
        val chip = RectF(width - pad - chipW, pad, width - pad, pad + chipH)
        paint.color = Color.argb(125, 12, 20, 43)
        canvas.drawRoundRect(chip, chipH * 0.38f, chipH * 0.38f, paint)
        strokePaint.strokeWidth = 1.5f
        strokePaint.color = Color.argb(80, 100, 210, 255)
        canvas.drawRoundRect(chip, chipH * 0.38f, chipH * 0.38f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = hudText * 0.61f
        textPaint.color = Color.rgb(190, 225, 245)
        canvas.drawText(
            "THREATS ${world.threatsRemaining}  |  ${formatTime(world.elapsedTime)}",
            chip.centerX(),
            chip.centerY() + textPaint.textSize * 0.34f,
            textPaint
        )

        // Pause button.
        if (world.state == GameState.RUNNING) {
            val pause = pauseButtonRect()
            paint.color = Color.argb(145, 20, 31, 58)
            canvas.drawRoundRect(pause, pause.height() * 0.30f, pause.height() * 0.30f, paint)
            val cx = pause.centerX()
            val cy = pause.centerY()
            paint.color = Color.WHITE
            val bw = pause.width() * 0.11f
            val bh = pause.height() * 0.26f
            canvas.drawRoundRect(RectF(cx - bw * 1.4f, cy - bh, cx - bw * 0.4f, cy + bh), 3f, 3f, paint)
            canvas.drawRoundRect(RectF(cx + bw * 0.4f, cy - bh, cx + bw * 1.4f, cy + bh), 3f, 3f, paint)
        }

        world.boss?.let { boss ->
            val w = width * 0.42f
            val x = (width - w) * 0.5f
            val y = pad
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = hudText * 0.62f
            textPaint.color = Color.rgb(255, 150, 225)
            canvas.drawText("RIFT OVERSEER", width * 0.5f, y + textPaint.textSize, textPaint)
            drawProgressBar(
                canvas,
                x,
                y + textPaint.textSize + 7f,
                w,
                10f,
                (boss.hp / boss.maxHp).coerceIn(0f, 1f),
                Color.rgb(255, 50, 170)
            )
        }

        if (world.combo > 1 && world.state == GameState.RUNNING) {
            val a = (110 + 145 * (world.comboTimer / 1.65f).coerceIn(0f, 1f)).toInt()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = short * 0.043f
            textPaint.color = Color.argb(a, 255, 95, 220)
            canvas.drawText("x${world.combo} COMBO", width * 0.5f, height * 0.12f, textPaint)
        }

        if (world.waveBannerTimer > 0f) {
            val a = (255f * min(1f, world.waveBannerTimer)).toInt()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = short * 0.064f
            textPaint.color = Color.argb(a, 220, 245, 255)
            val label = if (world.wave % 5 == 0) "BOSS WAVE ${world.wave}" else "WAVE ${world.wave}"
            canvas.drawText(label, width * 0.5f, height * 0.20f, textPaint)
        }
    }

    private fun drawProgressBar(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        progress: Float,
        color: Int
    ) {
        val r = h * 0.5f
        paint.color = Color.argb(145, 10, 14, 31)
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), r, r, paint)
        val fill = w * progress.coerceIn(0f, 1f)
        if (fill > 0.5f) {
            paint.color = color
            canvas.drawRoundRect(RectF(x, y, x + fill, y + h), r, r, paint)
        }
    }

    private fun drawControls(canvas: Canvas) {
        if (world.state != GameState.RUNNING) return
        val short = min(width, height).toFloat()
        if (controlHintsEnabled) {
            if (leftPointer < 0) drawControlGuide(canvas, width * 0.16f, height * 0.78f, "MOVE", Color.rgb(65, 220, 255))
            if (rightPointer < 0) drawControlGuide(canvas, width * 0.84f, height * 0.78f, "AIM + FIRE", Color.rgb(255, 70, 215))
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = short * 0.020f
            textPaint.color = Color.argb(120, 210, 225, 240)
            canvas.drawText("Drag both sides at the same time", width * 0.5f, height - short * 0.040f, textPaint)
        }
        if (leftPointer >= 0) drawJoystick(canvas, leftStartX, leftStartY, leftNowX, leftNowY, Color.rgb(65, 220, 255))
        if (rightPointer >= 0) drawJoystick(canvas, rightStartX, rightStartY, rightNowX, rightNowY, Color.rgb(255, 70, 215))
    }

    private fun drawControlGuide(canvas: Canvas, cx: Float, cy: Float, label: String, color: Int) {
        strokePaint.strokeWidth = 2.5f
        strokePaint.color = Color.argb(42, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, joystickRadius, strokePaint)
        paint.color = Color.argb(26, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(cx, cy, joystickRadius * 0.34f, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = min(width, height) * 0.021f
        textPaint.color = Color.argb(90, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawText(label, cx, cy + joystickRadius + textPaint.textSize * 1.4f, textPaint)
    }

    private fun drawJoystick(canvas: Canvas, sx: Float, sy: Float, nx: Float, ny: Float, color: Int) {
        strokePaint.strokeWidth = 4f
        strokePaint.color = Color.argb(125, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(sx, sy, joystickRadius, strokePaint)
        paint.color = Color.argb(28, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(sx, sy, joystickRadius * 0.82f, paint)
        var dx = nx - sx
        var dy = ny - sy
        val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len > joystickRadius && len > 0f) {
            dx = dx / len * joystickRadius
            dy = dy / len * joystickRadius
        }
        paint.color = Color.argb(175, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawCircle(sx + dx, sy + dy, joystickRadius * 0.33f, paint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.argb(220, 240, 250, 255)
        canvas.drawCircle(sx + dx, sy + dy, joystickRadius * 0.33f, strokePaint)
    }

    private fun drawOverlay(canvas: Canvas) {
        when (world.state) {
            GameState.MENU -> when (menuPage) {
                MenuPage.HOME -> drawMenu(canvas)
                MenuPage.HANGAR -> drawHangar(canvas)
                MenuPage.SOCIAL -> drawSocial(canvas)
                MenuPage.HOW_TO -> drawHowTo(canvas)
                MenuPage.SETTINGS -> drawSettings(canvas, false)
                MenuPage.ABOUT -> drawAbout(canvas)
                MenuPage.PRIVACY -> drawPrivacyPolicy(canvas)
            }
            GameState.UPGRADE -> drawUpgradeOverlay(canvas)
            GameState.PAUSED -> when (pausePage) {
                PausePage.MAIN -> drawPauseOverlay(canvas)
                PausePage.SETTINGS -> drawSettings(canvas, true)
            }
            GameState.GAME_OVER -> drawGameOver(canvas)
            GameState.RUNNING -> Unit
        }
    }

    private fun drawDim(canvas: Canvas, alpha: Int = 190) {
        paint.shader = null
        paint.color = Color.argb(alpha, 2, 4, 14)
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawMenu(canvas: Canvas) {
        drawDim(canvas, 55)
        val short = min(width, height).toFloat()

        val markX = width * 0.22f
        val markY = height * 0.32f
        val markR = short * 0.115f
        strokePaint.strokeWidth = short * 0.008f
        strokePaint.color = Color.argb(90, 55, 225, 255)
        canvas.drawCircle(markX, markY, markR * (1f + sin(uiSeconds * 1.5f) * 0.04f), strokePaint)
        strokePaint.color = Color.argb(75, 255, 70, 220)
        canvas.drawCircle(markX, markY, markR * 0.72f, strokePaint)
        drawShipSkin(canvas, markX, markY, markR * 0.82f, CosmeticsCatalog.ship(economy.selectedShip))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = short * 0.100f
        textPaint.color = Color.rgb(82, 235, 255)
        canvas.drawText("RIFTREIGN", width * 0.08f, height * 0.20f, textPaint)
        textPaint.textSize = short * 0.029f
        textPaint.color = Color.rgb(220, 212, 245)
        canvas.drawText("SURVIVE. EVOLVE. CUSTOMIZE.", width * 0.082f, height * 0.255f, textPaint)
        textPaint.textSize = short * 0.018f
        textPaint.color = Color.argb(150, 180, 205, 230)
        canvas.drawText("v${BuildConfig.VERSION_NAME}  //  OFFLINE-FIRST", width * 0.082f, height * 0.294f, textPaint)

        val stats = RectF(width * 0.075f, height * 0.60f, width * 0.47f, height * 0.84f)
        paint.color = Color.argb(125, 12, 19, 42)
        canvas.drawRoundRect(stats, 24f, 24f, paint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.argb(65, 80, 205, 255)
        canvas.drawRoundRect(stats, 24f, 24f, strokePaint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = short * 0.021f
        textPaint.color = Color.rgb(145, 205, 235)
        canvas.drawText("PILOT RECORD", stats.left + short * 0.030f, stats.top + short * 0.050f, textPaint)
        textPaint.textSize = short * 0.033f
        textPaint.color = Color.WHITE
        canvas.drawText("BEST ${compactNumber(bestScore)}", stats.left + short * 0.030f, stats.top + short * 0.105f, textPaint)
        textPaint.textSize = short * 0.022f
        textPaint.color = Color.rgb(190, 210, 230)
        canvas.drawText("BEST WAVE $bestWave", stats.left + short * 0.030f, stats.top + short * 0.150f, textPaint)
        canvas.drawText("RUNS $totalRuns", stats.left + stats.width() * 0.53f, stats.top + short * 0.105f, textPaint)
        canvas.drawText("KILLS ${compactNumber(totalKills)}", stats.left + stats.width() * 0.53f, stats.top + short * 0.150f, textPaint)

        drawCrystalBalance(canvas)
        drawButton(canvas, menuPlayRect(), "PLAY", true)
        drawButton(canvas, menuHangarRect(), "HANGAR + STORE", false, Color.rgb(255, 188, 55))
        drawButton(canvas, menuSocialRect(), if (social.isOnlineFeaturesAvailable()) "PLAY GAMES + CLOUD" else "ACHIEVEMENTS", false, Color.rgb(115, 225, 155))
        drawButton(canvas, menuHowRect(), "HOW TO PLAY", false)
        drawButton(canvas, menuSettingsRect(), "SETTINGS", false)
        drawButton(canvas, menuAboutRect(), "ABOUT + CONTACT", false, Color.rgb(185, 145, 255))

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.017f
        textPaint.color = Color.rgb(150, 190, 220)
        canvas.drawText("DIFFICULTY", width * 0.72f, height * 0.765f, textPaint)
        difficultyRects().forEachIndexed { i, r ->
            val diff = Difficulty.entries[i]
            drawPill(canvas, r, diff.label, diff == selectedDifficulty)
        }
        textPaint.textSize = short * 0.014f
        textPaint.color = Color.argb(145, 185, 205, 225)
        val diffHint = when (selectedDifficulty) {
            Difficulty.CASUAL -> "Lower pressure // 0.85x score"
            Difficulty.STANDARD -> "Balanced survival // 1.00x score"
            Difficulty.OVERDRIVE -> "Faster, tougher // 1.35x score"
        }
        canvas.drawText(diffHint, width * 0.72f, height * 0.925f, textPaint)
    }

    private fun drawCrystalBalance(canvas: Canvas) {
        val short = min(width, height).toFloat()
        val r = RectF(width * 0.79f, height * 0.075f, width * 0.93f, height * 0.145f)
        paint.color = Color.argb(205, 24, 19, 52)
        canvas.drawRoundRect(r, r.height() * 0.5f, r.height() * 0.5f, paint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.argb(145, 255, 193, 65)
        canvas.drawRoundRect(r, r.height() * 0.5f, r.height() * 0.5f, strokePaint)
        paint.color = Color.rgb(255, 203, 62)
        val d = r.height() * 0.24f
        path.reset()
        path.moveTo(r.left + r.height() * 0.38f, r.centerY() - d)
        path.lineTo(r.left + r.height() * 0.38f + d, r.centerY())
        path.lineTo(r.left + r.height() * 0.38f, r.centerY() + d)
        path.lineTo(r.left + r.height() * 0.38f - d, r.centerY())
        path.close()
        canvas.drawPath(path, paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.WHITE
        canvas.drawText(economy.crystals.toString(), r.centerX() + r.width() * 0.08f, r.centerY() + textPaint.textSize * 0.34f, textPaint)
    }

    private fun drawShipMark(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        drawShipSkin(canvas, cx, cy, size, CosmeticsCatalog.ship("nova"), 0f, 255)
    }

    private fun drawShipSkin(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        skin: ShipSkin,
        rotationDeg: Float = 0f,
        alpha: Int = 255
    ) {
        fun withAlpha(color: Int, a: Int): Int = Color.argb(
            a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
        )
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationDeg)
        paint.style = Paint.Style.FILL
        val engineAlpha = (alpha * 0.72f).toInt()
        paint.color = withAlpha(skin.accent, engineAlpha)
        canvas.drawCircle(-size * 0.72f, 0f, size * 0.22f, paint)

        path.reset()
        when (skin.geometry) {
            1 -> { // Falcon: broad swept wings.
                path.moveTo(size * 1.10f, 0f)
                path.lineTo(-size * 0.28f, -size * 0.25f)
                path.lineTo(-size * 0.96f, -size * 0.82f)
                path.lineTo(-size * 0.60f, 0f)
                path.lineTo(-size * 0.96f, size * 0.82f)
                path.lineTo(-size * 0.28f, size * 0.25f)
            }
            2 -> { // Phantom: low-profile wing.
                path.moveTo(size * 1.02f, 0f)
                path.lineTo(-size * 0.45f, -size * 0.62f)
                path.lineTo(-size * 0.18f, -size * 0.10f)
                path.lineTo(-size * 0.82f, 0f)
                path.lineTo(-size * 0.18f, size * 0.10f)
                path.lineTo(-size * 0.45f, size * 0.62f)
            }
            3 -> { // Viper: narrow spear.
                path.moveTo(size * 1.18f, 0f)
                path.lineTo(-size * 0.35f, -size * 0.33f)
                path.lineTo(-size * 0.92f, -size * 0.56f)
                path.lineTo(-size * 0.52f, 0f)
                path.lineTo(-size * 0.92f, size * 0.56f)
                path.lineTo(-size * 0.35f, size * 0.33f)
            }
            4 -> { // Void Hunter: angular twin-fin craft.
                path.moveTo(size * 1.08f, 0f)
                path.lineTo(size * 0.05f, -size * 0.34f)
                path.lineTo(-size * 0.80f, -size * 0.78f)
                path.lineTo(-size * 0.48f, -size * 0.12f)
                path.lineTo(-size * 0.72f, 0f)
                path.lineTo(-size * 0.48f, size * 0.12f)
                path.lineTo(-size * 0.80f, size * 0.78f)
                path.lineTo(size * 0.05f, size * 0.34f)
            }
            else -> {
                path.moveTo(size * 1.10f, 0f)
                path.lineTo(-size * 0.72f, -size * 0.66f)
                path.lineTo(-size * 0.32f, 0f)
                path.lineTo(-size * 0.72f, size * 0.66f)
            }
        }
        path.close()
        paint.color = withAlpha(skin.primary, alpha)
        canvas.drawPath(path, paint)
        strokePaint.strokeWidth = max(2f, size * 0.10f)
        strokePaint.color = withAlpha(skin.secondary, alpha)
        canvas.drawPath(path, strokePaint)

        path.reset()
        path.moveTo(size * 0.34f, 0f)
        path.lineTo(-size * 0.18f, -size * 0.20f)
        path.lineTo(-size * 0.04f, 0f)
        path.lineTo(-size * 0.18f, size * 0.20f)
        path.close()
        paint.color = withAlpha(skin.accent, alpha)
        canvas.drawPath(path, paint)
        canvas.restore()
    }


    private fun drawSocial(canvas: Canvas) {
        drawDim(canvas, 120)
        val short = min(width, height).toFloat()
        val onlineAvailable = social.isOnlineFeaturesAvailable()

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = short * 0.060f
        textPaint.color = Color.rgb(90, 235, 175)
        canvas.drawText(if (onlineAvailable) "PLAY GAMES" else "ACHIEVEMENTS", width * 0.07f, height * 0.15f, textPaint)
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(190, 215, 230)
        canvas.drawText(
            if (onlineAvailable) "Global competition + achievements + cross-device pilot progress"
            else "Eight milestones are tracked locally and work completely offline.",
            width * 0.07f, height * 0.205f, textPaint
        )

        val status = RectF(width * 0.07f, height * 0.245f, width * 0.45f, height * 0.36f)
        paint.color = Color.argb(185, 12, 22, 42)
        canvas.drawRoundRect(status, 20f, 20f, paint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = if (social.isConnected()) Color.rgb(80, 230, 160) else Color.argb(130, 245, 180, 75)
        canvas.drawRoundRect(status, 20f, 20f, strokePaint)
        textPaint.textSize = short * 0.019f
        textPaint.color = if (social.isConnected()) Color.rgb(110, 245, 180) else Color.rgb(255, 205, 100)
        canvas.drawText(
            when {
                social.isConnected() -> "ONLINE PROFILE CONNECTED"
                onlineAvailable -> "OFFLINE PILOT MODE"
                else -> "LOCAL PILOT MODE"
            },
            status.left + short * 0.025f, status.top + short * 0.040f, textPaint
        )
        textPaint.textSize = short * 0.014f
        textPaint.color = Color.rgb(185, 205, 225)
        canvas.drawText(social.status().take(64), status.left + short * 0.025f, status.top + short * 0.078f, textPaint)

        if (onlineAvailable) {
            drawButton(canvas, socialConnectRect(), if (social.isConnected()) "CONNECTED" else "CONNECT PLAY GAMES", social.isConnected(), Color.rgb(80, 230, 160))
            drawButton(canvas, socialLeaderboardRect(), "GLOBAL LEADERBOARD", false, Color.rgb(90, 205, 255))
            drawButton(canvas, socialAchievementsRect(), "PLAY ACHIEVEMENTS", false, Color.rgb(185, 120, 255))
            drawButton(canvas, socialSaveRect(), "CLOUD SAVE", false, Color.rgb(255, 190, 70))
            drawButton(canvas, socialLoadRect(), "CLOUD LOAD", false, Color.rgb(255, 190, 70))
            drawButton(
                canvas,
                socialDeleteRect(),
                if (pendingCloudDelete) "TAP AGAIN TO DELETE CLOUD" else "DELETE CLOUD SAVE",
                false,
                Color.rgb(255, 95, 125)
            )
        } else {
            val info = RectF(width * 0.07f, height * 0.40f, width * 0.45f, height * 0.73f)
            paint.color = Color.argb(155, 12, 22, 42)
            canvas.drawRoundRect(info, 20f, 20f, paint)
            strokePaint.strokeWidth = 1.5f
            strokePaint.color = Color.argb(90, 80, 210, 165)
            canvas.drawRoundRect(info, 20f, 20f, strokePaint)
            textPaint.textSize = short * 0.021f
            textPaint.color = Color.rgb(115, 235, 175)
            canvas.drawText("OFFLINE-FIRST", info.left + short * 0.025f, info.top + short * 0.055f, textPaint)
            textPaint.textSize = short * 0.015f
            textPaint.color = Color.rgb(190, 210, 228)
            canvas.drawText("Full gameplay and achievements work without internet.", info.left + short * 0.025f, info.top + short * 0.105f, textPaint)
            canvas.drawText("Online leaderboard and cloud sync can be enabled later.", info.left + short * 0.025f, info.top + short * 0.150f, textPaint)
            canvas.drawText("Your local progress stays on this device.", info.left + short * 0.025f, info.top + short * 0.195f, textPaint)
        }

        val achievements = social.localAchievements()
        val unlocked = achievements.count { it.unlocked }
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(225, 235, 245)
        canvas.drawText("LOCAL ACHIEVEMENTS  $unlocked/${achievements.size}", width * 0.52f, height * 0.275f, textPaint)
        val rows = socialAchievementRects()
        achievements.take(rows.size).forEachIndexed { i, a ->
            val r = rows[i]
            paint.color = if (a.unlocked) Color.argb(165, 17, 48, 43) else Color.argb(150, 15, 22, 43)
            canvas.drawRoundRect(r, 15f, 15f, paint)
            strokePaint.strokeWidth = 1.5f
            strokePaint.color = if (a.unlocked) Color.argb(190, 85, 235, 165) else Color.argb(85, 100, 155, 190)
            canvas.drawRoundRect(r, 15f, 15f, strokePaint)
            textPaint.textSize = short * 0.016f
            textPaint.color = if (a.unlocked) Color.rgb(125, 245, 185) else Color.rgb(205, 220, 235)
            canvas.drawText((if (a.unlocked) "✓ " else "○ ") + a.title, r.left + short * 0.018f, r.top + short * 0.035f, textPaint)
            textPaint.textSize = short * 0.012f
            textPaint.color = Color.rgb(155, 180, 205)
            canvas.drawText(a.description, r.left + short * 0.018f, r.top + short * 0.066f, textPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.013f
        textPaint.color = Color.rgb(155, 180, 205)
        val note = if (socialMessage.isNotBlank()) socialMessage
        else if (onlineAvailable) "Offline play always works. Online progress uses Google Play Games when connected."
        else "No internet connection is required for core gameplay."
        canvas.drawText(note.take(92), width * 0.5f, height * 0.835f, textPaint)
        if (onlineAvailable) {
            textPaint.textSize = short * 0.011f
            textPaint.color = Color.argb(150, 180, 195, 215)
            canvas.drawText("Cloud progress excludes paid Rift Crystal balance and Remove Ads entitlement for purchase safety.", width * 0.5f, height * 0.865f, textPaint)
        }
        drawButton(canvas, backRect(), "BACK", false)
    }

    private fun socialConnectRect() = RectF(width * 0.07f, height * 0.40f, width * 0.45f, height * 0.49f)
    private fun socialLeaderboardRect() = RectF(width * 0.07f, height * 0.52f, width * 0.255f, height * 0.61f)
    private fun socialAchievementsRect() = RectF(width * 0.265f, height * 0.52f, width * 0.45f, height * 0.61f)
    private fun socialSaveRect() = RectF(width * 0.07f, height * 0.64f, width * 0.255f, height * 0.73f)
    private fun socialLoadRect() = RectF(width * 0.265f, height * 0.64f, width * 0.45f, height * 0.73f)
    private fun socialDeleteRect() = RectF(width * 0.07f, height * 0.755f, width * 0.45f, height * 0.815f)

    private fun socialAchievementRects(): List<RectF> {
        val left = width * 0.52f
        val right = width * 0.93f
        val top = height * 0.31f
        val bottom = height * 0.75f
        val gapX = width * 0.012f
        val gapY = height * 0.018f
        val w = (right - left - gapX) / 2f
        val h = (bottom - top - gapY * 3f) / 4f
        return List(8) { i ->
            val row = i / 2
            val col = i % 2
            RectF(left + col * (w + gapX), top + row * (h + gapY), left + col * (w + gapX) + w, top + row * (h + gapY) + h)
        }
    }

    private fun handleSocialTouch(x: Float, y: Float) {
        if (backRect().contains(x, y)) {
            menuPage = MenuPage.HOME
            socialMessage = ""
            pendingCloudDelete = false
            return
        }
        if (!social.isOnlineFeaturesAvailable()) return
        when {
            socialConnectRect().contains(x, y) -> {
                pendingCloudDelete = false
                social.signIn()
            }
            socialLeaderboardRect().contains(x, y) -> {
                pendingCloudDelete = false
                social.showLeaderboard()
            }
            socialAchievementsRect().contains(x, y) -> {
                pendingCloudDelete = false
                social.showAchievements()
            }
            socialSaveRect().contains(x, y) -> {
                pendingCloudDelete = false
                socialMessage = "Saving pilot progress..."
                social.saveCloud(exportCloudProgress()) { msg -> socialMessage = msg; postInvalidateOnAnimation() }
            }
            socialLoadRect().contains(x, y) -> {
                pendingCloudDelete = false
                socialMessage = "Loading pilot progress..."
                social.loadCloud { payload, msg ->
                    if (payload != null) applyCloudProgress(payload)
                    socialMessage = msg
                    postInvalidateOnAnimation()
                }
            }
            socialDeleteRect().contains(x, y) -> {
                if (!pendingCloudDelete) {
                    pendingCloudDelete = true
                    socialMessage = "Tap DELETE CLOUD SAVE again to confirm permanent deletion"
                } else {
                    pendingCloudDelete = false
                    socialMessage = "Deleting cloud progress..."
                    social.deleteCloud { msg ->
                        socialMessage = msg
                        postInvalidateOnAnimation()
                    }
                }
            }
        }
    }

    private fun exportCloudProgress(): String = JSONObject().apply {
        put("schema", 1)
        put("bestScore", bestScore)
        put("bestWave", bestWave)
        put("totalRuns", totalRuns)
        put("totalKills", totalKills)
        put("totalScore", totalScore)
        put("difficulty", selectedDifficulty.name)
        put("sound", soundEnabled)
        put("vibration", vibrationEnabled)
        put("shake", shakeEnabled)
        put("hints", controlHintsEnabled)
        put("controlScale", controlScaleIndex)
        put("economy", economy.exportCloudProgress())
    }.toString()

    private fun applyCloudProgress(payload: String) {
        try {
            val json = JSONObject(payload)
            bestScore = max(bestScore, json.optLong("bestScore", 0L))
            bestWave = max(bestWave, json.optInt("bestWave", 0))
            totalRuns = max(totalRuns, json.optInt("totalRuns", 0))
            totalKills = max(totalKills, json.optLong("totalKills", 0L))
            totalScore = max(totalScore, json.optLong("totalScore", 0L))
            runCatching { Difficulty.valueOf(json.optString("difficulty", selectedDifficulty.name)) }
                .getOrNull()?.let { selectedDifficulty = it; world.setDifficulty(it) }
            soundEnabled = json.optBoolean("sound", soundEnabled)
            vibrationEnabled = json.optBoolean("vibration", vibrationEnabled)
            shakeEnabled = json.optBoolean("shake", shakeEnabled)
            controlHintsEnabled = json.optBoolean("hints", controlHintsEnabled)
            controlScaleIndex = json.optInt("controlScale", controlScaleIndex).coerceIn(0, 2)
            json.optJSONObject("economy")?.let(economy::mergeCloudProgress)
            prefs.edit()
                .putLong("best_score", bestScore)
                .putInt("best_wave", bestWave)
                .putInt("total_runs", totalRuns)
                .putLong("total_kills", totalKills)
                .putLong("total_score", totalScore)
                .putString("difficulty", selectedDifficulty.name)
                .putBoolean("sound_enabled", soundEnabled)
                .putBoolean("vibration_enabled", vibrationEnabled)
                .putBoolean("shake_enabled", shakeEnabled)
                .putBoolean("control_hints_enabled", controlHintsEnabled)
                .putInt("control_scale", controlScaleIndex)
                .apply()
        } catch (t: Throwable) {
            Log.w("RiftReign", "Cloud progress parse failed", t)
            socialMessage = "Cloud save was incompatible"
        }
    }

    private fun drawHangar(canvas: Canvas) {
        drawDim(canvas, 135)
        val short = min(width, height).toFloat()
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = short * 0.058f
        textPaint.color = Color.WHITE
        canvas.drawText(if (!monetization.isAdsEnabled() && !monetization.isBillingEnabled()) "HANGAR // RIFT CRYSTALS" else "HANGAR // RIFT STORE", width * 0.065f, height * 0.12f, textPaint)
        textPaint.textSize = short * 0.018f
        textPaint.color = Color.rgb(150, 200, 230)
        canvas.drawText("Cosmetic loadouts only — every pilot keeps the same combat power.", width * 0.067f, height * 0.165f, textPaint)
        drawCrystalBalance(canvas)

        hangarTabRects().forEachIndexed { i, r ->
            val tabLabels = if (!monetization.isAdsEnabled() && !monetization.isBillingEnabled())
                listOf("AIRCRAFT", "WEAPONS", "TARGETS", "CRYSTALS")
            else listOf("AIRCRAFT", "WEAPONS", "TARGETS", "STORE")
            drawPill(canvas, r, tabLabels[i], hangarTab.ordinal == i)
        }

        when (hangarTab) {
            HangarTab.SHIPS -> drawShipShop(canvas)
            HangarTab.WEAPONS -> drawWeaponShop(canvas)
            HangarTab.TARGETS -> drawTargetShop(canvas)
            HangarTab.STORE -> drawRiftStore(canvas)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.016f
        textPaint.color = if (shopMessage.isNotBlank()) Color.rgb(255, 205, 90) else Color.rgb(140, 180, 210)
        val message = if (shopMessage.isNotBlank()) shopMessage else when (hangarTab) {
            HangarTab.STORE -> monetization.status()
            else -> if (!monetization.isAdsEnabled() && !monetization.isBillingEnabled())
                "Earn Rift Crystals through gameplay and unlock new cosmetic loadouts."
            else "Earn Rift Crystals from runs, rewarded ads, or optional Play Store packs."
        }
        canvas.drawText(message.take(92), width * 0.5f, height * 0.835f, textPaint)
        drawButton(canvas, backRect(), "BACK", false)
        if (hangarTab == HangarTab.STORE && monetization.isPrivacyOptionsRequired()) {
            drawButton(canvas, privacyRect(), "PRIVACY OPTIONS", false, Color.rgb(160, 150, 255))
        }
    }

    private fun drawShipShop(canvas: Canvas) {
        val short = min(width, height).toFloat()
        val cards = cosmeticCardRects(CosmeticsCatalog.ships.size)
        CosmeticsCatalog.ships.forEachIndexed { i, skin ->
            val r = cards[i]
            val unlocked = economy.isShipUnlocked(skin.id)
            val selected = economy.selectedShip == skin.id
            drawCosmeticCardFrame(canvas, r, selected)
            drawShipSkin(canvas, r.centerX(), r.top + r.height() * 0.38f, short * 0.050f, skin, -10f + sin(uiSeconds * 1.4f + i) * 4f)
            drawCardText(canvas, r, skin.name, if (selected) "EQUIPPED" else if (unlocked) "TAP TO EQUIP" else "◆ ${skin.cost}", unlocked || selected)
        }
    }

    private fun drawWeaponShop(canvas: Canvas) {
        val short = min(width, height).toFloat()
        val cards = cosmeticCardRects(CosmeticsCatalog.weapons.size)
        CosmeticsCatalog.weapons.forEachIndexed { i, skin ->
            val r = cards[i]
            val unlocked = economy.isWeaponUnlocked(skin.id)
            val selected = economy.selectedWeapon == skin.id
            drawCosmeticCardFrame(canvas, r, selected)
            val cy = r.top + r.height() * 0.37f
            for (j in -1..1) {
                val y = cy + j * short * 0.022f
                strokePaint.strokeWidth = short * 0.007f
                strokePaint.color = skin.glow
                canvas.drawLine(r.centerX() - short * 0.065f, y, r.centerX() + short * 0.050f, y, strokePaint)
                paint.color = skin.core
                canvas.drawCircle(r.centerX() + short * 0.055f, y, short * 0.009f, paint)
            }
            drawCardText(canvas, r, skin.name, if (selected) "EQUIPPED" else if (unlocked) "TAP TO EQUIP" else "◆ ${skin.cost}", unlocked || selected)
        }
    }

    private fun drawTargetShop(canvas: Canvas) {
        val short = min(width, height).toFloat()
        val cards = cosmeticCardRects(CosmeticsCatalog.targets.size)
        CosmeticsCatalog.targets.forEachIndexed { i, theme ->
            val r = cards[i]
            val unlocked = economy.isTargetUnlocked(theme.id)
            val selected = economy.selectedTarget == theme.id
            drawCosmeticCardFrame(canvas, r, selected)
            val cy = r.top + r.height() * 0.36f
            val radius = short * 0.024f
            paint.color = theme.chaser
            path.reset(); path.moveTo(r.centerX() - short * 0.055f, cy - radius); path.lineTo(r.centerX() - short * 0.055f + radius, cy); path.lineTo(r.centerX() - short * 0.055f, cy + radius); path.lineTo(r.centerX() - short * 0.055f - radius, cy); path.close(); canvas.drawPath(path, paint)
            paint.color = theme.shooter
            canvas.drawCircle(r.centerX(), cy, radius, paint)
            paint.color = theme.tank
            canvas.drawRoundRect(RectF(r.centerX() + short * 0.035f, cy - radius, r.centerX() + short * 0.035f + radius * 2f, cy + radius), radius * 0.25f, radius * 0.25f, paint)
            drawCardText(canvas, r, theme.name, if (selected) "EQUIPPED" else if (unlocked) "TAP TO EQUIP" else "◆ ${theme.cost}", unlocked || selected)
        }
    }

    private fun drawRiftStore(canvas: Canvas) {
        val short = min(width, height).toFloat()
        val cards = storeCardRects()
        val billingEnabled = monetization.isBillingEnabled()
        val consumablesEnabled = monetization.isConsumablePurchasesEnabled()
        val adsEnabled = monetization.isAdsEnabled()

        if (!billingEnabled && !adsEnabled) {
            val offlineCards = cards.take(3)
            val titles = listOf("COMPLETE RUNS", "PUSH DEEPER", "OVERDRIVE")
            val details = listOf("Every finished run earns Rift Crystals", "Higher waves and more kills increase rewards", "Overdrive runs earn an extra crystal bonus")
            offlineCards.forEachIndexed { i, r ->
                drawCosmeticCardFrame(canvas, r, i == 0)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = short * 0.028f
                textPaint.color = if (i == 0) Color.rgb(90, 235, 245) else Color.WHITE
                canvas.drawText(titles[i], r.centerX(), r.top + r.height() * 0.38f, textPaint)
                textPaint.textSize = short * 0.015f
                textPaint.color = Color.rgb(165, 195, 220)
                canvas.drawText(details[i], r.centerX(), r.top + r.height() * 0.60f, textPaint)
            }
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = short * 0.016f
            textPaint.color = Color.rgb(115, 235, 175)
            canvas.drawText("OFFLINE ECONOMY // 100% EARNED IN GAME // NO ADS // NO REAL-MONEY PURCHASES", width * 0.5f, height * 0.785f, textPaint)
            return
        }

        val entries = listOf(
            Triple("120 CRYSTALS", StoreProducts.CRYSTALS_120, "Small boost"),
            Triple("650 CRYSTALS", StoreProducts.CRYSTALS_650, "Popular pack"),
            Triple("1500 CRYSTALS", StoreProducts.CRYSTALS_1500, "Best value"),
            Triple("WATCH AD", "rewarded", "+${StoreProducts.REWARDED_CRYSTALS} crystals"),
            Triple("REMOVE ADS", StoreProducts.REMOVE_ADS, "One-time purchase"),
            Triple("RESTORE", "restore", "Restore purchases")
        )
        entries.forEachIndexed { i, entry ->
            val r = cards[i]
            val highlighted = (i == 1 && billingEnabled) || (i == 3 && adsEnabled)
            drawCosmeticCardFrame(canvas, r, highlighted)
            val title = entry.first
            val id = entry.second
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = short * 0.026f
            textPaint.color = when {
                i == 3 && adsEnabled -> Color.rgb(90, 245, 165)
                i == 4 && billingEnabled -> Color.rgb(255, 205, 85)
                else -> Color.WHITE
            }
            canvas.drawText(title, r.centerX(), r.top + r.height() * 0.36f, textPaint)
            textPaint.textSize = short * 0.016f
            textPaint.color = Color.rgb(155, 190, 220)
            canvas.drawText(entry.third, r.centerX(), r.top + r.height() * 0.52f, textPaint)

            val priceText = when (id) {
                "rewarded" -> if (!adsEnabled) "OFFLINE" else if (monetization.isRewardedReady()) "READY" else "LOADING"
                "restore" -> if (billingEnabled) "TAP" else "OFFLINE"
                StoreProducts.REMOVE_ADS -> when {
                    !billingEnabled || !adsEnabled -> "NOT NEEDED"
                    economy.adsRemoved -> "OWNED"
                    else -> monetization.price(id) ?: "PLAY STORE"
                }
                else -> if (billingEnabled && consumablesEnabled) monetization.price(id) ?: "PLAY STORE" else "EARN IN GAME"
            }
            textPaint.textSize = short * 0.019f
            textPaint.color = when (priceText) {
                "OWNED" -> Color.rgb(100, 245, 160)
                "COMING SOON", "OFFLINE", "NOT NEEDED", "EARN IN GAME" -> Color.rgb(160, 180, 205)
                else -> Color.rgb(255, 208, 90)
            }
            canvas.drawText(priceText, r.centerX(), r.top + r.height() * 0.72f, textPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.013f
        textPaint.color = Color.argb(165, 180, 200, 220)
        val note = when {
            billingEnabled && adsEnabled ->
                if (consumablesEnabled)
                    "Rewarded ads are optional. Remove Ads removes occasional post-run interstitials, not rewarded ads."
                else
                    "Launch-safe economy: earn crystals in-game; Google Play Billing is used only for Remove Ads."
            billingEnabled ->
                if (consumablesEnabled)
                    "Purchases use Google Play Billing. This release does not request advertising."
                else
                    "Rift Crystals are earned in-game. Google Play Billing is reserved for permanent entitlements."
            adsEnabled ->
                "Rewarded ads are optional. Paid store products are not enabled in this release."
            else ->
                "Offline release: earn Rift Crystals through gameplay. No ads or real-money purchases are active."
        }
        canvas.drawText(note, width * 0.5f, height * 0.785f, textPaint)
    }

    private fun drawCosmeticCardFrame(canvas: Canvas, r: RectF, selected: Boolean) {
        paint.color = if (selected) Color.argb(220, 18, 42, 62) else Color.argb(190, 11, 18, 40)
        canvas.drawRoundRect(r, 20f, 20f, paint)
        strokePaint.strokeWidth = if (selected) 3f else 1.5f
        strokePaint.color = if (selected) Color.rgb(65, 230, 250) else Color.argb(70, 100, 185, 230)
        canvas.drawRoundRect(r, 20f, 20f, strokePaint)
    }

    private fun drawCardText(canvas: Canvas, r: RectF, title: String, status: String, owned: Boolean) {
        val short = min(width, height).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.WHITE
        canvas.drawText(title, r.centerX(), r.top + r.height() * 0.70f, textPaint)
        textPaint.textSize = short * 0.015f
        textPaint.color = if (owned) Color.rgb(100, 235, 175) else Color.rgb(255, 205, 80)
        canvas.drawText(status, r.centerX(), r.top + r.height() * 0.86f, textPaint)
    }

    private fun drawAbout(canvas: Canvas) {
        drawDim(canvas, 165)
        val short = min(width, height).toFloat()

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.060f
        textPaint.color = Color.WHITE
        canvas.drawText("ABOUT RIFTREIGN", width * 0.5f, height * 0.14f, textPaint)

        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(155, 205, 235)
        canvas.drawText("An offline-first twin-stick space survival shooter for Android.", width * 0.5f, height * 0.195f, textPaint)

        val info = RectF(width * 0.16f, height * 0.245f, width * 0.84f, height * 0.57f)
        paint.color = Color.argb(205, 11, 18, 40)
        canvas.drawRoundRect(info, 24f, 24f, paint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.argb(105, 90, 215, 255)
        canvas.drawRoundRect(info, 24f, 24f, strokePaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(135, 205, 238)
        canvas.drawText("DEVELOPER & OWNER", info.left + short * 0.040f, info.top + short * 0.060f, textPaint)
        textPaint.textSize = short * 0.036f
        textPaint.color = Color.WHITE
        canvas.drawText("Minhajul Abedin", info.left + short * 0.040f, info.top + short * 0.120f, textPaint)
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(195, 215, 235)
        canvas.drawText("Support, feedback, business and advertising enquiries", info.left + short * 0.040f, info.top + short * 0.175f, textPaint)
        textPaint.textSize = short * 0.024f
        textPaint.color = Color.rgb(255, 205, 90)
        canvas.drawText("minhajasif667@gmail.com", info.left + short * 0.040f, info.top + short * 0.225f, textPaint)
        textPaint.textSize = short * 0.016f
        textPaint.color = Color.rgb(155, 185, 210)
        canvas.drawText("Brands can contact the developer for clearly labelled sponsorships or campaigns.", info.left + short * 0.040f, info.top + short * 0.272f, textPaint)
        canvas.drawText("Version ${BuildConfig.VERSION_NAME}  •  Package com.minhajul.riftreign", info.left + short * 0.040f, info.top + short * 0.305f, textPaint)

        drawButton(canvas, aboutBusinessRect(), "ADVERTISING / PARTNERSHIPS", true, Color.rgb(255, 188, 55))
        drawButton(canvas, aboutSupportRect(), "SUPPORT / FEEDBACK", false, Color.rgb(80, 220, 245))
        drawButton(canvas, aboutPrivacyRect(), "PRIVACY POLICY", false, Color.rgb(150, 215, 180))

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.013f
        textPaint.color = Color.argb(155, 180, 205, 225)
        canvas.drawText("Sponsored content never changes combat power and should always be clearly identified.", width * 0.5f, height * 0.855f, textPaint)
        drawButton(canvas, backRect(), "BACK", false)
    }

    private fun drawPrivacyPolicy(canvas: Canvas) {
        drawDim(canvas, 175)
        val short = min(width, height).toFloat()

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.052f
        textPaint.color = Color.WHITE
        canvas.drawText("PRIVACY POLICY", width * 0.5f, height * 0.115f, textPaint)
        textPaint.textSize = short * 0.016f
        textPaint.color = Color.rgb(145, 205, 235)
        canvas.drawText("RiftReign // Developer: Minhajul Abedin // minhajasif667@gmail.com", width * 0.5f, height * 0.160f, textPaint)

        val left = width * 0.10f
        val right = width * 0.90f
        val card = RectF(left, height * 0.205f, right, height * 0.78f)
        paint.color = Color.argb(205, 10, 17, 38)
        canvas.drawRoundRect(card, 22f, 22f, paint)
        strokePaint.strokeWidth = 1.5f
        strokePaint.color = Color.argb(90, 80, 205, 245)
        canvas.drawRoundRect(card, 22f, 22f, strokePaint)

        val lines = mutableListOf(
            "LOCAL DATA  •  Settings, scores, career statistics, earned Rift Crystals and cosmetic unlocks are stored on this device.",
            "RETENTION  •  Local data remains until you clear app storage or uninstall RiftReign.",
            if (monetization.isAdsEnabled())
                "ADVERTISING  •  Google Mobile Ads may process device, advertising and app-interaction data; UMP manages applicable privacy choices."
            else
                "ADVERTISING  •  This build does not request or display advertising.",
            if (monetization.isBillingEnabled())
                if (monetization.isConsumablePurchasesEnabled())
                    "PURCHASES  •  Google Play processes digital purchases. RiftReign does not receive your payment-card number."
                else
                    "PURCHASES  •  Google Play may process permanent entitlements such as Remove Ads. RiftReign does not receive card numbers."
            else
                "PURCHASES  •  Real-money purchases are not enabled in this build.",
            if (social.isOnlineFeaturesAvailable())
                "PLAY GAMES  •  If connected, Google Play Games can provide authentication, achievements, leaderboards and cloud progress."
            else
                "PLAY GAMES  •  Online Play Games services are not enabled in this build; local achievements work offline.",
            "SECURITY  •  Network services use HTTPS/TLS through Google SDKs. RiftReign does not request location, contacts, camera or microphone permissions.",
            "DELETION  •  Clear the app's storage/uninstall to remove local data. Contact the developer for privacy questions about RiftReign.",
            "CONTACT  •  minhajasif667@gmail.com"
        )

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = short * 0.015f
        textPaint.color = Color.rgb(205, 220, 235)
        var y = card.top + short * 0.050f
        val lineGap = short * 0.058f
        lines.forEach { line ->
            canvas.drawText(line.take(128), card.left + short * 0.028f, y, textPaint)
            y += lineGap
        }

        if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) {
            drawButton(canvas, privacyWebRect(), "OPEN PUBLIC PRIVACY POLICY", false, Color.rgb(115, 225, 175))
        }
        drawButton(canvas, backRect(), "BACK", false)
    }

    private fun openPrivacyPolicyUrl() {
        if (BuildConfig.PRIVACY_POLICY_URL.isBlank()) return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
        }
    }

    private fun openEmail(subject: String, body: String) {
        try {
            val uri = Uri.parse("mailto:minhajasif667@gmail.com?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(body))
            context.startActivity(Intent(Intent.ACTION_SENDTO, uri))
        } catch (_: Exception) {
            shopMessage = "Email: minhajasif667@gmail.com"
        }
    }

    private fun drawHowTo(canvas: Canvas) {
        drawDim(canvas, 120)
        val short = min(width, height).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.060f
        textPaint.color = Color.WHITE
        canvas.drawText("FLIGHT MANUAL", width * 0.5f, height * 0.15f, textPaint)
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(155, 205, 235)
        canvas.drawText("Two thumbs. One ship. Every wave gets worse.", width * 0.5f, height * 0.205f, textPaint)

        val cards = howToCards()
        val titles = listOf("MOVE", "AIM + FIRE", "EVOLVE", "POWER CORES")
        val symbols = listOf("L", "R", "+", "P")
        val lines = listOf(
            listOf("Drag anywhere on", "the left half."),
            listOf("Drag the right half.", "Holding fires."),
            listOf("Gain XP and choose", "1 of 3 upgrades."),
            listOf("Collect heal, rapid,", "shield and nova cores.")
        )
        cards.forEachIndexed { i, r ->
            paint.color = Color.argb(180, 13, 22, 48)
            canvas.drawRoundRect(r, 22f, 22f, paint)
            strokePaint.strokeWidth = 2f
            strokePaint.color = if (i % 2 == 0) Color.argb(120, 60, 225, 255) else Color.argb(120, 255, 70, 215)
            canvas.drawRoundRect(r, 22f, 22f, strokePaint)
            paint.color = Color.argb(95, 50, 225, 255)
            canvas.drawCircle(r.centerX(), r.top + r.height() * 0.24f, short * 0.055f, paint)
            textPaint.textSize = short * 0.043f
            textPaint.color = Color.WHITE
            canvas.drawText(symbols[i], r.centerX(), r.top + r.height() * 0.275f, textPaint)
            textPaint.textSize = short * 0.026f
            textPaint.color = Color.WHITE
            canvas.drawText(titles[i], r.centerX(), r.top + r.height() * 0.50f, textPaint)
            textPaint.textSize = short * 0.020f
            textPaint.color = Color.rgb(178, 205, 230)
            canvas.drawText(lines[i][0], r.centerX(), r.top + r.height() * 0.66f, textPaint)
            canvas.drawText(lines[i][1], r.centerX(), r.top + r.height() * 0.76f, textPaint)
        }

        textPaint.textSize = short * 0.019f
        textPaint.color = Color.rgb(170, 200, 225)
        canvas.drawText("Boss every 5 waves  //  Pause button in the top-right  //  Keyboard: WASD + IJKL", width * 0.5f, height * 0.82f, textPaint)
        drawButton(canvas, backRect(), "BACK", false)
    }

    private fun drawSettings(canvas: Canvas, fromPause: Boolean) {
        drawDim(canvas, 180)
        val short = min(width, height).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.060f
        textPaint.color = Color.WHITE
        canvas.drawText("SETTINGS", width * 0.5f, height * 0.15f, textPaint)
        textPaint.textSize = short * 0.019f
        textPaint.color = Color.rgb(155, 205, 235)
        canvas.drawText("Saved automatically on this device", width * 0.5f, height * 0.205f, textPaint)

        val labels = listOf("SOUND FX", "HAPTICS", "SCREEN SHAKE", "CONTROL GUIDES", "CONTROL SIZE")
        val values = listOf(
            if (soundEnabled) "ON" else "OFF",
            if (vibrationEnabled) "ON" else "OFF",
            if (shakeEnabled) "ON" else "OFF",
            if (controlHintsEnabled) "ON" else "OFF",
            when (controlScaleIndex) { 0 -> "SMALL"; 2 -> "LARGE"; else -> "MEDIUM" }
        )
        settingsRows().forEachIndexed { i, r ->
            paint.color = Color.argb(185, 12, 21, 45)
            canvas.drawRoundRect(r, 18f, 18f, paint)
            strokePaint.strokeWidth = 1.5f
            strokePaint.color = Color.argb(55, 100, 200, 255)
            canvas.drawRoundRect(r, 18f, 18f, strokePaint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = short * 0.024f
            textPaint.color = Color.rgb(220, 232, 245)
            canvas.drawText(labels[i], r.left + short * 0.030f, r.centerY() + textPaint.textSize * 0.34f, textPaint)
            val enabled = when (i) {
                0 -> soundEnabled
                1 -> vibrationEnabled
                2 -> shakeEnabled
                3 -> controlHintsEnabled
                else -> true
            }
            val valueRect = RectF(r.right - r.width() * 0.24f, r.top + r.height() * 0.18f, r.right - short * 0.018f, r.bottom - r.height() * 0.18f)
            paint.color = if (i == 4 || enabled) Color.rgb(36, 205, 230) else Color.rgb(45, 55, 78)
            canvas.drawRoundRect(valueRect, valueRect.height() * 0.5f, valueRect.height() * 0.5f, paint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = short * 0.018f
            textPaint.color = if (i == 4 || enabled) Color.rgb(4, 16, 25) else Color.rgb(185, 195, 210)
            canvas.drawText(values[i], valueRect.centerX(), valueRect.centerY() + textPaint.textSize * 0.34f, textPaint)
        }

        drawButton(canvas, backRect(), if (fromPause) "BACK TO PAUSE" else "BACK", false)
    }

    private fun drawUpgradeOverlay(canvas: Canvas) {
        drawDim(canvas, 205)
        val short = min(width, height).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.textSize = short * 0.052f
        canvas.drawText("CORE EVOLUTION", width * 0.5f, height * 0.16f, textPaint)
        textPaint.textSize = short * 0.019f
        textPaint.color = Color.rgb(155, 205, 235)
        canvas.drawText("Choose one upgrade. Time is frozen.", width * 0.5f, height * 0.215f, textPaint)
        val cards = upgradeCardRects()
        world.upgradeChoices.forEachIndexed { i, up ->
            if (i >= cards.size) return@forEachIndexed
            val r = cards[i]
            paint.color = Color.argb(235, 13, 22, 49)
            canvas.drawRoundRect(r, 24f, 24f, paint)
            val accent = when (i) {
                0 -> Color.rgb(55, 225, 255)
                1 -> Color.rgb(185, 90, 255)
                else -> Color.rgb(255, 70, 185)
            }
            strokePaint.strokeWidth = 3f
            strokePaint.color = accent
            canvas.drawRoundRect(r, 24f, 24f, strokePaint)
            paint.color = Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawCircle(r.centerX(), r.top + r.height() * 0.22f, short * 0.055f, paint)
            textPaint.textSize = short * 0.026f
            textPaint.color = Color.WHITE
            canvas.drawText(upgradeSymbol(up.type), r.centerX(), r.top + r.height() * 0.245f, textPaint)
            textPaint.textSize = short * 0.032f
            canvas.drawText(up.title, r.centerX(), r.top + r.height() * 0.48f, textPaint)
            textPaint.textSize = short * 0.021f
            textPaint.color = Color.rgb(190, 215, 240)
            canvas.drawText(up.description, r.centerX(), r.top + r.height() * 0.63f, textPaint)
            textPaint.textSize = short * 0.016f
            textPaint.color = Color.argb(150, 200, 215, 230)
            canvas.drawText("TAP TO INSTALL", r.centerX(), r.bottom - r.height() * 0.12f, textPaint)
        }
    }

    private fun upgradeSymbol(type: UpgradeType): String = when (type) {
        UpgradeType.DAMAGE -> "DMG"
        UpgradeType.FIRE_RATE -> "RATE"
        UpgradeType.MOVE_SPEED -> "SPD"
        UpgradeType.MAX_HP -> "HP+"
        UpgradeType.MULTI_SHOT -> "+1"
        UpgradeType.BULLET_SPEED -> "VEL"
        UpgradeType.PIERCE -> "PIR"
        UpgradeType.SHIELD -> "SHD"
    }

    private fun drawPauseOverlay(canvas: Canvas) {
        drawDim(canvas, 205)
        val short = min(width, height).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.065f
        textPaint.color = Color.WHITE
        canvas.drawText("PAUSED", width * 0.5f, height * 0.27f, textPaint)
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(155, 205, 235)
        canvas.drawText("SCORE ${compactNumber(world.score)}   //   WAVE ${world.wave}   //   ${formatTime(world.elapsedTime)}", width * 0.5f, height * 0.33f, textPaint)
        drawButton(canvas, pauseResumeRect(), "RESUME", true)
        drawButton(canvas, pauseRestartRect(), "RESTART", false)
        drawButton(canvas, pauseSettingsRect(), "SETTINGS", false)
        drawButton(canvas, pauseMenuRect(), "MAIN MENU", false, Color.rgb(255, 85, 135))
    }

    private fun drawGameOver(canvas: Canvas) {
        drawDim(canvas, 215)
        val short = min(width, height).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.070f
        textPaint.color = Color.rgb(255, 72, 126)
        canvas.drawText("RIFT COLLAPSED", width * 0.5f, height * 0.22f, textPaint)
        drawCrystalBalance(canvas)

        if (newBestThisRun) {
            val badge = RectF(width * 0.42f, height * 0.255f, width * 0.58f, height * 0.315f)
            paint.color = Color.rgb(255, 205, 65)
            canvas.drawRoundRect(badge, badge.height() * 0.5f, badge.height() * 0.5f, paint)
            textPaint.textSize = short * 0.018f
            textPaint.color = Color.rgb(30, 20, 5)
            canvas.drawText("NEW BEST", badge.centerX(), badge.centerY() + textPaint.textSize * 0.34f, textPaint)
        }

        val stats = RectF(width * 0.23f, height * 0.36f, width * 0.77f, height * 0.57f)
        paint.color = Color.argb(190, 12, 20, 44)
        canvas.drawRoundRect(stats, 22f, 22f, paint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.argb(65, 255, 80, 165)
        canvas.drawRoundRect(stats, 22f, 22f, strokePaint)
        val columns = listOf(
            "SCORE" to compactNumber(world.score),
            "WAVE" to world.wave.toString(),
            "KILLS" to world.kills.toString(),
            "ACCURACY" to "${world.accuracyPercent}%"
        )
        columns.forEachIndexed { i, pair ->
            val cx = stats.left + stats.width() * (i + 0.5f) / 4f
            textPaint.textSize = short * 0.017f
            textPaint.color = Color.rgb(145, 190, 220)
            canvas.drawText(pair.first, cx, stats.top + stats.height() * 0.38f, textPaint)
            textPaint.textSize = short * 0.032f
            textPaint.color = Color.WHITE
            canvas.drawText(pair.second, cx, stats.top + stats.height() * 0.72f, textPaint)
        }

        textPaint.textSize = short * 0.018f
        textPaint.color = Color.rgb(150, 195, 225)
        canvas.drawText("BEST ${compactNumber(bestScore)}   //   BEST WAVE $bestWave", width * 0.5f, height * 0.63f, textPaint)
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.rgb(255, 205, 80)
        canvas.drawText("RUN REWARD  +$lastRunCrystalReward ◆", width * 0.5f, height * 0.675f, textPaint)
        drawButton(canvas, gameOverRetryRect(), "RETRY", true)
        drawButton(canvas, gameOverMenuRect(), "MAIN MENU", false)
        if (monetization.isAdsEnabled()) {
            drawButton(
                canvas,
                gameOverAdRect(),
                if (monetization.isRewardedReady()) "WATCH AD  +${StoreProducts.REWARDED_CRYSTALS} ◆" else "REWARD AD LOADING",
                false,
                Color.rgb(95, 235, 160)
            )
        }
    }

    private fun drawTutorialHint(canvas: Canvas) {
        val short = min(width, height).toFloat()
        val alpha = if (tutorialTimer < 1f) (tutorialTimer * 190f).toInt() else 190
        val r = RectF(width * 0.29f, height * 0.80f, width * 0.71f, height * 0.91f)
        paint.color = Color.argb(alpha.coerceIn(0, 190), 10, 18, 40)
        canvas.drawRoundRect(r, 18f, 18f, paint)
        strokePaint.strokeWidth = 2f
        strokePaint.color = Color.argb((alpha * 0.55f).toInt(), 75, 225, 255)
        canvas.drawRoundRect(r, 18f, 18f, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = short * 0.020f
        textPaint.color = Color.argb(alpha.coerceIn(0, 220), 225, 238, 250)
        canvas.drawText("LEFT: MOVE    //    RIGHT: AIM + FIRE    //    SURVIVE", r.centerX(), r.centerY() + textPaint.textSize * 0.34f, textPaint)
    }

    private fun drawButton(canvas: Canvas, r: RectF, label: String, primary: Boolean, accent: Int = Color.rgb(50, 220, 242)) {
        val radius = min(r.width(), r.height()) * 0.18f
        if (primary) {
            paint.color = accent
            canvas.drawRoundRect(r, radius, radius, paint)
            textPaint.color = Color.rgb(4, 15, 24)
        } else {
            paint.color = Color.argb(180, 14, 24, 49)
            canvas.drawRoundRect(r, radius, radius, paint)
            strokePaint.strokeWidth = 2f
            strokePaint.color = Color.argb(135, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(r, radius, radius, strokePaint)
            textPaint.color = Color.WHITE
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = min(width, height) * 0.026f
        canvas.drawText(label, r.centerX(), r.centerY() + textPaint.textSize * 0.34f, textPaint)
    }

    private fun drawPill(canvas: Canvas, r: RectF, label: String, selected: Boolean) {
        val radius = r.height() * 0.5f
        paint.color = if (selected) Color.rgb(52, 215, 237) else Color.argb(170, 15, 25, 49)
        canvas.drawRoundRect(r, radius, radius, paint)
        if (!selected) {
            strokePaint.strokeWidth = 1.5f
            strokePaint.color = Color.argb(75, 90, 190, 225)
            canvas.drawRoundRect(r, radius, radius, strokePaint)
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = min(width, height) * 0.016f
        textPaint.color = if (selected) Color.rgb(5, 18, 26) else Color.rgb(200, 220, 235)
        canvas.drawText(label, r.centerX(), r.centerY() + textPaint.textSize * 0.34f, textPaint)
    }

    private fun menuPlayRect() = RectF(width * 0.60f, height * 0.205f, width * 0.84f, height * 0.285f)
    private fun menuHangarRect() = RectF(width * 0.60f, height * 0.305f, width * 0.84f, height * 0.385f)
    private fun menuSocialRect() = RectF(width * 0.60f, height * 0.405f, width * 0.84f, height * 0.485f)
    private fun menuHowRect() = RectF(width * 0.60f, height * 0.505f, width * 0.84f, height * 0.585f)
    private fun menuSettingsRect() = RectF(width * 0.60f, height * 0.595f, width * 0.84f, height * 0.665f)
    private fun menuAboutRect() = RectF(width * 0.60f, height * 0.680f, width * 0.84f, height * 0.745f)

    private fun difficultyRects(): List<RectF> {
        val gap = width * 0.008f
        val totalW = width * 0.36f
        val left = width * 0.54f
        val w = (totalW - gap * 2f) / 3f
        val top = height * 0.800f
        val bottom = height * 0.860f
        return List(3) { i -> RectF(left + i * (w + gap), top, left + i * (w + gap) + w, bottom) }
    }

    private fun backRect() = RectF(width * 0.055f, height * 0.87f, width * 0.19f, height * 0.95f)
    private fun aboutBusinessRect() = RectF(width * 0.18f, height * 0.62f, width * 0.49f, height * 0.71f)
    private fun aboutSupportRect() = RectF(width * 0.51f, height * 0.62f, width * 0.82f, height * 0.71f)
    private fun aboutPrivacyRect() = RectF(width * 0.34f, height * 0.735f, width * 0.66f, height * 0.82f)
    private fun privacyWebRect() = RectF(width * 0.67f, height * 0.87f, width * 0.94f, height * 0.95f)

    private fun privacyRect() = RectF(width * 0.73f, height * 0.87f, width * 0.94f, height * 0.95f)

    private fun hangarTabRects(): List<RectF> {
        val left = width * 0.12f
        val right = width * 0.88f
        val gap = width * 0.012f
        val w = (right - left - gap * 3f) / 4f
        return List(4) { i -> RectF(left + i * (w + gap), height * 0.205f, left + i * (w + gap) + w, height * 0.265f) }
    }

    private fun cosmeticCardRects(count: Int): List<RectF> {
        val columns = 3
        val rows = (count + columns - 1) / columns
        val left = width * 0.08f
        val right = width * 0.92f
        val top = height * 0.305f
        val bottom = height * 0.745f
        val gapX = width * 0.018f
        val gapY = height * 0.025f
        val w = (right - left - gapX * (columns - 1)) / columns
        val h = (bottom - top - gapY * (rows - 1)) / rows.coerceAtLeast(1)
        return List(count) { i ->
            val row = i / columns
            val col = i % columns
            RectF(left + col * (w + gapX), top + row * (h + gapY), left + col * (w + gapX) + w, top + row * (h + gapY) + h)
        }
    }

    private fun storeCardRects(): List<RectF> = cosmeticCardRects(6)

    private fun howToCards(): List<RectF> {
        val left = width * 0.07f
        val totalW = width * 0.86f
        val gap = width * 0.014f
        val w = (totalW - gap * 3f) / 4f
        return List(4) { i -> RectF(left + i * (w + gap), height * 0.28f, left + i * (w + gap) + w, height * 0.72f) }
    }

    private fun settingsRows(): List<RectF> {
        val left = width * 0.24f
        val right = width * 0.76f
        val firstTop = height * 0.265f
        val rowH = height * 0.095f
        val gap = height * 0.023f
        return List(5) { i -> RectF(left, firstTop + i * (rowH + gap), right, firstTop + i * (rowH + gap) + rowH) }
    }

    private fun upgradeCardRects(): List<RectF> {
        val gap = width * 0.025f
        val totalW = width * 0.82f
        val cardW = (totalW - gap * 2f) / 3f
        val left = (width - totalW) / 2f
        val top = height * 0.29f
        val bottom = height * 0.76f
        return List(3) { i -> RectF(left + i * (cardW + gap), top, left + i * (cardW + gap) + cardW, bottom) }
    }

    private fun pauseButtonRect(): RectF {
        val s = min(width, height) * 0.076f
        return RectF(width - s * 1.30f, s * 0.30f, width - s * 0.30f, s * 1.30f)
    }

    private fun pauseResumeRect() = RectF(width * 0.39f, height * 0.40f, width * 0.61f, height * 0.51f)
    private fun pauseRestartRect() = RectF(width * 0.39f, height * 0.54f, width * 0.495f, height * 0.65f)
    private fun pauseSettingsRect() = RectF(width * 0.505f, height * 0.54f, width * 0.61f, height * 0.65f)
    private fun pauseMenuRect() = RectF(width * 0.39f, height * 0.69f, width * 0.61f, height * 0.80f)
    private fun gameOverRetryRect() = RectF(width * 0.36f, height * 0.70f, width * 0.50f, height * 0.82f)
    private fun gameOverMenuRect() = RectF(width * 0.52f, height * 0.70f, width * 0.66f, height * 0.82f)
    private fun gameOverAdRect() = RectF(width * 0.39f, height * 0.855f, width * 0.61f, height * 0.94f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        requestFocus()
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)

        synchronized(world) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    when (world.state) {
                        GameState.MENU -> {
                            when (menuPage) {
                                MenuPage.HOME -> {
                                    when {
                                        menuPlayRect().contains(x, y) -> startGame()
                                        menuHangarRect().contains(x, y) -> { menuPage = MenuPage.HANGAR; hangarTab = HangarTab.SHIPS; shopMessage = ""; playTone(ToneGenerator.TONE_PROP_BEEP, 45) }
                                        menuSocialRect().contains(x, y) -> { menuPage = MenuPage.SOCIAL; socialMessage = ""; playTone(ToneGenerator.TONE_PROP_BEEP, 45) }
                                        menuHowRect().contains(x, y) -> { menuPage = MenuPage.HOW_TO; playTone(ToneGenerator.TONE_PROP_BEEP, 45) }
                                        menuSettingsRect().contains(x, y) -> { menuPage = MenuPage.SETTINGS; playTone(ToneGenerator.TONE_PROP_BEEP, 45) }
                                        menuAboutRect().contains(x, y) -> { menuPage = MenuPage.ABOUT; playTone(ToneGenerator.TONE_PROP_BEEP, 45) }
                                        else -> difficultyRects().forEachIndexed { i, r ->
                                            if (r.contains(x, y)) {
                                                selectedDifficulty = Difficulty.entries[i]
                                                world.setDifficulty(selectedDifficulty)
                                                prefs.edit().putString("difficulty", selectedDifficulty.name).apply()
                                                playTone(ToneGenerator.TONE_PROP_BEEP2, 55)
                                                return true
                                            }
                                        }
                                    }
                                }
                                MenuPage.HANGAR -> handleHangarTouch(x, y)
                                MenuPage.SOCIAL -> handleSocialTouch(x, y)
                                MenuPage.HOW_TO -> if (backRect().contains(x, y)) menuPage = MenuPage.HOME
                                MenuPage.SETTINGS -> handleSettingsTouch(x, y, false)
                                MenuPage.ABOUT -> when {
                                    backRect().contains(x, y) -> menuPage = MenuPage.HOME
                                    aboutBusinessRect().contains(x, y) -> openEmail(
                                        "RiftReign advertising / partnership enquiry",
                                        "Hello Minhajul,\n\nI would like to discuss advertising or a promotional partnership in RiftReign.\n\nCompany / brand:\nCampaign idea:\nBudget / dates:\nContact details:\n"
                                    )
                                    aboutSupportRect().contains(x, y) -> openEmail(
                                        "RiftReign ${BuildConfig.VERSION_NAME} closed-test feedback / support",
                                        "Hello Minhajul,\n\nI am testing RiftReign ${BuildConfig.VERSION_NAME}.\n\nDevice model:\nAndroid version:\nWhat I liked:\nProblem / suggestion:\nSteps to reproduce (if any):\n"
                                    )
                                    aboutPrivacyRect().contains(x, y) -> menuPage = MenuPage.PRIVACY
                                }
                                MenuPage.PRIVACY -> when {
                                    backRect().contains(x, y) -> menuPage = MenuPage.ABOUT
                                    BuildConfig.PRIVACY_POLICY_URL.isNotBlank() && privacyWebRect().contains(x, y) -> openPrivacyPolicyUrl()
                                }
                            }
                            return true
                        }
                        GameState.GAME_OVER -> {
                            when {
                                gameOverRetryRect().contains(x, y) -> startGame()
                                gameOverMenuRect().contains(x, y) -> returnToMenuWithOptionalAd()
                                monetization.isAdsEnabled() && gameOverAdRect().contains(x, y) -> monetization.showRewardedCrystals()
                            }
                            return true
                        }
                        GameState.UPGRADE -> {
                            upgradeCardRects().forEachIndexed { i, r ->
                                if (r.contains(x, y)) {
                                    world.applyUpgrade(i)
                                    playTone(ToneGenerator.TONE_PROP_BEEP2, 80)
                                    vibrate(22)
                                    return true
                                }
                            }
                            return true
                        }
                        GameState.PAUSED -> {
                            if (pausePage == PausePage.SETTINGS) {
                                handleSettingsTouch(x, y, true)
                            } else {
                                when {
                                    pauseResumeRect().contains(x, y) -> world.togglePause()
                                    pauseRestartRect().contains(x, y) -> startGame()
                                    pauseSettingsRect().contains(x, y) -> pausePage = PausePage.SETTINGS
                                    pauseMenuRect().contains(x, y) -> {
                                        world.returnToMenu()
                                        menuPage = MenuPage.HOME
                                        pausePage = PausePage.MAIN
                                    }
                                }
                            }
                            resetPointers()
                            return true
                        }
                        GameState.RUNNING -> {
                            if (pauseButtonRect().contains(x, y)) {
                                world.togglePause()
                                pausePage = PausePage.MAIN
                                resetPointers()
                                playTone(ToneGenerator.TONE_PROP_BEEP, 40)
                                return true
                            }
                            if (x < width * 0.5f && leftPointer < 0) {
                                leftPointer = pointerId
                                leftStartX = x; leftStartY = y; leftNowX = x; leftNowY = y
                                updateMoveInput()
                            } else if (rightPointer < 0) {
                                rightPointer = pointerId
                                rightStartX = x; rightStartY = y; rightNowX = x; rightNowY = y
                                world.shooting = true
                                updateAimInput()
                            }
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.pointerCount) {
                        val id = event.getPointerId(i)
                        if (id == leftPointer) { leftNowX = event.getX(i); leftNowY = event.getY(i) }
                        if (id == rightPointer) { rightNowX = event.getX(i); rightNowY = event.getY(i) }
                    }
                    updateMoveInput()
                    updateAimInput()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pointerId == leftPointer) {
                        leftPointer = -1
                        if (!hasKeyboardMove()) { world.moveX = 0f; world.moveY = 0f }
                    }
                    if (pointerId == rightPointer) {
                        rightPointer = -1
                        if (!hasKeyboardAim() && !keyFire) world.shooting = false
                    }
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) resetPointers()
                    if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleHangarTouch(x: Float, y: Float) {
        if (backRect().contains(x, y)) {
            menuPage = MenuPage.HOME
            shopMessage = ""
            pendingCosmeticPurchase = ""
            playTone(ToneGenerator.TONE_PROP_BEEP, 40)
            return
        }
        if (hangarTab == HangarTab.STORE && monetization.isPrivacyOptionsRequired() && privacyRect().contains(x, y)) {
            monetization.showPrivacyOptions()
            return
        }
        hangarTabRects().forEachIndexed { i, r ->
            if (r.contains(x, y)) {
                hangarTab = HangarTab.entries[i]
                shopMessage = ""
                pendingCosmeticPurchase = ""
                playTone(ToneGenerator.TONE_PROP_BEEP2, 45)
                return
            }
        }
        when (hangarTab) {
            HangarTab.SHIPS -> cosmeticCardRects(CosmeticsCatalog.ships.size).forEachIndexed { i, r ->
                if (r.contains(x, y)) {
                    val skin = CosmeticsCatalog.ships[i]
                    val before = economy.isShipUnlocked(skin.id)
                    val purchaseKey = "ship:${skin.id}"
                    if (!before && pendingCosmeticPurchase != purchaseKey) {
                        pendingCosmeticPurchase = purchaseKey
                        shopMessage = "Tap again to unlock ${skin.name} for ◆ ${skin.cost}"
                        playTone(ToneGenerator.TONE_PROP_BEEP, 45)
                        postInvalidateOnAnimation()
                        return
                    }
                    if (economy.unlockAndSelectShip(skin)) {
                        pendingCosmeticPurchase = ""
                        shopMessage = if (before) "${skin.name} equipped" else "${skin.name} unlocked and equipped"
                        playTone(ToneGenerator.TONE_PROP_ACK, 70)
                        vibrate(18)
                    } else {
                        pendingCosmeticPurchase = ""
                        shopMessage = "Need ${max(0, skin.cost - economy.crystals)} more Rift Crystals"
                        playTone(ToneGenerator.TONE_PROP_NACK, 55)
                    }
                    postInvalidateOnAnimation()
                    return
                }
            }
            HangarTab.WEAPONS -> cosmeticCardRects(CosmeticsCatalog.weapons.size).forEachIndexed { i, r ->
                if (r.contains(x, y)) {
                    val skin = CosmeticsCatalog.weapons[i]
                    val before = economy.isWeaponUnlocked(skin.id)
                    val purchaseKey = "weapon:${skin.id}"
                    if (!before && pendingCosmeticPurchase != purchaseKey) {
                        pendingCosmeticPurchase = purchaseKey
                        shopMessage = "Tap again to unlock ${skin.name} for ◆ ${skin.cost}"
                        playTone(ToneGenerator.TONE_PROP_BEEP, 45)
                        postInvalidateOnAnimation()
                        return
                    }
                    if (economy.unlockAndSelectWeapon(skin)) {
                        pendingCosmeticPurchase = ""
                        shopMessage = if (before) "${skin.name} equipped" else "${skin.name} unlocked and equipped"
                        playTone(ToneGenerator.TONE_PROP_ACK, 70)
                    } else {
                        pendingCosmeticPurchase = ""
                        shopMessage = "Need ${max(0, skin.cost - economy.crystals)} more Rift Crystals"
                        playTone(ToneGenerator.TONE_PROP_NACK, 55)
                    }
                    postInvalidateOnAnimation()
                    return
                }
            }
            HangarTab.TARGETS -> cosmeticCardRects(CosmeticsCatalog.targets.size).forEachIndexed { i, r ->
                if (r.contains(x, y)) {
                    val theme = CosmeticsCatalog.targets[i]
                    val before = economy.isTargetUnlocked(theme.id)
                    val purchaseKey = "target:${theme.id}"
                    if (!before && pendingCosmeticPurchase != purchaseKey) {
                        pendingCosmeticPurchase = purchaseKey
                        shopMessage = "Tap again to unlock ${theme.name} for ◆ ${theme.cost}"
                        playTone(ToneGenerator.TONE_PROP_BEEP, 45)
                        postInvalidateOnAnimation()
                        return
                    }
                    if (economy.unlockAndSelectTarget(theme)) {
                        pendingCosmeticPurchase = ""
                        shopMessage = if (before) "${theme.name} equipped" else "${theme.name} unlocked and equipped"
                        playTone(ToneGenerator.TONE_PROP_ACK, 70)
                    } else {
                        pendingCosmeticPurchase = ""
                        shopMessage = "Need ${max(0, theme.cost - economy.crystals)} more Rift Crystals"
                        playTone(ToneGenerator.TONE_PROP_NACK, 55)
                    }
                    postInvalidateOnAnimation()
                    return
                }
            }
            HangarTab.STORE -> storeCardRects().forEachIndexed { i, r ->
                if (r.contains(x, y)) {
                    shopMessage = ""
                    when (i) {
                        0 -> if (monetization.isBillingEnabled() && monetization.isConsumablePurchasesEnabled()) monetization.buy(StoreProducts.CRYSTALS_120)
                             else shopMessage = "Earn Rift Crystals through gameplay in this release"
                        1 -> if (monetization.isBillingEnabled() && monetization.isConsumablePurchasesEnabled()) monetization.buy(StoreProducts.CRYSTALS_650)
                             else shopMessage = "Earn Rift Crystals through gameplay in this release"
                        2 -> if (monetization.isBillingEnabled() && monetization.isConsumablePurchasesEnabled()) monetization.buy(StoreProducts.CRYSTALS_1500)
                             else shopMessage = "Earn Rift Crystals through gameplay in this release"
                        3 -> if (monetization.isAdsEnabled()) monetization.showRewardedCrystals()
                             else shopMessage = "Rewarded ads are not enabled in this release"
                        4 -> when {
                            !monetization.isBillingEnabled() || !monetization.isAdsEnabled() ->
                                shopMessage = "Forced ads are not active in this release"
                            economy.adsRemoved -> shopMessage = "Ads are already removed"
                            else -> monetization.buy(StoreProducts.REMOVE_ADS)
                        }
                        5 -> if (monetization.isBillingEnabled()) monetization.restorePurchases()
                             else shopMessage = "There are no Play purchases to restore in this release"
                    }
                    postInvalidateOnAnimation()
                    return
                }
            }
        }
    }

    private fun returnToMenuWithOptionalAd() {
        resetPointers()
        monetization.maybeShowInterstitial(totalRuns) {
            post {
                synchronized(world) {
                    world.returnToMenu()
                    menuPage = MenuPage.HOME
                    pausePage = PausePage.MAIN
                    shopMessage = ""
                }
                postInvalidateOnAnimation()
            }
        }
    }

    private fun handleSettingsTouch(x: Float, y: Float, fromPause: Boolean) {
        if (backRect().contains(x, y)) {
            if (fromPause) pausePage = PausePage.MAIN else menuPage = MenuPage.HOME
            playTone(ToneGenerator.TONE_PROP_BEEP, 40)
            return
        }
        settingsRows().forEachIndexed { i, r ->
            if (!r.contains(x, y)) return@forEachIndexed
            when (i) {
                0 -> soundEnabled = !soundEnabled
                1 -> vibrationEnabled = !vibrationEnabled
                2 -> shakeEnabled = !shakeEnabled
                3 -> controlHintsEnabled = !controlHintsEnabled
                4 -> controlScaleIndex = (controlScaleIndex + 1) % 3
            }
            saveSettings()
            if (i != 0 || soundEnabled) playTone(ToneGenerator.TONE_PROP_BEEP2, 55)
            if (i == 1 && vibrationEnabled) vibrate(22)
            return
        }
    }

    private fun saveSettings() {
        prefs.edit()
            .putBoolean("sound_enabled", soundEnabled)
            .putBoolean("vibration_enabled", vibrationEnabled)
            .putBoolean("shake_enabled", shakeEnabled)
            .putBoolean("control_hints_enabled", controlHintsEnabled)
            .putInt("control_scale", controlScaleIndex)
            .putString("difficulty", selectedDifficulty.name)
            .apply()
    }

    private fun startGame() {
        world.setDifficulty(selectedDifficulty)
        world.startNewGame(selectedDifficulty)
        newBestThisRun = false
        menuPage = MenuPage.HOME
        pausePage = PausePage.MAIN
        resetPointers()
        tutorialTimer = if (tutorialSeen) 2.6f else 7.0f
        if (!tutorialSeen) {
            tutorialSeen = true
            prefs.edit().putBoolean("tutorial_seen", true).apply()
        }
        playTone(ToneGenerator.TONE_PROP_ACK, 80)
        vibrate(18)
    }

    private fun updateMoveInput() {
        if (leftPointer < 0) return
        var dx = leftNowX - leftStartX
        var dy = leftNowY - leftStartY
        val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val r = joystickRadius
        if (len > r && len > 0f) { dx = dx / len * r; dy = dy / len * r }
        world.moveX = dx / r
        world.moveY = dy / r
    }

    private fun updateAimInput() {
        if (rightPointer < 0) return
        val dx = rightNowX - rightStartX
        val dy = rightNowY - rightStartY
        val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len > joystickRadius * 0.12f) {
            world.aimX = dx / len
            world.aimY = dy / len
        }
    }

    private fun resetPointers() {
        leftPointer = -1
        rightPointer = -1
        world.moveX = 0f
        world.moveY = 0f
        world.shooting = false
    }

    private fun hasKeyboardMove() = keyLeft || keyRight || keyUp || keyDown
    private fun hasKeyboardAim() = keyAimLeft || keyAimRight || keyAimUp || keyAimDown

    private fun updateKeyboardInput() {
        if (world.state != GameState.RUNNING) return
        if (leftPointer < 0) {
            val mx = (if (keyRight) 1f else 0f) - (if (keyLeft) 1f else 0f)
            val my = (if (keyDown) 1f else 0f) - (if (keyUp) 1f else 0f)
            world.moveX = mx
            world.moveY = my
        }
        if (rightPointer < 0) {
            val ax = (if (keyAimRight) 1f else 0f) - (if (keyAimLeft) 1f else 0f)
            val ay = (if (keyAimDown) 1f else 0f) - (if (keyAimUp) 1f else 0f)
            if (ax != 0f || ay != 0f) {
                val len = kotlin.math.hypot(ax.toDouble(), ay.toDouble()).toFloat().coerceAtLeast(0.001f)
                world.aimX = ax / len
                world.aimY = ay / len
                world.shooting = true
            } else {
                world.shooting = keyFire
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> keyUp = true
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> keyDown = true
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> keyLeft = true
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> keyRight = true
            KeyEvent.KEYCODE_I -> keyAimUp = true
            KeyEvent.KEYCODE_K -> keyAimDown = true
            KeyEvent.KEYCODE_J -> keyAimLeft = true
            KeyEvent.KEYCODE_L -> keyAimRight = true
            KeyEvent.KEYCODE_SPACE -> keyFire = true
            KeyEvent.KEYCODE_ENTER -> {
                if (world.state == GameState.MENU && menuPage == MenuPage.HOME) startGame()
                else if (world.state == GameState.GAME_OVER) startGame()
                return true
            }
            KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_ESCAPE -> {
                if (world.state == GameState.RUNNING || world.state == GameState.PAUSED) {
                    world.togglePause()
                    pausePage = PausePage.MAIN
                    resetPointers()
                    return true
                }
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> keyUp = false
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> keyDown = false
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> keyLeft = false
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> keyRight = false
            KeyEvent.KEYCODE_I -> keyAimUp = false
            KeyEvent.KEYCODE_K -> keyAimDown = false
            KeyEvent.KEYCODE_J -> keyAimLeft = false
            KeyEvent.KEYCODE_L -> keyAimRight = false
            KeyEvent.KEYCODE_SPACE -> keyFire = false
            else -> return super.onKeyUp(keyCode, event)
        }
        return true
    }

    fun handleBackPressed(): Boolean {
        synchronized(world) {
            when (world.state) {
                GameState.MENU -> {
                    if (menuPage != MenuPage.HOME) {
                        menuPage = MenuPage.HOME
                        return true
                    }
                    return false
                }
                GameState.RUNNING -> {
                    world.togglePause()
                    pausePage = PausePage.MAIN
                    resetPointers()
                    return true
                }
                GameState.PAUSED -> {
                    if (pausePage == PausePage.SETTINGS) pausePage = PausePage.MAIN
                    else world.togglePause()
                    return true
                }
                GameState.UPGRADE -> return true
                GameState.GAME_OVER -> {
                    world.returnToMenu()
                    menuPage = MenuPage.HOME
                    return true
                }
            }
        }
    }

    private fun compactNumber(value: Long): String = when {
        value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000f)
        value >= 1_000L -> String.format("%.1fK", value / 1_000f)
        else -> value.toString()
    }

    private fun formatTime(seconds: Float): String {
        val total = max(0, seconds.toInt())
        return "%02d:%02d".format(total / 60, total % 60)
    }

}
