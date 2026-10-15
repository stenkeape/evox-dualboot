package com.evox.dualboot;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.Toast;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Adds a "Boot Evolution X" row to Settings and boots the installed DSU image once.
 * Target screen: Developer options. Every fragment that is opened is logged
 * (tag EvoXDualBoot), so you can retarget TARGET_SUFFIX if you prefer another page.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "EvoXDualBoot";
    private static final String KEY = "evox_dualboot";
    // Fragment class-name parts that identify the "Additional settings" page.
    // HyperOS names vary by build; if the row doesn't show, check the log and add the real name here.
    private static final String[] TARGETS = {"OtherPersonalSettings"};

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.settings".equals(lpparam.packageName)) return;

        final ClassLoader cl = lpparam.classLoader;
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
        XposedHelpers.callMethod(pref, "setTitle", "Boot Evolution X");
        XposedHelpers.callMethod(pref, "setSummary", "Restart into Evolution X (second system)");
        XposedHelpers.callMethod(pref, "setOrder", -1000);

        Object listener = Proxy.newProxyInstance(cl, new Class<?>[]{listenerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method m, Object[] args) {
                        if (m.getName().equals("onPreferenceClick")) {
                            confirm(ctx);
                            return true;
                        }
                        return null;
                    }
                });
        XposedHelpers.callMethod(pref, "setOnPreferenceClickListener", listener);
        XposedHelpers.callMethod(screen, "addPreference", pref);
    }

    private void confirm(final Context ctx) {
        new AlertDialog.Builder(ctx)
                .setTitle("Boot Evolution X")
                .setMessage("The phone will restart into Evolution X. "
                        + "Restarting from there brings you back to HyperOS.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Restart", (d, w) -> new Thread(() -> bootEvoX(ctx)).start())
                .show();
    }

    private void bootEvoX(Context ctx) {
        boolean ok = false;
        // 1) Direct call to the system DSU service (works only if Settings is allowed).
        try {
            Object dsm = ctx.getSystemService("dynamic_system");
            if (dsm != null) {
                Object r = XposedHelpers.callMethod(dsm, "setEnable", true, true);
                ok = Boolean.TRUE.equals(r);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": direct DSU call failed: " + t);
        }
        // 2) Fallback: root (grant Settings in the ReSukiSU app list).
        if (!ok) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "gsi_tool enable -s"});
                ok = p.waitFor() == 0;
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": su fallback failed: " + t);
            }
        }
        if (!ok) {
            toast(ctx, "Could not enable Evolution X. Check Settings root access.");
            return;
        }
        try {
            ((PowerManager) ctx.getSystemService(Context.POWER_SERVICE)).reboot(null);
        } catch (Throwable t) {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
            } catch (Throwable t2) {
                toast(ctx, "Enabled, but reboot failed. Reboot manually.");
            }
        }
    }

    private void toast(final Context ctx, final String msg) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }
}
