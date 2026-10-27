package br.visaobox.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Se o app fechar com erro, guarda o motivo para mostrar na próxima abertura
 * (assim dá para mandar o texto do erro em vez de só "fechou").
 */
final class CrashLog {
    private static boolean installed;

    static synchronized void install(Context ctx) {
        if (installed) return;
        installed = true;
        final SharedPreferences prefs = ctx.getApplicationContext().getSharedPreferences("visaobox", Context.MODE_PRIVATE);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String s = sw.toString();
                if (s.length() > 3000) s = s.substring(0, 3000);
                prefs.edit().putString("lastCrash", "Versão " + BuildInfo.VERSION + " · linha \"" + t.getName() + "\"\n" + s)
                        .putBoolean("ctlMode", false)          // não abre direto no controle de novo
                        .commit();
            } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    /** Erro da última vez (e já apaga), ou null. */
    static String take(SharedPreferences prefs) {
        String s = prefs.getString("lastCrash", null);
        if (s != null) prefs.edit().remove("lastCrash").apply();
        return s;
    }
}
