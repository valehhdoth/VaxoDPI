package xyz.valeh.vaxodpi;

import android.net.VpnService;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;

public class VaxoVpnService extends VpnService {

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private volatile boolean running = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running) {
            return START_STICKY;
        }

        try {
            Builder builder = new Builder();

            builder.setSession("VaxoDPI");
            builder.addAddress("10.10.0.2", 32);
            builder.addRoute("10.10.0.0", 24);
            builder.addDnsServer("1.1.1.1");

            vpnInterface = builder.establish();

            if (vpnInterface == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            running = true;

            vpnThread = new Thread(() -> {
                // Henüz paket yönlendirme motoru eklenmedi.
                // Bu aşama yalnızca VPN arayüzünü oluşturur.
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "VaxoVPN");

            vpnThread.start();

        } catch (Exception e) {
            Log.e("VaxoDPI", "VPN başlatılamadı", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;

        if (vpnThread != null) {
            vpnThread.interrupt();
            vpnThread = null;
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {
            }
            vpnInterface = null;
        }

        super.onDestroy();
    }
}
