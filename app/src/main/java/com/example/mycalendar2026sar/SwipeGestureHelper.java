package com.example.mycalendar2026sar;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;

public class SwipeGestureHelper {

    private static final int SWIPE_THRESHOLD = 120;
    private static final int SWIPE_VELOCITY_THRESHOLD = 120;

    public static GestureDetector createSwipeDetector(Context context, Runnable onRightToLeft, Runnable onLeftToRight) {
        return new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX < 0) {
                            if (onRightToLeft != null) {
                                onRightToLeft.run();
                                return true;
                            }
                        } else {
                            if (onLeftToRight != null) {
                                onLeftToRight.run();
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
    }
}
