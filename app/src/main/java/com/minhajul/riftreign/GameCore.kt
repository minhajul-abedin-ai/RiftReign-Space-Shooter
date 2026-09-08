package com.minhajul.riftreign

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float) { x = nx; y = ny }
    fun length(): Float = hypot(x.toDouble(), y.toDouble()).toFloat()
    fun normalize(): Vec2 {
        val len = length()
        if (len > 0.0001f) { x /= len; y /= len }
        return this
    }
}

enum class GameState { MENU, RUNNING, UPGRADE, PAUSED, GAME_OVER }
enum class Difficulty(
    val label: String,
    val hpScale: Float,
    val speedScale: Float,
    val damageScale: Float,
    val spawnScale: Float,
    val scoreMultiplier: Float
) {
    CASUAL("CASUAL", 0.82f, 0.90f, 0.82f, 0.88f, 0.85f),
    STANDARD("STANDARD", 1.00f, 1.00f, 1.00f, 1.00f, 1.00f),
    OVERDRIVE("OVERDRIVE", 1.22f, 1.10f, 1.18f, 1.16f, 1.35f)
}
enum class EnemyType { CHASER, SHOOTER, TANK, SPLITTER, BOSS }
enum class PowerType { HEAL, RAPID, SHIELD, NOVA }
enum class UpgradeType { DAMAGE, FIRE_RATE, MOVE_SPEED, MAX_HP, MULTI_SHOT, BULLET_SPEED, PIERCE, SHIELD }

data class UpgradeChoice(val type: UpgradeType, val title: String, val description: String)

data class Player(
    var x: Float = 0f,
    var y: Float = 0f,
    var radius: Float = 24f,
    var hp: Float = 100f,
    var maxHp: Float = 100f,
    var speed: Float = 430f,
    var fireInterval: Float = 0.17f,
    var bulletDamage: Float = 20f,
    var bulletSpeed: Float = 940f,
    var multiShot: Int = 1,
    var pierce: Int = 0,
    var shieldCharges: Int = 0,
    var rapidTimer: Float = 0f,
    var fireCooldown: Float = 0f,
    var invulnerable: Float = 0f
)

data class Bullet(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var damage: Float,
    var friendly: Boolean,
    var life: Float,
    var pierceLeft: Int = 0,
    val hitEnemyIds: MutableSet<Long> = mutableSetOf()
)

data class Enemy(
    val id: Long,
    var type: EnemyType,
    var x: Float,
    var y: Float,
    var radius: Float,
    var hp: Float,
    var maxHp: Float,
    var speed: Float,
    var contactDamage: Float,
    var shootCooldown: Float = 0f,
    var phase: Float = 0f
)

data class PowerUp(
    var type: PowerType,
    var x: Float,
    var y: Float,
    var radius: Float = 18f,
    var life: Float = 10f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    var maxLife: Float,
    var size: Float,
    var colorTag: Int
)

class GameWorld(private val random: Random = Random.Default) {
    var width: Float = 1920f
        private set
    var height: Float = 1080f
        private set

    var state: GameState = GameState.MENU
        private set
    val player = Player()
    val bullets = mutableListOf<Bullet>()
    val enemies = mutableListOf<Enemy>()
    val powerUps = mutableListOf<PowerUp>()
    val particles = mutableListOf<Particle>()

    var wave: Int = 1
        private set
    var score: Long = 0
        private set
    var kills: Int = 0
        private set
    var level: Int = 1
        private set
    var xp: Int = 0
        private set
    var xpNeeded: Int = 8
        private set
    var combo: Int = 1
        private set
    var comboTimer: Float = 0f
        private set
    var waveBannerTimer: Float = 0f
        private set
    var screenShake: Float = 0f
        private set
    var damageEventId: Long = 0
        private set
    var bossEventId: Long = 0
        private set
    var upgradeEventId: Long = 0
        private set
    var gameOverEventId: Long = 0
        private set

    var elapsedTime: Float = 0f
        private set
    var shotsFired: Int = 0
        private set
    var hitsLanded: Int = 0
        private set
    var difficulty: Difficulty = Difficulty.STANDARD
        private set

