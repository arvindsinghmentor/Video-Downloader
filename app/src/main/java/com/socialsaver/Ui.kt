package com.socialsaver

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.txt(s: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
    text = s; textSize = size; setPadding(0, dp(6), 0, dp(6))
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

fun Context.btn(s: String, onClick: () -> Unit) = Button(this).apply { text = s; setOnClickListener { onClick() } }

fun Context.radioGroup(items: List<Pair<String, Int>>, checked: Int = 0, onChange: (() -> Unit)? = null) =
    RadioGroup(this).apply {
        orientation = RadioGroup.VERTICAL
        items.forEach { (t, v) ->
            addView(RadioButton(context).apply { id = View.generateViewId(); text = t; tag = v })
        }
        check(getChildAt(checked).id)
        setOnCheckedChangeListener { _, _ -> onChange?.invoke() }
    }

fun RadioGroup.value(): Int = (findViewById<RadioButton>(checkedRadioButtonId)?.tag as? Int) ?: 0

fun Context.row(vararg v: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    v.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
}
