package com.uobidah.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.uobidah.launcher.core.Contrast
import java.util.Locale

/**
 * بطاقات المباريات: 10 تصميمات للسطح × 10 لوحات ألوان متناسقة × 10 تخطيطات للمحتوى.
 * الثلاثة مستقلة ويمكن الجمع بينها (Prefs.matchCardDesign / matchCardPalette / matchCardLayout).
 * كل لوحة ألوان مجموعة كاملة مضبوطة معًا (خلفية، إطار، تمييز، نص، وألوان الحالة) حتى لا تتداخل الألوان.
 */

class MatchPalette(
    val name: String,
    val bgA: Int,
    val bgB: Int,
    val border: Int,
    val accent: Int,
    val secondary: Int,
    val text: Int,
    val dim: Int,
    val up: Int,
    val live: Int,
    val fin: Int,
    val post: Int,
    val cancel: Int,
    val light: Boolean
) {
    /** لون النص فوق خلفية التمييز. */
    val onAccent: Int get() = if (light) Color.WHITE else bgB
}

object MatchCardPalettes {
    private fun c(s: String) = Color.parseColor(s)

    private fun dark(
        name: String, a: String, b: String, border: String, accent: String, second: String,
        text: String, dim: String, fin: String = "#FB7185"
    ) = MatchPalette(
        name, c(a), c(b), c(border), c(accent), c(second), c(text), c(dim),
        up = c("#4ADE80"), live = c("#FACC15"), fin = c(fin), post = c("#FDBA74"), cancel = c("#94A3B8"), light = false
    )

    private fun light(
        name: String, a: String, b: String, border: String, accent: String, second: String,
        text: String, dim: String
    ) = MatchPalette(
        name, c(a), c(b), c(border), c(accent), c(second), c(text), c(dim),
        up = c("#15803D"), live = c("#A16207"), fin = c("#BE123C"), post = c("#C2410C"), cancel = c("#475569"), light = true
    )

    val all: List<MatchPalette> = listOf(
        // 0 الأهلي: عنابي عميق + عاجي (الأحمر للزخارف فقط حتى لا يختلط بلون «منتهية»)
        dark("الأهلي عنابي", "#3B0A14", "#14040A", "#7A1F2E", "#FDEBD3", "#E11D2E", "#FFF7F0", "#E3B5B5", fin = "#FDA4AF"),
        // 1 فحمي وفضي
        dark("فحمي فضي", "#1F2128", "#08090C", "#3F4452", "#CBD5E1", "#64748B", "#F8FAFC", "#9AA4B5"),
        // 2 بحري
        dark("بحري سماوي", "#0B2E3D", "#041320", "#176B80", "#67E8F9", "#0891B2", "#ECFEFF", "#8FC3CF"),
        // 3 أزرق ملكي
        dark("أزرق ملكي", "#0E2257", "#040B24", "#2F5BC4", "#93C5FD", "#3B82F6", "#F1F6FF", "#9DB2DD"),
        // 4 بنفسجي
        dark("بنفسجي ليلي", "#2A1650", "#0E0722", "#6B45C4", "#C4B5FD", "#8B5CF6", "#FAF7FF", "#B7A8DD"),
        // 5 نحاسي
        dark("نحاسي دافئ", "#2F1C12", "#100805", "#8A4B2A", "#FDBA74", "#EA580C", "#FFF8F1", "#D2AE96"),
        // 6 توتي
        dark("توتي وردي", "#3C0D31", "#16040F", "#9B2F7E", "#F9A8D4", "#DB2777", "#FFF5FA", "#DDA6C4"),
        // 7 صحراوي
        dark("رملي صحراوي", "#2E2718", "#0F0C07", "#7C6A3A", "#E7D3A3", "#A16207", "#FFFBF0", "#C9BA93"),
        // 8 فاتح: نعناعي
        light("نعناعي فاتح", "#EAF7F2", "#CDE8DE", "#6FB7A3", "#0F766E", "#14B8A6", "#0B2B24", "#3F6A60"),
        // 9 فاتح: عاجي بحري
        light("عاجي وكحلي", "#FBF4E6", "#EEDFC2", "#C7A66A", "#1E3A8A", "#B45309", "#22190A", "#6B5A3A")
    )

    val names: List<String> = all.map { it.name }
    fun get(i: Int): MatchPalette = all[i.coerceIn(0, all.size - 1)]

    /**
     * نسخة مضبوطة التباين من اللوحة فوق خلفية البطاقة الفعلية: أي لون نص أو حالة أضعف من 4.5:1
     * يُغمَّق/يُفتَّح تلقائياً، فلا يختلط النص مع الخلفية مهما كان تصميم البطاقة.
     */
    fun fitted(p: MatchPalette): MatchPalette {
        val bg = Ui.over(Ui.withAlpha(p.bgB, 128), p.bgA)
        fun f(c: Int) = Contrast.ensure(c, bg)
        return MatchPalette(
            p.name, p.bgA, p.bgB, p.border, Contrast.ensure(p.accent, bg), Contrast.ensure(p.secondary, bg),
            f(p.text), f(p.dim), f(p.up), f(p.live), f(p.fin), f(p.post), f(p.cancel), p.light
        )
    }
}

