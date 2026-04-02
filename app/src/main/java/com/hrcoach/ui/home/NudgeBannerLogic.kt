package com.hrcoach.ui.home

import com.hrcoach.domain.model.PartnerActivity

data class NudgeBannerState(
    val text: String,
    val subtitle: String?,
    val partners: List<PartnerActivity>,
)

fun computeNudgeBanner(
    partners: List<PartnerActivity>,
    userRanToday: Boolean,
): NudgeBannerState? {
    if (partners.isEmpty() || userRanToday) return null

    val recentPartners = partners
        .filter { it.isRecentlyActive() }
        .sortedByDescending { it.lastRunDate }

    if (recentPartners.isEmpty()) return null

    val todayRunners = recentPartners.filter { it.ranToday() }

    val text: String
    val subtitle: String?

    when {
        todayRunners.size >= 2 -> {
            text = "${todayRunners[0].displayName} and ${todayRunners[1].displayName} both ran today"
            subtitle = "Your turn?"
        }
        todayRunners.size == 1 -> {
            text = "${todayRunners[0].displayName} just finished a run"
            val others = recentPartners.filter { !it.ranToday() }
            subtitle = if (others.isNotEmpty()) {
                "${others[0].displayName} ran yesterday · Both keeping it up!"
            } else {
                "Your turn?"
            }
        }
        else -> {
            text = "${recentPartners[0].displayName} ran yesterday"
            subtitle = "Your turn?"
        }
    }

    return NudgeBannerState(
        text = text,
        subtitle = subtitle,
        partners = recentPartners,
    )
}
