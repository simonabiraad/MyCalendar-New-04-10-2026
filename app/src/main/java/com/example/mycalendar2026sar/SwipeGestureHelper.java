package com.example.mycalendar2026sar;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * Helper for swipe gestures. Page navigation swipes have been disabled as requested.
 */
public class SwipeGestureHelper {

    public static GestureDetector createSwipeDetector(Context context, Runnable onRightToLeft, Runnable onLeftToRight) {
        return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // Page navigation swipes disabled
                return false;
            }
        });
    }
}
