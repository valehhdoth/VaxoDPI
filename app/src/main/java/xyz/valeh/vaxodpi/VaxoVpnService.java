package xyz.valeh.vaxodpi;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import io.github.dovecoteescapee.byedpi.core.ByeDpiProxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class VaxoVpnService extends VpnService {

    private static final String TAG = "VaxoDPI";
    public static final String ACTION_STOP =
            "xyz.valeh.vaxodpi.STOP";
    public static final String ACTION_STATUS =
            "xyz.valeh.vaxodpi.STATUS";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_MESSAGE = "message";

    public static final String[] STRATEGY_NAMES = {
        "Varsayılan", "Split", "Disorder", "OOB"};
    public static final String[] STRATEGY_DESCS = {
        "Disorder + otomatik + TLS parçalama (şu an çalışan)",
        "Paketi bölme + otomatik + TLS parçalama",
        "Geç sıralı gönderim + otomatik + TLS parçalama",
        "Bant dışı veri + otomatik + TLS parçalama"};
    public static final String[] STRATEGY_ARGS = {
        "--disorder 1 --auto=torst --tlsrec 1+s",
        "--split 1+s --auto=torst --tlsrec 1+s",
        "--disorder 1+s --auto=torst --tlsrec 1+s",
        "--oob 1+s --auto=torst --tlsrec 1+s"};

    private void sendStatus(String state, String message) {
        Intent statusIntent = new Intent(ACTION_STATUS);
        statusIntent.setPackage(getPackageName());
        statusIntent.putExtra(EXTRA_STATE, state);
        statusIntent.putExtra(EXTRA_MESSAGE, message);
        sendBroadcast(statusIntent);
    }

    private ParcelFileDescriptor vpnInterface;
    private Thread proxyThread;

    private volatile boolean running;
    private volatile int proxyFd = -1;
    private boolean tunnelStarted;
    private int tunFdOwned = -1;

    private final StringBuilder dbg = new StringBuilder();

    private synchronized void flog(String msg) {
        Log.i(TAG, msg);
        dbg.append(new java.text.SimpleDateFormat("HH:mm:ss",
                java.util.Locale.US).format(new java.util.Date()))
           .append(' ').append(msg).append('\n');
    }

    private void startDiagnostics() {
        new Thread(() -> {
            try (java.net.Socket sock = new java.net.Socket()) {
                sock.connect(new java.net.InetSocketAddress(
                        "127.0.0.1", 1080), 3000);
                sock.setSoTimeout(5000);
                java.io.OutputStream o = sock.getOutputStream();
                java.io.InputStream in = sock.getInputStream();
                o.write(new byte[]{5, 1, 0});
                o.flush();
                byte[] r = new byte[2];
                int n = in.read(r);
                flog("SOCKS5 handshake: n=" + n + " yanit="
                        + (n == 2 ? r[0] + "," + r[1] : "yok"));
                o.write(new byte[]{5, 1, 0, 1, 1, 1, 1, 1, 0, 80});
                o.flush();
                byte[] c = new byte[10];
                n = in.read(c);
                flog("SOCKS5 CONNECT 1.1.1.1:80 n=" + n
                        + " durum=" + (n > 1 ? c[1] : -1));
            } catch (Throwable e) {
                flog("SOCKS5 test hatasi: " + e);
            }
            for (int k = 0; k < 5 && running; k++) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    long[] st = TProxyService.TProxyGetStats();
                    flog("HEV stats: " + java.util.Arrays.toString(st));
                } catch (Throwable e) {
                    flog("stats hatasi: " + e);
                }
            }
            flog("tanilama bitti");
        }, "VaxoDPI-Diag").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (running) {
            return START_STICKY;
        }

        startFg();

        try {
            startTunnel();
            return START_STICKY;
        } catch (Exception e) {
            Log.e(TAG, "VPN başlatılamadı", e);
            sendStatus("error", "VPN başlatılamadı: " + e.getMessage());
            stopTunnel();
            stopSelf();
            return START_NOT_STICKY;
        }
    }

    private void startTunnel() throws Exception {
        File configFile = copyConfig();

        // Önce yerel ByeDPI SOCKS5 sunucusunu başlat.
        proxyFd = ByeDpiProxy.jniCreateSocketWithCommandLine(
                buildArgs()
        );

        if (proxyFd < 0) {
            throw new IOException("ByeDPI SOCKS5 sunucusu başlatılamadı.");
        }

        final int fd = proxyFd;

        running = true;

        proxyThread = new Thread(() -> {
            try {
                int result = ByeDpiProxy.jniStartProxy(fd);
                if (running) {
                    flog("ByeDPI sonlandi. Kod: " + result);
                }
            } catch (Throwable e) {
                if (running) {
                    Log.e(TAG, "ByeDPI çalışırken hata oluştu", e);
                }
            }
        }, "VaxoDPI-Proxy");

        proxyThread.start();

        // Proxy soketinin hazırlanması için kısa süre tanı.
        Thread.sleep(250);

        Builder builder = new Builder();
        builder.setSession("VaxoDPI");
        builder.setMtu(1500);

        // Hev'in yapılandırmasındaki 198.18.0.1 adresiyle
        // uyumlu bir istemci adresi kullan.
        builder.addAddress("198.18.0.2", 15);
        builder.addRoute("0.0.0.0", 0);
        builder.addDnsServer("1.1.1.1");

        // VPN'in kendi SOCKS5 bağlantılarının tünele dönmesini önle.
        // Bunun sonucu olarak VaxoDPI uygulamasının kendi trafiği
        // bu VPN'in dışında kalır.
        try {
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException(
                    "Uygulama VPN dışında bırakılamadı.", e);
        }

        vpnInterface = builder.establish();

        if (vpnInterface == null) {
            throw new IOException("Android VPN arayüzü oluşturulamadı.");
        }

        // Dosya tanımlayıcısının sahipliğini native tünel motoruna aktar.
        int tunFd = vpnInterface.detachFd();
        tunFdOwned = tunFd;
        vpnInterface = null;

        try {
            TProxyService.TProxyStartService(
                    configFile.getAbsolutePath(), tunFd);
            tunnelStarted = true;
        } catch (Throwable e) {
            try {
                ParcelFileDescriptor.adoptFd(tunFd).close();
            } catch (Throwable ignored) {
            }
            throw new IOException("Hev tüneli başlatılamadı.", e);
        }

        flog("VPN ve tunel baslatma cagrilari tamamlandi");
        startDiagnostics();
        startNotifUpdater();
        sendStatus("connected",
                "Tünel başlatıldı; trafik aktarımı henüz doğrulanmadı.");
    }

    private static final int NOTIF_ID = 7001;
    private static final String CHANNEL_ID = "vaxodpi_vpn";
    private Thread notifThread;

    private String nt(String tr, String en, String ru) {
        String sys = java.util.Locale.getDefault().getLanguage();
        int d = "tr".equals(sys) ? 0 : ("ru".equals(sys) ? 2 : 1);
        int l = getSharedPreferences("vaxodpi", MODE_PRIVATE).getInt("lang", d);
        return l == 0 ? tr : (l == 2 ? ru : en);
    }

    private android.app.Notification buildNotif(String text) {
        android.app.NotificationManager nm = (android.app.NotificationManager)
                getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new android.app.NotificationChannel(
                    CHANNEL_ID, "VaxoDPI",
                    android.app.NotificationManager.IMPORTANCE_LOW));
        }
        int fl = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                | android.app.PendingIntent.FLAG_IMMUTABLE;
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent openPi =
                android.app.PendingIntent.getActivity(this, 0, open, fl);
        Intent stop = new Intent(this, VaxoVpnService.class);
        stop.setAction(ACTION_STOP);
        android.app.PendingIntent stopPi =
                android.app.PendingIntent.getService(this, 1, stop, fl);

        android.app.Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                ? new android.app.Notification.Builder(this, CHANNEL_ID)
                : new android.app.Notification.Builder(this);
        int icon = getResources().getIdentifier("ic_stat", "drawable", getPackageName());
        b.setSmallIcon(icon != 0 ? icon : android.R.drawable.ic_lock_lock)
         .setContentTitle("VaxoDPI - " + nt("aktif", "active", "активен"))
         .setContentText(text)
         .setContentIntent(openPi)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setShowWhen(false)
         .addAction(0, nt("Bağlantıyı kes", "Disconnect", "Отключить"), stopPi);
        return b.build();
    }

    private void startFg() {
        try {
            android.app.Notification n = buildNotif(
                    nt("Başlatılıyor…", "Starting…", "Запуск…"));
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable e) {
            flog("startForeground hatasi: " + e);
        }
    }

    private void startNotifUpdater() {
        final long start = android.os.SystemClock.elapsedRealtime();
        Thread t = new Thread(() -> {
            long lastTx = 0, lastRx = 0, lastT = 0;
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                try {
                    long[] st = TProxyService.TProxyGetStats();
                    if (st == null || st.length < 4) continue;
                    long now = android.os.SystemClock.elapsedRealtime();
                    double dl = 0, ul = 0;
                    if (lastT > 0 && now > lastT) {
                        double dt = (now - lastT) / 1000.0;
                        dl = Math.max(0, st[3] - lastRx) * 8.0 / 1e6 / dt;
                        ul = Math.max(0, st[1] - lastTx) * 8.0 / 1e6 / dt;
                    }
                    lastT = now; lastTx = st[1]; lastRx = st[3];
                    long s = (now - start) / 1000;
                    double mb = (st[1] + st[3]) / 1048576.0;
                    String data = mb < 1024
                            ? String.format(java.util.Locale.US, "%.1f MB", mb)
                            : String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
                    String text = String.format(java.util.Locale.US,
                            "%02d:%02d:%02d  ↓ %.2f  ↑ %.2f Mbps  %s",
                            s / 3600, (s / 60) % 60, s % 60, dl, ul, data);
                    if (!running) return;
                    ((android.app.NotificationManager)
                            getSystemService(NOTIFICATION_SERVICE))
                            .notify(NOTIF_ID, buildNotif(text));
                } catch (Throwable ignored) {
                }
            }
        }, "VaxoDPI-Notif");
        notifThread = t;
        t.start();
    }

    private String[] buildArgs() {
        int idx = getSharedPreferences("vaxodpi", MODE_PRIVATE)
                .getInt("strategy", 0);
        if (idx < 0 || idx >= STRATEGY_ARGS.length) idx = 0;
        java.util.ArrayList<String> a = new java.util.ArrayList<>();
        a.add("-i"); a.add("127.0.0.1"); a.add("-p"); a.add("1080");
        for (String x : STRATEGY_ARGS[idx].split(" ")) a.add(x);
        flog("Strateji: " + STRATEGY_NAMES[idx]);
        return a.toArray(new String[0]);
    }

    private File copyConfig() throws IOException {
        File output = new File(getFilesDir(), "vaxodpi-tunnel.yml");

        try (InputStream in = getAssets().open("vaxodpi-tunnel.yml");
             FileOutputStream out = new FileOutputStream(output)) {

            byte[] buffer = new byte[4096];
            int count;

            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            out.flush();
        }

        return output;
    }

    private void stopTunnel() {
        flog("stopTunnel cagrildi");
        boolean wasRunning = running || tunnelStarted;
        running = false;

        Thread nthr = notifThread;
        notifThread = null;
        if (nthr != null) nthr.interrupt();
        try {
            stopForeground(true);
            ((android.app.NotificationManager)
                    getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIF_ID);
        } catch (Throwable ignored) {
        }

        if (tunnelStarted) {
            try {
                flog("Hev durdurma basliyor");
                TProxyService.TProxyStopService();
                flog("Hev durdurma bitti");
            } catch (Throwable e) {
                Log.e(TAG, "Hev durdurulamadı", e);
            }
            tunnelStarted = false;
        }

        if (proxyFd >= 0) {
            try {
                ByeDpiProxy.jniStopProxy(proxyFd);
            } catch (Throwable e) {
                Log.e(TAG, "ByeDPI durdurulamadı", e);
            }
            proxyFd = -1;
        }

        Thread threadToJoin = proxyThread;
        proxyThread = null;

        if (threadToJoin != null) {
            threadToJoin.interrupt();
            try {
                threadToJoin.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (tunFdOwned >= 0) {
            int f = tunFdOwned;
            tunFdOwned = -1;
            try {
                String t = android.system.Os.readlink("/proc/self/fd/" + f);
                flog("TUN fd " + f + " hala acik -> " + t);
                if (t.contains("tun")) {
                    ParcelFileDescriptor.adoptFd(f).close();
                    flog("TUN fd kapatildi");
                }
            } catch (Throwable e) {
                flog("TUN fd zaten kapali");
            }
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {
            }
            vpnInterface = null;
        }

        if (wasRunning) {
            sendStatus("disconnected", "VPN bağlantısı durduruldu.");
        }
    }

    @Override
    public void onDestroy() {
        stopTunnel();
        super.onDestroy();
    }
}
