package ru.dimorinny.showcasecard

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.support.annotation.ColorInt
import android.support.v4.app.Fragment
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import ru.dimorinny.showcasecard.position.Position
import ru.dimorinny.showcasecard.position.ShowCasePosition
import ru.dimorinny.showcasecard.position.ViewPosition
import ru.dimorinny.showcasecard.radius.Radius
import ru.dimorinny.showcasecard.radius.ShowCaseRadius
import ru.dimorinny.showcasecard.radius.ViewRadius
import ru.dimorinny.showcasecard.util.ActivityUtils
import ru.dimorinny.showcasecard.util.MeasuredUtils
import ru.dimorinny.showcasecard.util.NavigationBarUtils
import ru.dimorinny.showcasecard.util.ViewUtils

// TODO: public
class ShowCaseView(context: Context) : FrameLayout(context) {

    companion object {
        private const val MAX_CARD_WIDTH = 0.40
        private const val CARD_ANIMATION_PROPERTY = "translationY"
        private const val CARD_ANIMATION_DURATION = 200L
        private const val VIEW_FADE_IN_DURATION = 200L
        private const val ANIMATION_START_DELAY = 200L
    }

    private val CARD_PADDING_VERTICAL = ViewUtils.convertDpToPx(this, 16)
    private val CARD_PADDING_HORIZONTAL = ViewUtils.convertDpToPx(this, 8)
    private val CARD_TO_ARROW_OFFSET = ViewUtils.convertDpToPx(this, 25)
    private val CARD_MIN_MARGIN = ViewUtils.convertDpToPx(this, 14)

    private val CARD_ANIMATION_OFFSET = ViewUtils.convertDpToPx(this, 16)

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var dismissListener: ShowCaseViewJava.DismissListener? = null

    private var hideAnimationPerforming = false

    private lateinit var position: PointF
    lateinit var typedPosition: ShowCasePosition

    private var radius: Float = -1F
    lateinit var typedRadius: ShowCaseRadius

    private var cardRightOffset = 0
    private var cardLeftOffset = 0
    var overlayColor: Int = -1
        set (value) {
            field = value
            overlayPaint.color = value
        }

    private lateinit var cardContent: TextView

    init {
        initView()
        initPaints()
    }

    // TODO: public
    fun setContent(contentView: TextView, text: String) {
        cardContent = contentView
        cardContent.text = text
    }

