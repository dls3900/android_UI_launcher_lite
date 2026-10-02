package com.aditya.stilllauncher;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.AlarmClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HomeActivity extends ComponentActivity {
    private static final String PREFS = "launcher";
    private static final String FAVORITES = "favorites";
    private static final String DIM = "dim";
    private static final String LEFT = "left";
    private static final String OFFSET = "offset";
    private static final String RECENTS = "recents";
    private static final int SHOWN_RECENTS = 3;
    private final IconCache iconCache = new IconCache();
    private final List<AppEntry> apps = new ArrayList<>();
    private final LinkedHashSet<String> favoriteKeys = new LinkedHashSet<>();
    private final List<String> recentKeys = new ArrayList<>();
    private SharedPreferences prefs;
    private AppRepository repository;
    private FrameLayout root;
    private LinearLayout homeContent;
    private LinearLayout favoriteList;
    private FrameLayout appPanel;
    private ListView appList;
    private AppListAdapter adapter;
    private AlphabetRail rail;
    private EditText search;
    private TextView clock;
    private TextView date;
    private boolean showingApps;
    private int topInset;
    private int bottomInset;
    /** Letter picked on the rail; the list shows only that section. 0 shows every app. */
    private char letterFilter;
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateClock(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(FAVORITES, "");
        if (!saved.isEmpty()) favoriteKeys.addAll(Arrays.asList(saved.split("\\|")));
        String recent = prefs.getString(RECENTS, "");
        if (!recent.isEmpty()) recentKeys.addAll(Arrays.asList(recent.split("\\|")));
        createUi();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (showingApps) hideAllApps();
            }
        });
        repository = new AppRepository(this, this::onAppsChanged);
        repository.refresh();
        root.post(this::offerHomeRole);
    }

    private void createUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(prefs.getBoolean(DIM, true) ? 0x44000000 : Color.TRANSPARENT);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            topInset = insets.getSystemWindowInsetTop();
            bottomInset = insets.getSystemWindowInsetBottom();
            root.post(this::updatePositions);
            return insets;
        });
        setContentView(root);

        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        root.addView(homeContent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(48);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        homeContent.addView(clock);
        date = new TextView(this);
        date.setTextColor(0xF2FFFFFF);
        date.setTextSize(15);
        date.setPadding(0, 0, 0, 0);
        homeContent.addView(date);
        clock.setOnClickListener(v -> openSystemApp(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "Clock"));
        date.setOnClickListener(v -> openSystemApp(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_APP_CALENDAR), "Calendar"));
        clock.setOnLongClickListener(v -> { showSettings(); return true; });
        date.setOnLongClickListener(v -> { showSettings(); return true; });

        favoriteList = new LinearLayout(this);
        favoriteList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams favoritesLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        favoritesLp.topMargin = dp(46);
        homeContent.addView(favoriteList, favoritesLp);

        appPanel = new FrameLayout(this);
        appPanel.setBackgroundColor(Color.TRANSPARENT);
        appPanel.setVisibility(View.GONE);
        root.addView(appPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(0x99FFFFFF);
        search.setTextSize(18);
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48), Gravity.TOP);
        appPanel.addView(search, searchLp);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        appList = new ListView(this);
        appList.setCacheColorHint(Color.TRANSPARENT);
        appList.setBackgroundColor(Color.TRANSPARENT);
        appList.setDivider(null);
        appList.setSelector(android.R.color.transparent);
        appList.setVerticalScrollBarEnabled(false);
        appList.setClipToPadding(false);
        adapter = new AppListAdapter(this, iconCache);
        appList.setAdapter(adapter);
        FrameLayout.LayoutParams listLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        listLp.topMargin = dp(58);
        appPanel.addView(appList, listLp);
        appList.setOnItemClickListener((parent, view, position, id) -> launch(adapter.entryAt(position)));
        appList.setOnItemLongClickListener((parent, view, position, id) -> { showAppMenu(adapter.entryAt(position)); return true; });

        rail = new AlphabetRail(this);
        rail.setListener(letter -> {
            if (letter == '☆') { hideAllApps(); return; }
            if (letter == '○') {
                letterFilter = 0;
                showAllApps(true);
                filterApps(search.getText().toString());
                scrollListToTop();
                return;
            }
            if (!showingApps) showAllApps(false);
            else if (search.hasFocus()) {
                search.clearFocus();
                hideKeyboard();
            }
            letterFilter = letter;
            refreshList();
            scrollListToTop();
        });
        rail.setReleaseListener(() -> {
            if (!showingApps || letterFilter == 0) return;
            // Finger lifted: show the full list around the picked letter, keeping its apps
            // exactly where they were on screen instead of jumping to the top.
            char letter = letterFilter;
            int y = (int) appList.getTranslationY();
            letterFilter = 0;
            filterApps("");
            int index = adapter.indexFor(letter);
            // Room below the last apps, so letters near Z can stay in place too.
            int rest = 0;
            for (int i = index; i < adapter.getCount() && rest < appList.getHeight(); i++) {
                rest += dp(52);
                if (i == index || !adapter.entryAt(i).section.equals(adapter.entryAt(i - 1).section)) rest += dp(28);
            }
            appList.setPadding(0, 0, 0, Math.max(0, appList.getHeight() - y - rest));
            appList.smoothScrollBy(0, 0);
            appList.setSelectionFromTop(index, y);
            // Some devices drop a jump made right after the list's data changes and lay it out
            // from the top. Apply it again once that layout is done, before anything is drawn.
            appList.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    appList.getViewTreeObserver().removeOnPreDrawListener(this);
                    if (letterFilter != 0 || index >= adapter.getCount()) return true;
                    View first = appList.getChildAt(0);
                    boolean placed = appList.getFirstVisiblePosition() <= index
                            && index <= appList.getLastVisiblePosition()
                            && appList.getChildAt(index - appList.getFirstVisiblePosition()).getTop() == y;
                    if (placed || first == null) return true;
                    appList.setSelectionFromTop(index, y);
                    return false;
                }
            });
        });
        root.addView(rail, new FrameLayout.LayoutParams(rail.railWidth(), ViewGroup.LayoutParams.MATCH_PARENT));
        updateClock();
        root.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (b - t != oldB - oldT) updatePositions();
        });
    }

    private void updatePositions() {
        if (root.getHeight() == 0) return;
        int height = root.getHeight();
        FrameLayout.LayoutParams homeLp = (FrameLayout.LayoutParams) homeContent.getLayoutParams();
        homeLp.leftMargin = dp(48);
        homeLp.rightMargin = dp(52);
        int offset = prefs.getInt(OFFSET, 0);
        homeLp.topMargin = Math.max(topInset + dp(18), (int) (height * .22f) + dp(offset));
        homeContent.setLayoutParams(homeLp);
        FrameLayout.LayoutParams panelLp = (FrameLayout.LayoutParams) appPanel.getLayoutParams();
        panelLp.topMargin = topInset + dp(30);
        panelLp.bottomMargin = bottomInset;
        panelLp.leftMargin = dp(prefs.getBoolean(LEFT, false) ? 46 : 22);
        panelLp.rightMargin = dp(prefs.getBoolean(LEFT, false) ? 22 : 46);
        appPanel.setLayoutParams(panelLp);
        FrameLayout.LayoutParams railLp = (FrameLayout.LayoutParams) rail.getLayoutParams();
        // The rail reaches the screen edge and pads itself, so the letters stay where they were.
        railLp.gravity = prefs.getBoolean(LEFT, false) ? Gravity.LEFT | Gravity.TOP : Gravity.RIGHT | Gravity.TOP;
        railLp.leftMargin = 0;
        railLp.rightMargin = 0;
        railLp.topMargin = Math.max(topInset + dp(40), (int) (height * .39f)) - rail.railPad();
        railLp.height = Math.min((int) (height * .55f), dp(18) * rail.count()) + 2 * rail.railPad();
        railLp.bottomMargin = 0;
        rail.setLeftSide(prefs.getBoolean(LEFT, false));
        rail.setLayoutParams(railLp);
    }

    private void onAppsChanged(List<AppEntry> updated) {
        apps.clear();
        apps.addAll(updated);
        if (!prefs.contains(FAVORITES) && !apps.isEmpty()) chooseInitialFavorites();
        StringBuilder sections = new StringBuilder();
        for (AppEntry entry : apps) {
            if (sections.indexOf(entry.section) < 0) sections.append(entry.section);
        }
        rail.setSections(sections.toString());
        updatePositions();
        renderFavorites();
        filterApps(search.getText().toString());
    }

    private void chooseInitialFavorites() {
        // Let users add favorites from the app list, matching an empty Niagara setup.
        saveFavorites();
    }

    private void renderFavorites() {
        favoriteList.removeAllViews();
        for (String key : favoriteKeys) {
            AppEntry entry = findByKey(key);
            if (entry == null) continue;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(54));
            ImageView icon = new ImageView(this);
            int density = getResources().getDisplayMetrics().densityDpi;
            Bitmap cachedIcon = iconCache.getCached(entry, density);
            if (cachedIcon != null) icon.setImageBitmap(cachedIcon);
            else {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
                iconCache.request(entry, density, () -> {
                    Bitmap loaded = iconCache.getCached(entry, density);
                    if (loaded != null) icon.setImageBitmap(loaded);
                });
            }
            row.addView(icon, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView title = new TextView(this);
            title.setText(entry.label);
            title.setTextColor(Color.WHITE);
            title.setTextSize(17);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            titleLp.leftMargin = dp(17);
            row.addView(title, titleLp);
            favoriteList.addView(row);
            row.setOnClickListener(v -> launch(entry));
            row.setOnLongClickListener(v -> { showAppMenu(entry); return true; });
        }
    }

    private AppEntry findByKey(String key) {
        for (AppEntry entry : apps) if (entry.key.equals(key)) return entry;
        return null;
    }

    private void filterApps(String query) {
        if (adapter == null) return;
        String needle = query.trim().toLowerCase(Locale.getDefault());
        int top = 0;
        if (!needle.isEmpty()) {
            // Typing searches every app, whichever letter was picked before.
            if (letterFilter != 0) {
                letterFilter = 0;
                rail.clearSelection();
            }
            List<AppEntry> filtered = new ArrayList<>();
            for (AppEntry app : apps) if (app.label.toLowerCase(Locale.getDefault()).contains(needle)) filtered.add(app);
            adapter.setEntries(java.util.Collections.emptyList(), filtered);
        } else if (letterFilter == 0) {
            adapter.setEntries(recentApps(), apps);
        } else {
            // While a finger is on the rail, only that letter's apps show, in the middle of the screen.
            List<AppEntry> section = new ArrayList<>();
            for (AppEntry app : apps) if (app.section.charAt(0) == letterFilter) section.add(app);
            adapter.setEntries(java.util.Collections.emptyList(), section);
            int listTop = topInset + dp(30) + dp(58);
            int content = dp(32) + section.size() * dp(52);
            top = Math.max(0, root.getHeight() / 2 - content / 2 - listTop);
        }
        // Moved rather than padded, so the list's own scroll position math is unaffected.
        appList.setTranslationY(top);
    }

    /** Clears the search box and rebuilds the list for the current letter. */
    private void refreshList() {
        if (search.length() > 0) search.setText("");
        else filterApps("");
    }

    private void scrollListToTop() {
        // Stop any fling first, otherwise it keeps scrolling the new list.
        appList.smoothScrollBy(0, 0);
        if (appList.getPaddingBottom() != 0) appList.setPadding(0, 0, 0, 0);
        appList.setSelection(0);
    }

    private List<AppEntry> recentApps() {
        List<AppEntry> recent = new ArrayList<>();
        for (String key : recentKeys) {
            AppEntry entry = findByKey(key);
            if (entry != null) recent.add(entry);
            if (recent.size() == SHOWN_RECENTS) break;
        }
        return recent;
    }

    private void rememberRecent(AppEntry entry) {
        recentKeys.remove(entry.key);
        recentKeys.add(0, entry.key);
        // Keep a few spares so the row stays full after an app is uninstalled.
        while (recentKeys.size() > 2 * SHOWN_RECENTS) recentKeys.remove(recentKeys.size() - 1);
        prefs.edit().putString(RECENTS, String.join("|", recentKeys)).apply();
    }

    private void showAllApps(boolean focusSearch) {
        showingApps = true;
        homeContent.setVisibility(View.INVISIBLE);
        root.setBackgroundColor(0x88000000);
        appPanel.setVisibility(View.VISIBLE);
        if (focusSearch) {
            search.requestFocus();
            search.post(() -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT));
        } else {
            search.clearFocus();
            hideKeyboard();
        }
    }

    private void hideAllApps() {
        showingApps = false;
        letterFilter = 0;
        appPanel.setVisibility(View.GONE);
        homeContent.setVisibility(View.VISIBLE);
        root.setBackgroundColor(prefs.getBoolean(DIM, true) ? 0x44000000 : Color.TRANSPARENT);
        refreshList();
        rail.clearSelection();
        hideKeyboard();
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void launch(AppEntry entry) {
        try {
            repository.launch(entry);
            rememberRecent(entry);
            hideAllApps();
        } catch (RuntimeException e) {
            Toast.makeText(this, "Could not open " + entry.label, Toast.LENGTH_SHORT).show();
            repository.refresh();
        }
    }

    private void showAppMenu(AppEntry entry) {
        boolean favorite = favoriteKeys.contains(entry.key);
        MenuSheet sheet = new MenuSheet(this, entry.label, favorite ? "In favorites" : null);
        int density = getResources().getDisplayMetrics().densityDpi;
        Bitmap icon = iconCache.getCached(entry, density);
        if (icon != null) sheet.setIcon(icon);
        else iconCache.request(entry, density, () -> sheet.setIcon(iconCache.getCached(entry, density)));
        sheet.item(favorite ? R.drawable.ic_sheet_star : R.drawable.ic_sheet_star_outline,
                favorite ? "Remove from favorites" : "Add to favorites", () -> {
                    if (favorite) favoriteKeys.remove(entry.key);
                    else favoriteKeys.add(entry.key);
                    saveFavorites();
                    renderFavorites();
                });
        // Reordering only applies to apps that are already favorites.
        if (favorite) {
            sheet.item(R.drawable.ic_sheet_up, "Move favorite up", () -> moveFavorite(entry, -1));
            sheet.item(R.drawable.ic_sheet_down, "Move favorite down", () -> moveFavorite(entry, 1));
        }
        sheet.item(R.drawable.ic_sheet_info, "App info", () -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + entry.component.getPackageName()))));
        sheet.show();
    }

    private void moveFavorite(AppEntry entry, int step) {
        List<String> order = new ArrayList<>(favoriteKeys);
        int at = order.indexOf(entry.key);
        int next = at + step;
        if (at < 0 || next < 0 || next >= order.size()) return;
        java.util.Collections.swap(order, at, next);
        favoriteKeys.clear();
        favoriteKeys.addAll(order);
        saveFavorites();
        renderFavorites();
    }

    private void saveFavorites() { prefs.edit().putString(FAVORITES, String.join("|", favoriteKeys)).apply(); }

    private void showSettings() {
        boolean left = prefs.getBoolean(LEFT, false);
        boolean dimmed = prefs.getBoolean(DIM, true);
        new MenuSheet(this, "Still Launcher", "Home screen settings")
                .item(R.drawable.ic_sheet_up, "Move content up", () -> {
                    prefs.edit().putInt(OFFSET, Math.max(-120, prefs.getInt(OFFSET, 0) - 20)).apply();
                    updatePositions();
                })
                .item(R.drawable.ic_sheet_down, "Move content down", () -> {
                    prefs.edit().putInt(OFFSET, Math.min(120, prefs.getInt(OFFSET, 0) + 20)).apply();
                    updatePositions();
                })
                .item(R.drawable.ic_sheet_swap, "Switch alphabet side", left ? "Left" : "Right", false, () -> {
                    prefs.edit().putBoolean(LEFT, !left).apply();
                    updatePositions();
                })
                .item(R.drawable.ic_sheet_dim, dimmed ? "Remove wallpaper dim" : "Dim wallpaper", dimmed ? "On" : "Off", false, () -> {
                    prefs.edit().putBoolean(DIM, !dimmed).apply();
                    root.setBackgroundColor(!dimmed ? 0x44000000 : Color.TRANSPARENT);
                })
                .item(R.drawable.ic_sheet_home, "Choose default Home app", this::openHomeSettings)
                .item(R.drawable.ic_sheet_reset, "Reset favorites", null, true, () -> {
                    favoriteKeys.clear();
                    prefs.edit().remove(FAVORITES).apply();
                    chooseInitialFavorites();
                    renderFavorites();
                })
                .show();
    }

    private void offerHomeRole() {
        if (prefs.getBoolean("home_prompted", false)) return;
        prefs.edit().putBoolean("home_prompted", true).apply();
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = (RoleManager) getSystemService(ROLE_SERVICE);
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1);
            }
        } else openHomeSettings();
    }

    private void openHomeSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (RuntimeException e) { Toast.makeText(this, "Select Still Launcher in Default apps", Toast.LENGTH_LONG).show(); }
    }

    private void openSystemApp(Intent intent, String appName) {
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) {
            AppEntry fallback = null;
            for (AppEntry app : apps) {
                if (app.label.equalsIgnoreCase(appName)) { fallback = app; break; }
                if (fallback == null && app.component.getPackageName().toLowerCase(Locale.ROOT)
                        .contains(appName.toLowerCase(Locale.ROOT))) fallback = app;
            }
            if (fallback != null) {
                launch(fallback);
            } else Toast.makeText(this, "No app available for this shortcut", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateClock() {
        if (clock == null) return;
        Date now = new Date();
        boolean is24 = DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(is24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now));
        String day = new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(now);
        date.setText(day);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockReceiver, filter);
        updateClock();
    }

    @Override protected void onStop() {
        unregisterReceiver(clockReceiver);
        super.onStop();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        hideAllApps();
        updateClock();
    }

    @Override protected void onDestroy() {
        if (repository != null) repository.close();
        iconCache.close();
        super.onDestroy();
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
}
