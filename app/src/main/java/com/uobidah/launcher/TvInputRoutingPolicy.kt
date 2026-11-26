package com.uobidah.launcher

/** Pure routing policy for the dedicated TV-input shortcut; kept Android-service-free for tests. */
internal object TvInputRoutingPolicy {
    enum class DirectRoute { SAVED_TV_APP, CONNECTED_HDMI, INPUTS_LIST }

    /**
     * A physically connected HDMI input has priority over the saved TV app.
     * The saved app is a fallback only when no HDMI input is connected/standby.
     * Disconnected entries are never promoted just because the TV advertises them.
     */
    fun selectDirectRoute(
        savedTvAppResolvable: Boolean,
        hdmiStates: List<Int>,
        connectedState: Int,
        connectedStandbyState: Int
    ): DirectRoute = when {
        hdmiStates.any { isDirectlyAvailableInputState(it, connectedState, connectedStandbyState) } -> DirectRoute.CONNECTED_HDMI
        savedTvAppResolvable -> DirectRoute.SAVED_TV_APP
        else -> DirectRoute.INPUTS_LIST
    }

    fun isDirectlyAvailableInputState(state: Int, connectedState: Int, connectedStandbyState: Int): Boolean =
        state == connectedState || state == connectedStandbyState

    /**
     * Among the HDMI inputs that can be opened directly (connected or standby), choose the
     * one the HDMI button opens by default: HDMI 1. An input counts as HDMI 1 when its
     * label/custom label names it ("HDMI 1", "hdmi1", "1 HDMI"); otherwise inputs are
     * already in the TV's port order, so the first available HDMI input wins.
     * Returns the index within [labels]/[states], or -1 when no HDMI input is available.
     */
    fun indexOfPreferredHdmi(
        labels: List<String>,
        states: List<Int>,
        connectedState: Int,
        connectedStandbyState: Int
    ): Int {
        var firstAvailable = -1
        for (i in states.indices) {
            if (!isDirectlyAvailableInputState(states[i], connectedState, connectedStandbyState)) continue
            if (firstAvailable < 0) firstAvailable = i
            if (isHdmiOneLabel(labels.getOrElse(i) { "" })) return i
        }
        return firstAvailable
    }

    /** True when the input's own label marks it as the first HDMI port. */
    fun isHdmiOneLabel(rawLabel: String): Boolean {
        val label = rawLabel.lowercase().trim()
        if (label.isEmpty()) return false
        val compact = label.replace(" ", "").replace("-", "").replace("_", "")
        if (compact == "hdmi1" || compact == "1hdmi") return true
        // "HDMI 1" / "hdmi 1 (arc)" style labels, but never "HDMI 10"/"HDMI 21".
        val m = Regex("hdmi[^0-9]*([0-9]+)").find(compact) ?: return false
        return m.groupValues[1] == "1"
    }
}