object MatchCardDesigns {
    val names = listOf(
        "هادئ متدرج", "متدرج ناعم", "إطار مزدوج", "شريط علوي", "شريط جانبي",
        "مسطح", "نيون", "قطري", "حاد", "كبسولة"
    )
    val descriptions = listOf(
        "تدرج هادئ وحد رفيع",
        "سطح معتم بتدرج هادئ",
        "إطاران متداخلان بلون التمييز",
        "شريط بلون حالة المباراة في الأعلى",
        "شريط بلون حالة المباراة على الجانب",
        "لون واحد بدون حدود",
        "خلفية داكنة وحد متوهج",
        "تدرج قطري بلمسة اللون الثانوي",
        "زوايا حادة وحد دقيق",
        "زوايا دائرية كبيرة"
    )
    val count = names.size

    private fun radiusDp(design: Int): Float = when (design) {
        1 -> 24f; 2 -> 22f; 5 -> 14f; 6 -> 20f; 7 -> 22f; 8 -> 6f; 9 -> 40f; else -> 20f
    }

    private fun mix(top: Int, base: Int, alpha: Int) = Ui.over(Ui.withAlpha(top, alpha), base)

    private fun grad(colors: IntArray, r: Float, strokeW: Int = 0, strokeC: Int = 0): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = r
            if (strokeW > 0) setStroke(strokeW, strokeC)
        }

    private fun base(ctx: Context, design: Int, p: MatchPalette, state: Int): Drawable {
        val r = Ui.p(ctx, radiusDp(design)).toFloat()
        return when (design) {
            1 -> grad(intArrayOf(p.bgA, p.bgB), r, Ui.p(ctx, 1f), p.border)
            2 -> {
                val outer = grad(intArrayOf(p.bgA, p.bgB), r, Ui.p(ctx, 2f), Ui.withAlpha(p.accent, 170))
                val inner = Ui.round(Color.TRANSPARENT, r - Ui.p(ctx, 6f), Ui.p(ctx, 1f), Ui.withAlpha(p.accent, 95))
                val l = LayerDrawable(arrayOf<Drawable>(outer, inner))
                val i = Ui.p(ctx, 7f)
                l.setLayerInset(1, i, i, i, i)
                l
            }
            3 -> {
                val b = grad(intArrayOf(p.bgA, p.bgB), r, Ui.p(ctx, 1f), p.border)
                val bar = Ui.round(state, Ui.p(ctx, 3f).toFloat())
                val l = LayerDrawable(arrayOf<Drawable>(b, bar))
                l.setLayerHeight(1, Ui.p(ctx, 6f))
                l.setLayerGravity(1, Gravity.TOP or Gravity.FILL_HORIZONTAL)
                val side = Ui.p(ctx, 22f)
                l.setLayerInset(1, side, Ui.p(ctx, 1f), side, 0)
                l
            }
            4 -> {
                val b = grad(intArrayOf(p.bgA, p.bgB), r, Ui.p(ctx, 1f), p.border)
                val bar = Ui.round(state, Ui.p(ctx, 4f).toFloat())
                val l = LayerDrawable(arrayOf<Drawable>(b, bar))
                l.setLayerWidth(1, Ui.p(ctx, 8f))
                l.setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
                val v = Ui.p(ctx, 18f)
                l.setLayerInset(1, Ui.p(ctx, 1f), v, 0, v)
                l
            }
            5 -> Ui.round(mix(p.bgA, p.bgB, 235), r)
            6 -> {
                val glow = Ui.round(Ui.withAlpha(p.accent, 42), r + Ui.p(ctx, 4f))
                val core = Ui.round(Ui.over(Ui.withAlpha(p.bgB, 250), Color.BLACK), r, Ui.p(ctx, 2.5f), p.accent)
                val l = LayerDrawable(arrayOf<Drawable>(glow, core))
                val i = Ui.p(ctx, 4f)
                l.setLayerInset(1, i, i, i, i)
                l
            }
            7 -> grad(intArrayOf(mix(p.secondary, p.bgA, 120), p.bgA, p.bgB), r, Ui.p(ctx, 1.5f), p.border)
            8 -> grad(intArrayOf(p.bgA, p.bgB), r, Ui.p(ctx, 1f), Ui.withAlpha(p.accent, 130))
            9 -> grad(intArrayOf(p.bgA, p.bgB), r, Ui.p(ctx, 2f), p.border)
            else -> grad(intArrayOf(p.bgA, p.bgB), r, Ui.p(ctx, 1.5f), p.border)
        }
    }

    /** خلفية البطاقة مع حالة التركيز (هالة + إطار بلون التمييز، تعمل مع كل التصاميم). */
    fun background(ctx: Context, design: Int, p: MatchPalette, state: Int): Drawable {
        val d = design.coerceIn(0, count - 1)
        val r = Ui.p(ctx, radiusDp(d)).toFloat()
        val inset = Ui.p(ctx, 5f)
        val halo = Ui.round(Ui.withAlpha(p.accent, 80), r + inset)
        val ring = Ui.round(Color.TRANSPARENT, r, Ui.p(ctx, 4f), p.accent)
        val focused = LayerDrawable(arrayOf<Drawable>(halo, base(ctx, d, p, state), ring))
        focused.setLayerInset(1, inset, inset, inset, inset)
        focused.setLayerInset(2, inset, inset, inset, inset)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), base(ctx, d, p, state))
        }
    }
}

