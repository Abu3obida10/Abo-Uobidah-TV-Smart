package com.uobidah.launcher

/** Narrow, inert-by-default synchronization hooks for deterministic UI tests. */
internal object LauncherTestHooks {
    const val FAVORITES_SESSION_EXTRA = "com.uobidah.launcher.test.FAVORITES_SESSION"

    private fun isSession(activity: MainLauncherActivity, token: String): Boolean =
        token.isNotBlank() && activity.intent.getStringExtra(FAVORITES_SESSION_EXTRA) == token

    @Volatile var favoritesSessionToken: String? = null
    @Volatile var beforeFavoritesLoad: ((MainLauncherActivity) -> Unit)? = null
    @Volatile var afterFavoritesCallbackQueued: ((MainLauncherActivity) -> Unit)? = null
    @Volatile var afterFavoritesRendered: ((MainLauncherActivity) -> Unit)? = null

    fun beforeLoad(activity: MainLauncherActivity) {
        val token = favoritesSessionToken ?: return
        if (isSession(activity, token)) beforeFavoritesLoad?.invoke(activity)
    }

    fun callbackQueued(activity: MainLauncherActivity) {
        val token = favoritesSessionToken ?: return
        if (isSession(activity, token)) afterFavoritesCallbackQueued?.invoke(activity)
    }

    fun rendered(activity: MainLauncherActivity) {
        val token = favoritesSessionToken ?: return
        if (isSession(activity, token)) afterFavoritesRendered?.invoke(activity)
    }

    fun clearFavoritesSession() {
        beforeFavoritesLoad = null
        afterFavoritesCallbackQueued = null
        afterFavoritesRendered = null
        favoritesSessionToken = null
    }
}
