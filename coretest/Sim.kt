import com.minhajul.riftreign.*
import kotlin.random.Random

fun main() {
    val rng = Random(42)
    var frames = 0L
    repeat(120) {
        val w = GameWorld(Random(it + 1))
        w.resize(1920f, 1080f)
        w.startNewGame(Difficulty.entries[it % Difficulty.entries.size])
        repeat(6000) {
            if (w.state == GameState.GAME_OVER) return@repeat
            w.moveX = rng.nextFloat() * 2f - 1f
            w.moveY = rng.nextFloat() * 2f - 1f
            w.aimX = rng.nextFloat() * 2f - 1f
            w.aimY = rng.nextFloat() * 2f - 1f
            w.shooting = rng.nextFloat() > 0.12f
            w.update(1f/60f)
            frames++
            check(w.player.x.isFinite() && w.player.y.isFinite())
            check(w.enemies.size <= 180)
            check(w.bullets.size <= 700)
            check(w.particles.size <= 520)
            if (w.state == GameState.UPGRADE) w.applyUpgrade(rng.nextInt(3))
        }
    }
    println("OK frames=$frames")
}
