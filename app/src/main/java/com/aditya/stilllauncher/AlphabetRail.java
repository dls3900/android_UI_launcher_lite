package com.aditya.stilllauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

final class AlphabetRail extends View {
    interface Listener { void onLetter(char letter); }
    private static final int ACCENT = 0xFF4C6EDB;
    private static final float RISE_MS = 55f;
    private static final float FALL_MS = 85f;
    private String letters = "☆ABCDEFGHIJKLMNOPQRSTUVWXYZ○";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float edge;
    private final float strip;
    private final float bulge;
    private final float sigma;
    private final float bubbleRadius;
    private final float bubbleGap;
    private final float pad;
    private Listener listener;
    private boolean leftSide;
    private int selected = -1;
    private int touched = -1;
    private boolean dragging;
    private float step;
    private float baseline;
    private float bubbleBaseline;
    private float focusY;
    private float wave;
    private float waveTarget;
    private long lastFrame;

    AlphabetRail(Context context) {
        super(context);
        setContentDescription("Alphabet app index");
        setFocusable(true);
        density = getResources().getDisplayMetrics().density;
        edge = 12 * density;
        strip = 34 * density;
        bulge = 84 * density;
        sigma = 74 * density;
        bubbleRadius = 16 * density;
        bubbleGap = 26 * density;
        pad = bubbleRadius + 2 * density;
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.CENTER);
        bubblePaint.setColor(ACCENT);
        bubbleTextPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setColor(Color.WHITE);
        bubbleTextPaint.setTextSize(18 * density);
        bubbleBaseline = -(bubbleTextPaint.ascent() + bubbleTextPaint.descent()) / 2f;
    }

    void setListener(Listener listener) { this.listener = listener; }
    void clearSelection() { selected = -1; invalidate(); }
    void setSections(String sections) {
        String next = "☆" + sections + "○";
        // App list refreshes are frequent; keep the highlight and any drag when nothing changed.
        if (next.equals(letters)) return;
        letters = next;
        selected = -1;
        touched = -1;
        measureLetters();
        invalidate();
    }
    void setLeftSide(boolean left) {
        if (leftSide == left) return;
        leftSide = left;
        invalidate();
    }
    int count() { return letters.length(); }
    /** Width needed for the letters plus the wave and bubble that swing out of them. */
    int railWidth() { return (int) Math.ceil(edge + strip / 2f + bulge + bubbleGap + bubbleRadius + 4 * density); }
    /** Space kept above the first and below the last letter so the bubble is never clipped. */
    int railPad() { return (int) Math.ceil(pad); }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        measureLetters();
    }

    private void measureLetters() {
        step = Math.max(0f, getHeight() - 2 * pad) / letters.length();
        paint.setTextSize(Math.min(17f * density, step * .88f));
        baseline = -(paint.ascent() + paint.descent()) / 2f;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = letters.length();
        if (step <= 0f) return;
        boolean animating = advanceWave();
        float baseX = leftSide ? edge + strip / 2f : getWidth() - edge - strip / 2f;
        float direction = leftSide ? 1f : -1f;
        float reach = direction * bulge * wave;
        for (int i = 0; i < count; i++) {
            float centerY = pad + (i + .5f) * step;
            float x = baseX;
            if (wave > 0f) {
                float distance = (centerY - focusY) / sigma;
                x += reach * (float) Math.exp(-.5f * distance * distance);
            }
            paint.setColor(i == selected ? Color.WHITE : 0xCCFFFFFF);
            canvas.drawText(letters, i, i + 1, x, centerY + baseline, paint);
        }
        if (wave > 0f && touched >= 0 && touched < count) {
            float bubbleX = baseX + reach + direction * bubbleGap * wave;
            int alpha = (int) (255 * wave);
            bubblePaint.setAlpha(alpha);
            bubbleTextPaint.setAlpha(alpha);
            int save = canvas.save();
            canvas.scale(wave, wave, bubbleX, focusY);
            canvas.drawCircle(bubbleX, focusY, bubbleRadius, bubblePaint);
            canvas.drawText(letters, touched, touched + 1, bubbleX, focusY + bubbleBaseline, bubbleTextPaint);
            canvas.restoreToCount(save);
        }
        if (animating) postInvalidateOnAnimation();
    }

    /** Eases the wave toward its target; time based so it stays even when frames drop. */
    private boolean advanceWave() {
        if (wave == waveTarget) return false;
        long now = SystemClock.uptimeMillis();
        float elapsed = Math.min(now - lastFrame, 48);
        lastFrame = now;
        wave += (waveTarget - wave) * (1f - (float) Math.exp(-elapsed / (waveTarget > wave ? RISE_MS : FALL_MS)));
        if (Math.abs(waveTarget - wave) < .004f) wave = waveTarget;
        return wave != waveTarget;
    }

    private void setWaveTarget(float target) {
        waveTarget = target;
        lastFrame = SystemClock.uptimeMillis();
        if (!ValueAnimator.areAnimatorsEnabled()) wave = target;
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Only the letter strip is touchable; the rest of the view is room for the wave.
                if (step <= 0f || !inStrip(event.getX())) return false;
                dragging = true;
                touched = -1;
                getParent().requestDisallowInterceptTouchEvent(true);
                track(event.getY());
                setWaveTarget(1f);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) track(event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                setWaveTarget(0f);
                performClick();
                return true;
            default: return dragging;
        }
    }

    private boolean inStrip(float x) {
        return leftSide ? x <= edge + strip : x >= getWidth() - edge - strip;
    }

    private void track(float y) {
        int count = letters.length();
        if (step <= 0f) return;
        focusY = Math.max(pad + step / 2f, Math.min(getHeight() - pad - step / 2f, y));
        int next = Math.max(0, Math.min(count - 1, (int) ((y - pad) / step)));
        invalidate();
        if (next != touched) {
            touched = next;
            selected = next;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onLetter(letters.charAt(next));
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
