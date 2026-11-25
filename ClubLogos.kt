package com.uobidah.launcher

/** شعارات الأندية الكاملة (PNG شفاف في drawable-nodpi). art: 1 الأهلي، 2 مانشستر يونايتد، 3 ريال مدريد. */
object ClubLogos {
    fun res(art: Int): Int = when (art) {
        1 -> R.drawable.club_ahly
        2 -> R.drawable.club_manutd
        3 -> R.drawable.club_realmadrid
        else -> 0
    }
}
