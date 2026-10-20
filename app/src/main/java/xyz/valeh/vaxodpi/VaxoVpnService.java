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
                new String[]{"-i", "127.0.0.1", "-p", "1080"}
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
                    Log.e(TAG, "ByeDPI sonlandı. Kod: " + result);
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

        Log.i(TAG, "VPN ve tünel başlatma çağrıları tamamlandı.");
        sendStatus("connected",
                "Tünel başlatıldı; trafik aktarımı henüz doğrulanmadı.");
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
        boolean wasRunning = running || tunnelStarted;
        running = false;

        if (tunnelStarted) {
            try {
                TProxyService.TProxyStopService();
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
