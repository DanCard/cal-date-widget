package ai.dcar.caldatewidget

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar

object ColorPickerDialog {

    /**
        * Reusable RGB color picker dialog used by both config screens.
        */
    fun show(context: Context, title: String, initialColor: Int, onColorPicked: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val seekR = dialogView.findViewById<SeekBar>(R.id.seek_r)
        val seekG = dialogView.findViewById<SeekBar>(R.id.seek_g)
        val seekB = dialogView.findViewById<SeekBar>(R.id.seek_b)
        val preview = dialogView.findViewById<View>(R.id.cp_preview)

        seekR.max = 255
        seekG.max = 255
        seekB.max = 255
        seekR.progress = Color.red(initialColor)
        seekG.progress = Color.green(initialColor)
        seekB.progress = Color.blue(initialColor)
        preview.setBackgroundColor(initialColor or -0x1000000) // Force full alpha for preview

        val updatePreview = {
            val c = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
            preview.setBackgroundColor(c)
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { updatePreview() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }
        seekR.setOnSeekBarChangeListener(listener)
        seekG.setOnSeekBarChangeListener(listener)
        seekB.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Select") { _, _ ->
                val color = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
                onColorPicked(color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
