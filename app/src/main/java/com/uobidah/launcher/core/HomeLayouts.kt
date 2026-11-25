package com.uobidah.launcher.core

/** أقسام واجهة الشاشة الرئيسية. DOCK محفوظ للتوافق مع الإصدارات القديمة فقط. */
enum class HomeSection { TOP, CARDS, PINNED, FAV, DOCK, SPACE, GRID, CHANNELS }

enum class HomeCard { CLOCK, WEATHER, PRAYER }

/**
 * قوالب واجهات فعلية، وليست مجرد اختلاف في ترتيب البطاقات أو أوزانها.
 * لكل قالب توزيع مستقل في MainLauncherActivity وفي معاينة معرض التخطيطات.
 */
enum class HomeLayoutTemplate {
    BALANCED,
    CLOCK_HERO,
    PRAYER_HERO,
    APP_FIRST,
    TV_REFERENCE,
    APP_GRID_FIRST,
    CLOCK_ONLY,
    WEATHER_PRAYER_STACK,
    CINEMA,
    CARDS_BOTTOM,
    CLOCK_APPS_SPLIT,
    CARD_SHOWCASE,
    COMPACT,
    WEATHER_HERO,
    MATCHES_FIRST,
    PRAYER_MINIMAL,
    CHANNELS_RACK,
    SPLIT_COLUMNS,
    SPORTS_BOARD,
    FAVORITES_GRID,
    APP_GRID,
    CHANNELS_FIRST,
    MATCH_CENTER,
    PRAYER_LONG,
    DUSK_BOARD,
    DASHBOARD
}

/**
 * تعريف تخطيط واحد. cardsHeightDp قيمة مساعدة للبطاقات في القوالب القديمة.
 * template هو الذي يحدد الآن شكل الشاشة ومكان كل مجموعة فعليًا.
 */
class HomeLayoutSpec(
    val index: Int,
    val nameAr: String,
    val descAr: String,
    val tv: Boolean,
    val order: List<HomeSection>,
    val cards: List<Pair<HomeCard, Float>>,
    val cardsHeightDp: Int = 0,
    val template: HomeLayoutTemplate = HomeLayoutTemplate.BALANCED
)

object HomeLayouts {
    const val TV_INDEX = 4

    private val header = HomeSection.TOP
    private val cards = HomeSection.CARDS
    private val apps = HomeSection.PINNED
    private val matches = HomeSection.FAV
    private val grid = HomeSection.GRID
    private val channels = HomeSection.CHANNELS
    private val gap = HomeSection.SPACE

