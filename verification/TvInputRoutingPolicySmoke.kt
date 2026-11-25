package com.uobidah.launcher

/** Standalone smoke harness for the exact production routing policy source. */
object TvInputManager {
    const val INPUT_STATE_CONNECTED = 0
    const val INPUT_STATE_CONNECTED_STANDBY = 1
    const val INPUT_STATE_DISCONNECTED = 2
}

fun main() {
    val connected = TvInputManager.INPUT_STATE_CONNECTED
    val standby = TvInputManager.INPUT_STATE_CONNECTED_STANDBY
    val disconnected = TvInputManager.INPUT_STATE_DISCONNECTED

    check(TvInputRoutingPolicy.selectDirectRoute(true, listOf(connected), connected, standby) ==
        TvInputRoutingPolicy.DirectRoute.CONNECTED_HDMI) { "Connected HDMI must win over a saved TV app" }
    check(TvInputRoutingPolicy.selectDirectRoute(true, listOf(standby), connected, standby) ==
        TvInputRoutingPolicy.DirectRoute.CONNECTED_HDMI) { "Connected-standby HDMI must win over a saved TV app" }
    check(TvInputRoutingPolicy.selectDirectRoute(false, listOf(disconnected, connected), connected, standby) ==
        TvInputRoutingPolicy.DirectRoute.CONNECTED_HDMI) { "Connected HDMI must be selected when no saved app exists" }
    check(TvInputRoutingPolicy.selectDirectRoute(true, listOf(disconnected), connected, standby) ==
        TvInputRoutingPolicy.DirectRoute.SAVED_TV_APP) { "Saved TV app is the fallback when no HDMI is connected" }
    check(TvInputRoutingPolicy.selectDirectRoute(false, listOf(disconnected), connected, standby) ==
        TvInputRoutingPolicy.DirectRoute.INPUTS_LIST) { "Disconnected HDMI must not be auto-selected" }
    check(TvInputRoutingPolicy.selectDirectRoute(false, emptyList(), connected, standby) ==
        TvInputRoutingPolicy.DirectRoute.INPUTS_LIST) { "No discovered HDMI and no saved app must leave the inputs list open" }
    check(!TvInputRoutingPolicy.isDirectlyAvailableInputState(disconnected, connected, standby))
    check(TvInputRoutingPolicy.isDirectlyAvailableInputState(connected, connected, standby))
    check(TvInputRoutingPolicy.isDirectlyAvailableInputState(standby, connected, standby))
    println("PASS 9/9: HDMI priority, standby priority, saved-app fallback, disconnected exclusion, empty-input handling")
}
