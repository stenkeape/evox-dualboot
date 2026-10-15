package com.evox.dualboot;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 1) Settings: adds a "Dual system" row to Additional settings that opens DualSystemActivity.
 * 2) com.android.dynsystem: swallows the DSU notifications.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "EvoXDualBoot";
    private static final String KEY = "evox_dual_system";
    private static final String[] TARGETS = {"OtherPersonalSettings"};

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.android.settings".equals(lpparam.packageName)) {
            hookSettings(lpparam.classLoader);
        } else if ("com.android.dynsystem".equals(lpparam.packageName)) {
            hideDsuNotifications();
        }
    }

    // ---------------------------------------------------------------- Settings row

    private void hookSettings(final ClassLoader cl) {
        Class<?> fragClass = XposedHelpers.findClassIfExists(
                "androidx.preference.PreferenceFragmentCompat", cl);
        if (fragClass == null) {
            XposedBridge.log(TAG + ": PreferenceFragmentCompat not found");
            return;
        }
        XposedHelpers.findAndHookMethod(fragClass, "onViewCreated",
                View.class, Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object frag = param.thisObject;
                            String name = frag.getClass().getName();
                            XposedBridge.log(TAG + ": fragment " + name);
                            for (String t : TARGETS) {
                                if (name.contains(t)) {
                                    addRow(frag, cl);
                                    break;
                                }
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": " + t);
                        }
                    }
                });
    }

    private void addRow(Object frag, ClassLoader cl) throws Throwable {
        Object screen = XposedHelpers.callMethod(frag, "getPreferenceScreen");
        if (screen == null) return;
        if (XposedHelpers.callMethod(screen, "findPreference", KEY) != null) return;

        final Context ctx = (Context) XposedHelpers.callMethod(frag, "requireContext");
        Class<?> prefClass = XposedHelpers.findClass("androidx.preference.Preference", cl);
        Class<?> listenerClass = XposedHelpers.findClass(
                "androidx.preference.Preference$OnPreferenceClickListener", cl);

        Object pref = XposedHelpers.newInstance(prefClass, ctx);
        XposedHelpers.callMethod(pref, "setKey", KEY);
        XposedHelpers.callMethod(pref, "setTitle", "Dual system");
        XposedHelpers.callMethod(pref, "setSummary", "HyperOS · Evolution X");
        XposedHelpers.callMethod(pref, "setOrder", -1000);
        try {
            XposedHelpers.callMethod(pref, "setIcon", makeIcon(ctx));
        } catch (Throwable ignored) { }

        Object listener = Proxy.newProxyInstance(cl, new Class<?>[]{listenerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method m, Object[] args) {
                        if (m.getName().equals("onPreferenceClick")) {
                            try {
                                Intent i = new Intent();
                                i.setClassName("com.evox.dualboot",
                                        "com.evox.dualboot.DualSystemActivity");
                                ctx.startActivity(i);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": open page failed: " + t);
                            }
                            return true;
                        }
                        return null;
                    }
                });
        XposedHelpers.callMethod(pref, "setOnPreferenceClickListener", listener);
        XposedHelpers.callMethod(screen, "addPreference", pref);
    }

    /** Blue rounded square with two overlapping windows, drawn in code. */
    private Drawable makeIcon(Context ctx) {
        int s = 120;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, s, s, 0xFF5AA9FF, 0xFF2F5BFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, s, s), 34, 34, p);
        p.setShader(null);
        p.setColor(0x66FFFFFF);
        c.drawRoundRect(new RectF(24, 26, 76, 78), 12, 12, p);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(44, 44, 96, 96), 12, 12, p);
        return new BitmapDrawable(ctx.getResources(), bmp);
    }

    // ---------------------------------------------------------------- DSU notifications

    private void hideDsuNotifications() {
        XC_MethodHook block = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                param.setResult(null);
            }
        };
        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notifyAsUser",
                    String.class, int.class, Notification.class, UserHandle.class, block);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": notifyAsUser hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notify",
                    String.class, int.class, Notification.class, block);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": notify(tag) hook failed: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(NotificationManager.class, "notify",
                    int.class, Notification.class, block);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": notify hook failed: " + t);
        }
        XposedBridge.log(TAG + ": DSU notifications blocked");
    }
}
