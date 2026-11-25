package com.dumbos.launcher;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Display;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.util.List;

/**
 * Ultra-lightweight QuickStep Service inside com.dumbos.launcher (~0 MB extra process).
 *
 * 1. Satisfies Settings SystemNavigationPreferenceController & SystemUI OverviewProxyService
 *    for ACTION_QUICKSTEP ("android.intent.action.QUICKSTEP_SERVICE") so the Navigation Mode
 *    menu (Gesture navigation vs 3-button navigation) is always available in Settings.
 * 2. Implements wire-compatible IOverviewProxy Binder transactions across Android 13, 14 & 15
 *    without pulling heavy SystemUISharedLib / Kotlin / Dagger dependencies.
 * 3. Monitors bottom-edge touch input via InputManager.monitorGestureInput when Gesture or
 *    2-Button Navigation is active:
 *      - Swipe up & release -> Go Home
 *      - Swipe up & hold    -> Open RecentsActivity
 *      - Bottom pill flick  -> Quick-switch to previous app
 *    (Left/Right edge Back gestures are handled natively by SystemUI EdgeBackGestureHandler.)
 */
public class DumbQuickStepService extends Service {

    private static final String OVERVIEW_PROXY_DESCRIPTOR =
            "com.android.systemui.shared.recents.IOverviewProxy";

    private static final int TRANSACTION_ON_OVERVIEW_TOGGLE = IBinder.FIRST_CALL_TRANSACTION + 6;
    private static final int TRANSACTION_ON_OVERVIEW_SHOWN = IBinder.FIRST_CALL_TRANSACTION + 7;
    private static final int TRANSACTION_ON_OVERVIEW_HIDDEN = IBinder.FIRST_CALL_TRANSACTION + 8;
    private static final int TRANSACTION_ON_UNBIND = IBinder.FIRST_CALL_TRANSACTION + 35;

    private static final String KEY_NAVIGATION_MODE = "navigation_mode";
    private static final int NAV_MODE_3BUTTON = 0;

    private static final long HOLD_RECENTS_DELAY_MS = 195L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private InputMonitor inputMonitor;
    private GestureInputReceiver inputReceiver;
    private int screenWidth = 1080;
    private int screenHeight = 1920;

