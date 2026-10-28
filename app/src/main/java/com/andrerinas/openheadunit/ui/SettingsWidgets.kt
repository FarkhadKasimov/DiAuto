package com.andrerinas.openheadunit.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.andrerinas.openheadunit.R
import kotlin.math.roundToInt

object SettingsWidgets {

    private fun TextView.applyTextStyle(context: Context, theme: SettingsTheme, bold: Boolean = false) {
        if (theme.isOverlay) {
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            includeFontPadding = false
        } else {
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(theme.dp(context, 3).toFloat(), 1f)
        }
    }

    data class SwitchRowResult(
        val rowView: View,
        val switch: Switch,
    )

    data class ChoiceRowResult<T>(
        val container: View,
        val radioGroup: RadioGroup,
    )

    data class SliderResult(
        val container: View,
        val valueTextView: TextView,
        val seekBar: SeekBar,
    )

    fun createCategoryHeader(
        context: Context,
        title: String,
        theme: SettingsTheme = SettingsTheme.OVERLAY,
    ): TextView = TextView(context).apply {
        text = title
        textSize = if (theme.isOverlay) 16f else 22f
        setTextColor(if (theme.isOverlay) theme.accent else theme.textPrimary)
        applyTextStyle(context, theme, bold = true)
    }

    fun applyLargeSwitchStyle(switch: Switch, thumbOn: Int, thumbOff: Int, trackOn: Int, trackOff: Int) {
        val density = switch.resources.displayMetrics.density
        fun px(value: Int) = (value * density).roundToInt()
        val thumbInset = px(3)
        switch.thumbDrawable = InsetDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(px(28), px(28))
                setColor(Color.WHITE)
            },
            thumbInset,
        )
        switch.trackDrawable = GradientDrawable().apply {
            cornerRadius = px(17).toFloat()
            setSize(px(60), px(34))
            setColor(Color.WHITE)
            alpha = (0.3f * 255).roundToInt()
        }
        switch.switchMinWidth = px(60)
        switch.showText = false
        switch.thumbTintList = switchStates(thumbOn, thumbOff)
        switch.trackTintList = switchStates(trackOn, trackOff)
    }

    private fun switchStates(on: Int, off: Int) = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(),
        ),
        intArrayOf(ColorUtils.setAlphaComponent(off, 0x61), on, off),
    )

    fun createSwitchRow(
        context: Context,
        label: String,
        description: String,
        checked: Boolean,
        theme: SettingsTheme = SettingsTheme.OVERLAY,
        contentDescription: String = label,
        enabled: Boolean = true,
        labelSizeSp: Float = 18f,
        onChanged: (Boolean) -> Unit,
    ): SwitchRowResult {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val switch = Switch(context).apply {
            isChecked = checked
            this.contentDescription = contentDescription
            isEnabled = enabled
            applyLargeSwitchStyle(this, theme.accent, theme.textSecondary, theme.accentTrack, theme.trackOff)
            if (!theme.isOverlay) minHeight = theme.dp(context, 56)
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }

        if (theme.isOverlay) {
            val labelView = TextView(context).apply {
                text = label
                textSize = labelSizeSp
                setTextColor(theme.textSecondary)
                applyTextStyle(context, theme)
            }
            row.addView(labelView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(switch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else {
            row.setPadding(0, theme.dp(context, 12), 0, theme.dp(context, 12))
            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            val labelView = TextView(context).apply {
                text = label
                textSize = labelSizeSp
                setTextColor(theme.textPrimary)
                applyTextStyle(context, theme, bold = true)
            }
            val descView = TextView(context).apply {
                text = description
                textSize = 14f
                setTextColor(theme.textSecondary)
                setPadding(0, theme.dp(context, 6), theme.dp(context, 16), 0)
                applyTextStyle(context, theme)
            }
            textCol.addView(labelView)
            if (description.isNotEmpty()) {
                textCol.addView(descView)
            }
            row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(switch)
            switch.isFocusable = false
            row.isFocusable = true
            row.background = RippleDrawable(
                ColorStateList.valueOf(theme.ripple), null, android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
            row.setOnClickListener { if (switch.isEnabled) switch.toggle() }
        }

        return SwitchRowResult(row, switch)
    }

    fun createActionRow(
        context: Context,
        label: String,
        description: String = "",
        value: String = "",
        theme: SettingsTheme = SettingsTheme.OVERLAY,
        onClick: () -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, theme.dp(context, 12), 0, theme.dp(context, 12))
            isFocusable = true
            background = RippleDrawable(
                ColorStateList.valueOf(theme.ripple), null, android.graphics.drawable.ColorDrawable(Color.WHITE)
            )
            setOnClickListener { onClick() }
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val labelView = TextView(context).apply {
            text = label
            textSize = 18f
            setTextColor(theme.textPrimary)
            applyTextStyle(context, theme, bold = true)
        }
        textCol.addView(labelView)
        if (description.isNotEmpty()) {
            val descView = TextView(context).apply {
                text = description
                textSize = 14f
                setTextColor(theme.textSecondary)
                setPadding(0, theme.dp(context, 4), theme.dp(context, 16), 0)
                applyTextStyle(context, theme)
            }
            textCol.addView(descView)
        }
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (value.isNotEmpty()) {
            val valueView = TextView(context).apply {
                text = value
                textSize = 16f
                setTextColor(theme.accent)
                applyTextStyle(context, theme, bold = true)
                setPadding(0, 0, theme.dp(context, 8), 0)
            }
            row.addView(valueView)
        }

        val chevron = TextView(context).apply {
            text = "›"
            textSize = 22f
            setTextColor(theme.textSecondary)
        }
        row.addView(chevron)

        return row
    }

    fun createCategoryCard(
        context: Context,
        title: String,
        subtitle: String,
        iconRes: Int = 0,
        theme: SettingsTheme = SettingsTheme.CARD,
        onClick: () -> Unit,
    ): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(theme.dp(context, 18), theme.dp(context, 16), theme.dp(context, 18), theme.dp(context, 16))
            background = GradientDrawable().apply {
                setColor(theme.accentTrack)
                cornerRadius = theme.dp(context, 16).toFloat()
            }
            isFocusable = true
            setOnClickListener { onClick() }
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(theme.textPrimary)
            applyTextStyle(context, theme, bold = true)
        }
        card.addView(titleView)

        if (subtitle.isNotEmpty()) {
            val subView = TextView(context).apply {
                text = subtitle
                textSize = 14f
                setTextColor(theme.textSecondary)
                setPadding(0, theme.dp(context, 6), 0, 0)
                applyTextStyle(context, theme)
            }
            card.addView(subView)
        }

        return card
    }

    fun <T> createChoiceRow(
        context: Context,
        label: String,
        options: List<Pair<T, String>>,
        selected: T,
        theme: SettingsTheme = SettingsTheme.OVERLAY,
        onSelected: (T) -> Unit,
    ): ChoiceRowResult<T> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val labelView = TextView(context).apply {
            text = label
            textSize = 18f
            setTextColor(if (theme.isOverlay) theme.textSecondary else theme.textPrimary)
            applyTextStyle(context, theme, bold = !theme.isOverlay)
        }
        container.addView(labelView)
        val group = RadioGroup(context).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, theme.dp(context, 4), 0, 0)
        }
        var selectedId = View.NO_ID
        for ((value, text) in options) {
            val button = RadioButton(context).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 17f
                setTextColor(theme.textSecondary)
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(theme.accent, theme.textSecondary),
                )
                tag = value
                isChecked = value == selected
            }
            if (value == selected) selectedId = button.id
            group.addView(button)
        }
        if (selectedId != View.NO_ID) group.check(selectedId)
        group.setOnCheckedChangeListener { radioGroup, checkedId ->
            if (checkedId == View.NO_ID) return@setOnCheckedChangeListener
            val button = radioGroup.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            if (!button.isChecked) return@setOnCheckedChangeListener
            @Suppress("UNCHECKED_CAST")
            val value = button.tag as? T ?: return@setOnCheckedChangeListener
            onSelected(value)
        }
        container.addView(group)
        return ChoiceRowResult(container, group)
    }

    fun createSliderRow(
        context: Context,
        label: String,
        initialValue: Int,
        minValue: Int,
        maxValue: Int,
        step: Int = 1,
        valueFormatter: (Int) -> String = { "$it" },
        theme: SettingsTheme = SettingsTheme.OVERLAY,
        onValueChanged: (Int) -> Unit,
    ): SliderResult {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, theme.dp(context, 8), 0, theme.dp(context, 8))
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val labelView = TextView(context).apply {
            text = label
            textSize = 18f
            setTextColor(theme.textPrimary)
            applyTextStyle(context, theme, bold = true)
        }
        header.addView(labelView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val valueView = TextView(context).apply {
            text = valueFormatter(initialValue)
            textSize = 20f
            setTextColor(theme.accent)
            applyTextStyle(context, theme, bold = true)
        }
        header.addView(valueView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(header)

        val seekBar = SeekBar(context).apply {
            max = (maxValue - minValue) / step
            progress = (initialValue - minValue) / step
            splitTrack = false
            progressTintList = ColorStateList.valueOf(theme.accent)
            thumbTintList = ColorStateList.valueOf(theme.accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, prog: Int, fromUser: Boolean) {
                    val calc = minValue + (prog * step)
                    valueView.text = valueFormatter(calc)
                    if (fromUser) onValueChanged(calc)
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }
        container.addView(seekBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = theme.dp(context, 8)
        })

        return SliderResult(container, valueView, seekBar)
    }
}
