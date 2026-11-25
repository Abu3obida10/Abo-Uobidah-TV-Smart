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
}