    private final ContentObserver navModeObserver = new ContentObserver(mainHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateGestureMonitorState();
        }
    };

    private final Binder overviewProxyBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(OVERVIEW_PROXY_DESCRIPTOR);
                }
                return true;
            }
            if (code == TRANSACTION_ON_OVERVIEW_TOGGLE) {
                mainHandler.post(() -> launchRecents(RecentsActivity.ACTION_TOGGLE_RECENTS));
                return true;
            }
            if (code == TRANSACTION_ON_OVERVIEW_SHOWN) {
                mainHandler.post(() -> launchRecents(RecentsActivity.ACTION_SHOW_RECENTS));
                return true;
            }
            if (code == TRANSACTION_ON_OVERVIEW_HIDDEN) {
                mainHandler.post(RecentsActivity::finishIfActive);
                return true;
            }
            if (code == TRANSACTION_ON_UNBIND) {
                try {
                    data.enforceInterface(OVERVIEW_PROXY_DESCRIPTOR);
                    IBinder cbBinder = data.readStrongBinder();
                    if (cbBinder != null) {
                        IRemoteCallback cb = IRemoteCallback.Stub.asInterface(cbBinder);
                        if (cb != null) {
                            cb.sendResult(null);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }
            if (code >= IBinder.FIRST_CALL_TRANSACTION && code <= 50) {
                // Oneway SystemUI state callbacks across A13/A14/A15
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        refreshDisplayBounds();
        try {
            getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(KEY_NAVIGATION_MODE),
                    false,
                    navModeObserver);
        } catch (Throwable ignored) {
        }
        updateGestureMonitorState();
    }

    @Override
    public IBinder onBind(Intent intent) {
        refreshDisplayBounds();
        updateGestureMonitorState();
        return overviewProxyBinder;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshDisplayBounds();
    }

    @Override
    public void onDestroy() {
        try {
            getContentResolver().unregisterContentObserver(navModeObserver);
        } catch (Throwable ignored) {
        }
        disposeGestureMonitor();
        super.onDestroy();
    }

    private void refreshDisplayBounds() {
        try {
            WindowManager wm = getSystemService(WindowManager.class);
            if (wm != null) {
                Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                if (bounds.width() > 0 && bounds.height() > 0) {
                    screenWidth = bounds.width();
                    screenHeight = bounds.height();
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
    }

    private int currentNavMode() {
        try {
            return Settings.Secure.getInt(getContentResolver(), KEY_NAVIGATION_MODE, NAV_MODE_3BUTTON);
        } catch (Throwable ignored) {
            return NAV_MODE_3BUTTON;
        }
    }

    private void updateGestureMonitorState() {
        int mode = currentNavMode();
        if (mode != NAV_MODE_3BUTTON) {
            ensureGestureMonitor();
        } else {
            disposeGestureMonitor();
        }
    }

    private void ensureGestureMonitor() {
        if (inputMonitor != null && inputReceiver != null) {
            return;
        }
        disposeGestureMonitor();
        try {
            InputManager im = getSystemService(InputManager.class);
            if (im == null) {
                return;
            }
            inputMonitor = im.monitorGestureInput("dumbos-quickstep", Display.DEFAULT_DISPLAY);
            if (inputMonitor != null) {
                inputReceiver = new GestureInputReceiver(
                        inputMonitor.getInputChannel(), Looper.getMainLooper());
            }
        } catch (Throwable ignored) {
            disposeGestureMonitor();
        }
    }

    private void disposeGestureMonitor() {
        mainHandler.removeCallbacksAndMessages(null);
        if (inputReceiver != null) {
            try {
                inputReceiver.dispose();
            } catch (Throwable ignored) {
            }
            inputReceiver = null;
        }
        if (inputMonitor != null) {
            try {
                inputMonitor.dispose();
            } catch (Throwable ignored) {
            }
            inputMonitor = null;
        }
    }

    private boolean isKeyguardLocked() {
        try {
            KeyguardManager km = getSystemService(KeyguardManager.class);
            return km != null && km.isKeyguardLocked();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void goHome() {
        RecentsActivity.finishIfActive();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.setPackage(getPackageName());
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(home);
        } catch (Throwable ignored) {
        }
    }

    private void launchRecents(String action) {
        try {
            Intent intent = new Intent(action);
            intent.setClass(this, RecentsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    private void quickSwitchPreviousApp() {
        try {
            List<ActivityManager.RecentTaskInfo> tasks =
                    ActivityTaskManager.getInstance().getRecentTasks(
                            6, ActivityManager.RECENT_IGNORE_UNAVAILABLE, UserHandle.myUserId());
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            // Index 0 is usually the foreground app (or launcher if on home); pick next valid app task
            int targetTaskId = -1;
            int validSeen = 0;
            for (ActivityManager.RecentTaskInfo t : tasks) {
                if (t == null) continue;
                String pkg = null;
                if (t.baseIntent != null && t.baseIntent.getComponent() != null) {
                    pkg = t.baseIntent.getComponent().getPackageName();
                } else if (t.realActivity != null) {
                    pkg = t.realActivity.getPackageName();
                }
                if (pkg == null
                        || "com.dumbos.launcher".equals(pkg)
                        || "com.android.systemui".equals(pkg)) {
                    continue;
                }
                validSeen++;
                if (!t.isRunning || validSeen >= 2) {
                    targetTaskId = t.taskId;
                    break;
                }
                if (targetTaskId == -1) {
                    targetTaskId = t.taskId;
                }
            }
            if (targetTaskId != -1) {
                RecentsActivity.finishIfActive();
                ActivityOptions opts = ActivityOptions.makeBasic();
                ActivityTaskManager.getService().startActivityFromRecents(
                        targetTaskId, opts.toBundle());
            }
        } catch (Throwable ignored) {
        }
    }

    private void tickHaptic() {
        try {
            Vibrator vib = getSystemService(Vibrator.class);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            }
        } catch (Throwable ignored) {
        }
    }

    private int dp(int val) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, val, getResources().getDisplayMetrics()));
    }

    private final class GestureInputReceiver extends InputEventReceiver {

        private boolean tracking = false;
        private boolean pilfered = false;
        private boolean completed = false;
        private float downX;
        private float downY;
        private float lastX;
        private float lastY;

        private final Runnable holdRecentsRunnable = () -> {
            if (!tracking || !pilfered || completed) {
                return;
            }
            float dy = downY - lastY;
            if (dy >= dp(42)) {
                completed = true;
                tickHaptic();
                launchRecents(RecentsActivity.ACTION_SHOW_RECENTS);
            }
        };

        GestureInputReceiver(InputChannel inputChannel, Looper looper) {
            super(inputChannel, looper);
        }

        @Override
        public void onInputEvent(InputEvent event) {
            boolean handled = false;
            try {
                if (event instanceof MotionEvent) {
                    handleMotion((MotionEvent) event);
                    handled = pilfered;
                }
            } catch (Throwable ignored) {
            } finally {
                finishInputEvent(event, handled);
            }
        }

        private void handleMotion(MotionEvent ev) {
            int action = ev.getActionMasked();
            float x = ev.getRawX();
            float y = ev.getRawY();

            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    mainHandler.removeCallbacks(holdRecentsRunnable);
                    tracking = false;
                    pilfered = false;
                    completed = false;

                    if (isKeyguardLocked()) {
                        return;
                    }
                    refreshDisplayBounds();
                    int bottomEdgeZone = dp(34);
                    int sideCornerGuard = dp(20);
                    if (y >= (screenHeight - bottomEdgeZone)
                            && x >= sideCornerGuard
                            && x <= (screenWidth - sideCornerGuard)) {
                        tracking = true;
                        downX = lastX = x;
                        downY = lastY = y;
                    }
                    break;
                }

                case MotionEvent.ACTION_MOVE: {
                    if (!tracking || completed) {
                        return;
                    }
                    float dy = downY - y;
                    float dx = x - downX;
                    float step = (float) Math.hypot(x - lastX, y - lastY);
                    lastX = x;
                    lastY = y;

                    if (!pilfered) {
                        boolean upwardSwipe = dy >= dp(14) && Math.abs(dy) > Math.abs(dx);
                        boolean bottomQuickSwitch = Math.abs(dx) >= dp(28) && Math.abs(dy) <= dp(24);
                        if (upwardSwipe || bottomQuickSwitch) {
                            pilfered = true;
                            if (inputMonitor != null) {
                                inputMonitor.pilferPointers();
                            }
                        } else if (dy < -dp(12)) {
                            tracking = false;
                            return;
                        }
                    }

                    if (pilfered && dy >= dp(42)) {
                        if (step > dp(9)) {
                            mainHandler.removeCallbacks(holdRecentsRunnable);
                            mainHandler.postDelayed(holdRecentsRunnable, HOLD_RECENTS_DELAY_MS);
                        } else if (!mainHandler.hasCallbacks(holdRecentsRunnable)) {
                            mainHandler.postDelayed(holdRecentsRunnable, HOLD_RECENTS_DELAY_MS);
                        }
                    } else {
                        mainHandler.removeCallbacks(holdRecentsRunnable);
                    }
                    break;
                }

                case MotionEvent.ACTION_UP: {
                    mainHandler.removeCallbacks(holdRecentsRunnable);
                    if (tracking && pilfered && !completed) {
                        float dy = downY - y;
                        float dx = x - downX;
                        completed = true;
                        if (dy >= dp(26)) {
                            goHome();
                        } else if (Math.abs(dx) >= dp(46) && Math.abs(dy) < dp(34)) {
                            tickHaptic();
                            quickSwitchPreviousApp();
                        }
                    }
                    tracking = false;
                    pilfered = false;
                    break;
                }

                case MotionEvent.ACTION_CANCEL: {
                    mainHandler.removeCallbacks(holdRecentsRunnable);
                    tracking = false;
                    pilfered = false;
                    completed = false;
                    break;
                }
            }
        }
    }
}
