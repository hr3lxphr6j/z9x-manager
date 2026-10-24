package local.z9x.manager

import android.content.Context

/**
 * GMUI color roles. The hex values live only in `res/values/colors.xml`.
 *
 * Focus is a white fill with black text. Selected (current tab, checked row)
 * and idle rows use the two translucent whites. Blue is status text only
 * and is replaced by the focus text while a row is focused.
 */
internal class GmuiPalette(context: Context) {
    val focusFill: Int = context.getColor(R.color.gmui_focus_fill)
    val focusText: Int = context.getColor(R.color.gmui_focus_text)
    val rowSelected: Int = context.getColor(R.color.gmui_row_selected)
    val rowIdle: Int = context.getColor(R.color.gmui_row_idle)
    val textPrimary: Int = context.getColor(R.color.gmui_text_primary)
    val textSecondary: Int = context.getColor(R.color.gmui_text_secondary)
    val panel: Int = context.getColor(R.color.gmui_panel)
    val window: Int = context.getColor(R.color.gmui_window)
    val statusEnabled: Int = context.getColor(R.color.gmui_status_enabled)
    val statusConnected: Int = context.getColor(R.color.gmui_status_connected)

    fun rowFill(focused: Boolean, selected: Boolean): Int = when {
        focused -> focusFill
        selected -> rowSelected
        else -> rowIdle
    }

    fun titleOn(focused: Boolean): Int = if (focused) focusText else textPrimary

    fun subtitleOn(focused: Boolean): Int = if (focused) focusText else textSecondary

    fun statusOn(focused: Boolean, active: Boolean): Int = when {
        focused -> focusText
        active -> statusEnabled
        else -> textSecondary
    }
}
