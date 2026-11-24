package com.dumbos.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure Black & White OLED Text-Only Recents Task Switcher.
 * Runs in the single com.dumbos.launcher process (~0 MB extra process overhead).
 */
public class RecentsActivity extends Activity {

    public static final String ACTION_TOGGLE_RECENTS = "com.dumbos.launcher.ACTION_TOGGLE_RECENTS";
    public static final String ACTION_SHOW_RECENTS = "com.dumbos.launcher.ACTION_SHOW_RECENTS";

    private static volatile WeakReference<RecentsActivity> sActiveRef;
    private static volatile boolean sIsVisible = false;

    private static final class RecentItem {
        final int taskId;
        final String packageName;
        final String label;
        final Intent baseIntent;
        final boolean isRunning;
        final int memoryMb;

        RecentItem(int taskId, String packageName, String label, Intent baseIntent,
                   boolean isRunning, int memoryMb) {
            this.taskId = taskId;
            this.packageName = packageName;
            this.label = label;
            this.baseIntent = baseIntent;
            this.isRunning = isRunning;
            this.memoryMb = memoryMb;
        }
    }

    private final List<RecentItem> recentItems = new ArrayList<>();
    private ArrayAdapter<RecentItem> adapter;
    private ListView listView;
    private TextView emptyView;
    private TextView ramInfoView;
    private TextView clearAllBtn;

