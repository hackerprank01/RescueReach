package com.rescuereach.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatTextView;

import com.rescuereach.R;
import com.rescuereach.service.appearance.AppearanceManager;

/**
 * A TextView that is aware of the app's font scaling settings
 * and can adjust its text size accordingly
 */
public class FontSizeAwareTextView extends AppCompatTextView {

    private float originalTextSize = 0;
    private boolean respectFontScaling = true;

    public FontSizeAwareTextView(Context context) {
        super(context);
        init(context, null);
    }

    public FontSizeAwareTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FontSizeAwareTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // Store the original text size
        originalTextSize = getTextSize() / context.getResources().getDisplayMetrics().scaledDensity;

        // Check if this view should respect font scaling
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FontSizeAwareTextView);
            respectFontScaling = a.getBoolean(R.styleable.FontSizeAwareTextView_respectFontScaling, true);
            a.recycle();
        }

        // Apply appropriate text size
        applyFontScaling(context);
    }

    private void applyFontScaling(Context context) {
        if (respectFontScaling) {
            // Get the current font scale from appearance manager
            float appFontScale = AppearanceManager.getInstance(context).getFontScaleFactor();

            // Apply scaling to the original text size
            setTextSize(TypedValue.COMPLEX_UNIT_SP, originalTextSize * appFontScale);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyFontScaling(getContext());
    }

    /**
     * Set whether this view should respect font scaling
     */
    public void setRespectFontScaling(boolean respectFontScaling) {
        this.respectFontScaling = respectFontScaling;
        applyFontScaling(getContext());
    }

    /**
     * Override to store original text size
     */
    @Override
    public void setTextSize(float size) {
        originalTextSize = size;
        applyFontScaling(getContext());
    }

    /**
     * Override to store original text size
     */
    @Override
    public void setTextSize(int unit, float size) {
        if (unit == TypedValue.COMPLEX_UNIT_SP) {
            originalTextSize = size;
        } else {
            // Convert to SP for consistent scaling
            originalTextSize = size / getContext().getResources().getDisplayMetrics().scaledDensity;
        }
        applyFontScaling(getContext());
    }
}