    private fun initView() {
        alpha = 0F
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    private fun initPaints() {
        overlayPaint.style = Paint.Style.FILL
        circlePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private fun isTouchInCircle(touchEvent: MotionEvent): Boolean {
        val dx = Math.abs(touchEvent.x - position.x)
        val dy = Math.abs(touchEvent.y - position.y)
        return dx <= radius && dy <= radius
    }

    private fun removeFromWindow() {
        if (parent != null && parent is ViewGroup) {
            (parent as ViewGroup).removeView(this)
        }
    }

    private fun getCardWidth() =
            (width * (MAX_CARD_WIDTH + getPositionXPercentageDistanceToCenter() / 3)).toInt()

    private fun getPositionXPercentageDistanceToCenter() =
            Math.abs(position.x - width / 2.0) / width

    private fun getCardGravity(): Int {
        val vertical = if (position.y <= height / 2) Gravity.TOP else Gravity.BOTTOM
        val horizontal = if (position.x <= width / 2) Gravity.START else Gravity.END
        return vertical or horizontal
    }

    private fun cardFromTop() = position.y > height / 2
    private fun cardFromBottom() = position.y <= height / 2
    private fun cardFromRight() = position.x <= width / 2
    private fun cardFromLeft() = position.x > width / 2

    private fun getCardBackgroundDrawable(): Int =
            when {
                cardFromBottom() && cardFromLeft() -> R.drawable.background_showcase_right_top
                cardFromBottom() && cardFromRight() -> R.drawable.background_showcase_left_top
                cardFromTop() && cardFromLeft() -> R.drawable.background_showcase_right_bottom
                else -> R.drawable.background_showcase_left_bottom
            }

    private fun getCardMarginTop(): Int {
        Gravity.BOTTOM
        return if (cardFromBottom()) {
            if (position.y + radius < CARD_MIN_MARGIN) CARD_MIN_MARGIN else (position.y + radius + CARD_MIN_MARGIN).toInt()
        } else 0
    }

    private fun getCardMarginBottom(): Int {
        return if (cardFromTop()) {
            if (position.y - radius >= height - CARD_MIN_MARGIN) CARD_MIN_MARGIN else (height - position.y + radius + CARD_MIN_MARGIN).toInt()
        } else 0
    }

    private fun getCardMarginLeft(): Int {
        return if (cardFromRight()) {
            if (position.x <= CARD_MIN_MARGIN + CARD_TO_ARROW_OFFSET + cardLeftOffset) cardLeftOffset + CARD_MIN_MARGIN else position.x.toInt() - CARD_TO_ARROW_OFFSET
        } else 0
    }

    private fun getCardMarginRight(): Int {
        return if (cardFromLeft()) {
            if (position.x >= width - CARD_MIN_MARGIN - CARD_TO_ARROW_OFFSET - cardRightOffset) cardRightOffset + CARD_MIN_MARGIN else width - CARD_TO_ARROW_OFFSET - position.x.toInt()
        } else 0
    }

    private fun configureCard(card: ViewGroup) = apply {
        // configure card content
        with(cardContent) {
            maxWidth = getCardWidth()
            setPadding(CARD_PADDING_VERTICAL, CARD_PADDING_HORIZONTAL, CARD_PADDING_VERTICAL, CARD_PADDING_HORIZONTAL)
            layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        card.setBackgroundResource(getCardBackgroundDrawable())
        card.addView(cardContent)

        // update layout params
        val cardLayoutParams = generateDefaultLayoutParams()
        cardLayoutParams.apply {
            width = LayoutParams.WRAP_CONTENT
            height = LayoutParams.WRAP_CONTENT
            gravity = getCardGravity()
            leftMargin = getCardMarginLeft()
            topMargin = getCardMarginTop()
            rightMargin = getCardMarginRight()
            bottomMargin = getCardMarginBottom()
        }
        card.layoutParams = cardLayoutParams
    }

    /**
     * Add view if it not already exists
     */
    private fun show(container: ViewGroup) {
        if (ViewUtils.findViewWithType(container, ShowCaseView::class.java) == null) {
            container.addView(this)

            val card = FrameLayout(context)
            MeasuredUtils.afterMeasured(
                    card,
                    object : MeasuredUtils.OnMeasuredHandler {
                        override fun onMeasured() {
                            configureCard(card)
                            ObjectAnimator.ofFloat(this, CARD_ANIMATION_PROPERTY, CARD_ANIMATION_OFFSET.toFloat(), 0F)
                                    .apply {
                                        startDelay = ANIMATION_START_DELAY
                                        duration = CARD_ANIMATION_DURATION
                                    }
                                    .start()
                        }
                    }
            )
            addView(card)

            animate()
                    .setStartDelay(ANIMATION_START_DELAY)
                    .setDuration(VIEW_FADE_IN_DURATION)
                    .alpha(1F)
        }
    }

    private fun initCardOffsets(activity: Activity) {
        if (ActivityUtils.getOrientation(activity) == Configuration.ORIENTATION_LANDSCAPE) {
            when (NavigationBarUtils.navigationBarPosition(activity)) {
                NavigationBarUtils.NavigationBarPosition.LEFT ->
                    cardLeftOffset = NavigationBarUtils.navigationBarHeight(activity)
                NavigationBarUtils.NavigationBarPosition.RIGHT ->
                    cardRightOffset = NavigationBarUtils.navigationBarHeight(activity)
                else -> {
                }
            }
        }
    }

    private fun showAfterMeasured(
            activity: Activity,
            container: ViewGroup,
            measuredView: View
    ) {
        MeasuredUtils.afterMeasured(
                measuredView,
                object : MeasuredUtils.OnMeasuredHandler {
                    override fun onMeasured() {
                        initCardOffsets(activity)

                        when {
                            typedPosition is ViewPosition
                                    || typedRadius is ViewRadius -> {

                                MeasuredUtils.afterOrAlreadyMeasuredViews(
                                        listOf(
                                                (typedPosition as? ViewPosition)?.view,
                                                (typedRadius as? ViewRadius)?.view
                                        ).filterNotNull(),
                                        object : MeasuredUtils.OnMeasuredHandler {
                                            override fun onMeasured() {
                                                position = typedPosition.getPosition(activity)
                                                radius = typedRadius.getRadius()
                                                show(container)
                                            }
                                        }
                                )
                            }
                            else -> {
                                position = typedPosition.getPosition(activity)
                                radius = typedRadius.getRadius()
                                show(container)
                            }
                        }
                    }
                })
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(overlayPaint)
        canvas.drawCircle(position.x, position.y, radius, circlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        dismissListener?.onDismiss()
        hide()
        return !isTouchInCircle(event)
    }

    fun show(activity: Activity) {
        val container = activity.window.decorView as ViewGroup
        showAfterMeasured(
                activity = activity,
                container = container,
                measuredView = container
        )
    }

    fun show(fragment: Fragment) {
        showAfterMeasured(
                activity = fragment.activity,
                container = fragment.activity.window.decorView as ViewGroup,
                measuredView = fragment.view!!
        )
    }

    fun hide() {
        if (!hideAnimationPerforming) {
            animate()
                    .withStartAction { hideAnimationPerforming = true }
                    .setDuration(VIEW_FADE_IN_DURATION)
                    .alpha(0F)
                    .withEndAction {
                        hideAnimationPerforming = false
                        removeFromWindow()
                    }
        }
    }

    class Builder(val activity: Activity) {

        companion object {
            val DEFAULT_RADIUS = Radius(128F)
        }

        var color: Int = R.color.black65
        var position: ShowCasePosition = Position(PointF(0F, 0F))
        var radius: ShowCaseRadius = DEFAULT_RADIUS
        var contentView: TextView? = null
        var contentText: String? = null
        var dismissListener: ShowCaseViewJava.DismissListener? = null

        fun withTypedRadius(showCaseRadius: ShowCaseRadius): Builder = apply {
            radius = showCaseRadius
        }

        fun withDismissListener(listener: ShowCaseViewJava.DismissListener): Builder = apply {
            dismissListener = listener
        }

        fun withTypedPosition(typedPosition: ShowCasePosition): Builder = apply {
            position = typedPosition
        }

        fun withColor(@ColorInt overlayColor: Int): Builder = apply {
            color = overlayColor
        }

        fun withContent(cardText: String): Builder = apply {
            contentView = activity.layoutInflater.inflate(
                    R.layout.item_show_case_content, null
            ) as TextView
            contentText = cardText
        }

        fun build() = ShowCaseView(activity).apply {
            dismissListener = this@Builder.dismissListener
            typedRadius = this@Builder.radius
            typedPosition = this@Builder.position
            overlayColor = this@Builder.color
            if (contentView != null && contentText != null) {
                setContent(contentView!!, contentText!!)
            }
        }
    }
}