    public static void finishIfActive() {
        WeakReference<RecentsActivity> ref = sActiveRef;
        RecentsActivity act = ref != null ? ref.get() : null;
        if (act != null && !act.isFinishing() && !act.isDestroyed()) {
            act.finish();
            act.overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sActiveRef = new WeakReference<>(this);
        setContentView(R.layout.activity_recents);

        listView = findViewById(R.id.recents_list);
        emptyView = findViewById(R.id.recents_empty_view);
        ramInfoView = findViewById(R.id.recents_ram_info);
        clearAllBtn = findViewById(R.id.btn_clear_all);

        findViewById(R.id.btn_close_recents).setOnClickListener(v -> closeRecentsScreen());
        clearAllBtn.setOnClickListener(v -> clearAllTasks());

        adapter = new ArrayAdapter<RecentItem>(this, R.layout.item_recent_task, recentItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView != null
                        ? convertView
                        : LayoutInflater.from(getContext()).inflate(R.layout.item_recent_task, parent, false);

                RecentItem item = getItem(position);
                if (item == null) {
                    return row;
                }

                TextView nameTv = row.findViewById(R.id.recent_app_name);
                TextView metaTv = row.findViewById(R.id.recent_app_meta);
                TextView closeBtn = row.findViewById(R.id.btn_recent_close);

                nameTv.setText(item.label);
                StringBuilder meta = new StringBuilder();
                if (item.memoryMb > 0) {
                    meta.append("~").append(item.memoryMb).append(" MB");
                } else {
                    meta.append("Cached");
                }
                meta.append(item.isRunning ? "  •  Active" : "  •  Background");
                metaTv.setText(meta.toString());

                closeBtn.setOnClickListener(v -> removeSingleTask(item, false));
                return row;
            }
        };

        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < recentItems.size()) {
                reopenTask(recentItems.get(position));
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < recentItems.size()) {
                showTaskOptionsDialog(recentItems.get(position));
            }
            return true;
        });

        loadRecentTasks();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && ACTION_TOGGLE_RECENTS.equals(intent.getAction()) && sIsVisible) {
            closeRecentsScreen();
            return;
        }
        loadRecentTasks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sActiveRef = new WeakReference<>(this);
        sIsVisible = true;
        loadRecentTasks();
    }

    @Override
    protected void onPause() {
        sIsVisible = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sIsVisible = false;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        closeRecentsScreen();
    }

    private void closeRecentsScreen() {
        finish();
        overridePendingTransition(0, 0);
    }

    private void loadRecentTasks() {
        recentItems.clear();
        PackageManager pm = getPackageManager();
        ActivityManager am = getSystemService(ActivityManager.class);
        Map<String, Integer> memMapMb = queryPackageMemoryMb(am);
        Set<String> seenPackages = new HashSet<>();

        List<ActivityManager.RecentTaskInfo> rawTasks = null;
        try {
            rawTasks = ActivityTaskManager.getInstance().getRecentTasks(
                    32, ActivityManager.RECENT_IGNORE_UNAVAILABLE, UserHandle.myUserId());
        } catch (Throwable ignored) {
        }

        if (rawTasks != null) {
            for (ActivityManager.RecentTaskInfo info : rawTasks) {
                if (info == null) {
                    continue;
                }
                String pkg = resolvePackageName(info);
                if (pkg == null || pkg.isEmpty()) {
                    continue;
                }
                if ("com.dumbos.launcher".equals(pkg)
                        || "com.android.systemui".equals(pkg)
                        || "com.android.launcher3".equals(pkg)) {
                    continue;
                }
                if (!seenPackages.add(pkg)) {
                    continue;
                }
                String label = resolveAppLabel(pm, pkg, info.baseIntent);
                int mb = memMapMb.containsKey(pkg) ? memMapMb.get(pkg) : 0;
                recentItems.add(new RecentItem(
                        info.taskId,
                        pkg,
                        label,
                        info.baseIntent,
                        info.isRunning,
                        mb));
            }
        }

        adapter.notifyDataSetChanged();
        updateHeaderAndEmptyState(am);
    }

    private Map<String, Integer> queryPackageMemoryMb(ActivityManager am) {
        Map<String, Integer> map = new HashMap<>();
        if (am == null) {
            return map;
        }
        try {
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs == null || procs.isEmpty()) {
                return map;
            }
            int[] pids = new int[procs.size()];
            for (int i = 0; i < procs.size(); i++) {
                pids[i] = procs.get(i).pid;
            }
            Debug.MemoryInfo[] memInfos = am.getProcessMemoryInfo(pids);
            if (memInfos == null) {
                return map;
            }
            for (int i = 0; i < procs.size() && i < memInfos.length; i++) {
                ActivityManager.RunningAppProcessInfo proc = procs.get(i);
                Debug.MemoryInfo mi = memInfos[i];
                if (proc == null || proc.pkgList == null || mi == null) {
                    continue;
                }
                int pssMb = Math.max(1, Math.round(mi.getTotalPss() / 1024f));
                for (String p : proc.pkgList) {
                    Integer prev = map.get(p);
                    map.put(p, prev == null ? pssMb : prev + pssMb);
                }
            }
        } catch (Throwable ignored) {
        }
        return map;
    }

    private void updateHeaderAndEmptyState(ActivityManager am) {
        boolean isEmpty = recentItems.isEmpty();
        listView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        clearAllBtn.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (ramInfoView != null && am != null) {
            try {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long freeMb = mi.availMem / (1024L * 1024L);
                long totalMb = mi.totalMem / (1024L * 1024L);
                ramInfoView.setText(String.format(Locale.US,
                        "RAM: %d MB free / %d MB", freeMb, totalMb));
            } catch (Throwable ignored) {
            }
        }
    }

    private String resolvePackageName(ActivityManager.RecentTaskInfo info) {
        if (info.baseIntent != null && info.baseIntent.getComponent() != null) {
            return info.baseIntent.getComponent().getPackageName();
        }
        if (info.realActivity != null) {
            return info.realActivity.getPackageName();
        }
        if (info.topActivity != null) {
            return info.topActivity.getPackageName();
        }
        if (info.baseIntent != null && info.baseIntent.getPackage() != null) {
            return info.baseIntent.getPackage();
        }
        return null;
    }

    private String resolveAppLabel(PackageManager pm, String pkg, Intent baseIntent) {
        try {
            if (baseIntent != null) {
                ResolveInfo ri = pm.resolveActivity(baseIntent, 0);
                if (ri != null) {
                    CharSequence l = ri.loadLabel(pm);
                    if (l != null && l.length() > 0) {
                        return l.toString().trim();
                    }
                }
            }
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) {
                return l.toString().trim();
            }
        } catch (Throwable ignored) {
        }
        return pkg;
    }

    private void reopenTask(RecentItem item) {
        try {
            ActivityOptions opts = ActivityOptions.makeBasic();
            ActivityTaskManager.getService().startActivityFromRecents(item.taskId, opts.toBundle());
            closeRecentsScreen();
            return;
        } catch (Throwable ignored) {
        }
        try {
            if (item.baseIntent != null) {
                Intent launch = new Intent(item.baseIntent);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                startActivity(launch);
            } else {
                Intent fallback = getPackageManager().getLaunchIntentForPackage(item.packageName);
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(fallback);
                }
            }
        } catch (Throwable ignored) {
        }
        closeRecentsScreen();
    }

    private void removeSingleTask(RecentItem item, boolean forceStop) {
        try {
            ActivityTaskManager.getInstance().removeTask(item.taskId);
        } catch (Throwable ignored) {
        }
        if (forceStop) {
            try {
                ActivityManager am = getSystemService(ActivityManager.class);
                if (am != null) {
                    am.forceStopPackage(item.packageName);
                }
            } catch (Throwable ignored) {
            }
        }
        recentItems.remove(item);
        adapter.notifyDataSetChanged();
        updateHeaderAndEmptyState(getSystemService(ActivityManager.class));
    }

    private void clearAllTasks() {
        List<RecentItem> snapshot = new ArrayList<>(recentItems);
        for (RecentItem item : snapshot) {
            try {
                ActivityTaskManager.getInstance().removeTask(item.taskId);
            } catch (Throwable ignored) {
            }
        }
        recentItems.clear();
        adapter.notifyDataSetChanged();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(home);
        } catch (Throwable ignored) {
        }
        closeRecentsScreen();
    }

    private void showTaskOptionsDialog(final RecentItem item) {
        String[] actions = new String[] {
                "Open",
                "Close Task",
                "Force Stop (Free RAM)",
                "App Info"
        };
        new AlertDialog.Builder(this, R.style.Theme_DumbOS_Dialog)
                .setTitle(item.label)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        reopenTask(item);
                    } else if (which == 1) {
                        removeSingleTask(item, false);
                    } else if (which == 2) {
                        removeSingleTask(item, true);
                    } else if (which == 3) {
                        try {
                            Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + item.packageName));
                            info.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(info);
                        } catch (Throwable ignored) {
                        }
                    }
                })
                .show();
    }
}
