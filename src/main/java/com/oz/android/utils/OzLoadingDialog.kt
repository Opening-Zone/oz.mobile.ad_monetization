package com.oz.android.utils

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Utility for showing a fullscreen loading dialog during ad loading.
 */
object OzLoadingDialog {

    private var loadingDialog: Dialog? = null

    fun showFullScreenLoadingDialog(activity: Activity) {
        if (loadingDialog?.isShowing == true) return

        loadingDialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
            val rootLayout = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#80000000")) // Dark semi-transparent

                val progressBar = ProgressBar(activity)
                addView(
                    progressBar, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                val textView = TextView(activity).apply {
                    text = "Loading ads "
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setPadding(0, 16, 0, 0)
                }
                addView(
                    textView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            setContentView(rootLayout)
            setCancelable(false)
            show()
        }
    }

    fun hideFullScreenLoadingDialog() {
        try {
            loadingDialog?.dismiss()
        } catch (e: Exception) {
            // Context might already be destroyed
        } finally {
            loadingDialog = null
        }
    }
}