object MatchCardLayouts {
    val names = listOf(
        "كلاسيكي", "سطر عريض", "شعارات كبيرة", "النتيجة أولاً", "فريقان مكدسان",
        "شريط التاريخ", "تذكرة", "مصغّر", "لوحة نتائج", "دوائر وكبسولة"
    )
    val descriptions = listOf(
        "عنوان وحالة، ثم الفريقان والنتيجة في الوسط",
        "الفريقان بأسمائهم بجانب الشعارات في صف واحد",
        "شعارات كبيرة والنتيجة صغيرة بينهما",
        "النتيجة بحجم كبير في الأعلى والفريقان تحتها",
        "كل فريق في سطر ونتيجته في آخر السطر",
        "كتلة للتاريخ والوقت بجانب المباراة",
        "شكل تذكرة بفاصل منقط وذيل للتفاصيل",
        "تصميم هادئ بأقل عدد من العناصر",
        "لوحة نتائج داخلية بخانات للأهداف",
        "شعارات داخل دوائر ونتيجة داخل كبسولة"
    )
    val count = names.size
}

/** بيانات بطاقة واحدة، مستقلة عن مصدر الشاشة (الرئيسية أو المباريات). */
class MatchCardModel(
    val kind: String,
    val title: String,
    val match: Match?,
    val teamName: String,
    val teamBadge: String,
    val teamOk: Boolean,
    val timezone: String
)

object MatchCards {
    const val DEFAULT_PALETTE = 0

    fun statePalette(p: MatchPalette, s: MatchState?): Int = when (s) {
        MatchState.LIVE -> p.live
        MatchState.FINISHED -> p.fin
        MatchState.UPCOMING -> p.up
        MatchState.POSTPONED -> p.post
        MatchState.CANCELED -> p.cancel
        MatchState.SUSPENDED -> p.post
        MatchState.UNKNOWN -> Ui.withAlpha(p.dim, 200)
        null -> Ui.withAlpha(p.dim, 200)
    }

    /**
     * يبني البطاقة كاملة: الخلفية، الحشو، المحتوى حسب التخطيط. المستدعي يضيف النقر ومعاملات التخطيط.
     * compact = البطاقة الصغيرة في الشاشة الرئيسية (ارتفاع محدود).
     */
    fun build(ctx: Context, prefs: Prefs, m: MatchCardModel, compact: Boolean): LinearLayout {
        val pal = MatchCardPalettes.fitted(MatchCardPalettes.get(prefs.matchCardPalette))
        val design = prefs.matchCardDesign.coerceIn(0, MatchCardDesigns.count - 1)
        val layout = prefs.matchCardLayout.coerceIn(0, MatchCardLayouts.count - 1)
        val b = Builder(ctx, pal, m, compact)
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER
        card.clipChildren = false
        card.background = MatchCardDesigns.background(ctx, design, pal, b.stateColor)
        card.isFocusable = true
        val padH = if (compact) 14f else 18f
        val padV = if (compact) 8f else 12f
        card.setPadding(Ui.p(ctx, padH), Ui.p(ctx, padV), Ui.p(ctx, padH), Ui.p(ctx, padV))
        if (compact) b.compactInto(card, layout) else b.fullInto(card, layout)
        return card
    }

    private class Builder(val ctx: Context, val pal: MatchPalette, val m: MatchCardModel, val compact: Boolean) {
        val match = m.match
        val now = System.currentTimeMillis()
        val st: MatchState? = match?.state(now)
        val stateColor = statePalette(pal, st)
        val hasScore = match?.hasDisplayableScore(now) == true
        val homeName = match?.home ?: m.teamName.ifBlank { "—" }
        val awayName = match?.away ?: "—"
        val homeBadge = match?.homeBadge ?: if (match == null) m.teamBadge else ""
        val awayBadge = match?.awayBadge ?: ""
        val scale = if (compact) 0.8f else 1f
        // text on a 10-foot screen must stay readable: the compact card shrinks shapes more than text
        val textScale = if (compact) 0.92f else 1f

        fun d(v: Float) = Ui.p(ctx, v * scale)
        fun lbl(t: String, size: Float, color: Int, bold: Boolean = false): TextView =
            Ui.label(ctx, t, size * textScale, color, bold)