    /** 21 تخطيطًا ظاهرًا للمستخدم، لكل واحد قالب عرض مستقل. */
    val all: List<HomeLayoutSpec> = listOf(
        HomeLayoutSpec(0, "لوحة متوازنة", "الوقت بالأعلى، بطاقتا الطقس والصلاة، ثم التطبيقات والمباريات", false,
            listOf(header, cards, apps, matches), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), template = HomeLayoutTemplate.BALANCED),
        HomeLayoutSpec(1, "الساعة البارزة", "ساعة كبيرة في رأس الشاشة مع شبكة تطبيقات ومعلومات مختصرة", false,
            listOf(header, grid, cards), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), template = HomeLayoutTemplate.CLOCK_HERO),
        HomeLayoutSpec(2, "الصلاة أولاً", "بطاقة صلاة واحدة عريضة تتصدر الشاشة يليها صف التطبيقات", false,
            listOf(header, cards, apps), listOf(HomeCard.PRAYER to 1f), 390, HomeLayoutTemplate.PRAYER_HERO),
        HomeLayoutSpec(3, "التطبيقات المعكوسة", "شبكة التطبيقات في المقدمة ثم بطاقات المعلومات أسفلها", false,
            listOf(header, grid, cards, matches), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), 220, HomeLayoutTemplate.APP_FIRST),
        HomeLayoutSpec(TV_INDEX, "مرجع التلفزيون", "شبكة تطبيقات وعمود بطاقات رأسي داخل تقسيم جانبي، ثم المباريات", true,
            listOf(header, cards, apps, matches), listOf(HomeCard.WEATHER to 1.15f, HomeCard.PRAYER to 1.35f), template = HomeLayoutTemplate.TV_REFERENCE),
        HomeLayoutSpec(5, "شبكة التطبيقات أولاً", "شبكة تطبيقات كبيرة متعددة الأعمدة مع بيانات مختصرة أسفلها", false,
            listOf(header, grid, cards), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), 190, HomeLayoutTemplate.APP_GRID_FIRST),
        HomeLayoutSpec(6, "ساعة وخلفية", "ساعة ضخمة فوق صف التطبيقات، والقنوات المفضلة عند توفرها فقط", false,
            listOf(header, apps, channels), emptyList(), template = HomeLayoutTemplate.CLOCK_ONLY),
        HomeLayoutSpec(7, "الطقس والصلاة رأسيًا", "بطاقة الطقس فوق بطاقة الصلاة في عمودين رأسيين", false,
            listOf(header, cards, apps), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), 260, HomeLayoutTemplate.WEATHER_PRAYER_STACK),
        HomeLayoutSpec(8, "سينمائي", "رف القنوات في المقدمة ثم جدار تطبيقات بأيقونات كبيرة", false,
            listOf(header, channels, grid), emptyList(), template = HomeLayoutTemplate.CINEMA),
        HomeLayoutSpec(9, "البطاقات في الأسفل", "التطبيقات والمباريات في أعلى المساحة وبطاقات الحالة أسفلها", false,
            listOf(header, apps, matches, cards), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), 220, HomeLayoutTemplate.CARDS_BOTTOM),
        HomeLayoutSpec(10, "تقسيم الساعة والتطبيقات", "عمود معلومات رأسي مقابل عمود تطبيقات مستقل", false,
            listOf(header, cards, apps), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), template = HomeLayoutTemplate.CLOCK_APPS_SPLIT),
        HomeLayoutSpec(11, "استعراض البطاقات", "بطاقة الطقس وبطاقة الصلاة في لوحتين متساويتين ثم شبكة تطبيقات", false,
            listOf(header, cards, grid), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), 410, HomeLayoutTemplate.CARD_SHOWCASE),
        HomeLayoutSpec(12, "واجهة مضغوطة", "رأس منخفض الارتفاع وشبكة تطبيقات كثيفة وبطاقات صغيرة", false,
            listOf(header, grid, cards), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), 170, HomeLayoutTemplate.COMPACT),
        HomeLayoutSpec(13, "الطقس البارز", "لوحة طقس عريضة تقابل بطاقة صلاة أصغر في تقسيم غير متساوٍ", false,
            listOf(header, cards, apps), listOf(HomeCard.WEATHER to 2.4f, HomeCard.PRAYER to 0.9f), 400, HomeLayoutTemplate.WEATHER_HERO),
        HomeLayoutSpec(14, "المباريات أولاً", "المباريات والفرق المختارة في المقدمة ثم التطبيقات والمعلومات", false,
            listOf(header, matches, grid, cards), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), 200, HomeLayoutTemplate.MATCHES_FIRST),

        // قوالب قديمة تبقى قابلة للقراءة إذا كانت محفوظة في إعدادات جهاز قديم، ولا تظهر كخيارات جديدة.
        HomeLayoutSpec(15, "صلاة مختصرة", "قالب قديم محفوظ للتوافق: بطاقة صلاة مختصرة وشبكة التطبيقات", false,
            listOf(header, cards, grid), listOf(HomeCard.PRAYER to 1f), 250, HomeLayoutTemplate.PRAYER_MINIMAL),
        HomeLayoutSpec(16, "رف القنوات", "قالب قديم محفوظ للتوافق: رف القنوات ثم التطبيقات", false,
            listOf(header, channels, apps), emptyList(), template = HomeLayoutTemplate.CHANNELS_RACK),
        HomeLayoutSpec(17, "عمودان", "عمود للقنوات وعمود للطقس والصلاة، ثم شبكة تطبيقات أسفل التقسيم", false,
            listOf(header, channels, cards, grid), listOf(HomeCard.WEATHER to 1f, HomeCard.PRAYER to 1f), template = HomeLayoutTemplate.SPLIT_COLUMNS),
        HomeLayoutSpec(18, "لوحة رياضية", "المباريات في عمود واسع والتطبيقات في عمود جانبي ثم القنوات", false,
            listOf(header, matches, grid, channels), emptyList(), template = HomeLayoutTemplate.SPORTS_BOARD),
        HomeLayoutSpec(19, "المفضلة الشبكية", "شبكة التطبيقات أولاً ثم شريط الفرق والمباريات المختارة", false,
            listOf(header, grid, matches), emptyList(), template = HomeLayoutTemplate.FAVORITES_GRID),

        HomeLayoutSpec(20, "شبكة التطبيقات", "شبكة واسعة بستة أعمدة مع رأس مدمج وقائمة جانبية", false,
            listOf(header, grid), emptyList(), template = HomeLayoutTemplate.APP_GRID),
        HomeLayoutSpec(21, "القنوات أولاً", "رف القنوات المفضلة أعلى شبكة التطبيقات", false,
            listOf(header, channels, grid), emptyList(), template = HomeLayoutTemplate.CHANNELS_FIRST),
        HomeLayoutSpec(22, "مركز المباريات", "المباريات ثم بطاقة طقس مختصرة ثم صف التطبيقات الأفقي", false,
            listOf(header, matches, cards, apps), listOf(HomeCard.WEATHER to 1f), 190, HomeLayoutTemplate.MATCH_CENTER),
        HomeLayoutSpec(23, "لوحة الصلاة الطويلة", "مواقيت الصلاة في لوحة ممتدة بعرض الشاشة وتحتها التطبيقات", false,
            listOf(header, cards, grid), listOf(HomeCard.PRAYER to 1f), 430, HomeLayoutTemplate.PRAYER_LONG),
        // مرجع R8: ثلاث بطاقات متجاورة (بث مباشر، مباراة، صلاة) فوق شريط التطبيقات.
        HomeLayoutSpec(24, "لوحة الغسق", "خلفية غروب ظاهرة وثلاث بطاقات متساوية: بث مباشر ومباراة واحدة والصلاة، ثم صف التطبيقات", false,
            listOf(header, cards, matches, apps), listOf(HomeCard.PRAYER to 1f), 400, HomeLayoutTemplate.DUSK_BOARD),
        // مرجع R9: شعار واسم التطبيق والإصدار في الأعلى، بطاقتا الطقس والصلاة، ثم بلاطات التطبيقات والمباريات.
        HomeLayoutSpec(25, "لوحة عبيدة", "شعار دائري وأزرار معنونة، بطاقتا الطقس والصلاة، شبكة تطبيقات فعلية ثم المباريات المفضلة", false,
            listOf(header, cards, grid, matches), listOf(HomeCard.WEATHER to 1.1f, HomeCard.PRAYER to 1.3f), 400, HomeLayoutTemplate.DASHBOARD)
    )

    /** التخطيطات 0–14 و20–25 هي الخيارات الظاهرة في معرض التخطيطات. */
    val featured: List<HomeLayoutSpec> = all.filter { it.index <= 14 || it.index in 20..25 }
    val featuredIndices: Set<Int> = featured.map { it.index }.toSet()

    fun isFeatured(index: Int): Boolean = index in featuredIndices
    fun isValid(index: Int): Boolean = all.any { it.index == index }
    fun get(index: Int): HomeLayoutSpec = all.firstOrNull { it.index == index } ?: all.first { it.index == TV_INDEX }
}
