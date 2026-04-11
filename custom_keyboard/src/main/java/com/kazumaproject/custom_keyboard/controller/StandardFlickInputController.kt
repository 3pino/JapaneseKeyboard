package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import com.kazumaproject.custom_keyboard.view.StandardFlickPopupView
import android.graphics.Color
import android.view.Gravity
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.core.graphics.drawable.toDrawable

class StandardFlickInputController(
    private val context: Context,
    private val flickSensitivity: Int
) : GridFlickInputController(context, flickSensitivity) {

    private val popupView = StandardFlickPopupView(context)
    private val popupWindow: PopupWindow = PopupWindow(
        popupView,
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

    private var popupTheme: FlickPopupColorTheme? = null

    override fun setPopupColors(theme: FlickPopupColorTheme) {
        super.setPopupColors(theme)
        this.popupTheme = theme
        popupView.setPopupColors(theme)
    }

    override fun onActionDown(view: View) {
        characterMap[FlickDirection.TAP]
            ?.takeIf(::hasContent)
            ?.let { listener?.onPress(it) }
        segmentedDrawable?.highlightDirection = FlickDirection.TAP
        showPopup(FlickDirection.TAP)
    }

    override fun onActionMove(dx: Float, dy: Float, distance: Float, direction: FlickDirection) {
        segmentedDrawable?.highlightDirection = direction
        showPopup(direction)
    }

    override fun onActionUp(finalDirection: FlickDirection) {
        segmentedDrawable?.highlightDirection = finalDirection
        if (characterMap[finalDirection]?.takeIf(::hasContent) != null) {
            characterMap[finalDirection]?.let {
                listener?.onFlick(it, isFlick = finalDirection != FlickDirection.TAP)
            }
        }
    }

    override fun onActionCancel() {
        segmentedDrawable?.highlightDirection = null
        dismissPopup()
    }

    override fun onActionUpEnd() {
        segmentedDrawable?.highlightDirection = null
        dismissPopup()
    }

    override fun showPopup(direction: FlickDirection) {
        val currentAnchor = anchorView ?: return

        if (!currentAnchor.isAttachedToWindow) {
            return
        }

        popupTheme?.let { popupView.setPopupColors(it) }
        popupView.setFlickDirection(direction)

        if (direction == FlickDirection.TAP) {
            popupView.updateMultiCharText(toPopupTextMap())
        } else {
            val text = actionToPopupText(characterMap[direction])
            popupView.updateText(text)
        }

        val baseOffsetY = 10
        val flickUpAdditionalOffset = 80

        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)
        val x = location[0] + (currentAnchor.width / 2) - (popupView.viewSize / 2)
        var y = location[1] - popupView.viewSize - baseOffsetY

        if (direction == FlickDirection.UP) {
            y -= flickUpAdditionalOffset
        }

        if (popupWindow.isShowing) {
            popupWindow.update(x, y, -1, -1)
        } else {
            popupWindow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        }
    }

    override fun dismissPopup() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
    }

    override fun onLongPressTriggered(tapAction: FlickAction.Action?) {
        dismissPopup()
    }

    override fun cancel() {
        dismissPopup()
        super.cancel()
    }

    private fun hasContent(action: FlickAction): Boolean {
        return when (action) {
            is FlickAction.Input -> action.char.isNotEmpty()
            is FlickAction.Action -> true
        }
    }

    private fun actionToPopupText(action: FlickAction?): String {
        return when (action) {
            is FlickAction.Input -> action.char
            is FlickAction.Action -> action.label ?: ""
            null -> ""
        }
    }

    private fun toPopupTextMap(): Map<FlickDirection, String> {
        return mapOf(
            FlickDirection.TAP to actionToPopupText(characterMap[FlickDirection.TAP]),
            FlickDirection.UP to actionToPopupText(characterMap[FlickDirection.UP]),
            FlickDirection.UP_LEFT_FAR to actionToPopupText(characterMap[FlickDirection.UP_LEFT_FAR]),
            FlickDirection.UP_RIGHT_FAR to actionToPopupText(characterMap[FlickDirection.UP_RIGHT_FAR]),
            FlickDirection.DOWN to actionToPopupText(characterMap[FlickDirection.DOWN])
        )
    }
}


