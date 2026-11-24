package com.dumbos.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeActivity extends Activity {

    private static final String PREFS_NAME = "dumb_launcher_prefs";
    private static final String KEY_FAVORITES = "favorites_json";

    private static final class AppEntry {
        final String packageName;
        final String activityName;
        final String label;

        AppEntry(String packageName, String activityName, String label) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.label = label;
        }

        String componentKey() {
            return packageName + "/" + activityName;
        }
    }

    private View homeView;
    private View drawerView;
    private LinearLayout favoritesContainer;
    private TextView btnModePill;
    private EditText drawerSearch;
    private ListView drawerList;

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> filteredApps = new ArrayList<>();
    private final List<AppEntry> favorites = new ArrayList<>();
    private ArrayAdapter<AppEntry> drawerAdapter;
    private GestureDetector homeGestureDetector;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver modeObserver = new ContentObserver(mainHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateModePill();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadAppsAndFavorites();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        homeView = findViewById(R.id.home_view);
        drawerView = findViewById(R.id.drawer_view);
        favoritesContainer = findViewById(R.id.favorites_container);
        btnModePill = findViewById(R.id.btn_mode_pill);
        drawerSearch = findViewById(R.id.drawer_search);
        drawerList = findViewById(R.id.drawer_list);

        findViewById(R.id.home_clock_block).setOnClickListener(v -> openClockApp());
        findViewById(R.id.btn_lock_screen).setOnClickListener(v -> DumbModeController.lockScreen(this));
        findViewById(R.id.btn_open_drawer).setOnClickListener(v -> showDrawer());
        findViewById(R.id.btn_close_drawer).setOnClickListener(v -> showHome());

        btnModePill.setOnClickListener(v -> {
            DumbModeController.cycleMode(this);
            updateModePill();
        });
        btnModePill.setOnLongClickListener(v -> {
            showModePickerDialog();
            return true;
        });

        setupDrawer();
        setupGestures();

        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(DumbModeController.SETTINGS_KEY_MODE),
                false, modeObserver);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        reloadAppsAndFavorites();
        updateModePill();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateModePill();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showHome();
    }

    @Override
    public void onBackPressed() {
        if (drawerView.getVisibility() == View.VISIBLE) {
            showHome();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            getContentResolver().unregisterContentObserver(modeObserver);
        } catch (Throwable ignored) {
        }
        try {
            unregisterReceiver(packageReceiver);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    private void setupDrawer() {
        drawerAdapter = new ArrayAdapter<AppEntry>(this, R.layout.item_drawer_app, filteredApps) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) (convertView != null
                        ? convertView
                        : LayoutInflater.from(getContext()).inflate(R.layout.item_drawer_app, parent, false));
                AppEntry item = getItem(position);
                tv.setText(item != null ? item.label : "");
                return tv;
            }
        };
        drawerList.setAdapter(drawerAdapter);
        drawerList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < filteredApps.size()) {
                launchApp(filteredApps.get(position));
                showHome();
            }
        });
        drawerList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < filteredApps.size()) {
                showAppOptionsDialog(filteredApps.get(position));
            }
            return true;
        });

        drawerSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterDrawer(s != null ? s.toString() : "");
            }
        });

        drawerSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                if (filteredApps.size() == 1) {
                    launchApp(filteredApps.get(0));
                    showHome();
                    return true;
                }
            }
            return false;
        });
    }

    private void setupGestures() {
        homeGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                float dy = e1.getY() - e2.getY();
                if (dy > 120 && Math.abs(velocityY) > 250) {
                    showDrawer();
                    return true;
                }
                return false;
            }
        });

        homeView.setOnTouchListener((v, event) -> homeGestureDetector.onTouchEvent(event));
    }

    private void showDrawer() {
        drawerSearch.setText("");
        filterDrawer("");
        homeView.setVisibility(View.GONE);
        drawerView.setVisibility(View.VISIBLE);
    }

    private void showHome() {
        hideKeyboard();
        drawerView.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null && drawerSearch != null) {
                imm.hideSoftInputFromWindow(drawerSearch.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateModePill() {
        String mode = DumbModeController.getCurrentMode(this);
        btnModePill.setText("Mode: " + mode);
    }

    private void reloadAppsAndFavorites() {
        allApps.clear();
        PackageManager pm = getPackageManager();
        Intent queryIntent = new Intent(Intent.ACTION_MAIN);
        queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = pm.queryIntentActivities(queryIntent, 0);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolved) {
            if (ri.activityInfo == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            String cls = ri.activityInfo.name;
            if ("com.dumbos.launcher".equals(pkg) || "com.android.launcher3".equals(pkg)) {
                continue;
            }
            String key = pkg + "/" + cls;
            if (!seen.add(key)) {
                continue;
            }
            CharSequence cs = ri.loadLabel(pm);
            String label = cs != null ? cs.toString().trim() : pkg;
            allApps.add(new AppEntry(pkg, cls, label));
        }

        final Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        Collections.sort(allApps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return collator.compare(a.label, b.label);
            }
        });

        loadFavorites();
        renderFavorites();
        filterDrawer(drawerSearch.getText() != null ? drawerSearch.getText().toString() : "");
    }

    private void filterDrawer(String query) {
        filteredApps.clear();
        String q = query == null ? "" : query.trim().toLowerCase();
        for (AppEntry entry : allApps) {
            if (q.isEmpty() || entry.label.toLowerCase().contains(q)) {
                filteredApps.add(entry);
            }
        }
        drawerAdapter.notifyDataSetChanged();
    }

    private void loadFavorites() {
        favorites.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_FAVORITES, null);
        if (jsonStr != null) {
            try {
                JSONArray arr = new JSONArray(jsonStr);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String pkg = obj.getString("pkg");
                    String cls = obj.getString("cls");
                    String label = obj.getString("label");
                    if (isAppInstalled(pkg, cls)) {
                        favorites.add(new AppEntry(pkg, cls, label));
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (favorites.isEmpty() && jsonStr == null) {
            seedDefaultFavorites();
            saveFavorites();
        }
    }

    private void seedDefaultFavorites() {
        addDefaultFavorite("com.android.dialer", "Phone");
        addDefaultFavorite("com.google.android.dialer", "Phone");
        addDefaultFavorite("com.android.messaging", "Messages");
        addDefaultFavorite("com.whatsapp", "WhatsApp");
        addDefaultFavorite("org.lineageos.aperture", "Camera");
        addDefaultFavorite("com.android.camera2", "Camera");
        addDefaultFavorite("net.gsantner.markor", "Notes");
        addDefaultFavorite("com.android.deskclock", "Clock");
        addDefaultFavorite("com.aurora.store", "App Store");
        addDefaultFavorite("com.android.settings", "Settings");
    }

    private void addDefaultFavorite(String pkgPrefix, String customLabel) {
        if (favorites.size() >= 7) {
            return;
        }
        for (AppEntry e : allApps) {
            if (e.packageName.equals(pkgPrefix)) {
                for (AppEntry existing : favorites) {
                    if (existing.packageName.equals(pkgPrefix)) {
                        return;
                    }
                }
                favorites.add(new AppEntry(e.packageName, e.activityName, customLabel));
                return;
            }
        }
    }

    private void saveFavorites() {
        try {
            JSONArray arr = new JSONArray();
            for (AppEntry e : favorites) {
                JSONObject obj = new JSONObject();
                obj.put("pkg", e.packageName);
                obj.put("cls", e.activityName);
                obj.put("label", e.label);
                arr.put(obj);
            }
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_FAVORITES, arr.toString())
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private boolean isAppInstalled(String pkg, String cls) {
        for (AppEntry e : allApps) {
            if (e.packageName.equals(pkg) && e.activityName.equals(cls)) {
                return true;
            }
        }
        return false;
    }

    private void renderFavorites() {
        favoritesContainer.removeAllViews();
        if (favorites.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Open All Apps to pin favorites");
            empty.setTextColor(Color.parseColor("#666666"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setOnClickListener(v -> showDrawer());
            favoritesContainer.addView(empty);
            return;
        }

        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);

        for (int i = 0; i < favorites.size(); i++) {
            final int index = i;
            final AppEntry entry = favorites.get(i);
            TextView tv = new TextView(this);
            tv.setText(entry.label);
            tv.setTextColor(Color.parseColor("#E0E0E0"));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tv.setPadding(0, dp(10), 0, dp(10));
            if (ripple.resourceId != 0) {
                tv.setBackgroundResource(ripple.resourceId);
            }
            tv.setOnClickListener(v -> launchApp(entry));
            tv.setOnLongClickListener(v -> {
                showFavoriteOptionsDialog(index);
                return true;
            });
            favoritesContainer.addView(tv);
        }
    }

    private void launchApp(AppEntry entry) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(entry.packageName, entry.activityName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void openClockApp() {
        for (AppEntry e : allApps) {
            if (e.packageName.contains("deskclock")) {
                launchApp(e);
                return;
            }
        }
    }

    private void showModePickerDialog() {
        new AlertDialog.Builder(this, R.style.Theme_DumbOS_Dialog)
                .setTitle("Select DumbOS Mode")
                .setItems(DumbModeController.MODES, (dialog, which) -> {
                    DumbModeController.applyMode(this, DumbModeController.MODES[which]);
                    updateModePill();
                })
                .show();
    }

    private void showFavoriteOptionsDialog(final int index) {
        final AppEntry entry = favorites.get(index);
        String[] actions = new String[] { "Move Up", "Rename", "Remove from Home", "App Info" };
        new AlertDialog.Builder(this, R.style.Theme_DumbOS_Dialog)
                .setTitle(entry.label)
                .setItems(actions, (d, which) -> {
                    if (which == 0 && index > 0) {
                        Collections.swap(favorites, index, index - 1);
                        saveFavorites();
                        renderFavorites();
                    } else if (which == 1) {
                        showRenameFavoriteDialog(index);
                    } else if (which == 2) {
                        favorites.remove(index);
                        saveFavorites();
                        renderFavorites();
                    } else if (which == 3) {
                        openAppInfo(entry.packageName);
                    }
                })
                .show();
    }

    private void showRenameFavoriteDialog(final int index) {
        final AppEntry entry = favorites.get(index);
        final EditText input = new EditText(this);
        input.setText(entry.label);
        input.setTextColor(Color.WHITE);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        new AlertDialog.Builder(this, R.style.Theme_DumbOS_Dialog)
                .setTitle("Rename")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String next = input.getText().toString().trim();
                    if (!next.isEmpty()) {
                        favorites.set(index, new AppEntry(entry.packageName, entry.activityName, next));
                        saveFavorites();
                        renderFavorites();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAppOptionsDialog(final AppEntry entry) {
        boolean isFav = false;
        for (AppEntry f : favorites) {
            if (f.componentKey().equals(entry.componentKey())) {
                isFav = true;
                break;
            }
        }
        String favAction = isFav ? "Remove from Favorites" : "Pin to Home";
        final boolean alreadyFav = isFav;
        String[] actions = new String[] { favAction, "App Info", "Uninstall" };
        new AlertDialog.Builder(this, R.style.Theme_DumbOS_Dialog)
                .setTitle(entry.label)
                .setItems(actions, (d, which) -> {
                    if (which == 0) {
                        if (alreadyFav) {
                            for (int i = 0; i < favorites.size(); i++) {
                                if (favorites.get(i).componentKey().equals(entry.componentKey())) {
                                    favorites.remove(i);
                                    break;
                                }
                            }
                        } else {
                            favorites.add(entry);
                        }
                        saveFavorites();
                        renderFavorites();
                    } else if (which == 1) {
                        openAppInfo(entry.packageName);
                    } else if (which == 2) {
                        Intent del = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entry.packageName));
                        del.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(del);
                    }
                })
                .show();
    }

    private void openAppInfo(String pkg) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private int dp(int val) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics()));
    }
}
