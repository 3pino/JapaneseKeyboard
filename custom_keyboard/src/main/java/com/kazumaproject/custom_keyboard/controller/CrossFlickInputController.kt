package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.view.DirectionalKeyPopupView
import com.kazumaproject.custom_keyboard.view.CrossFlickPopupView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class CrossFlickInputController(
    private val context: Context,
    flickSensitivity: Int = 80
) {

    interface CrossFlickListener {
        fun onPress(action: KeyAction)
        fun onFlick(action: KeyAction, isFlick: Boolean)
        fun onFlickLongPress(action: KeyAction)
        fun onFlickUpAfterLongPress(action: KeyAction, isFlick: Boolean)
    }

    private enum class PopupStyle {
        ACTION_PREVIEW,
        TEXT_GRID
    }

    var listener: CrossFlickListener? = null

    private var popupStyle: PopupStyle = PopupStyle.ACTION_PREVIEW
    private var anchorView: View? = null
    private var initialTouchPoint = PointF(0f, 0f)
    private var currentDirection = FlickDirection.TAP
    private val flickThreshold = flickSensitivity.toFloat().coerceAtLeast(1f)

    private var baseActionMap: Map<FlickDirection, FlickAction> = emptyMap()
    private var longPressActionMap: Map<FlickDirection, FlickAction> = emptyMap()

    private val actionPopupWindows = mutableMapOf<FlickDirection, PopupWindow>()
    private val actionPopupViews = mutableMapOf<FlickDirection, CrossFlickPopupView>()

    private val directionalPopupMap = mutableMapOf<FlickDirection, PopupWindow>()
    private var currentVisibleDirectionalPopup: PopupWindow? = null
    private var currentVisibleDirectional: FlickDirection? = null
    private var originalKeyText: CharSequence? = null

    private val gridPopup = PopupWindow(
        CrossFlickPopupView(context),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        false
    ).apply {
        setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        isClippingEnabled = false
        elevation = 8f
        animationStyle = 0
        enterTransition = null
        exitTransition = null
    }

    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null
    private var isLongPressMode = false
    private var isLongPressTriggered = false
    private var longPressTimeout: Long = ViewConfiguration.getLongPressTimeout().toLong()

    private var popupColorTheme: FlickPopupColorTheme? = null

    // 色設定。FlickPopupColorTheme をまとめて受け取り、全ポップアップに適用する。
    fun setPopupColors(theme: FlickPopupColorTheme) {
        popupColorTheme = theme
    }

    // 長押し判定までの待機時間を変更する。FlickKeyboardView から端末設定に合わせて呼ばれる。
    fun setLongPressTimeout(timeoutMillis: Long) {
        longPressTimeout = timeoutMillis.coerceIn(100L, 2000L)
    }

    // コントローラを破棄する。ビューのデタッチやキーボードビューの再構築時に FlickKeyboardView から呼ばれる。
    fun cancel() {
        longPressJob?.cancel()
        controllerScope.cancel()
        restoreOriginalButtonText()
        dismissAllPopups()
    }

    // ACTION モードでビューにアタッチする。CROSS_FLICK キー（アイコン付き特殊キー）向け。
    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, map: Map<FlickDirection, FlickAction>) {
        popupStyle = PopupStyle.ACTION_PREVIEW
        baseActionMap = map
        longPressActionMap = emptyMap()
        view.setOnTouchListener { v, event -> handleTouchEvent(v, event) }
    }

    // TEXT モードでビューにアタッチする。PETAL_FLICK キー（文字入力キー）向け。
    // longPressMap を省略するとグリッドポップアップには通常 map の内容がそのまま表示される。
    @SuppressLint("ClickableViewAccessibility")
    fun attachText(
        view: View,
        map: Map<FlickDirection, String>,
        longPressMap: Map<FlickDirection, String> = emptyMap()
    ) {
        attachTextActions(
            view = view,
            map = map.toInputActionMap(),
            longPressMap = longPressMap
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachTextActions(
        view: View,
        map: Map<FlickDirection, FlickAction>,
        longPressMap: Map<FlickDirection, String> = emptyMap()
    ) {
        popupStyle = PopupStyle.TEXT_GRID
        baseActionMap = map
        longPressActionMap = longPressMap.toInputActionMap()
        view.setOnTouchListener { v, event -> handleTouchEvent(v, event) }
    }

    // タッチイベントの中枢。ACTION_DOWN/MOVE/UP・CANCEL をまとめて処理し、長押しタイマーも管理する。
    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                view.isPressed = true
                view.drawableHotspotChanged(event.x, event.y)

                isLongPressMode = false
                isLongPressTriggered = false
                anchorView = view
                initialTouchPoint.set(event.rawX, event.rawY)
                currentDirection = FlickDirection.TAP

                if (popupStyle == PopupStyle.TEXT_GRID) {
                    (anchorView as? Button)?.let { button ->
                        originalKeyText = button.text
                        button.text = ""
                    }
                    createDirectionalPopups()
                    showDirectionalPopup(FlickDirection.TAP)
                }

                notifyPressAction()

                longPressJob?.cancel()
                longPressJob = controllerScope.launch {
                    delay(longPressTimeout)
                    isLongPressTriggered = true
                    isLongPressMode = true
                    onLongPressTriggered()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                view.drawableHotspotChanged(event.x, event.y)

                val dx = event.rawX - initialTouchPoint.x
                val dy = event.rawY - initialTouchPoint.y
                val newDirection = calculateDirection(dx, dy)

                if (newDirection != currentDirection) {
                    currentDirection = newDirection
                    if (isLongPressMode) {
                        updateLongPressHighlight(newDirection)
                    } else {
                        updateNormalPopup(newDirection)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                longPressJob?.cancel()

                if (event.action == MotionEvent.ACTION_UP) {
                    commitAction()
                }

                restoreOriginalButtonText()
                dismissAllPopups()
                anchorView = null
                return true
            }
        }
        return false
    }

    // 長押しタイマーが満了したときに呼ばれる。モードに応じてポップアップ表示を切り替える。
    // ACTION: 全方向のアクションポップアップを展開。TEXT: 方向ポップアップを閉じてグリッド表示に切り替える。
    private fun onLongPressTriggered() {
        withPopupStyle(
            onActionPreview = {
                showAllActionPopups()
                highlightActionPopup(currentDirection)
                notifyLongPressActionPreview()
            },
            onTextGrid = {
                dismissDirectionalPopups()
                showGridPopup()
                highlightGrid(currentDirection)
            }
        )
    }

    // ACTION_DOWN 時に listener へ押下通知を送る。TAP 方向のアクション／テキストを事前通知する。
    private fun notifyPressAction() {
        resolveNormalized(FlickDirection.TAP, preferLongPress = false)?.let { normalized ->
            listener?.onPress(normalized.commitAction)
        }
    }

    // ACTION_UP 時に確定処理を行う。長押し済みかどうかで呼ぶコールバックを切り替える。
    private fun commitAction() {
        val isFlick = currentDirection != FlickDirection.TAP
        withPopupStyle(
            onActionPreview = {
                val normalized = resolveNormalized(currentDirection, preferLongPress = false)
                if (isLongPressTriggered) {
                    listener?.onFlickUpAfterLongPress(
                        normalized?.commitAction ?: KeyAction.Cancel,
                        isFlick
                    )
                } else {
                    normalized?.let { listener?.onFlick(it.commitAction, isFlick) }
                }
            },
            onTextGrid = {
                resolveNormalized(
                    direction = currentDirection,
                    preferLongPress = isLongPressMode
                )?.let { normalized ->
                    listener?.onFlick(normalized.commitAction, isFlick)
                }
            }
        )
    }

    // 通常フリック中（長押し前）に方向が変わったときのポップアップ更新。ACTION_MOVE から呼ばれる。
    private fun updateNormalPopup(direction: FlickDirection) {
        withPopupStyle(
            onActionPreview = {
                dismissAllActionPopups()
                showActionPopup(direction, highlighted = true)
            },
            onTextGrid = { showDirectionalPopup(direction) }
        )
    }

    // 長押しモード中に指が動いたときのハイライト更新。ACTION_MOVE から呼ばれる。
    private fun updateLongPressHighlight(direction: FlickDirection) {
        withPopupStyle(
            onActionPreview = {
                highlightActionPopup(direction)
                notifyLongPressActionPreview()
            },
            onTextGrid = { highlightGrid(direction) }
        )
    }

    // ACTION モードの長押し中、現在ハイライトされている方向のアクションを listener へプレビュー通知する。
    private fun notifyLongPressActionPreview() {
        resolveNormalized(currentDirection, preferLongPress = false)?.let { normalized ->
            listener?.onFlickLongPress(normalized.commitAction)
        }
    }

    // 初期タッチ位置からの差分を FlickDirection に変換する。両軸が閾値未満なら TAP とみなす。
    // LEFT は UP_LEFT_FAR、RIGHT は UP_RIGHT_FAR で表現する（フォールバック候補は getDirectionCandidates が担う）。
    private fun calculateDirection(dx: Float, dy: Float): FlickDirection {
        val absDx = abs(dx)
        val absDy = abs(dy)

        if (absDx < flickThreshold && absDy < flickThreshold) {
            return FlickDirection.TAP
        }

        return if (absDx > absDy) {
            if (dx > 0) FlickDirection.UP_RIGHT_FAR else FlickDirection.UP_LEFT_FAR
        } else {
            if (dy > 0) FlickDirection.DOWN else FlickDirection.UP
        }
    }

    private data class NormalizedFlick(
        val commitAction: KeyAction,
        val displayText: String?,
        val displayLabel: String?,
        val drawableResId: Int?
    )

    // FlickDirection を候補リストに展開して FlickAction を解決する。preferLongPress=true のとき長押しマップを優先する。
    private fun resolveFlickAction(
        direction: FlickDirection,
        preferLongPress: Boolean
    ): FlickAction? {
        if (preferLongPress) {
            resolveFlickActionFromMap(direction, longPressActionMap)?.let { return it }
        }
        return resolveFlickActionFromMap(direction, baseActionMap)
    }

    private fun resolveFlickActionFromMap(
        direction: FlickDirection,
        source: Map<FlickDirection, FlickAction>
    ): FlickAction? {
        for (candidate in getDirectionCandidates(direction)) {
            val action = source[candidate]
            if (action != null) {
                return action
            }
        }
        return null
    }

    // commit 用の正規化入力を解決する。preferLongPress=true のとき長押しマップを優先する。
    private fun resolveNormalized(
        direction: FlickDirection,
        preferLongPress: Boolean
    ): NormalizedFlick? {
        if (preferLongPress) {
            resolveNormalizedFromMap(direction, longPressActionMap)?.let { return it }
        }
        return resolveNormalizedFromMap(direction, baseActionMap)
    }

    private fun resolveNormalizedFromMap(
        direction: FlickDirection,
        source: Map<FlickDirection, FlickAction>
    ): NormalizedFlick? {
        for (candidate in getDirectionCandidates(direction)) {
            val action = source[candidate] ?: continue
            val normalized = action.toNormalizedFlick()
            if (normalized.isCommittable()) {
                return normalized
            }
        }
        return null
    }

    // FlickDirection を優先候補リストへ展開する。UP_LEFT_FAR/UP_RIGHT_FAR は near 方向へフォールバックする。
    // UP_LEFT/UP_RIGHT は far→near の逆順でフォールバックし、どちらの表記で渡されても解決できる。
    private fun getDirectionCandidates(direction: FlickDirection): List<FlickDirection> {
        return when (direction) {
            FlickDirection.TAP -> listOf(FlickDirection.TAP)
            FlickDirection.UP -> listOf(FlickDirection.UP)
            FlickDirection.DOWN -> listOf(FlickDirection.DOWN)
            FlickDirection.UP_LEFT_FAR -> listOf(FlickDirection.UP_LEFT_FAR, FlickDirection.UP_LEFT)
            FlickDirection.UP_LEFT -> listOf(FlickDirection.UP_LEFT, FlickDirection.UP_LEFT_FAR)
            FlickDirection.UP_RIGHT_FAR -> listOf(FlickDirection.UP_RIGHT_FAR, FlickDirection.UP_RIGHT)
            FlickDirection.UP_RIGHT -> listOf(FlickDirection.UP_RIGHT, FlickDirection.UP_RIGHT_FAR)
        }
    }

    // 指定方向に CrossFlickPopupView を生成して表示する。通常フリック中は highlighted=true で単体表示する。
    private fun showActionPopup(direction: FlickDirection, highlighted: Boolean) {
        if (direction == FlickDirection.TAP) return

        val flickAction = resolveFlickAction(direction, preferLongPress = false) ?: return
        val anchor = anchorView ?: return
        if (!anchor.isAttachedToWindow) return

        val popupView = CrossFlickPopupView(context).apply {
            setCells(
                map = mapOf(direction to flickAction),
                keyWidth = anchor.width,
                keyHeight = anchor.height,
                compactSingleCell = true
            )
            popupColorTheme?.let { setColors(it) }
            if (highlighted) highlightDirection(direction)
        }

        val popupWindow = PopupWindow(popupView, anchor.width, anchor.height, false).apply {
            isClippingEnabled = false
            elevation = 8f
            animationStyle = 0
            enterTransition = null
            exitTransition = null
        }

        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val x = location[0]
        val y = location[1]

        val popupX = when (direction) {
            FlickDirection.UP_LEFT_FAR, FlickDirection.UP_LEFT -> x - anchor.width
            FlickDirection.UP_RIGHT_FAR, FlickDirection.UP_RIGHT -> x + anchor.width
            else -> x
        }
        val popupY = when (direction) {
            FlickDirection.UP -> y - anchor.height
            FlickDirection.DOWN -> y + anchor.height
            else -> y
        }

        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, popupX, popupY)
        actionPopupWindows[direction] = popupWindow
        actionPopupViews[direction] = popupView
    }

    // ACTION モードの長押し発動時に全方向のポップアップをまとめて表示する。
    private fun showAllActionPopups() {
        dismissAllActionPopups()
        val anchor = anchorView ?: return
        if (!anchor.isAttachedToWindow) return

        listOf(
            FlickDirection.UP,
            FlickDirection.DOWN,
            FlickDirection.UP_LEFT_FAR,
            FlickDirection.UP_RIGHT_FAR
        ).forEach { direction ->
            showActionPopup(direction, highlighted = false)
        }
    }

    // 全アクションポップアップのうち指定方向だけをハイライト状態にする。長押し中の指移動で呼ばれる。
    private fun highlightActionPopup(direction: FlickDirection) {
        actionPopupViews.forEach { (dir, popupView) ->
            popupView.highlightDirection(if (dir == direction) dir else null)
        }
    }

    // 表示中のアクションポップアップを全て閉じてマップをクリアする。
    private fun dismissAllActionPopups() {
        actionPopupWindows.values.forEach { if (it.isShowing) it.dismiss() }
        actionPopupWindows.clear()
        actionPopupViews.clear()
    }

    // TEXT モードの ACTION_DOWN 時に全方向の DirectionalKeyPopupView を事前生成してマップに保持する。
    // 実際の表示は showDirectionalPopup が担うため、ここでは showAtLocation は呼ばない。
    private fun createDirectionalPopups() {
        directionalPopupMap.values.forEach { if (it.isShowing) it.dismiss() }
        directionalPopupMap.clear()
        currentVisibleDirectionalPopup = null
        currentVisibleDirectional = null

        val currentAnchor = anchorView ?: return
        val directions = listOf(
            FlickDirection.TAP,
            FlickDirection.UP,
            FlickDirection.DOWN,
            FlickDirection.UP_LEFT_FAR,
            FlickDirection.UP_RIGHT_FAR
        )

        directions.forEach { direction ->
            val displayText = resolveNormalized(
                direction = direction,
                preferLongPress = false
            )?.toDisplayText() ?: return@forEach

            val popupView = DirectionalKeyPopupView(context).apply {
                this.text = displayText
                popupColorTheme?.let { setColors(it) }
                setFlickDirection(direction)
            }

            val popupHeight = when (direction) {
                FlickDirection.UP, FlickDirection.DOWN -> {
                    currentAnchor.height + (currentAnchor.height / 4)
                }

                else -> currentAnchor.height
            }

            val popupWidth = when (direction) {
                FlickDirection.UP, FlickDirection.DOWN -> {
                    currentAnchor.width - (currentAnchor.height / 4)
                }

                FlickDirection.TAP -> currentAnchor.width
                else -> {
                    currentAnchor.width + (currentAnchor.width / 2 - currentAnchor.width / 4)
                }
            }

            directionalPopupMap[direction] = PopupWindow(
                popupView,
                popupWidth,
                popupHeight,
                false
            ).apply {
                isClippingEnabled = false
                elevation = 8f
                animationStyle = 0
                enterTransition = null
                exitTransition = null
            }
        }
    }

    // TEXT モードで指定方向のポップアップを表示し、直前に表示していたものを閉じる。
    // 同じ方向が連続した場合はちらつき防止のためスキップする。
    private fun showDirectionalPopup(direction: FlickDirection) {
        if (direction == currentVisibleDirectional) {
            return
        }

        currentVisibleDirectionalPopup?.dismiss()
        if (isLongPressMode) return

        val popupToShow = directionalPopupMap[direction] ?: return
        val currentAnchor = anchorView ?: return
        if (!currentAnchor.isAttachedToWindow) return

        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)
        val anchorX = location[0]
        val anchorY = location[1]
        val keyWidth = currentAnchor.width
        val keyHeight = currentAnchor.height
        val anchorCenterX = anchorX + keyWidth / 2
        val anchorCenterY = anchorY + keyHeight / 2

        val popupWidth = popupToShow.width
        val popupHeight = popupToShow.height

        val x: Int
        val y: Int

        when (direction) {
            FlickDirection.TAP -> {
                x = anchorCenterX - popupWidth / 2
                y = anchorCenterY - popupHeight / 2
            }

            FlickDirection.UP -> {
                x = anchorCenterX - popupWidth / 2
                y = anchorCenterY - popupHeight
            }

            FlickDirection.DOWN -> {
                x = anchorCenterX - popupWidth / 2
                y = anchorCenterY
            }

            FlickDirection.UP_LEFT_FAR, FlickDirection.UP_LEFT -> {
                x = anchorCenterX - popupWidth
                y = anchorCenterY - popupHeight / 2
            }

            FlickDirection.UP_RIGHT_FAR, FlickDirection.UP_RIGHT -> {
                x = anchorCenterX
                y = anchorCenterY - popupHeight / 2
            }
        }

        popupToShow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        currentVisibleDirectionalPopup = popupToShow
        currentVisibleDirectional = direction
    }

    // TEXT モードの長押し発動時にグリッドポップアップを表示する。既に表示中なら位置を更新する。
    private fun showGridPopup() {
        val currentAnchor = anchorView ?: return
        if (!currentAnchor.isAttachedToWindow) return

        val popupView = gridPopup.contentView as CrossFlickPopupView
        popupColorTheme?.let { popupView.setColors(it) }

        popupView.setCells(getLongPressDisplayMap(), currentAnchor.width, currentAnchor.height)
        popupView.highlightDirection(currentDirection)

        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

        val x = location[0] + currentAnchor.width / 2 - popupView.measuredWidth / 2
        val y = location[1] + currentAnchor.height / 2 - popupView.measuredHeight / 2

        gridPopup.width = WindowManager.LayoutParams.WRAP_CONTENT
        gridPopup.height = WindowManager.LayoutParams.WRAP_CONTENT

        if (!gridPopup.isShowing) {
            gridPopup.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        } else {
            gridPopup.update(x, y, -1, -1)
        }
    }

    // グリッドポップアップ内の対応セルをハイライトする。長押し中の指移動で呼ばれる。
    private fun highlightGrid(direction: FlickDirection) {
        (gridPopup.contentView as? CrossFlickPopupView)?.highlightDirection(direction)
    }

    // グリッドポップアップに渡す表示マップを生成する。各方向で longPressActionMap を優先し、なければ baseActionMap を使う。
    private fun getLongPressDisplayMap(): Map<FlickDirection, FlickAction> {
        val directions = listOf(
            FlickDirection.TAP,
            FlickDirection.UP,
            FlickDirection.DOWN,
            FlickDirection.UP_LEFT_FAR,
            FlickDirection.UP_RIGHT_FAR
        )
        return directions.mapNotNull { dir ->
            val action = resolveFlickAction(dir, preferLongPress = true) ?: return@mapNotNull null
            val normalized = action.toNormalizedFlick()
            if (!normalized.hasDisplayContent()) return@mapNotNull null
            dir to action
        }.toMap()
    }

    // TEXT モードで ACTION_DOWN 時に消したボタンのラベルを復元する。ACTION_UP/CANCEL および cancel() から呼ばれる。
    private fun restoreOriginalButtonText() {
        (anchorView as? Button)?.let { button ->
            if (popupStyle == PopupStyle.TEXT_GRID) {
                button.text = originalKeyText
            }
        }
        originalKeyText = null
    }

    // TEXT モードのポップアップ（方向ポップアップとグリッドポップアップ）をすべて閉じる。
    private fun dismissDirectionalPopups() {
        currentVisibleDirectionalPopup?.dismiss()
        currentVisibleDirectionalPopup = null
        currentVisibleDirectional = null
        directionalPopupMap.values.forEach { if (it.isShowing) it.dismiss() }
        directionalPopupMap.clear()
        if (gridPopup.isShowing) {
            gridPopup.dismiss()
        }
    }

    // ACTION・TEXT 両モードのポップアップをすべて閉じる。
    fun dismissAllPopups() {
        dismissAllActionPopups()
        dismissDirectionalPopups()
    }

    private inline fun withPopupStyle(
        onActionPreview: () -> Unit,
        onTextGrid: () -> Unit
    ) {
        when (popupStyle) {
            PopupStyle.ACTION_PREVIEW -> onActionPreview()
            PopupStyle.TEXT_GRID -> onTextGrid()
        }
    }

    private fun Map<FlickDirection, String>.toInputActionMap(): Map<FlickDirection, FlickAction> {
        return mapNotNull { (direction, text) ->
            text.takeUnless { it.isEmpty() }?.let { direction to FlickAction.Input(it) }
        }.toMap()
    }

    private fun FlickAction.toNormalizedFlick(): NormalizedFlick = when (this) {
        is FlickAction.Input -> {
            NormalizedFlick(
                commitAction = KeyAction.Text(char),
                displayText = char.takeUnless { it.isEmpty() },
                displayLabel = null,
                drawableResId = null
            )
        }

        is FlickAction.Action -> {
            val text = when (action) {
                is KeyAction.Text -> action.text
                is KeyAction.InputText -> action.text
                else -> null
            }?.takeUnless { it.isEmpty() }

            val fallbackLabel = if (drawableResId == null && label.isNullOrEmpty() && text == null) {
                action.javaClass.simpleName.firstOrNull()?.toString()
            } else {
                null
            }

            NormalizedFlick(
                commitAction = action,
                displayText = text,
                displayLabel = label?.takeUnless { it.isEmpty() } ?: fallbackLabel,
                drawableResId = drawableResId
            )
        }
    }

    private fun NormalizedFlick.isCommittable(): Boolean {
        return when (val action = commitAction) {
            is KeyAction.Text -> action.text.isNotEmpty()
            is KeyAction.InputText -> action.text.isNotEmpty()
            else -> true
        }
    }

    private fun NormalizedFlick.hasDisplayContent(): Boolean {
        return drawableResId != null || !displayLabel.isNullOrEmpty() || !displayText.isNullOrEmpty()
    }

    private fun NormalizedFlick.toDisplayText(): String? {
        return displayLabel ?: displayText
    }
}