        fun badge(name: String, url: String, size: Int): View {
            val s = (size * scale).toInt().coerceAtLeast(28)
            return TeamBadgeUi.create(ctx, name, url, s, pal.accent)
        }

        fun px(v: Float) = Ui.p(ctx, v)

        private fun pillText(): String = when {
            match == null && !m.teamOk -> "غير متاح"
            match == null && m.kind == "live" -> "لا يوجد مباشر"
            match == null -> "لا توجد بيانات"
            st == MatchState.LIVE -> "● جارية"
            st == MatchState.FINISHED -> "■ منتهية"
            st == MatchState.POSTPONED -> "⏸ مؤجلة"
            st == MatchState.CANCELED -> "✕ ملغاة"
            st == MatchState.SUSPENDED -> "⏸ متوقفة مؤقتًا"
            st == MatchState.UNKNOWN -> "؟ حالة غير مؤكدة"
            else -> "▦ قادمة"
        }

        fun pill(size: Float = 17f): TextView {
            val t = lbl(pillText(), size, stateColor, true)
            t.gravity = Gravity.CENTER
            t.setPadding(d(10f), d(4f), d(10f), d(4f))
            t.background = Ui.round(Ui.withAlpha(stateColor, if (pal.light) 38 else 50), d(8f).toFloat())
            return t
        }

        fun titleView(size: Float = 19f): TextView = lbl(m.title, size, pal.dim, true)

        fun dateText(fmt: String): String =
            if (match != null && match.startMs > 0)
                DateUtil.formatInstant(match.startMs, m.timezone, fmt, Locale("ar", "EG"))
            else ""

        fun timeText(): String =
            if (match != null && match.startMs > 0)
                DateUtil.formatInstant(match.startMs, m.timezone, "HH:mm", Locale.US)
            else ""

        fun minuteText(): String = match?.let { x -> x.progress.ifBlank { x.status } } ?: ""

        fun noScoreYet(): Boolean = !hasScore

        /** الأهداف كخانتين منفصلتين لتبقى أمام فريقيها مهما كان اتجاه الواجهة. */
        fun scoreRow(size: Float, boxed: Boolean = false): LinearLayout {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER
            if (noScoreYet()) {
                row.addView(lbl("VS", size * 0.75f, if (match == null) pal.dim else pal.text, true))
                return row
            }
            fun cell(v: Int): TextView {
                val t = lbl(v.toString(), size, pal.text, true)
                t.gravity = Gravity.CENTER
                if (boxed) {
                    t.setPadding(d(14f), d(4f), d(14f), d(4f))
                    t.background = Ui.round(Ui.withAlpha(pal.accent, if (pal.light) 40 else 55), d(10f).toFloat())
                }
                return t
            }
            row.addView(cell(match!!.homeScore!!))
            row.addView(lbl(if (boxed) "" else "  -  ", size * 0.8f, pal.dim, true))
            if (boxed) row.addView(View(ctx), LinearLayout.LayoutParams(d(8f), 1))
            row.addView(cell(match.awayScore!!))
            return row
        }

        /** تحت النتيجة: الدقيقة للمباشرة، وإلا التاريخ والوقت. */
        fun infoColumn(timeSize: Float = 23f, dateSize: Float = 16f): LinearLayout {
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER_HORIZONTAL
            if (match == null) return col
            if (m.kind == "live" || st == MatchState.LIVE) {
                val mn = minuteText()
                if (mn.isNotBlank()) col.addView(lbl(mn, 18f, pal.live, true))
            } else if (match.startMs > 0) {
                col.addView(lbl(dateText("EEE d MMM"), dateSize, pal.dim))
                if (m.kind == "next") col.addView(lbl(timeText(), timeSize, pal.text, true))
            }
            return col
        }

        fun footerText(): String {
            val league = match?.league?.takeIf { it.isNotBlank() }
                ?: if (!m.teamOk) "تعذر جلب بيانات هذا النادي" else "لا توجد مباراة ضمن الفترة"
            return league + if (match?.venue?.isNotBlank() == true) "  •  🏟 ${match.venue}" else ""
        }

        fun footer(size: Float = 16f): TextView {
            val f = lbl(footerText(), size, pal.dim)
            f.gravity = Gravity.CENTER
            Ui.ellipsize(f)
            return f
        }

        fun teamCol(name: String, url: String, badgeSize: Int, nameSize: Float = 17f, lines: Int = 2): LinearLayout {
            val col = LinearLayout(ctx)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            val bs = d(badgeSize.toFloat()).coerceAtLeast(28)
            col.addView(badge(name, url, badgeSize), LinearLayout.LayoutParams(bs, bs))
            val tv = lbl(name, nameSize, pal.text, true)
            tv.gravity = Gravity.CENTER
            tv.maxLines = lines
            col.addView(tv, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(3f), 0, 0))
            return col
        }

