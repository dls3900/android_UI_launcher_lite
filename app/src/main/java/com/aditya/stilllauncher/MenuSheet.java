package com.aditya.stilllauncher;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Rounded bottom sheet used for the app menu and the launcher settings. */
final class MenuSheet {
    private static final int SURFACE = 0xF015171E;
    private static final int OUTLINE = 0x1FFFFFFF;
    private static final int ACCENT = 0xFFAFC2FF;
    private static final int ACCENT_CHIP = 0x334C6EDB;
    private static final int WARN = 0xFFFF9A8F;
    private static final int WARN_CHIP = 0x33E5534B;
    private final Context context;
    private final Dialog dialog;
    private final FrameLayout root;
    private final LinearLayout list;
    private final ImageView headerIcon;
    private final float density;
    private int rows;

    MenuSheet(Context context, CharSequence title, CharSequence subtitle) {
        this.context = context;
        density = context.getResources().getDisplayMetrics().density;
        dialog = new Dialog(context, R.style.SheetDialog);

        root = new FrameLayout(context);
        root.setOnClickListener(v -> dialog.dismiss());
        int gap = dp(12);
        root.setPadding(gap, gap, gap, gap);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(gap, gap, gap, gap + insets.getSystemWindowInsetBottom());
            return insets;
        });

        GradientDrawable surface = new GradientDrawable();
        surface.setColor(SURFACE);
        surface.setCornerRadius(dp(28));
        surface.setStroke(Math.max(1, dp(.75f)), OUTLINE);
        ScrollView card = new ScrollView(context);
        card.setBackground(surface);
        card.setClipToOutline(true);
        card.setVerticalScrollBarEnabled(false);
        card.setOverScrollMode(View.OVER_SCROLL_NEVER);
        card.setClickable(true);
        // Full width on phones, capped so it stays a compact card on tablets and in landscape.
        int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - 2 * gap, dp(440));
        root.addView(card, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(10), 0, dp(10));
        card.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View handle = new View(context);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0x40FFFFFF);
        pill.setCornerRadius(dp(2));
        handle.setBackground(pill);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(36), dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        list.addView(handle, handleLp);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(16), dp(22), dp(14));
        headerIcon = new ImageView(context);
        headerIcon.setVisibility(View.GONE);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconLp.rightMargin = dp(16);
        header.addView(headerIcon, iconLp);
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(title, 20, Color.WHITE));
        if (subtitle != null) {
            TextView sub = text(subtitle, 13, 0x99FFFFFF);
            sub.setTypeface(Typeface.DEFAULT);
            sub.setPadding(0, dp(2), 0, 0);
            titles.addView(sub);
        }
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        list.addView(header);

        View divider = new View(context);
        divider.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        dividerLp.leftMargin = dp(22);
        dividerLp.rightMargin = dp(22);
        dividerLp.bottomMargin = dp(6);
        list.addView(divider, dividerLp);

        dialog.setContentView(root);
    }

    void setIcon(Bitmap icon) {
        if (icon == null) return;
        headerIcon.setImageBitmap(icon);
        headerIcon.setVisibility(View.VISIBLE);
    }

    MenuSheet item(int iconRes, CharSequence label, Runnable action) {
        return item(iconRes, label, null, false, action);
    }

    MenuSheet item(int iconRes, CharSequence label, CharSequence value, boolean warn, Runnable action) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        row.setPadding(dp(22), 0, dp(22), 0);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x30FFFFFF), null, new ColorDrawable(Color.WHITE)));
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });

        ImageView chip = new ImageView(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(warn ? WARN_CHIP : ACCENT_CHIP);
        chip.setBackground(circle);
        chip.setImageResource(iconRes);
        chip.setImageTintList(ColorStateList.valueOf(warn ? WARN : ACCENT));
        int inset = dp(10);
        chip.setPadding(inset, inset, inset, inset);
        row.addView(chip, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        labelLp.leftMargin = dp(16);
        row.addView(text(label, 16, warn ? WARN : Color.WHITE), labelLp);
        if (value != null) {
            TextView state = text(value, 14, 0x99FFFFFF);
            LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stateLp.leftMargin = dp(12);
            row.addView(state, stateLp);
        }

        list.addView(row);
        rows++;
        return this;
    }

    void show() {
        // Rows drift in one after another while the sheet slides up.
        for (int i = 0; i < rows; i++) {
            View row = list.getChildAt(list.getChildCount() - rows + i);
            row.setAlpha(0f);
            row.setTranslationY(dp(14));
            row.animate().alpha(1f).translationY(0f).setStartDelay(70 + 28L * i).setDuration(220).start();
        }
        Window window = dialog.getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (Build.VERSION.SDK_INT >= 31) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                window.getAttributes().setBlurBehindRadius(dp(16));
            }
        }
        dialog.show();
    }

    private TextView text(CharSequence value, int sp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private int dp(float value) { return (int) (value * density + .5f); }
}
