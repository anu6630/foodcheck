package com.foodcheck.ads

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
actual fun BannerAd(modifier: Modifier) {
    if (AdsController.adsSuppressed) return

    val context = LocalContext.current
    // Fixed 50dp banner — predictable height, avoids adaptive overflow into Compose.
    val adSize = AdSize.BANNER
    val heightPx = adSize.getHeightInPixels(context)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color(0xFF020617))
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clipToBounds(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    clipChildren = true
                    clipToPadding = true
                    setBackgroundColor(0xFF020617.toInt())
                    addView(
                        AdView(ctx).apply {
                            setAdSize(adSize)
                            adUnitId = AdUnits.BANNER
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                heightPx,
                            )
                            loadAd(AdRequest.Builder().build())
                        },
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            heightPx,
                            android.view.Gravity.CENTER,
                        ),
                    )
                }
            },
            onRelease = { frame ->
                (0 until frame.childCount).map { frame.getChildAt(it) }.forEach { child ->
                    (child as? AdView)?.destroy()
                }
                frame.removeAllViews()
            },
        )
    }
}