        fun head(): LinearLayout {
            val h = LinearLayout(ctx)
            h.orientation = LinearLayout.HORIZONTAL
            h.layoutDirection = View.LAYOUT_DIRECTION_RTL
            h.gravity = Gravity.CENTER_VERTICAL
            h.addView(titleView(), Ui.lp(0, Ui.WRAP, 1f))
            h.addView(pill())
            return h
        }

        fun spacer(w: Int = 0, h: Int = 0): View = View(ctx).also { it.minimumWidth = w; it.minimumHeight = h }

        // ======================= التخطيطات الكاملة =======================
        fun fullInto(card: LinearLayout, layout: Int) {
            when (layout) {
                1 -> wideRow(card)
                2 -> bigBadges(card)
                3 -> scoreFirst(card)
                4 -> stacked(card)
                5 -> dateStrip(card)
                6 -> ticket(card)
                7 -> minimal(card)
                8 -> scoreboard(card)
                9 -> circles(card)
                else -> classic(card)
            }
        }

        private fun classic(card: LinearLayout) {
            card.addView(head(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(5f)))
            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.HORIZONTAL
            mid.gravity = Gravity.CENTER
            mid.addView(teamCol(homeName, homeBadge, 76), Ui.lp(0, Ui.WRAP, 1f))
            val center = LinearLayout(ctx)
            center.orientation = LinearLayout.VERTICAL
            center.gravity = Gravity.CENTER
            center.addView(scoreRow(if (m.kind == "live") 54f else 42f))
            center.addView(infoColumn())
            mid.addView(center, Ui.lpm(0, Ui.WRAP, 1.25f, d(6f), 0, d(6f), 0))
            mid.addView(teamCol(awayName, awayBadge, 76), Ui.lp(0, Ui.WRAP, 1f))
            card.addView(mid, Ui.lp(Ui.MATCH, 0, 1f))
            card.addView(footer(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(4f), 0, 0))
        }

        private fun wideRow(card: LinearLayout) {
            card.addView(head(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(6f)))
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(badge(homeName, homeBadge, 62), Ui.lp(d(62f), d(62f)))
            val hn = lbl(homeName, 18f, pal.text, true); hn.maxLines = 2; hn.gravity = Gravity.CENTER
            row.addView(hn, Ui.lpm(0, Ui.WRAP, 1f, d(6f), 0, d(6f), 0))
            val c = LinearLayout(ctx); c.orientation = LinearLayout.VERTICAL; c.gravity = Gravity.CENTER
            c.addView(scoreRow(40f)); c.addView(infoColumn(20f, 15f))
            row.addView(c, Ui.lp(Ui.WRAP, Ui.WRAP))
            val an = lbl(awayName, 18f, pal.text, true); an.maxLines = 2; an.gravity = Gravity.CENTER
            row.addView(an, Ui.lpm(0, Ui.WRAP, 1f, d(6f), 0, d(6f), 0))
            row.addView(badge(awayName, awayBadge, 62), Ui.lp(d(62f), d(62f)))
            card.addView(row, Ui.lp(Ui.MATCH, 0, 1f))
            card.addView(footer(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(4f), 0, 0))
        }

