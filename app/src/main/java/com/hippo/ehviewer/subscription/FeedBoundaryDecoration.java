package com.hippo.ehviewer.subscription;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hippo.ehviewer.client.data.GalleryInfo;

public final class FeedBoundaryDecoration extends RecyclerView.ItemDecoration {
    public interface ItemProvider { GalleryInfo get(int adapterPosition); }

    private final ItemProvider provider;
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;
    private final int height;
    private volatile FeedBoundary boundary = FeedBoundary.EMPTY;

    public FeedBoundaryDecoration(float density, float scaledDensity, int color,
                                  String label, ItemProvider provider) {
        this.provider = provider;
        this.label = label;
        this.height = Math.round(32 * density);
        linePaint.setColor(color);
        linePaint.setStrokeWidth(Math.max(1, density));
        textPaint.setColor(color);
        textPaint.setTextSize(13 * scaledDensity);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setBoundary(FeedBoundary value) {
        boundary = value == null ? FeedBoundary.EMPTY : value;
    }

    private boolean isMarker(int position) {
        GalleryInfo item = provider.get(position);
        return item != null && boundary.isFirstOld(item.postedTimestamp, item.gid);
    }

    @Override public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                                         @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position >= 0 && isMarker(position) && (position == 0 || !isMarker(position - 1))) {
            outRect.top = height;
        }
    }

    @Override public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                                 @NonNull RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position < 0 || !isMarker(position) || (position > 0 && isMarker(position - 1))) continue;
            float y = child.getTop() - height / 2f;
            float center = parent.getWidth() / 2f;
            float gap = textPaint.measureText(label) / 2f + 12 * parent.getResources().getDisplayMetrics().density;
            canvas.drawLine(parent.getPaddingLeft(), y, center - gap, y, linePaint);
            canvas.drawText(label, center, y - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint);
            canvas.drawLine(center + gap, y, parent.getWidth() - parent.getPaddingRight(), y, linePaint);
            break;
        }
    }
}
