package com.aditya.stilllauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class AppListAdapter extends BaseAdapter {
    private final Context context;
    private final IconCache icons;
    private final List<AppEntry> entries = new ArrayList<>();
    private final int density;

    AppListAdapter(Context context, IconCache icons) {
        this.context = context;
        this.icons = icons;
        density = context.getResources().getDisplayMetrics().densityDpi;
    }

    void setEntries(List<AppEntry> next) {
        entries.clear();
        entries.addAll(next);
        notifyDataSetChanged();
    }

    AppEntry entryAt(int position) { return entries.get(position); }
    int indexFor(char letter) {
        String target = String.valueOf(letter);
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).section.compareTo(target) >= 0) return i;
        return Math.max(0, entries.size() - 1);
    }
    @Override public int getCount() { return entries.size(); }
    @Override public Object getItem(int position) { return entries.get(position); }
    @Override public long getItemId(int position) { return entries.get(position).key.hashCode(); }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Row row;
        if (convertView == null) {
            row = new Row(context);
            convertView = row;
        } else row = (Row) convertView;
        AppEntry entry = entries.get(position);
        row.setTag(entry.key);
        row.title.setText(entry.label);
        Bitmap icon = icons.getCached(entry, density);
        if (icon == null) {
            row.icon.setImageResource(android.R.drawable.sym_def_app_icon);
            Row boundRow = row;
            icons.request(entry, density, () -> {
                if (entry.key.equals(boundRow.getTag())) {
                    Bitmap loaded = icons.getCached(entry, density);
                    if (loaded != null) boundRow.icon.setImageBitmap(loaded);
                }
            });
        } else row.icon.setImageBitmap(icon);
        boolean header = position == 0 || !entry.section.equals(entries.get(position - 1).section);
        row.header.setVisibility(header ? View.VISIBLE : View.GONE);
        if (header) row.header.setText(entry.section);
        return row;
    }

    private static final class Row extends LinearLayout {
        final TextView header;
        final ImageView icon;
        final TextView title;
        Row(Context context) {
            super(context);
            setOrientation(VERTICAL);
            int side = dp(context, 4);
            setPadding(side, 0, side, 0);
            header = new TextView(context);
            header.setTextColor(0xBBFFFFFF);
            header.setTextSize(12);
            header.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            header.setPadding(0, dp(context, 12), 0, dp(context, 4));
            addView(header);
            LinearLayout line = new LinearLayout(context);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(dp(context, 52));
            icon = new ImageView(context);
            line.addView(icon, new LayoutParams(dp(context, 32), dp(context, 32)));
            title = new TextView(context);
            title.setTextColor(Color.WHITE);
            title.setTextSize(17);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LayoutParams titleLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            titleLp.leftMargin = dp(context, 16);
            line.addView(title, titleLp);
            addView(line);
        }
    }

    private static int dp(Context c, float value) { return (int) (value * c.getResources().getDisplayMetrics().density + .5f); }
}