        private fun bigBadges(card: LinearLayout) {
            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.HORIZONTAL
            mid.gravity = Gravity.CENTER
            mid.addView(teamCol(homeName, homeBadge, 104, 16f), Ui.lp(0, Ui.WRAP, 1f))
            val c = LinearLayout(ctx); c.orientation = LinearLayout.VERTICAL; c.gravity = Gravity.CENTER
            c.addView(scoreRow(34f))
            c.addView(pill(15f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, d(4f), 0, d(2f)))
            c.addView(infoColumn(20f, 14f))
            mid.addView(c, Ui.lpm(0, Ui.WRAP, 0.9f, d(4f), 0, d(4f), 0))
            mid.addView(teamCol(awayName, awayBadge, 104, 16f), Ui.lp(0, Ui.WRAP, 1f))
            card.addView(mid, Ui.lp(Ui.MATCH, 0, 1f))
            card.addView(footer(15f), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(2f), 0, 0))
        }

        private fun scoreFirst(card: LinearLayout) {
            val top = LinearLayout(ctx)
            top.orientation = LinearLayout.HORIZONTAL
            top.gravity = Gravity.CENTER
            top.addView(titleView(17f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, d(12f), 0))
            top.addView(pill(15f))
            card.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(2f)))
            card.addView(scoreRow(66f), Ui.lp(Ui.WRAP, Ui.WRAP))
            card.addView(infoColumn(20f, 15f))
            val names = LinearLayout(ctx)
            names.orientation = LinearLayout.HORIZONTAL
            names.gravity = Gravity.CENTER_VERTICAL
            fun side(n: String, u: String): LinearLayout {
                val s = LinearLayout(ctx); s.orientation = LinearLayout.HORIZONTAL; s.gravity = Gravity.CENTER
                s.addView(badge(n, u, 46), Ui.lp(d(46f), d(46f)))
                val t = lbl(n, 17f, pal.text, true); t.maxLines = 1; Ui.ellipsize(t)
                s.addView(t, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, d(8f), 0, 0, 0))
                return s
            }
            names.addView(side(homeName, homeBadge), Ui.lp(0, Ui.WRAP, 1f))
            names.addView(side(awayName, awayBadge), Ui.lp(0, Ui.WRAP, 1f))
            card.addView(names, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(6f), 0, 0))
            card.addView(footer(15f), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(4f), 0, 0))
        }

        private fun stacked(card: LinearLayout) {
            card.addView(head(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(6f)))
            fun teamRow(n: String, u: String, score: Int?): LinearLayout {
                val r = LinearLayout(ctx)
                r.orientation = LinearLayout.HORIZONTAL
                r.gravity = Gravity.CENTER_VERTICAL
                r.addView(badge(n, u, 54), Ui.lp(d(54f), d(54f)))
                val t = lbl(n, 22f, pal.text, true); t.maxLines = 1; Ui.ellipsize(t)
                r.addView(t, Ui.lpm(0, Ui.WRAP, 1f, d(10f), 0, d(10f), 0))
                r.addView(lbl(score?.toString() ?: "–", 42f, if (score == null) pal.dim else pal.text, true))
                return r
            }
            val hs = if (hasScore) match?.homeScore else null
            val aws = if (hasScore) match?.awayScore else null
            card.addView(teamRow(homeName, homeBadge, hs), Ui.lp(Ui.MATCH, 0, 1f))
            card.addView(View(ctx).also { it.setBackgroundColor(Ui.withAlpha(pal.dim, 60)) }, Ui.lpm(Ui.MATCH, 1, 0f, d(60f), 0, 0, 0))
            card.addView(teamRow(awayName, awayBadge, aws), Ui.lp(Ui.MATCH, 0, 1f))
            val sub = if (m.kind == "live") minuteText() else (dateText("EEE d MMM") + (if (m.kind == "next") "  •  " + timeText() else ""))
            val f = lbl((if (sub.isNotBlank()) "$sub  •  " else "") + footerText(), 15f, pal.dim)
            Ui.ellipsize(f)
            card.addView(f, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(4f), 0, 0))
        }

        private fun dateStrip(card: LinearLayout) {
            card.orientation = LinearLayout.HORIZONTAL
            val block = LinearLayout(ctx)
            block.orientation = LinearLayout.VERTICAL
            block.gravity = Gravity.CENTER
            block.setPadding(d(8f), d(8f), d(8f), d(8f))
            block.background = Ui.round(Ui.withAlpha(pal.accent, if (pal.light) 34 else 46), d(16f).toFloat())
            if (match != null && match.startMs > 0) {
                block.addView(lbl(dateText("d"), 56f, pal.accent, true))
                block.addView(lbl(dateText("MMM"), 20f, pal.text, true))
                block.addView(lbl(timeText(), 22f, pal.dim, true))
            } else {
                block.addView(lbl("⚽", 48f, pal.accent, true))
                block.addView(lbl("—", 20f, pal.dim))
            }
            card.addView(block, Ui.lpm(d(112f), Ui.MATCH, 0f, 0, 0, d(14f), 0))
            val right = LinearLayout(ctx)
            right.orientation = LinearLayout.VERTICAL
            right.gravity = Gravity.CENTER_VERTICAL
            right.addView(head(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(6f)))
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER
            row.addView(teamCol(homeName, homeBadge, 58, 16f, 1), Ui.lp(0, Ui.WRAP, 1f))
            row.addView(scoreRow(38f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, d(6f), 0, d(6f), 0))
            row.addView(teamCol(awayName, awayBadge, 58, 16f, 1), Ui.lp(0, Ui.WRAP, 1f))
            right.addView(row)
            right.addView(footer(15f), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(4f), 0, 0))
            card.addView(right, Ui.lp(0, Ui.MATCH, 1f))
        }

        private fun ticket(card: LinearLayout) {
            card.addView(head(), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(6f)))
            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.HORIZONTAL
            mid.gravity = Gravity.CENTER
            mid.addView(teamCol(homeName, homeBadge, 64, 17f), Ui.lp(0, Ui.WRAP, 1f))
            mid.addView(scoreRow(38f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, d(4f), 0, d(4f), 0))
            mid.addView(teamCol(awayName, awayBadge, 64, 17f), Ui.lp(0, Ui.WRAP, 1f))
            card.addView(mid, Ui.lp(Ui.MATCH, 0, 1f))
            val line = View(ctx)
            line.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            line.background = GradientDrawable().apply {
                shape = GradientDrawable.LINE
                setStroke(d(2f).coerceAtLeast(1), Ui.withAlpha(pal.dim, 140), d(8f).toFloat(), d(6f).toFloat())
            }
            card.addView(line, Ui.lpm(Ui.MATCH, d(4f).coerceAtLeast(3), 0f, 0, d(6f), 0, d(6f)))
            val stub = LinearLayout(ctx)
            stub.orientation = LinearLayout.HORIZONTAL
            stub.gravity = Gravity.CENTER_VERTICAL
            val whenText = when {
                m.kind == "live" -> minuteText()
                match != null && match.startMs > 0 -> dateText("EEE d MMM") + "  " + timeText()
                else -> ""
            }
            stub.addView(lbl(whenText, 18f, pal.text, true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, d(12f), 0))
            val lg = lbl(footerText(), 15f, pal.dim); Ui.ellipsize(lg)
            stub.addView(lg, Ui.lp(0, Ui.WRAP, 1f))
            card.addView(stub, Ui.lp(Ui.MATCH, Ui.WRAP))
        }

        private fun minimal(card: LinearLayout) {
            val tag = lbl(pillText(), 16f, stateColor, true)
            tag.gravity = Gravity.CENTER
            card.addView(tag, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, 0, d(4f)))
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER
            row.addView(badge(homeName, homeBadge, 56), Ui.lp(d(56f), d(56f)))
            row.addView(scoreRow(48f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, d(18f), 0, d(18f), 0))
            row.addView(badge(awayName, awayBadge, 56), Ui.lp(d(56f), d(56f)))
            card.addView(row)
            val names = lbl("$homeName   •   $awayName", 17f, pal.text, true)
            names.gravity = Gravity.CENTER; Ui.ellipsize(names)
            card.addView(names, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(5f), 0, 0))
            val sub = if (m.kind == "live") minuteText() else dateText("EEE d MMM") + (if (m.kind == "next") "  •  " + timeText() else "")
            if (sub.isNotBlank()) card.addView(lbl(sub, 15f, pal.dim).also { it.gravity = Gravity.CENTER }, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(2f), 0, 0))
        }

        private fun scoreboard(card: LinearLayout) {
            val badges = LinearLayout(ctx)
            badges.orientation = LinearLayout.HORIZONTAL
            badges.gravity = Gravity.CENTER
            badges.addView(badge(homeName, homeBadge, 54), Ui.lp(0, d(54f), 1f))
            badges.addView(spacer(d(110f), 1))
            badges.addView(badge(awayName, awayBadge, 54), Ui.lp(0, d(54f), 1f))
            card.addView(badges, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(6f)))
            val panel = LinearLayout(ctx)
            panel.orientation = LinearLayout.HORIZONTAL
            panel.gravity = Gravity.CENTER_VERTICAL
            panel.setPadding(d(14f), d(8f), d(14f), d(8f))
            panel.background = Ui.round(Ui.withAlpha(if (pal.light) Color.WHITE else Color.BLACK, if (pal.light) 110 else 95), d(14f).toFloat(), d(1f).coerceAtLeast(1), Ui.withAlpha(pal.accent, 90))
            val hn = lbl(homeName, 18f, pal.text, true); hn.maxLines = 1; Ui.ellipsize(hn); hn.gravity = Gravity.CENTER
            val an = lbl(awayName, 18f, pal.text, true); an.maxLines = 1; Ui.ellipsize(an); an.gravity = Gravity.CENTER
            panel.addView(hn, Ui.lp(0, Ui.WRAP, 1f))
            panel.addView(scoreRow(44f, boxed = true), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, d(8f), 0, d(8f), 0))
            panel.addView(an, Ui.lp(0, Ui.WRAP, 1f))
            card.addView(panel, Ui.lp(Ui.MATCH, Ui.WRAP))
            val bottom = LinearLayout(ctx)
            bottom.orientation = LinearLayout.HORIZONTAL
            bottom.gravity = Gravity.CENTER_VERTICAL
            bottom.addView(pill(15f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, 0, d(10f), 0))
            val f = lbl(footerText(), 15f, pal.dim); Ui.ellipsize(f)
            bottom.addView(f, Ui.lp(0, Ui.WRAP, 1f))
            card.addView(bottom, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(8f), 0, 0))
        }

        private fun circleBadge(name: String, url: String, size: Int): View {
            val box = LinearLayout(ctx)
            box.gravity = Gravity.CENTER
            val outer = d(size.toFloat()).coerceAtLeast(36)
            box.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Ui.withAlpha(if (pal.light) Color.WHITE else Color.BLACK, if (pal.light) 150 else 70))
                setStroke(d(3f).coerceAtLeast(2), Ui.withAlpha(pal.accent, 190))
            }
            val inner = (outer * 0.68f).toInt()
            box.addView(TeamBadgeUi.create(ctx, name, url, (size * scale * 0.68f).toInt().coerceAtLeast(20), pal.accent), LinearLayout.LayoutParams(inner, inner))
            box.layoutParams = LinearLayout.LayoutParams(outer, outer)
            return box
        }

        private fun capsule(size: Float): TextView {
            val t = lbl(
                if (noScoreYet()) "VS" else "${match!!.homeScore}  :  ${match.awayScore}",
                size, pal.onAccent, true
            )
            t.gravity = Gravity.CENTER
            t.setPadding(d(18f), d(6f), d(18f), d(6f))
            t.background = Ui.round(pal.accent, d(30f).toFloat())
            return t
        }

        private fun circles(card: LinearLayout) {
            val top = LinearLayout(ctx)
            top.orientation = LinearLayout.HORIZONTAL
            top.layoutDirection = View.LAYOUT_DIRECTION_RTL
            top.gravity = Gravity.CENTER_VERTICAL
            top.addView(titleView(17f), Ui.lp(0, Ui.WRAP, 1f))
            top.addView(pill(15f))
            card.addView(top, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, 0, 0, d(4f)))
            val mid = LinearLayout(ctx)
            mid.orientation = LinearLayout.HORIZONTAL
            mid.gravity = Gravity.CENTER
            fun side(n: String, u: String): LinearLayout {
                val c = LinearLayout(ctx); c.orientation = LinearLayout.VERTICAL; c.gravity = Gravity.CENTER
                c.addView(circleBadge(n, u, 96))
                val t = lbl(n, 16f, pal.text, true); t.gravity = Gravity.CENTER; t.maxLines = 1; Ui.ellipsize(t)
                c.addView(t, Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(3f), 0, 0))
                return c
            }
            mid.addView(side(homeName, homeBadge), Ui.lp(0, Ui.WRAP, 1f))
            val c = LinearLayout(ctx); c.orientation = LinearLayout.VERTICAL; c.gravity = Gravity.CENTER
            c.addView(capsule(34f))
            c.addView(infoColumn(19f, 14f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, d(4f), 0, 0))
            mid.addView(c, Ui.lpm(0, Ui.WRAP, 1.1f, d(4f), 0, d(4f), 0))
            mid.addView(side(awayName, awayBadge), Ui.lp(0, Ui.WRAP, 1f))
            card.addView(mid, Ui.lp(Ui.MATCH, 0, 1f))
            card.addView(footer(15f), Ui.lpm(Ui.MATCH, Ui.WRAP, 0f, 0, d(2f), 0, 0))
        }

        // ======================= التخطيطات المضغوطة (الشاشة الرئيسية) =======================
        fun compactInto(card: LinearLayout, layout: Int) {
            card.orientation = LinearLayout.HORIZONTAL
            when (layout) {
                2, 3, 8, 9 -> compactBadges(card)
                else -> compactNames(card)
            }
        }

        private fun compactCenter(timeSize: Float): LinearLayout {
            val c = LinearLayout(ctx)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER
            if (match != null && m.kind == "live" && hasScore) {
                c.addView(lbl(pillText(), 20f, stateColor, true))
                c.addView(scoreRow(52f))
            } else if (match != null && hasScore) {
                c.addView(lbl(pillText(), 18f, stateColor, true))
                c.addView(scoreRow(46f))
            } else {
                c.addView(lbl(dateText("EEE"), 22f, pal.dim))
                c.addView(lbl(timeText().ifBlank { "VS" }, timeSize, pal.text, true))
            }
            val lg = lbl(match?.league ?: "", 17f, pal.dim)
            Ui.ellipsize(lg); lg.gravity = Gravity.CENTER
            c.addView(lg, Ui.lpm(d(210f), Ui.WRAP, 0f, 0, d(2f), 0, 0))
            return c
        }

        private fun compactNames(card: LinearLayout) {
            card.gravity = Gravity.CENTER
            card.addView(teamCol(homeName, homeBadge, 92, 20f, 1), Ui.lp(0, Ui.WRAP, 1f))
            card.addView(compactCenter(50f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, d(8f), 0, d(8f), 0))
            card.addView(teamCol(awayName, awayBadge, 92, 20f, 1), Ui.lp(0, Ui.WRAP, 1f))
        }

        private fun compactBadges(card: LinearLayout) {
            card.gravity = Gravity.CENTER
            card.addView(circleBadge(homeName, homeBadge, 124), Ui.lp(d(124f), d(124f)))
            val c = LinearLayout(ctx)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER
            c.addView(lbl(pillText(), 18f, stateColor, true).also { it.gravity = Gravity.CENTER })
            c.addView(capsule(40f), Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, 0, d(4f), 0, d(4f)))
            val sub = if (m.kind == "live") minuteText() else (dateText("EEE") + (if (m.kind == "next") "  " + timeText() else ""))
            c.addView(lbl(sub, 20f, pal.dim).also { it.gravity = Gravity.CENTER })
            card.addView(c, Ui.lpm(Ui.WRAP, Ui.WRAP, 0f, d(12f), 0, d(12f), 0))
            card.addView(circleBadge(awayName, awayBadge, 124), Ui.lp(d(124f), d(124f)))
        }
    }
}