    val threatsRemaining: Int
        get() = enemies.size + spawnRemaining + if (wave % 5 == 0 && !bossSpawnedThisWave) 1 else 0
    val boss: Enemy?
        get() = enemies.firstOrNull { it.type == EnemyType.BOSS }
    val accuracyPercent: Int
        get() = if (shotsFired <= 0) 0 else ((hitsLanded * 100f / shotsFired).coerceIn(0f, 100f)).toInt()

    var upgradeChoices: List<UpgradeChoice> = emptyList()
        private set

    @Volatile var moveX: Float = 0f
    @Volatile var moveY: Float = 0f
    @Volatile var aimX: Float = 1f
    @Volatile var aimY: Float = 0f
    @Volatile var shooting: Boolean = false

    private var spawnRemaining = 0
    private var spawnCooldown = 0f
    private var waveClearTimer = 0f
    private var nextEnemyId = 1L
    private var bossSpawnedThisWave = false

    private val maxEnemies = 180
    private val maxBullets = 700

    fun resize(w: Float, h: Float) {
        width = max(1f, w)
        height = max(1f, h)
        if (state == GameState.MENU) {
            player.x = width * 0.5f
            player.y = height * 0.5f
        }
    }

    fun setDifficulty(value: Difficulty) {
        if (state == GameState.MENU || state == GameState.GAME_OVER) difficulty = value
    }

    fun startNewGame(selectedDifficulty: Difficulty = difficulty) {
        difficulty = selectedDifficulty
        bullets.clear(); enemies.clear(); powerUps.clear(); particles.clear()
        player.x = width * 0.5f
        player.y = height * 0.5f
        player.radius = max(18f, min(width, height) * 0.022f)
        player.hp = 100f; player.maxHp = 100f; player.speed = 430f
        player.fireInterval = 0.17f; player.bulletDamage = 20f; player.bulletSpeed = 940f
        player.multiShot = 1; player.pierce = 0; player.shieldCharges = 0
        player.rapidTimer = 0f; player.fireCooldown = 0f; player.invulnerable = 0f
        wave = 1; score = 0; kills = 0; level = 1; xp = 0; xpNeeded = 8
        combo = 1; comboTimer = 0f; screenShake = 0f
        elapsedTime = 0f; shotsFired = 0; hitsLanded = 0
        nextEnemyId = 1L
        state = GameState.RUNNING
        prepareWave()
    }

    fun togglePause() {
        state = when (state) {
            GameState.RUNNING -> GameState.PAUSED
            GameState.PAUSED -> GameState.RUNNING
            else -> state
        }
    }

    fun update(dtRaw: Float) {
        if (state != GameState.RUNNING) return
        val dt = min(dtRaw, 0.033f)
        elapsedTime += dt

        updateTimers(dt)
        updatePlayer(dt)
        updateWave(dt)
        updateEnemies(dt)
        updateBullets(dt)
        updatePowerUps(dt)
        updateParticles(dt)
        resolveCollisions()
        checkLevelUp()

        if (player.hp <= 0f && state == GameState.RUNNING) {
            player.hp = 0f
            state = GameState.GAME_OVER
            shooting = false
            gameOverEventId++
            burst(player.x, player.y, 42, 4)
        }
    }

