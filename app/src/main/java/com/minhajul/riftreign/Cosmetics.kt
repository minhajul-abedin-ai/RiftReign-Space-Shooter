package com.minhajul.riftreign

import android.graphics.Color

data class ShipSkin(
    val id: String,
    val name: String,
    val cost: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val geometry: Int
)

data class WeaponSkin(
    val id: String,
    val name: String,
    val cost: Int,
    val core: Int,
    val glow: Int,
    val trailScale: Float
)

data class TargetTheme(
    val id: String,
    val name: String,
    val cost: Int,
    val chaser: Int,
    val shooter: Int,
    val tank: Int,
    val splitter: Int,
    val boss: Int,
    val trim: Int
)

object CosmeticsCatalog {
    val ships = listOf(
        ShipSkin("nova", "NOVA CORE", 0, Color.rgb(50, 230, 255), Color.rgb(225, 255, 255), Color.rgb(255, 65, 215), 0),
        ShipSkin("falcon", "CYBER FALCON", 180, Color.rgb(80, 255, 185), Color.rgb(220, 255, 240), Color.rgb(20, 150, 255), 1),
        ShipSkin("phantom", "PHANTOM WING", 420, Color.rgb(155, 95, 255), Color.rgb(235, 220, 255), Color.rgb(70, 245, 255), 2),
        ShipSkin("viper", "CRIMSON VIPER", 650, Color.rgb(255, 70, 100), Color.rgb(255, 225, 230), Color.rgb(255, 165, 35), 3),
        ShipSkin("gold", "GOLDEN NOVA", 900, Color.rgb(255, 205, 55), Color.rgb(255, 250, 210), Color.rgb(255, 115, 35), 1),
        ShipSkin("void", "VOID HUNTER", 1200, Color.rgb(45, 35, 80), Color.rgb(185, 130, 255), Color.rgb(255, 40, 205), 4)
    )

    val weapons = listOf(
        WeaponSkin("pulse", "PULSE BEAM", 0, Color.rgb(70, 245, 255), Color.argb(65, 70, 245, 255), 1.0f),
        WeaponSkin("plasma", "PLASMA ARC", 160, Color.rgb(120, 255, 160), Color.argb(70, 80, 255, 145), 1.25f),
        WeaponSkin("ion", "ION LANCE", 380, Color.rgb(180, 105, 255), Color.argb(75, 160, 80, 255), 1.55f),
        WeaponSkin("prism", "PRISM SHOT", 620, Color.rgb(255, 205, 70), Color.argb(80, 255, 90, 215), 1.85f)
    )

    val targets = listOf(
        TargetTheme("neon", "NEON RAIDERS", 0,
            Color.rgb(255, 58, 120), Color.rgb(185, 70, 255), Color.rgb(255, 76, 45),
            Color.rgb(255, 150, 40), Color.rgb(255, 35, 92), Color.rgb(235, 205, 255)),
        TargetTheme("drones", "ROGUE DRONES", 300,
            Color.rgb(70, 230, 255), Color.rgb(75, 150, 255), Color.rgb(90, 220, 185),
            Color.rgb(100, 255, 120), Color.rgb(60, 160, 255), Color.rgb(210, 250, 255)),
        TargetTheme("void", "VOID SWARM", 600,
            Color.rgb(155, 80, 255), Color.rgb(95, 55, 190), Color.rgb(115, 70, 145),
            Color.rgb(215, 60, 255), Color.rgb(120, 20, 220), Color.rgb(245, 215, 255)),
        TargetTheme("inferno", "INFERNO LEGION", 900,
            Color.rgb(255, 75, 30), Color.rgb(255, 135, 35), Color.rgb(210, 50, 35),
            Color.rgb(255, 205, 55), Color.rgb(255, 35, 35), Color.rgb(255, 225, 165))
    )

    fun ship(id: String): ShipSkin = ships.firstOrNull { it.id == id } ?: ships.first()
    fun weapon(id: String): WeaponSkin = weapons.firstOrNull { it.id == id } ?: weapons.first()
    fun target(id: String): TargetTheme = targets.firstOrNull { it.id == id } ?: targets.first()
}
