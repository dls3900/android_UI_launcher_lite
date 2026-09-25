package com.aditya.stilllauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

final class AlphabetRail extends View {
    interface Listener { void onLetter(char letter); }
    private String letters = "☆ABCDEFGHIJKLMNOPQRSTUVWXYZ○";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private int selected = -1;
    private boolean dragging;

    AlphabetRail(Context context) {
        super(context);
        setContentDescription("Alphabet app index");
        setFocusable(true);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
    }

    void setListener(Listener listener) { this.listener = listener; }
    void clearSelection() { selected = -1; invalidate(); }
    void setSections(String sections) {
        letters = "☆" + sections + "○";
        selected = -1;
        invalidate();
    }
    int count() { return letters.length(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float textSize = Math.min(17f * density, getHeight() / (float) letters.length() * .88f);
        paint.setTextSize(textSize);
        float step = getHeight() / (float) letters.length();
        for (int i = 0; i < letters.length(); i++) {
            float y = (i + .5f) * step - (paint.ascent() + paint.descent()) / 2f;
            paint.setColor(i == selected ? Color.WHITE : 0xCCFFFFFF);
            if (i == selected) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x88484848);
                canvas.drawCircle(getWidth() / 2f, (i + .5f) * step, Math.max(step * .55f, 11 * density), paint);
                paint.setColor(Color.WHITE);
            }
            canvas.drawText(letters.substring(i, i + 1), getWidth() / 2f, y, paint);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                select(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) select(event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                performClick();
                return true;
            default: return false;
        }
    }

    private void select(float y) {
        int next = Math.max(0, Math.min(letters.length() - 1, (int) (y / getHeight() * letters.length())));
        if (next != selected) {
            selected = next;
            invalidate();
            if (listener != null) listener.onLetter(letters.charAt(next));
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
