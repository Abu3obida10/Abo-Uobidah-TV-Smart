package com.uobidah.launcher

import android.content.ComponentName
import android.content.Intent
import android.media.tv.TvInputManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-hdpi")
class TvInputsIntentTest {
    private fun find(root: View, tag: String): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val match = find(root.getChildAt(i), tag)
                if (match != null) return match
            }
        }
        return null
    }


    @Test fun directRoutePrefersConnectedHdmiOverResolvableSavedTvApplication() {
        val route = TvInputRoutingPolicy.selectDirectRoute(
            savedTvAppResolvable = true,
            hdmiStates = listOf(TvInputManager.INPUT_STATE_CONNECTED),
            connectedState = TvInputManager.INPUT_STATE_CONNECTED,
            connectedStandbyState = TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        )
        assertEquals("HDMI المتصل يسبق تطبيق التلفزيون المحفوظ", TvInputRoutingPolicy.DirectRoute.CONNECTED_HDMI, route)
    }

    @Test fun directRouteFallsBackToSavedTvApplicationWhenNoHdmiIsConnected() {
        val route = TvInputRoutingPolicy.selectDirectRoute(
            savedTvAppResolvable = true,
            hdmiStates = listOf(TvInputManager.INPUT_STATE_DISCONNECTED),
            connectedState = TvInputManager.INPUT_STATE_CONNECTED,
            connectedStandbyState = TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        )
        assertEquals("عند عدم اتصال HDMI، تطبيق التلفزيون المحفوظ هو البديل", TvInputRoutingPolicy.DirectRoute.SAVED_TV_APP, route)
    }

    @Test fun directRouteUsesConnectedHdmiWhenNoSavedTvApplicationExists() {
        val route = TvInputRoutingPolicy.selectDirectRoute(
            savedTvAppResolvable = false,
            hdmiStates = listOf(TvInputManager.INPUT_STATE_DISCONNECTED, TvInputManager.INPUT_STATE_CONNECTED),
            connectedState = TvInputManager.INPUT_STATE_CONNECTED,
            connectedStandbyState = TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        )
        assertEquals(TvInputRoutingPolicy.DirectRoute.CONNECTED_HDMI, route)
    }

    @Test fun connectedStandbyHdmiAlsoPrecedesSavedTvApplication() {
        val route = TvInputRoutingPolicy.selectDirectRoute(
            savedTvAppResolvable = true,
            hdmiStates = listOf(TvInputManager.INPUT_STATE_DISCONNECTED, TvInputManager.INPUT_STATE_CONNECTED_STANDBY),
            connectedState = TvInputManager.INPUT_STATE_CONNECTED,
            connectedStandbyState = TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        )
        assertEquals(TvInputRoutingPolicy.DirectRoute.CONNECTED_HDMI, route)
    }

    @Test fun directRouteKeepsInputsListWhenNoHdmiOrSavedTvApplicationExists() {
        val route = TvInputRoutingPolicy.selectDirectRoute(
            savedTvAppResolvable = false,
            hdmiStates = emptyList(),
            connectedState = TvInputManager.INPUT_STATE_CONNECTED,
            connectedStandbyState = TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        )
        assertEquals("عند عدم وجود HDMI أو تطبيق تلفزيون محفوظ تبقى شاشة المداخل مفتوحة", TvInputRoutingPolicy.DirectRoute.INPUTS_LIST, route)
    }

    @Test fun directRouteNeverAutoSelectsDisconnectedHdmi() {
        val route = TvInputRoutingPolicy.selectDirectRoute(
            savedTvAppResolvable = false,
            hdmiStates = listOf(TvInputManager.INPUT_STATE_DISCONNECTED),
            connectedState = TvInputManager.INPUT_STATE_CONNECTED,
            connectedStandbyState = TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        )
        assertEquals(TvInputRoutingPolicy.DirectRoute.INPUTS_LIST, route)
        assertFalse(TvInputRoutingPolicy.isDirectlyAvailableInputState(
            TvInputManager.INPUT_STATE_DISCONNECTED,
            TvInputManager.INPUT_STATE_CONNECTED,
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        ))
    }

    @Test fun connectedStandbyHdmiIsAvailableButDisconnectedHdmiIsNot() {
        assertTrue(TvInputRoutingPolicy.isDirectlyAvailableInputState(
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY,
            TvInputManager.INPUT_STATE_CONNECTED,
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        ))
        assertFalse(TvInputRoutingPolicy.isDirectlyAvailableInputState(
            TvInputManager.INPUT_STATE_DISCONNECTED,
            TvInputManager.INPUT_STATE_CONNECTED,
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        ))
    }

    @Test fun hdmiShortcutTargetsDedicatedInputsActivityAndRequestsDirectPassthrough() {
        val context = RuntimeEnvironment.getApplication()
        val intent = TvInputsActivity.directInputsIntent(context)

        assertEquals(
            "زر المدخل يجب أن يفتح شاشة مداخل التطبيق مباشرة",
            ComponentName(context, TvInputsActivity::class.java),
            intent.component
        )
        assertTrue(
            "يجب أن يطلب المسار المباشر محاولة فتح مدخل HDMI المتصل",
            intent.getBooleanExtra(TvInputsActivity.EXTRA_OPEN_HDMI_DIRECT, false)
        )
        assertFalse(
            "يجب ألا يرجع زر HDMI إلى إعدادات Android العامة",
            intent.action == Settings.ACTION_SETTINGS
        )
    }

    @Test fun r9HeaderInputButtonLaunchesDedicatedIntentInsteadOfAndroidSettings() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = Prefs(context)
        prefs.layoutIndex = 25
        prefs.showClockCard = true
        prefs.showWeatherCard = true
        prefs.showPrayerCard = true
        prefs.showMatchFavorites = false
        prefs.matchFavoriteTeams = emptyList()
        prefs.pinnedInit = true
        prefs.askedPerms = true
        prefs.homeCleanV1 = true
        prefs.proHomeDesignV1 = true

        val controller = Robolectric.buildActivity(MainLauncherActivity::class.java, Intent(context, MainLauncherActivity::class.java))
            .create().start().resume().visible()
        try {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val activity = controller.get()
            val inputAction = find(activity.window.decorView, "r9-action-input")
            assertNotNull("زر HDMI/المدخل المسمى في هيدر R9 غير موجود", inputAction)
            assertTrue("زر HDMI/المدخل لم يستجب للنقر", inputAction!!.performClick())

            val launched = Shadows.shadowOf(activity).nextStartedActivity
            assertNotNull("زر HDMI لم يطلق شاشة مداخل التطبيق", launched)
            assertEquals(
                "زر HDMI يجب أن يفتح TvInputsActivity مباشرة",
                ComponentName(context, TvInputsActivity::class.java),
                launched!!.component
            )
            assertTrue("Intent زر HDMI لا يطلب محاولة فتح مدخل HDMI مباشرة",
                launched.getBooleanExtra(TvInputsActivity.EXTRA_OPEN_HDMI_DIRECT, false))
            assertFalse("زر HDMI لا يجوز أن يفتح إعدادات Android العامة", launched.action == Settings.ACTION_SETTINGS)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
