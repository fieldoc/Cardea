package com.hrcoach.domain.emblem

enum class Emblem(val id: String, val displayName: String) {
    PULSE("pulse", "Pulse"),
    BOLT("bolt", "Bolt"),
    SUMMIT("summit", "Summit"),
    FLAME("flame", "Flame"),
    COMPASS("compass", "Compass"),
    SHIELD("shield", "Shield"),
    ASCENT("ascent", "Ascent"),
    CROWN("crown", "Crown"),
    ORBIT("orbit", "Orbit"),
    INFINITY("infinity", "Infinity"),
    DIAMOND("diamond", "Diamond"),
    NOVA("nova", "Nova"),
    VORTEX("vortex", "Vortex"),
    ANCHOR("anchor", "Anchor"),
    PHOENIX("phoenix", "Phoenix"),
    ARROW("arrow", "Arrow"),
    CREST("crest", "Crest"),
    PRISM("prism", "Prism"),
    RIPPLE("ripple", "Ripple"),
    COMET("comet", "Comet"),
    THRESHOLD("threshold", "Threshold"),
    CIRCUIT("circuit", "Circuit"),
    APEX("apex", "Apex"),
    FORGE("forge", "Forge");

    companion object {
        private val byId = entries.associateBy { it.id }

        private val legacyMap = mapOf(
            "\u2665" to PULSE,
            "\u2605" to NOVA,
            "\u26A1" to BOLT,
            "\u25C6" to DIAMOND,
            "\u25B2" to ASCENT,
            "\u25CF" to ORBIT,
            "\u2726" to NOVA,
            "\u2666" to DIAMOND,
            "\u2191" to ARROW,
            "\u221E" to INFINITY,
        )

        fun fromId(id: String): Emblem =
            byId[id] ?: legacyMap[id] ?: PULSE
    }
}
