package com.example.reflowoven.ui.screen

import android.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet

@Composable
fun OvenChart(
    points: List<Entry>,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)

                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.textColor = Color.LTGRAY
                xAxis.axisLineColor = Color.LTGRAY

                axisLeft.axisMinimum = 0f
                axisLeft.axisMaximum = 280f
                axisLeft.textColor = Color.LTGRAY
                axisLeft.axisLineColor = Color.LTGRAY

                axisRight.isEnabled = false
                legend.textColor = Color.LTGRAY
            }
        },
        update = { chart ->
            if (points.isNotEmpty()) {
                val dataSet = LineDataSet(points, "Temperature (°C)").apply {
                    color = Color.RED
                    setDrawCircles(false)
                    lineWidth = 3f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawValues(false)
                }

                if (chart.data != null && chart.data.dataSetCount > 0) {
                    val set = chart.data.getDataSetByIndex(0) as LineDataSet
                    set.values = points
                    set.notifyDataSetChanged()
                    chart.data.notifyDataChanged()
                    chart.notifyDataSetChanged()
                } else {
                    chart.data = LineData(dataSet)
                }
                chart.invalidate()
            } else {
                chart.clear()
            }
        }
    )
}