    private fun updateTimers(dt: Float) {
        if (player.invulnerable > 0f) player.invulnerable -= dt
        if (player.rapidTimer > 0f) player.rapidTimer -= dt
        if (player.fireCooldown > 0f) player.fireCooldown -= dt
        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 1
        }
        if (waveBannerTimer > 0f) waveBannerTimer -= dt
        if (screenShake > 0f) screenShake = max(0f, screenShake - dt * 24f)
    }

    private fun updatePlayer(dt: Float) {
        val moveLen = hypot(moveX.toDouble(), moveY.toDouble()).toFloat()
        val mx = if (moveLen > 1f) moveX / moveLen else moveX
        val my = if (moveLen > 1f) moveY / moveLen else moveY
        player.x += mx * player.speed * dt
        player.y += my * player.speed * dt
        player.x = player.x.coerceIn(player.radius, width - player.radius)
        player.y = player.y.coerceIn(player.radius, height - player.radius)

        if (shooting && player.fireCooldown <= 0f) {
            firePlayerVolley()
            val rapidMultiplier = if (player.rapidTimer > 0f) 0.48f else 1f
            player.fireCooldown = max(0.045f, player.fireInterval * rapidMultiplier)
        }
    }

    private fun firePlayerVolley() {
        var ax = aimX
        var ay = aimY
        val len = hypot(ax.toDouble(), ay.toDouble()).toFloat()
        if (len < 0.1f) return
        ax /= len; ay /= len
        val baseAngle = atan2(ay.toDouble(), ax.toDouble()).toFloat()
        val count = player.multiShot.coerceIn(1, 7)
        val spread = 0.12f
        for (i in 0 until count) {
            val offset = (i - (count - 1) / 2f) * spread
            val angle = baseAngle + offset
            val vx = cos(angle) * player.bulletSpeed
            val vy = sin(angle) * player.bulletSpeed
            addBullet(Bullet(
                x = player.x + cos(angle) * (player.radius + 8f),
                y = player.y + sin(angle) * (player.radius + 8f),
                vx = vx,
                vy = vy,
                radius = 6f,
                damage = player.bulletDamage,
                friendly = true,
                life = 1.8f,
                pierceLeft = player.pierce
            ))
            shotsFired++
        }
    }

    private fun prepareWave() {
        spawnRemaining = max(1, ((4 + wave * 2) * difficulty.spawnScale).toInt())
        spawnCooldown = 0.6f
        waveClearTimer = 0f
        bossSpawnedThisWave = false
        waveBannerTimer = 2.2f
        if (wave % 5 == 0) spawnRemaining = max(4, (wave * difficulty.spawnScale).toInt())
    }

    private fun updateWave(dt: Float) {
        if (wave % 5 == 0 && !bossSpawnedThisWave && enemies.size < maxEnemies) {
            if (spawnEnemy(EnemyType.BOSS)) {
                bossSpawnedThisWave = true
                bossEventId++
            }
        }

        if (spawnRemaining > 0) {
            spawnCooldown -= dt
            if (spawnCooldown <= 0f) {
                if (spawnEnemy(selectEnemyType())) spawnRemaining--
                spawnCooldown = max(0.16f, 0.72f - wave * 0.018f)
            }
        } else if (enemies.isEmpty()) {
            waveClearTimer += dt
            if (waveClearTimer >= 1.8f) {
                wave++
                prepareWave()
            }
        }
    }

    private fun selectEnemyType(): EnemyType {
        val roll = random.nextFloat()
        return when {
            wave < 2 -> EnemyType.CHASER
            wave < 4 -> if (roll < 0.72f) EnemyType.CHASER else EnemyType.SHOOTER
            wave < 7 -> when {
                roll < 0.48f -> EnemyType.CHASER
                roll < 0.72f -> EnemyType.SHOOTER
                roll < 0.9f -> EnemyType.TANK
                else -> EnemyType.SPLITTER
            }
            else -> when {
                roll < 0.34f -> EnemyType.CHASER
                roll < 0.58f -> EnemyType.SHOOTER
                roll < 0.79f -> EnemyType.TANK
                else -> EnemyType.SPLITTER
            }
        }
    }

    private fun spawnEnemy(type: EnemyType, sx: Float? = null, sy: Float? = null): Boolean {
        if (enemies.size >= maxEnemies) return false
        val baseRadius = min(width, height) * 0.021f
        val (x, y) = if (sx != null && sy != null) {
            sx to sy
        } else {
            val margin = 40f
            when (random.nextInt(4)) {
                0 -> random.nextFloat() * width to -margin
                1 -> width + margin to random.nextFloat() * height
                2 -> random.nextFloat() * width to height + margin
                else -> -margin to random.nextFloat() * height
            }
        }
        val scale = (1f + wave * 0.075f) * difficulty.hpScale
        val speedScale = difficulty.speedScale
        val damageScale = difficulty.damageScale
        val enemy = when (type) {
            EnemyType.CHASER -> Enemy(nextEnemyId++, type, x, y, baseRadius, 34f * scale, 34f * scale, (145f + wave * 3f) * speedScale, 18f * damageScale)
            EnemyType.SHOOTER -> Enemy(nextEnemyId++, type, x, y, baseRadius * 0.92f, 42f * scale, 42f * scale, (105f + wave * 2f) * speedScale, 12f * damageScale, 0.8f)
            EnemyType.TANK -> Enemy(nextEnemyId++, type, x, y, baseRadius * 1.42f, 110f * scale, 110f * scale, (72f + wave * 1.5f) * speedScale, 30f * damageScale)
            EnemyType.SPLITTER -> Enemy(nextEnemyId++, type, x, y, baseRadius * 1.08f, 58f * scale, 58f * scale, (125f + wave * 2.2f) * speedScale, 16f * damageScale)
            EnemyType.BOSS -> Enemy(nextEnemyId++, type, x, y, baseRadius * 3.0f, (650f + wave * 95f) * difficulty.hpScale, (650f + wave * 95f) * difficulty.hpScale, (78f + wave) * speedScale, 38f * damageScale, 0.65f)
        }
        enemies += enemy
        return true
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            e.phase += dt
            var dx = player.x - e.x
            var dy = player.y - e.y
            val dist = max(0.001f, hypot(dx.toDouble(), dy.toDouble()).toFloat())
            dx /= dist; dy /= dist

            when (e.type) {
                EnemyType.CHASER, EnemyType.TANK, EnemyType.SPLITTER -> {
                    e.x += dx * e.speed * dt
                    e.y += dy * e.speed * dt
                }
                EnemyType.SHOOTER -> {
                    val desired = min(width, height) * 0.33f
                    val dir = when {
                        dist > desired + 70f -> 1f
                        dist < desired - 70f -> -1f
                        else -> 0f
                    }
                    val strafe = sin(e.phase * 2.4f) * 0.65f
                    e.x += (dx * dir - dy * strafe) * e.speed * dt
                    e.y += (dy * dir + dx * strafe) * e.speed * dt
                    e.shootCooldown -= dt
                    if (e.shootCooldown <= 0f) {
                        fireEnemyBullet(e, dx, dy, 290f + wave * 5f, 13f + wave * 0.4f)
                        e.shootCooldown = max(0.45f, 1.25f - wave * 0.025f)
                    }
                }
                EnemyType.BOSS -> {
                    val orbit = sin(e.phase * 1.5f) * 0.55f
                    e.x += (dx - dy * orbit) * e.speed * dt
                    e.y += (dy + dx * orbit) * e.speed * dt
                    e.shootCooldown -= dt
                    if (e.shootCooldown <= 0f) {
                        fireBossPattern(e)
                        e.shootCooldown = max(0.28f, 0.82f - wave * 0.018f)
                    }
                }
            }
        }
    }

    private fun fireEnemyBullet(e: Enemy, dx: Float, dy: Float, speed: Float, damage: Float) {
        addBullet(Bullet(e.x, e.y, dx * speed, dy * speed, 7f, damage * difficulty.damageScale, false, 4.5f))
    }

    private fun fireBossPattern(e: Enemy) {
        val spokes = 10
        val spin = e.phase * 0.85f
        for (i in 0 until spokes) {
            val angle = spin + (Math.PI * 2.0 * i / spokes).toFloat()
            val speed = 245f + wave * 4f
            addBullet(Bullet(e.x, e.y, cos(angle) * speed, sin(angle) * speed, 8f, (15f + wave * 0.45f) * difficulty.damageScale, false, 5f))
        }
        val dx = player.x - e.x
        val dy = player.y - e.y
        val len = max(0.001f, hypot(dx.toDouble(), dy.toDouble()).toFloat())
        fireEnemyBullet(e, dx / len, dy / len, 390f + wave * 4f, 20f + wave * 0.5f)
    }

    private fun addBullet(bullet: Bullet) {
        if (bullets.size >= maxBullets) bullets.removeAt(0)
        bullets += bullet
    }

    private fun updateBullets(dt: Float) {
        val it = bullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.vx * dt; b.y += b.vy * dt; b.life -= dt
            if (b.life <= 0f || b.x < -120f || b.y < -120f || b.x > width + 120f || b.y > height + 120f) {
                it.remove()
            }
        }
    }

    private fun updatePowerUps(dt: Float) {
        val it = powerUps.iterator()
        while (it.hasNext()) {
            val p = it.next(); p.life -= dt
            if (p.life <= 0f) it.remove()
        }
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vx *= (1f - min(0.92f, dt * 2.2f)); p.vy *= (1f - min(0.92f, dt * 2.2f))
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
        if (particles.size > 520) particles.subList(0, particles.size - 520).clear()
    }

    private fun resolveCollisions() {
        val deadEnemies = mutableListOf<Enemy>()
        val deadBullets = mutableListOf<Bullet>()

        for (b in bullets) {
            if (!b.friendly) continue
            for (e in enemies) {
                if (e in deadEnemies || e.id in b.hitEnemyIds) continue
                if (circleHit(b.x, b.y, b.radius, e.x, e.y, e.radius)) {
                    if (b.hitEnemyIds.isEmpty()) hitsLanded++
                    b.hitEnemyIds += e.id
                    e.hp -= b.damage
                    burst(b.x, b.y, 3, 1)
                    if (e.hp <= 0f) deadEnemies += e
                    if (b.pierceLeft > 0) b.pierceLeft-- else deadBullets += b
                    break
                }
            }
        }

        if (player.invulnerable <= 0f) {
            for (b in bullets) {
                if (b.friendly) continue
                if (circleHit(b.x, b.y, b.radius, player.x, player.y, player.radius)) {
                    damagePlayer(b.damage)
                    deadBullets += b
                    break
                }
            }
        }

        if (player.invulnerable <= 0f) {
            for (e in enemies) {
                if (circleHit(e.x, e.y, e.radius, player.x, player.y, player.radius)) {
                    damagePlayer(e.contactDamage)
                    val dx = e.x - player.x
                    val dy = e.y - player.y
                    val len = max(0.001f, hypot(dx.toDouble(), dy.toDouble()).toFloat())
                    e.x += dx / len * 45f; e.y += dy / len * 45f
                    break
                }
            }
        }

        for (p in powerUps.toList()) {
            if (circleHit(p.x, p.y, p.radius, player.x, player.y, player.radius)) {
                applyPowerUp(p.type)
                powerUps.remove(p)
            }
        }

        if (deadBullets.isNotEmpty()) bullets.removeAll(deadBullets)
        for (e in deadEnemies.distinctBy { it.id }) killEnemy(e)
    }

    private fun damagePlayer(amount: Float) {
        if (player.shieldCharges > 0) {
            player.shieldCharges--
            player.invulnerable = 0.35f
            burst(player.x, player.y, 14, 3)
        } else {
            player.hp -= amount
            player.invulnerable = 0.55f
            damageEventId++
            screenShake = max(screenShake, 9f)
            burst(player.x, player.y, 18, 4)
        }
    }

    private fun killEnemy(e: Enemy) {
        if (!enemies.remove(e)) return
        kills++
        combo = if (comboTimer > 0f) min(12, combo + 1) else 1
        comboTimer = 1.65f
        val base = when (e.type) {
            EnemyType.CHASER -> 100
            EnemyType.SHOOTER -> 150
            EnemyType.TANK -> 220
            EnemyType.SPLITTER -> 180
            EnemyType.BOSS -> 2200 + wave * 100
        }
        score += (base.toFloat() * combo * difficulty.scoreMultiplier).toLong()
        xp += when (e.type) {
            EnemyType.BOSS -> 8
            EnemyType.TANK -> 3
            EnemyType.SPLITTER -> 2
            else -> 1
        }
        screenShake = max(screenShake, if (e.type == EnemyType.BOSS) 15f else 4f)
        burst(e.x, e.y, if (e.type == EnemyType.BOSS) 46 else 14, when (e.type) {
            EnemyType.CHASER -> 1
            EnemyType.SHOOTER -> 2
            EnemyType.TANK -> 3
            EnemyType.SPLITTER -> 2
            EnemyType.BOSS -> 5
        })

        if (e.type == EnemyType.SPLITTER) {
            repeat(2) {
                if (spawnEnemy(EnemyType.CHASER, e.x + random.nextFloat() * 24f - 12f, e.y + random.nextFloat() * 24f - 12f)) {
                    enemies.last().apply {
                        radius *= 0.72f; hp *= 0.55f; maxHp = hp; speed *= 1.22f
                    }
                }
            }
        }

        val dropChance = if (e.type == EnemyType.BOSS) 1f else 0.105f
        if (random.nextFloat() < dropChance) {
            val type = if (e.type == EnemyType.BOSS) PowerType.NOVA else PowerType.entries[random.nextInt(PowerType.entries.size)]
            if (powerUps.size >= 18) powerUps.removeAt(0)
            powerUps += PowerUp(type, e.x, e.y)
        }
    }

    private fun applyPowerUp(type: PowerType) {
        when (type) {
            PowerType.HEAL -> player.hp = min(player.maxHp, player.hp + player.maxHp * 0.34f)
            PowerType.RAPID -> player.rapidTimer = max(player.rapidTimer, 7f)
            PowerType.SHIELD -> player.shieldCharges += 2
            PowerType.NOVA -> {
                val victims = enemies.toList()
                for (e in victims) {
                    e.hp -= max(45f, player.bulletDamage * 3.5f)
                    if (e.hp <= 0f) killEnemy(e)
                }
                screenShake = 13f
                burst(player.x, player.y, 55, 5)
            }
        }
    }

    private fun checkLevelUp() {
        if (state != GameState.RUNNING || xp < xpNeeded) return
        xp -= xpNeeded
        level++
        xpNeeded = 7 + level * 3
        upgradeChoices = generateUpgradeChoices()
        state = GameState.UPGRADE
        shooting = false
        upgradeEventId++
    }

    private fun generateUpgradeChoices(): List<UpgradeChoice> {
        return UpgradeType.entries.shuffled(random).take(3).map { type ->
            when (type) {
                UpgradeType.DAMAGE -> UpgradeChoice(type, "Overcharge", "+25% bullet damage")
                UpgradeType.FIRE_RATE -> UpgradeChoice(type, "Hyper Trigger", "+18% fire rate")
                UpgradeType.MOVE_SPEED -> UpgradeChoice(type, "Flux Drive", "+12% move speed")
                UpgradeType.MAX_HP -> UpgradeChoice(type, "Reinforced Core", "+25 max HP and heal 25")
                UpgradeType.MULTI_SHOT -> UpgradeChoice(type, "Forked Plasma", "+1 projectile per volley")
                UpgradeType.BULLET_SPEED -> UpgradeChoice(type, "Rail Accelerator", "+20% projectile speed")
                UpgradeType.PIERCE -> UpgradeChoice(type, "Phase Rounds", "+1 enemy pierce")
                UpgradeType.SHIELD -> UpgradeChoice(type, "Quantum Shield", "+2 shield charges")
            }
        }
    }

    fun applyUpgrade(index: Int) {
        if (state != GameState.UPGRADE || index !in upgradeChoices.indices) return
        when (upgradeChoices[index].type) {
            UpgradeType.DAMAGE -> player.bulletDamage *= 1.25f
            UpgradeType.FIRE_RATE -> player.fireInterval = max(0.055f, player.fireInterval * 0.82f)
            UpgradeType.MOVE_SPEED -> player.speed *= 1.12f
            UpgradeType.MAX_HP -> {
                player.maxHp += 25f; player.hp = min(player.maxHp, player.hp + 25f)
            }
            UpgradeType.MULTI_SHOT -> player.multiShot = min(7, player.multiShot + 1)
            UpgradeType.BULLET_SPEED -> player.bulletSpeed *= 1.20f
            UpgradeType.PIERCE -> player.pierce = min(5, player.pierce + 1)
            UpgradeType.SHIELD -> player.shieldCharges += 2
        }
        upgradeChoices = emptyList()
        state = GameState.RUNNING
    }

    fun returnToMenu() {
        state = GameState.MENU
        shooting = false
        bullets.clear(); enemies.clear(); powerUps.clear(); particles.clear()
    }

    private fun burst(x: Float, y: Float, count: Int, colorTag: Int) {
        repeat(count) {
            val angle = random.nextFloat() * (Math.PI * 2.0).toFloat()
            val speed = 45f + random.nextFloat() * 320f
            val life = 0.22f + random.nextFloat() * 0.62f
            particles += Particle(x, y, cos(angle) * speed, sin(angle) * speed, life, life, 2.5f + random.nextFloat() * 7f, colorTag)
        }
    }

    private fun circleHit(ax: Float, ay: Float, ar: Float, bx: Float, by: Float, br: Float): Boolean {
        val dx = ax - bx; val dy = ay - by; val rr = ar + br
        return dx * dx + dy * dy <= rr * rr
    }
}
