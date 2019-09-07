package org.jak_linux.dns66;

import android.content.Context;
import android.os.Build;
import androidx.annotation.Nullable;
import android.util.AttributeSet;

/**
 * Workaround for Android 5.0 and 5.1 not correctly scaling vector drawables.
 * <p>
 * Android 5.0 and 5.1 only correctly scale vector drawables for scale type fitXY. We want to
 * keep the aspect ratio of our images however. This works around this by using fitXY and in
 * onMeasure() restricting width and height to the minimum of both, creating a square image.
 * <p>
 * To use this view, you have to make sure it is centered horizontally in its layout.
 */
public class SquareImageView extends androidx.appcompat.widget.AppCompatImageView {
    public SquareImageView(Context context) {
        super(context);
        if (Build.VERSION.SDK_INT < 23)
            setScaleType(ScaleType.FIT_XY);
    }

    public SquareImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (Build.VERSION.SDK_INT < 23)
            setScaleType(ScaleType.FIT_XY);
    }

    public SquareImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (Build.VERSION.SDK_INT < 23)
            setScaleType(ScaleType.FIT_XY);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (Build.VERSION.SDK_INT < 23) {
            int width = getMeasuredWidth();
            int height = getMeasuredHeight();
            int result = Math.min(width, height);
            setMeasuredDimension(result, result);
        }
    }

}
