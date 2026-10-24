package xyz.valeh.vaxodpi;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.net.VpnService;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Random;

/**
 * VaxoDPI - arayüz katmanı.
 *
 * ÖNEMLİ: Bu dosyada GERÇEK bir VPN / DPI bypass bağlantısı YOKTUR.
 * - Gerçek bağlantı için ConnectionBackend arayüzü hazırdır (şu an NoBackend: isAvailable() = false).
 * - "Önizleme (DEMO)" modu yalnızca arayüz durumlarını göstermek içindir, trafik tünellemez,
 *   gösterilen değerler rastgele üretilir ve ekranda DEMO olarak işaretlenir.
 */
public class MainActivity extends Activity {

    private static final String APK_URL =
            "https://github.com/valehhdoth/VaxoDPI/releases/latest/download/app-release.apk";

    private static boolean isNewer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = 0, q = 0;
            try { p = i < x.length ? Integer.parseInt(x[i].replaceAll("\\D", "")) : 0; } catch (Exception e) { }
            try { q = i < y.length ? Integer.parseInt(y[i].replaceAll("\\D", "")) : 0; } catch (Exception e) { }
            if (p != q) return p > q;
        }
        return false;
    }

    private void checkUpdate() { checkUpdate(false); }

    private void toastUi(final String m) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkUpdate(final boolean manual) {
        try {
            String inst = getPackageManager().getInstallerPackageName(getPackageName());
            if ("org.fdroid.fdroid".equals(inst) || "org.fdroid.basic".equals(inst)) {
                if (manual) toastUi(L("Güncellemeler F-Droid üzerinden yapılır."));
                return;
            }
        } catch (Throwable ignored) {
        }
        final android.content.SharedPreferences sp = getSharedPreferences("vaxodpi", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - sp.getLong("upd_t", 0L) < 12L * 3600 * 1000) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL("https://api.github.com/repos/valehhdoth/VaxoDPI/releases/latest")
                                    .openConnection();
                    c.setConnectTimeout(6000);
                    c.setReadTimeout(6000);
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    if (c.getResponseCode() != 200) { if (manual) toastUi(L("Kontrol edilemedi.")); return; }
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9.]*)\"")
                            .matcher(bo.toString("UTF-8"));
                    if (!m.find()) { if (manual) toastUi(L("Kontrol edilemedi.")); return; }
                    final String latest = m.group(1);
                    sp.edit().putLong("upd_t", System.currentTimeMillis()).apply();
                    final String cur = appVersion().replace("v", "");
                    if (!isNewer(latest, cur)) { if (manual) toastUi(L("Uygulama güncel.")); return; }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing()) return;
                            android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(
                                    MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
                            b.setTitle(L("Güncelleme var"));
                            b.setMessage(L("Yeni sürüm çıktı: ") + "v" + latest);
                            b.setPositiveButton(L("İndir"), new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int w) {
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW,
                                                android.net.Uri.parse(APK_URL)));
                                    } catch (Exception ignored) { }
                                }
                            });
                            b.setNegativeButton(L("Sonra"), null);
                            b.show();
                        }
                    });
                } catch (Throwable ignored) { if (manual) toastUi(L("Kontrol edilemedi.")); }
            }
        }, "VaxoDPI-Update").start();
    }

    private String errText(String m) {
        if (m == null) return L("VPN hatası");
        String d = m.replaceFirst("^VPN başlatılamadı: ?", "");
        return L("VPN başlatılamadı") + (d.isEmpty() || d.equals("null") ? "" : ": " + L(d));
    }

    private String appVersion() {
        try {
            return "v" + getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "v1.1";
        }
    }

    // Renkler
    static final int BG = 0xFF000000;
    static final int CARD = 0xFF0D0D0D;
    static final int CARD_STROKE = 0xFF2A2A2A;
    static final int ACCENT = 0xFFFFFFFF;
    static final int ELECTRIC = 0xFFB8B8B8;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFF8C8C8C;
    static final int SLATE = 0xFF555555;
    static final int AMBER = 0xFFD0D0D0;
    static final int GREEN = 0xFFFFFFFF;
    static final int RED = 0xFFB8B8B8;

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    enum State { DISCONNECTED, CONNECTING, CONNECTED }

    private static final int REQ_VPN = 7301;
    private boolean realServiceStarted = false;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver vpnReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            String status = intent.getStringExtra(VaxoVpnService.EXTRA_STATE);
            String message = intent.getStringExtra(VaxoVpnService.EXTRA_MESSAGE);

            if ("connected".equals(status)) {
                realServiceStarted = true;
                demoMode = false;
                connectedAt = SystemClock.elapsedRealtime();
                applyState(State.CONNECTED);
            } else if ("error".equals(status)) {
                realServiceStarted = false;
                applyState(State.DISCONNECTED);
                Toast.makeText(MainActivity.this,
                        errText(message),
                        Toast.LENGTH_LONG).show();
            } else if ("disconnected".equals(status)) {
                realServiceStarted = false;
                applyState(State.DISCONNECTED);
            }
        }
    };

    private int lang = -1;

    private static final String[][] TR3 = {
 {"DPI bypass arayüzü","DPI bypass interface","Интерфейс обхода DPI"},
 {"DEMO – gösterilen değerler gerçek ölçüm değildir","DEMO – values shown are not real measurements","ДЕМО – показанные значения не являются реальными измерениями"},
 {"BAĞLI DEĞİL","NOT CONNECTED","НЕ ПОДКЛЮЧЕНО"},
 {"BAĞLAN","CONNECT","ПОДКЛЮЧИТЬ"},
 {"Bağlantı süresi","Connection time","Время соединения"},
 {"İNDİRME","DOWNLOAD","ЗАГРУЗКА"},
 {"YÜKLEME","UPLOAD","ОТДАЧА"},
 {"KULLANILAN VERİ","DATA USED","ТРАФИК"},
 {"Altyapı durumu","System status","Состояние системы"},
 {"VaxoDPI motoru aktif. BAĞLAN'a basınca tünel kurulur. İstatistikler tünel sayaçlarından hesaplanır.","VaxoDPI engine is active. Pressing CONNECT starts the tunnel. Statistics are calculated from tunnel counters.","Движок VaxoDPI активен. Нажатие «ПОДКЛЮЧИТЬ» запускает туннель. Статистика считается по счётчикам туннеля."},
 {"Modlar","Modes","Режимы"},
 {"Bağlantı yöntemini seç","Choose connection method","Выберите способ подключения"},
 {"DPI stratejisi","DPI strategy","Стратегия DPI"},
 {"Bir site açılmazsa başka bir strateji seç. Değişiklik bir sonraki bağlanışta geçerli olur.","If a site doesn't open, pick another strategy. Changes apply on the next connection.","Если сайт не открывается, выберите другую стратегию. Изменения вступят в силу при следующем подключении."},
 {"SEÇİLİ","SELECTED","ВЫБРАНО"},
 {"SEÇ","SELECT","ВЫБРАТЬ"},
 {"YAKINDA","SOON","СКОРО"},
 {"Yakında","Coming soon","Скоро"},
 {"Varsayılan","Default","По умолчанию"},
 {"Düşük TTL'li ilk parça + TLS parçalama","Low-TTL first fragment + TLS split","Фрагмент с малым TTL + TLS-фрагментация"},
 {"Paketi bölme + TLS parçalama","Packet split + TLS split","Разделение пакета + TLS-фрагментация"},
 {"SNI noktasında sıra bozma + TLS parçalama","Reordering at SNI + TLS split","Смена порядка на SNI + TLS-фрагментация"},
 {"Tüm uygulamalar","All apps","Все приложения"},
 {"uygulama seçili","apps selected","прилож. выбрано"},
 {"Uygulamaları seç","Select apps","Выберите приложения"},
 {"Tamam","OK","ОК"},
 {"Tümü","All","Все"},
 {"Değişiklik bir sonraki bağlanışta geçerli.","Applies on the next connection.","Применится при следующем подключении."},
 {"Log kaydı","Logging","Журнал"},
 {"Logu paylaş","Share log","Поделиться журналом"},
 {"Logu sil","Delete log","Удалить журнал"},
 {"Log boş.","Log is empty.","Журнал пуст."},
 {"Log silindi.","Log deleted.","Журнал удалён."},
 {"Uygulama ara…","Search apps…","Поиск приложений…"},
 {"Güncelleme var","Update available","Доступно обновление"},
 {"Yeni sürüm çıktı: ","New version available: ","Вышла новая версия: "},
 {"İndir","Download","Скачать"},
 {"Sonra","Later","Позже"},
 {"Güncellemeleri kontrol et","Check for updates","Проверить обновления"},
 {"Uygulama güncel.","App is up to date.","Приложение обновлено."},
 {"Kontrol edilemedi.","Could not check.","Не удалось проверить."},
 {"Kontrol ediliyor…","Checking…","Проверка…"},
 {"VPN başlatılamadı","VPN failed to start","Не удалось запустить VPN"},
 {"Android VPN arayüzü oluşturulamadı.","Could not create the Android VPN interface.","Не удалось создать интерфейс VPN Android."},
 {"Uygulama VPN dışında bırakılamadı.","Could not exclude the app from the VPN.","Не удалось исключить приложение из VPN."},
 {"Hev tüneli başlatılamadı.","Could not start the tunnel.","Не удалось запустить туннель."},
 {"Güncellemeler F-Droid üzerinden yapılır.","Updates are handled by F-Droid.","Обновления выполняет F-Droid."},
 {"Ayarlar","Settings","Настройки"},
 {"Uygulama ve önizleme seçenekleri","App and preview options","Параметры приложения и предпросмотра"},
 {"Önizleme modu (DEMO)","Preview mode (DEMO)","Режим предпросмотра (ДЕМО)"},
 {"KAPALI","OFF","ВЫКЛ"},
 {"AÇIK","ON","ВКЛ"},
 {"Yalnızca arayüz durumlarını görmek içindir. Gerçek bağlantı kurmaz, trafiği tünellemez; değerler rastgele üretilir ve DEMO olarak işaretlenir.","For viewing interface states only. It makes no real connection and tunnels no traffic; values are random and marked as DEMO.","Только для просмотра состояний интерфейса. Реального подключения нет, трафик не туннелируется; значения случайны и помечены как ДЕМО."},
 {"DPI bypass motoru","DPI bypass engine","Движок обхода DPI"},
 {"Aktif (VaxoDPI)","Active (VaxoDPI)","Активен (VaxoDPI)"},
 {"Aktif","Active","Активно"},
 {"Ağ ölçümü","Network metrics","Сетевые метрики"},
 {"Sürüm","Version","Версия"},
 {"Uygulama bazlı bölme","Per-app split","Раздельное туннелирование по приложениям"},
 {"DNS ayarları","DNS settings","Настройки DNS"},
 {"Otomatik bağlan","Auto-connect","Автоподключение"},
 {"Ana Sayfa","Home","Главная"},
 {"BAĞLANIYOR…","CONNECTING…","ПОДКЛЮЧЕНИЕ…"},
 {"Önizleme animasyonu çalışıyor (DEMO)","Preview animation running (DEMO)","Работает демо-анимация"},
 {"VPN tüneli başlatılıyor","Starting the tunnel","Запуск туннеля"},
 {"İPTAL","CANCEL","ОТМЕНА"},
 {"BAĞLI (DEMO)","CONNECTED (DEMO)","ПОДКЛЮЧЕНО (ДЕМО)"},
 {"TÜNEL BAŞLATILDI","TUNNEL STARTED","ТУННЕЛЬ ЗАПУЩЕН"},
 {"Bu bir önizlemedir; trafik tünellenmiyor","This is a preview; traffic is not tunneled","Это предпросмотр; трафик не туннелируется"},
 {"DPI atlatma etkin","DPI bypass is active","Обход DPI активен"},
 {"BAĞLANTIYI KES","DISCONNECT","ОТКЛЮЧИТЬ"},
 {"Önizleme modu açık – gerçek bağlantı kurulmaz","Preview mode is on – no real connection","Режим предпросмотра включён – реального подключения нет"},
 {"VPN bağlantısı etkin değil","Connection is not active","Соединение не активно"},
 {"VPN izni verilmedi.","VPN permission was not granted.","Разрешение VPN не предоставлено."},
 {"VPN durdurulamadı.","Could not stop the tunnel.","Не удалось остановить туннель."},
 {"VPN hatası","VPN error","Ошибка VPN"},
 {"Yeni strateji için bağlantıyı kesip yeniden bağlan.","Disconnect and reconnect to use the new strategy.","Отключитесь и подключитесь снова, чтобы применить новую стратегию."},
 {"Strateji seçildi: ","Strategy selected: ","Стратегия выбрана: "},
 {"Dil","Language","Язык"},
 {"Bağlantılar","Links","Ссылки"},
 {"Discord adı kopyalandı","Discord username copied","Ник Discord скопирован"},
    };

    private int langIdx() {
        if (lang < 0) {
            String sys = java.util.Locale.getDefault().getLanguage();
            int d = "tr".equals(sys) ? 0 : ("ru".equals(sys) ? 2 : 1);
            lang = getSharedPreferences("vaxodpi", MODE_PRIVATE).getInt("lang", d);
        }
        return lang;
    }

    private String L(String s) {
        int l = langIdx();
        for (String[] r : TR3) {
            if (r[0].equals(s) || r[1].equals(s) || r[2].equals(s)) return r[l];
        }
        return s;
    }

    private void translateTree(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            String cur = t.getText().toString();
            String n = L(cur);
            if (!n.equals(cur)) t.setText(n);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) translateTree(g.getChildAt(i));
        }
    }

    @Override
    protected void onPostCreate(android.os.Bundle b) {
        super.onPostCreate(b);
        translateTree(getWindow().getDecorView());
        checkUpdate();
    }

    private final java.util.ArrayList<TextView> langChips = new java.util.ArrayList<>();

    private void refreshLangChips() {
        int cur = langIdx();
        for (int i = 0; i < langChips.size(); i++) {
            TextView c = langChips.get(i);
            if (i == cur) {
                c.setTextColor(BG);
                c.setBackground(round(0xFFFFFFFF, 12, 0xFFFFFFFF));
            } else {
                c.setTextColor(MUTED);
                c.setBackground(round(0x14FFFFFF, 12, 0x33FFFFFF));
            }
        }
    }

    private void setLang(int l) {
        lang = l;
        getSharedPreferences("vaxodpi", MODE_PRIVATE).edit().putInt("lang", l).apply();
        translateTree(getWindow().getDecorView());
        refreshLangChips();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();

    private float density;
    private State state = State.DISCONNECTED;
    private boolean demoMode = false;
    private boolean resumed = false;
    private long connectedAt = 0L;
    private double usedMb = 0;
    private int currentPage = -1;

    private RingView ring;
    private TextView statusTitle, statusSub, connectBtn, timerText, demoBadge, demoToggle;
    private LinearLayout homeColumn, noticeCard;
    private final TextView[] statValues = new TextView[4];
    private final TextView[] statUnits = new TextView[4];
    private final View[] pages = new View[3];
    private final IconView[] navIcons = new IconView[3];
    private final TextView[] navLabels = new TextView[3];

    // ------------------------------------------------------------------ yaşam döngüsü

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        density = getResources().getDisplayMetrics().density;
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(BG));
        try {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        } catch (Throwable ignored) { }
        setContentView(buildRoot());

        IntentFilter filter = new IntentFilter(VaxoVpnService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(vpnReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(vpnReceiver, filter);
        }
        receiverRegistered = true;
        setPage(0, false);
        applyState(State.DISCONNECTED);
        playEntrance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        ring.setRunning(true);
        if (state == State.CONNECTED) {
            updateTimer();
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, 1000);
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        ring.setRunning(false);
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) {
            unregisterReceiver(vpnReceiver);
            receiverRegistered = false;
        }
        if (ring != null) ring.setRunning(false);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (currentPage != 0) {
            setPage(0, true);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ yardımcılar

    private int dp(float v) { return Math.round(v * density); }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.BOLD)
                : Typeface.create("sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    private GradientDrawable round(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1.2f)), stroke);
        return g;
    }

    private Drawable ripple(Drawable base, float radiusDp) {
        GradientDrawable mask = round(Color.WHITE, radiusDp, 0);
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, mask);
    }

    private LinearLayout.LayoutParams lp(int w, int h, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    /** Dokunma geri bildirimi: hafif küçülme. Tıklama olayını engellemez. */
    private void press(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                }
                return false;
            }
        });
    }

    private LinearLayout card(float padDp) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 20, CARD_STROKE));
        c.setPadding(dp(padDp), dp(padDp), dp(padDp), dp(padDp));
        return c;
    }

    private ScrollView scrollOf(LinearLayout col) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col, new ScrollView.LayoutParams(MATCH, WRAP));
        return sv;
    }

    private LinearLayout pageColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(12), dp(20), dp(24));
        return col;
    }

    // ------------------------------------------------------------------ kök yerleşim

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        pages[0] = buildHome();
        pages[1] = buildModes();
        pages[2] = buildSettings();
        for (int i = 0; i < 3; i++) {
            content.addView(pages[i], new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        root.addView(buildNav(), new LinearLayout.LayoutParams(MATCH, WRAP));

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                int l, t, r, b;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets i = in.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout());
                    l = i.left; t = i.top; r = i.right; b = i.bottom;
                } else {
                    l = in.getSystemWindowInsetLeft();
                    t = in.getSystemWindowInsetTop();
                    r = in.getSystemWindowInsetRight();
                    b = in.getSystemWindowInsetBottom();
                }
                v.setPadding(l, t, r, b);
                return in;
            }
        });
        return root;
    }

    // ------------------------------------------------------------------ ana sayfa

    private View buildHome() {
        LinearLayout col = pageColumn();
        homeColumn = col;

        // Üst başlık
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(getResources().getIdentifier("ic_launcher", "mipmap", getPackageName()));
        logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        logo.setBackground(round(0xFF000000, 13, 0x33FFFFFF));
        logo.setClipToOutline(true);
        header.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(12), 0, 0, 0);
        names.addView(tv("VaxoDPI", 20, TEXT, true));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(WRAP, WRAP);
        sp.topMargin = dp(3);
        names.addView(tv("DPI bypass arayüzü", 12, MUTED, false), sp);
        header.addView(names, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView ver = tv(appVersion(), 12, ACCENT, true);
        ver.setPadding(dp(10), dp(5), dp(10), dp(5));
        ver.setBackground(round(0x1AFFFFFF, 12, 0x55FFFFFF));
        header.addView(ver);
        col.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        // DEMO rozeti
        demoBadge = tv("DEMO – gösterilen değerler gerçek ölçüm değildir", 12, AMBER, true);
        demoBadge.setGravity(Gravity.CENTER);
        demoBadge.setPadding(dp(12), dp(8), dp(12), dp(8));
        demoBadge.setBackground(round(0x1AFFFFFF, 12, 0x55FFFFFF));
        demoBadge.setVisibility(View.GONE);
        col.addView(demoBadge, lp(MATCH, WRAP, 14));

        // Bağlantı halkası
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int ringSize = Math.min(dp(280), (int) (screenW * 0.72f));
        ring = new RingView(this, density);
        press(ring);
        ring.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onConnectClicked(); }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ringSize, ringSize);
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.topMargin = dp(18);
        col.addView(ring, rp);

        statusTitle = tv("BAĞLI DEĞİL", 22, TEXT, true);
        statusTitle.setGravity(Gravity.CENTER);
        statusTitle.setLetterSpacing(0.08f);
        col.addView(statusTitle, lp(MATCH, WRAP, 14));

        statusSub = tv("", 13, MUTED, false);
        statusSub.setGravity(Gravity.CENTER);
        col.addView(statusSub, lp(MATCH, WRAP, 6));

        connectBtn = tv("BAĞLAN", 16, Color.WHITE, true);
        connectBtn.setGravity(Gravity.CENTER);
        connectBtn.setLetterSpacing(0.1f);
        connectBtn.setClickable(true);
        press(connectBtn);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onConnectClicked(); }
        });
        col.addView(connectBtn, lp(MATCH, dp(56), 20));

        // Bağlantı süresi
        LinearLayout timerCard = card(16);
        timerCard.setOrientation(LinearLayout.HORIZONTAL);
        timerCard.setGravity(Gravity.CENTER_VERTICAL);
        timerCard.addView(tv("Bağlantı süresi", 13, MUTED, false),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        timerText = tv("00:00:00", 22, MUTED, true);
        timerText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        timerCard.addView(timerText);
        col.addView(timerCard, lp(MATCH, WRAP, 18));

        // İstatistik kartları
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(statCard("İNDİRME", 0), statParams(true));
        row1.addView(statCard("YÜKLEME", 1), statParams(false));
        col.addView(row1, lp(MATCH, WRAP, 12));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statCard("PING", 2), statParams(true));
        row2.addView(statCard("KULLANILAN VERİ", 3), statParams(false));
        col.addView(row2, lp(MATCH, WRAP, 12));

        // Altyapı bildirimi
        noticeCard = card(16);
        noticeCard.setBackground(round(0x12FFFFFF, 20, 0x33FFFFFF));
        noticeCard.addView(tv("Altyapı durumu", 13, ACCENT, true));
        TextView nt = tv("VaxoDPI motoru aktif. BAĞLAN'a basınca tünel kurulur. "
                + "İstatistikler tünel sayaçlarından hesaplanır.",
                13, MUTED, false);
        nt.setLineSpacing(0, 1.15f);
        noticeCard.addView(nt, lp(MATCH, WRAP, 6));
        col.addView(noticeCard, lp(MATCH, WRAP, 16));

        return scrollOf(col);
    }

    private LinearLayout.LayoutParams statParams(boolean left) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
        if (left) p.rightMargin = dp(6); else p.leftMargin = dp(6);
        return p;
    }

    private LinearLayout statCard(String label, int idx) {
        LinearLayout c = card(16);
        c.addView(tv(label, 11, MUTED, true));
        ((TextView) c.getChildAt(0)).setLetterSpacing(0.1f);

        LinearLayout valRow = new LinearLayout(this);
        valRow.setOrientation(LinearLayout.HORIZONTAL);
        valRow.setGravity(Gravity.BOTTOM);
        TextView v = tv("--", 26, TEXT, true);
        valRow.addView(v);
        TextView u = tv("", 12, MUTED, false);
        u.setPadding(dp(5), 0, 0, dp(3));
        valRow.addView(u);
        c.addView(valRow, lp(MATCH, WRAP, 10));
        statValues[idx] = v;
        statUnits[idx] = u;
        return c;
    }

    private void setStat(int idx, String value, String unit) {
        statValues[idx].setText(value);
        statUnits[idx].setText(unit);
        statValues[idx].setAlpha(0.55f);
        statValues[idx].animate().alpha(1f).setDuration(260).start();
    }

    private long lastTxBytes = 0L, lastRxBytes = 0L;
    private long lastStatTime = 0L, lastPingTime = 0L;

    private void updateRealStats() {
        long[] st;
        try {
            st = TProxyService.TProxyGetStats();
        } catch (Throwable e) {
            return;
        }
        if (st == null || st.length < 4) return;

        long now = SystemClock.elapsedRealtime();
        if (lastStatTime > 0) {
            double dt = (now - lastStatTime) / 1000.0;
            if (dt > 0) {
                double dl = Math.max(0, st[3] - lastRxBytes) * 8.0 / 1e6 / dt;
                double ul = Math.max(0, st[1] - lastTxBytes) * 8.0 / 1e6 / dt;
                setStat(0, String.format(Locale.US, "%.2f", dl), "Mbps");
                setStat(1, String.format(Locale.US, "%.2f", ul), "Mbps");
            }
        }
        lastStatTime = now;
        lastTxBytes = st[1];
        lastRxBytes = st[3];

        double mb = (st[1] + st[3]) / 1048576.0;
        if (mb < 1024) {
            setStat(3, String.format(Locale.US, "%.1f", mb), "MB");
        } else {
            setStat(3, String.format(Locale.US, "%.2f", mb / 1024.0), "GB");
        }

        if (now - lastPingTime >= 5000) {
            lastPingTime = now;
            startPing();
        }
    }

    private void startPing() {
        new Thread(new Runnable() {
            @Override public void run() {
                String result = "--";
                try (java.net.Socket sock = new java.net.Socket()) {
                    sock.connect(new java.net.InetSocketAddress("127.0.0.1", 1080), 2000);
                    sock.setSoTimeout(3000);
                    java.io.OutputStream o = sock.getOutputStream();
                    java.io.InputStream in = sock.getInputStream();
                    o.write(new byte[]{5, 1, 0});
                    o.flush();
                    byte[] r = new byte[2];
                    if (in.read(r) == 2) {
                        long t0 = SystemClock.elapsedRealtime();
                        o.write(new byte[]{5, 1, 0, 1, 1, 1, 1, 1, 0x01, (byte) 0xBB});
                        o.flush();
                        byte[] c = new byte[10];
                        int n = in.read(c);
                        long ms = SystemClock.elapsedRealtime() - t0;
                        if (n > 1 && c[1] == 0) result = String.valueOf(ms);
                    }
                } catch (Throwable ignored) {
                }
                final String shown = result;
                handler.post(new Runnable() {
                    @Override public void run() {
                        if (state == State.CONNECTED && !demoMode) {
                            setStat(2, shown, "ms");
                        }
                    }
                });
            }
        }, "VaxoDPI-Ping").start();
    }

    private void clearStats() {
        String[] units = {"Mbps", "Mbps", "ms", "MB"};
        for (int i = 0; i < 4; i++) {
            statValues[i].setText("--");
            statUnits[i].setText(units[i]);
            statValues[i].setAlpha(1f);
        }
    }

    private void setStatsDemo() {
        double dl = 35 + rnd.nextDouble() * 60;
        double ul = 8 + rnd.nextDouble() * 25;
        int ping = 18 + rnd.nextInt(40);
        usedMb += (dl + ul) / 8.0; // saniyede aktarılan yaklaşık MB
        setStat(0, String.format(Locale.US, "%.1f", dl), "Mbps");
        setStat(1, String.format(Locale.US, "%.1f", ul), "Mbps");
        setStat(2, String.valueOf(ping), "ms");
        if (usedMb < 1024) {
            setStat(3, String.format(Locale.US, "%.1f", usedMb), "MB");
        } else {
            setStat(3, String.format(Locale.US, "%.2f", usedMb / 1024.0), "GB");
        }
    }

    private void playEntrance() {
        int n = homeColumn.getChildCount();
        for (int i = 0; i < n; i++) {
            View c = homeColumn.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(dp(22));
            c.animate().alpha(1f).translationY(0f).setDuration(420)
                    .setStartDelay(70L * i)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ------------------------------------------------------------------ modlar sayfası

    private View buildModes() {
        LinearLayout col = pageColumn();
        col.addView(tv("Modlar", 26, TEXT, true));
        col.addView(tv("Bağlantı yöntemini seç", 13, MUTED, false), lp(WRAP, WRAP, 4));

        LinearLayout info = card(16);
        info.setBackground(round(0x14FFFFFF, 20, 0x44FFFFFF));
        info.addView(tv("DPI stratejisi", 13, AMBER, true));
        TextView it = tv("Bir site açılmazsa başka bir strateji seç. "
                + "Değişiklik bir sonraki bağlanışta geçerli olur.", 13, MUTED, false);
        it.setLineSpacing(0, 1.15f);
        info.addView(it, lp(MATCH, WRAP, 6));
        col.addView(info, lp(MATCH, WRAP, 18));

        addStrategyCards(col);
        
        
        return scrollOf(col);
    }

    private final java.util.ArrayList<TextView> stratChips = new java.util.ArrayList<>();

    private int currentStrategy() {
        return getSharedPreferences("vaxodpi", MODE_PRIVATE).getInt("strategy", 0);
    }

    private void refreshStrategyChips() {
        int cur = currentStrategy();
        for (int i = 0; i < stratChips.size(); i++) {
            TextView chip = stratChips.get(i);
            if (i == cur) {
                chip.setText(L("SEÇİLİ"));
                chip.setTextColor(GREEN);
                chip.setBackground(round(0x14FFFFFF, 10, 0x88FFFFFF));
            } else {
                chip.setText(L("SEÇ"));
                chip.setTextColor(MUTED);
                chip.setBackground(round(0x14FFFFFF, 10, 0x33FFFFFF));
            }
        }
    }

    private void addStrategyCards(LinearLayout col) {
        stratChips.clear();
        for (int i = 0; i < VaxoVpnService.STRATEGY_NAMES.length; i++) {
            final int idx = i;
            LinearLayout c = card(16);
            c.setOrientation(LinearLayout.HORIZONTAL);
            c.setGravity(Gravity.CENTER_VERTICAL);
            c.setClickable(true);
            press(c);

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.addView(tv(VaxoVpnService.STRATEGY_NAMES[i], 16, TEXT, true));
            txt.addView(tv(VaxoVpnService.STRATEGY_DESCS[i], 12, MUTED, false), lp(WRAP, WRAP, 4));
            c.addView(txt, new LinearLayout.LayoutParams(0, WRAP, 1f));

            TextView chip = tv("", 10, MUTED, true);
            chip.setLetterSpacing(0.1f);
            chip.setPadding(dp(10), dp(5), dp(10), dp(5));
            stratChips.add(chip);
            c.addView(chip);

            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    getSharedPreferences("vaxodpi", MODE_PRIVATE)
                            .edit().putInt("strategy", idx).apply();
                    refreshStrategyChips();
                    String msg = (state != State.DISCONNECTED)
                            ? L("Yeni strateji için bağlantıyı kesip yeniden bağlan.")
                            : L("Strateji seçildi: ") + L(VaxoVpnService.STRATEGY_NAMES[idx]);
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            });
            col.addView(c, lp(MATCH, WRAP, 12));
        }
        refreshStrategyChips();
    }

    private View modeCard(final String title, String desc) {
        LinearLayout c = card(16);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setClickable(true);
        press(c);

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(tv(title, 16, TEXT, true));
        txt.addView(tv(desc, 12, MUTED, false), lp(WRAP, WRAP, 4));
        c.addView(txt, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView chip = tv("YAKINDA", 10, MUTED, true);
        chip.setLetterSpacing(0.1f);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setBackground(round(0x14FFFFFF, 10, 0x33FFFFFF));
        c.addView(chip);

        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this,
                        "\"" + title + "\" modu henüz kullanılabilir değil.",
                        Toast.LENGTH_SHORT).show();
            }
        });
        return c;
    }

    // ------------------------------------------------------------------ ayarlar sayfası

    private View buildSettings() {
        LinearLayout col = pageColumn();
        col.addView(tv("Ayarlar", 26, TEXT, true));
        col.addView(tv("Uygulama ve önizleme seçenekleri", 13, MUTED, false), lp(WRAP, WRAP, 4));

        // Dil seçimi
        LinearLayout langCard = card(16);
        langCard.addView(tv("Dil", 13, ACCENT, true));
        LinearLayout langRow = new LinearLayout(this);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        langChips.clear();
        String[] langNames = {"TÜRKÇE", "ENGLISH", "РУССКИЙ"};
        for (int i = 0; i < 3; i++) {
            final int li = i;
            TextView lc = tv(langNames[i], 12, MUTED, true);
            lc.setGravity(Gravity.CENTER);
            lc.setClickable(true);
            press(lc);
            lc.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setLang(li); }
            });
            LinearLayout.LayoutParams lpar = new LinearLayout.LayoutParams(0, dp(36), 1f);
            if (i > 0) lpar.leftMargin = dp(8);
            langRow.addView(lc, lpar);
            langChips.add(lc);
        }
        langCard.addView(langRow, lp(MATCH, WRAP, 12));
        col.addView(langCard, lp(MATCH, WRAP, 18));
        refreshLangChips();

        // Demo anahtarı
        LinearLayout demo = card(16);
        LinearLayout demoRow = new LinearLayout(this);
        demoRow.setOrientation(LinearLayout.HORIZONTAL);
        demoRow.setGravity(Gravity.CENTER_VERTICAL);
        demoRow.addView(tv("Önizleme modu (DEMO)", 16, TEXT, true),
                new LinearLayout.LayoutParams(0, WRAP, 1f));
        demoToggle = tv("KAPALI", 12, MUTED, true);
        demoToggle.setLetterSpacing(0.1f);
        demoToggle.setGravity(Gravity.CENTER);
        demoToggle.setClickable(true);
        press(demoToggle);
        demoToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDemo(); }
        });
        demoRow.addView(demoToggle, new LinearLayout.LayoutParams(dp(84), dp(34)));
        demo.addView(demoRow, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView dt = tv("Yalnızca arayüz durumlarını görmek içindir. Gerçek bağlantı kurmaz, "
                + "trafiği tünellemez; değerler rastgele üretilir ve DEMO olarak işaretlenir.",
                12, MUTED, false);
        dt.setLineSpacing(0, 1.15f);
        demo.addView(dt, lp(MATCH, WRAP, 8));
        col.addView(demo, lp(MATCH, WRAP, 18));
        updateToggle();

        // Altyapı durumu
        LinearLayout status = card(16);
        status.addView(tv("Altyapı durumu", 13, ACCENT, true));
        status.addView(infoRow("VPN", "Yakında", MUTED), lp(MATCH, WRAP, 12));
        status.addView(infoRow("DPI bypass motoru", "Aktif (VaxoDPI)", GREEN), lp(MATCH, WRAP, 10));
        status.addView(infoRow("Ağ ölçümü", "Aktif", GREEN), lp(MATCH, WRAP, 10));
        status.addView(infoRow("Sürüm", appVersion(), TEXT), lp(MATCH, WRAP, 10));
        col.addView(status, lp(MATCH, WRAP, 12));

        // Yakında
        LinearLayout appsCard = card(16);
        View appsRow = infoRow("Uygulama bazlı bölme", appsValueText(), TEXT);
        appsRow.setClickable(true);
        appsRow.setPadding(0, dp(8), 0, dp(8));
        appsValue = (TextView) ((ViewGroup) appsRow).getChildAt(1);
        appsRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showAppPicker(); }
        });
        appsCard.addView(appsRow, lp(MATCH, WRAP, 0));
        col.addView(appsCard, lp(MATCH, WRAP, 12));

        LinearLayout toolsCard = card(16);
        View dnsRow = infoRow("DNS ayarları", dnsName(), TEXT);
        dnsRow.setClickable(true);
        dnsRow.setPadding(0, dp(8), 0, dp(8));
        dnsValue = (TextView) ((ViewGroup) dnsRow).getChildAt(1);
        dnsRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showDnsPicker(); }
        });
        toolsCard.addView(dnsRow, lp(MATCH, WRAP, 0));

        View logRow = infoRow("Log kaydı", logOn() ? L("AÇIK") : L("KAPALI"), TEXT);
        logRow.setClickable(true);
        logRow.setPadding(0, dp(8), 0, dp(8));
        logValue = (TextView) ((ViewGroup) logRow).getChildAt(1);
        logRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleLog(); }
        });
        toolsCard.addView(logRow, lp(MATCH, WRAP, 6));

        View shareRow = infoRow("Logu paylaş", ">", TEXT);
        shareRow.setClickable(true);
        shareRow.setPadding(0, dp(8), 0, dp(8));
        shareRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { shareLog(); }
        });
        toolsCard.addView(shareRow, lp(MATCH, WRAP, 6));

        View delRow = infoRow("Logu sil", ">", TEXT);
        delRow.setClickable(true);
        delRow.setPadding(0, dp(8), 0, dp(8));
        delRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearLog(); }
        });
        toolsCard.addView(delRow, lp(MATCH, WRAP, 6));

        View updRow = infoRow("Güncellemeleri kontrol et", ">", TEXT);
        updRow.setClickable(true);
        updRow.setPadding(0, dp(8), 0, dp(8));
        updRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this, L("Kontrol ediliyor…"),
                        Toast.LENGTH_SHORT).show();
                checkUpdate(true);
            }
        });
        toolsCard.addView(updRow, lp(MATCH, WRAP, 6));
        col.addView(toolsCard, lp(MATCH, WRAP, 12));

        LinearLayout soon = card(16);
        soon.addView(tv("Yakında", 13, ACCENT, true));
        soon.addView(infoRow("Otomatik bağlan", "Yakında", MUTED), lp(MATCH, WRAP, 10));
        col.addView(soon, lp(MATCH, WRAP, 12));

        LinearLayout about = card(16);
        about.addView(tv("Bağlantılar", 13, ACCENT, true));
        about.addView(linkRow("Discord", 0), lp(MATCH, WRAP, 12));
        about.addView(linkRow("GitHub", 1), lp(MATCH, WRAP, 10));
        col.addView(about, lp(MATCH, WRAP, 12));

        return scrollOf(col);
    }

    private TextView dnsValue, logValue;
    private static final String[] DNS_NAMES = {"Cloudflare", "Google", "Quad9", "AdGuard"};
    private static final String[] DNS_ITEMS = {"Cloudflare (1.1.1.1)", "Google (8.8.8.8)",
            "Quad9 (9.9.9.9)", "AdGuard (94.140.14.14)"};

    private int dnsIdx() {
        int i = getSharedPreferences("vaxodpi", MODE_PRIVATE).getInt("dns", 0);
        return (i < 0 || i >= DNS_NAMES.length) ? 0 : i;
    }

    private String dnsName() {
        return DNS_NAMES[dnsIdx()];
    }

    private void showDnsPicker() {
        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(
                this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle(L("DNS ayarları"));
        b.setSingleChoiceItems(DNS_ITEMS, dnsIdx(),
                new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                getSharedPreferences("vaxodpi", MODE_PRIVATE).edit()
                        .putInt("dns", which).apply();
                if (dnsValue != null) dnsValue.setText(dnsName());
                if (state != State.DISCONNECTED) {
                    Toast.makeText(MainActivity.this,
                            L("Değişiklik bir sonraki bağlanışta geçerli."),
                            Toast.LENGTH_SHORT).show();
                }
                d.dismiss();
            }
        });
        b.show();
    }

    private boolean logOn() {
        return getSharedPreferences("vaxodpi", MODE_PRIVATE).getBoolean("log_on", false);
    }

    private void toggleLog() {
        boolean on = !logOn();
        getSharedPreferences("vaxodpi", MODE_PRIVATE).edit().putBoolean("log_on", on).apply();
        if (logValue != null) logValue.setText(on ? L("AÇIK") : L("KAPALI"));
    }

    private void clearLog() {
        new java.io.File(getFilesDir(), "vaxodpi.log").delete();
        Toast.makeText(this, L("Log silindi."), Toast.LENGTH_SHORT).show();
    }

    private void shareLog() {
        try {
            java.io.File f = new java.io.File(getFilesDir(), "vaxodpi.log");
            if (!f.exists() || f.length() == 0) {
                Toast.makeText(this, L("Log boş."), Toast.LENGTH_SHORT).show();
                return;
            }
            long len = f.length();
            int n = (int) Math.min(len, 60000);
            byte[] buf = new byte[n];
            try (java.io.RandomAccessFile r = new java.io.RandomAccessFile(f, "r")) {
                r.seek(len - n);
                r.readFully(buf);
            }
            String text = "VaxoDPI " + appVersion() + "\nAndroid " + Build.VERSION.RELEASE
                    + " (" + Build.MODEL + ")\n\n" + new String(buf, "UTF-8");
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(send, "VaxoDPI log"));
        } catch (Exception e) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private TextView appsValue;

    private int selectedAppCount() {
        java.util.Set<String> s = getSharedPreferences("vaxodpi", MODE_PRIVATE)
                .getStringSet("apps", null);
        return s == null ? 0 : s.size();
    }

    private String appsValueText() {
        int n = selectedAppCount();
        return n == 0 ? L("Tüm uygulamalar") : n + " " + L("uygulama seçili");
    }

    private void showAppPicker() {
        final android.content.pm.PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN);
        q.addCategory(Intent.CATEGORY_LAUNCHER);
        java.util.List<android.content.pm.ResolveInfo> found = pm.queryIntentActivities(q, 0);
        java.util.ArrayList<String[]> items = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (android.content.pm.ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            items.add(new String[]{String.valueOf(ri.loadLabel(pm)), pkg});
        }
        java.util.Collections.sort(items, (x, y) -> x[0].compareToIgnoreCase(y[0]));
        final String[] labels = new String[items.size()];
        final String[] pkgs = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            labels[i] = items.get(i)[0];
            pkgs[i] = items.get(i)[1];
        }
        final java.util.HashSet<String> selected = new java.util.HashSet<>();
        java.util.Set<String> cur = getSharedPreferences("vaxodpi", MODE_PRIVATE)
                .getStringSet("apps", null);
        if (cur != null) selected.addAll(cur);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final android.widget.EditText search = new android.widget.EditText(this);
        search.setHint(L("Uygulama ara…"));
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(MUTED);
        box.addView(search, new LinearLayout.LayoutParams(MATCH, WRAP));
        final android.widget.ListView list = new android.widget.ListView(this);
        list.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
        box.addView(list, new LinearLayout.LayoutParams(MATCH, dp(360)));

        final java.util.ArrayList<Integer> shown = new java.util.ArrayList<>();
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                String qs = search.getText().toString().trim().toLowerCase();
                shown.clear();
                java.util.ArrayList<String> names = new java.util.ArrayList<>();
                for (int k = 0; k < labels.length; k++) {
                    if (qs.isEmpty() || labels[k].toLowerCase().contains(qs)
                            || pkgs[k].toLowerCase().contains(qs)) {
                        shown.add(k);
                        names.add(labels[k]);
                    }
                }
                list.setAdapter(new android.widget.ArrayAdapter<String>(MainActivity.this,
                        android.R.layout.simple_list_item_multiple_choice, names));
                for (int j = 0; j < shown.size(); j++) {
                    list.setItemChecked(j, selected.contains(pkgs[shown.get(j)]));
                }
            }
        };
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                String pk = pkgs[shown.get(pos)];
                if (list.isItemChecked(pos)) selected.add(pk); else selected.remove(pk);
            }
        });
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a2) { }
            @Override public void onTextChanged(CharSequence s, int st, int b2, int c) { refresh.run(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });
        refresh.run();

        android.app.AlertDialog.Builder bd = new android.app.AlertDialog.Builder(
                this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        bd.setTitle(L("Uygulamaları seç"));
        bd.setView(box);
        bd.setPositiveButton(L("Tamam"), new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                saveApps(new java.util.HashSet<String>(selected));
            }
        });
        bd.setNeutralButton(L("Tümü"), new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int w) {
                saveApps(new java.util.HashSet<String>());
            }
        });
        bd.show();
    }

    private void saveApps(java.util.Set<String> out) {
        getSharedPreferences("vaxodpi", MODE_PRIVATE).edit()
                .putStringSet("apps", out).apply();
        if (appsValue != null) appsValue.setText(appsValueText());
        if (state != State.DISCONNECTED) {
            Toast.makeText(this, L("Değişiklik bir sonraki bağlanışta geçerli."),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private View linkRow(String label, final int kind) {
        String value = kind == 0 ? "valeh.xyz" : "valehhdoth";
        View r = infoRow(label, value, TEXT);
        r.setClickable(true);
        r.setPadding(0, dp(8), 0, dp(8));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    if (kind == 0) {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("discord", "valeh.xyz"));
                        Toast.makeText(MainActivity.this, L("Discord adı kopyalandı"),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/valehhdoth")));
                    }
                } catch (Exception ignored) {
                }
            }
        });
        return r;
    }

    private View infoRow(String label, String value, int valueColor) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(tv(label, 14, TEXT, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        r.addView(tv(value, 13, valueColor, true));
        return r;
    }

    private void updateToggle() {
        if (demoToggle == null) return;
        if (demoMode) {
            demoToggle.setText(L("AÇIK"));
            demoToggle.setTextColor(AMBER);
            demoToggle.setBackground(round(0x1AFFFFFF, 17, 0x88FFFFFF));
        } else {
            demoToggle.setText(L("KAPALI"));
            demoToggle.setTextColor(MUTED);
            demoToggle.setBackground(round(0x14FFFFFF, 17, 0x33FFFFFF));
        }
    }

    private void toggleDemo() {
        demoMode = !demoMode;
        updateToggle();
        if (state != State.DISCONNECTED) {
            stopAll();
        } else {
            applyState(State.DISCONNECTED);
        }
    }

    // ------------------------------------------------------------------ alt navigasyon

    private View buildNav() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(0xFF050505);

        View line = new View(this);
        line.setBackgroundColor(0xFF222222);
        wrap.addView(line, new LinearLayout.LayoutParams(MATCH, 1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));

        String[] labels = {"Ana Sayfa", "Modlar", "Ayarlar"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, dp(8), 0, dp(8));
            item.setClickable(true);
            item.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT), 16));

            IconView ic = new IconView(this, i, density);
            item.addView(ic, new LinearLayout.LayoutParams(dp(26), dp(26)));
            TextView lb = tv(labels[i], 11, MUTED, true);
            item.addView(lb, lp(WRAP, WRAP, 4));
            navIcons[i] = ic;
            navLabels[i] = lb;

            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setPage(idx, true); }
            });
            row.addView(item, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        wrap.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return wrap;
    }

    private void setPage(int idx, boolean animate) {
        if (idx == currentPage) return;
        currentPage = idx;
        for (int i = 0; i < 3; i++) {
            boolean sel = (i == idx);
            pages[i].setVisibility(sel ? View.VISIBLE : View.GONE);
            navIcons[i].setColor(sel ? ACCENT : MUTED);
            navLabels[i].setTextColor(sel ? ACCENT : MUTED);
            navIcons[i].animate().scaleX(sel ? 1.15f : 1f).scaleY(sel ? 1.15f : 1f)
                    .setDuration(180).start();
        }
        if (animate) {
            View p = pages[idx];
            p.setAlpha(0f);
            p.setTranslationY(dp(14));
            p.animate().alpha(1f).translationY(0f).setDuration(240)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ------------------------------------------------------------------ bağlantı mantığı

    private void onConnectClicked() {
        if (state == State.CONNECTING || state == State.CONNECTED) {
            stopAll();
            return;
        }

        if (demoMode) {
            startDemo();
            return;
        }

        Intent permission = VpnService.prepare(this);

        if (permission != null) {
            startActivityForResult(permission, REQ_VPN);
        } else {
            startRealVpn();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQ_VPN) return;

        if (resultCode == RESULT_OK) {
            startRealVpn();
        } else {
            applyState(State.DISCONNECTED);
            Toast.makeText(this, L("VPN izni verilmedi."),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startRealVpn() {
        applyState(State.CONNECTING);

        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7302);
        }

        try {
            realServiceStarted = true;
            startService(new Intent(this, VaxoVpnService.class));
        } catch (Exception e) {
            realServiceStarted = false;
            applyState(State.DISCONNECTED);
            Toast.makeText(this, e.toString(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void shakeNotice() {
        noticeCard.animate().cancel();
        noticeCard.setTranslationX(0f);
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(380);
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) {
                float t = (Float) va.getAnimatedValue();
                float amp = dp(8) * (1f - t);
                noticeCard.setTranslationX((float) Math.sin(t * Math.PI * 6) * amp);
            }
        });
        a.start();
    }

    private final Runnable demoConnect = new Runnable() {
        @Override public void run() {
            if (state != State.CONNECTING) return;
            connectedAt = SystemClock.elapsedRealtime();
            usedMb = 0;
            applyState(State.CONNECTED);
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (state != State.CONNECTED) return;
            updateTimer();
            if (demoMode) setStatsDemo(); else updateRealStats();
            handler.postDelayed(this, 1000);
        }
    };

    private void startDemo() {
        applyState(State.CONNECTING);
        handler.removeCallbacks(demoConnect);
        handler.postDelayed(demoConnect, 2200);
    }

    private void stopAll() {
        handler.removeCallbacks(demoConnect);
        handler.removeCallbacks(ticker);

        if (realServiceStarted) {
            Intent stop = new Intent(this, VaxoVpnService.class);
            stop.setAction(VaxoVpnService.ACTION_STOP);

            try {
                startService(stop);
            } catch (Exception e) {
                Toast.makeText(this, L("VPN durdurulamadı."),
                        Toast.LENGTH_LONG).show();
            }

            realServiceStarted = false;
        }

        applyState(State.DISCONNECTED);
    }

    private void updateTimer() {
        if (state != State.CONNECTED) {
            timerText.setText("00:00:00");
            timerText.setTextColor(MUTED);
            return;
        }
        long s = Math.max(0, (SystemClock.elapsedRealtime() - connectedAt) / 1000);
        timerText.setText(String.format(Locale.US, "%02d:%02d:%02d",
                s / 3600, (s / 60) % 60, s % 60));
        timerText.setTextColor(TEXT);
    }

    private void applyState(State s) {
        state = s;
        lastStatTime = 0L;
        lastPingTime = 0L;
        handler.removeCallbacks(ticker);

        int color;
        String title, sub, btn;
        switch (s) {
            case CONNECTING:
                color = ACCENT;
                title = "BAĞLANIYOR…";
                sub = demoMode ? "Önizleme animasyonu çalışıyor (DEMO)" : "VPN tüneli başlatılıyor";
                btn = "İPTAL";
                break;
            case CONNECTED:
                color = GREEN;
                title = demoMode ? "BAĞLI (DEMO)" : "TÜNEL BAŞLATILDI";
                sub = demoMode ? "Bu bir önizlemedir; trafik tünellenmiyor" : "DPI atlatma etkin";
                btn = "BAĞLANTIYI KES";
                break;
            default:
                color = SLATE;
                title = "BAĞLI DEĞİL";
                sub = demoMode ? "Önizleme modu açık – gerçek bağlantı kurulmaz"
                        : "VPN bağlantısı etkin değil";
                btn = "BAĞLAN";
                break;
        }

        title = L(title);
        sub = L(sub);
        btn = L(btn);

        ring.setState(s, color);
        statusTitle.setText(title);
        statusTitle.setTextColor(s == State.DISCONNECTED ? TEXT : color);
        statusSub.setText(sub);
        statusTitle.setAlpha(0f);
        statusTitle.animate().alpha(1f).setDuration(220).start();
        connectBtn.setText(btn);
        styleButton(s);

        demoBadge.setVisibility(demoMode && s != State.DISCONNECTED ? View.VISIBLE : View.GONE);

        if (s == State.CONNECTED && demoMode) {
            setStatsDemo();
        } else {
            clearStats();
        }
        updateTimer();

        if (s == State.CONNECTED && resumed) {
            handler.postDelayed(ticker, 1000);
        }
    }

    private void styleButton(State s) {
        if (s == State.DISCONNECTED) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{ELECTRIC, ACCENT});
            g.setCornerRadius(dp(28));
            connectBtn.setBackground(ripple(g, 28));
            connectBtn.setTextColor(BG);
        } else if (s == State.CONNECTING) {
            connectBtn.setBackground(ripple(round(0x15FFFFFF, 28, ACCENT), 28));
            connectBtn.setTextColor(ACCENT);
        } else {
            connectBtn.setBackground(ripple(round(0x15FFFFFF, 28, RED), 28));
            connectBtn.setTextColor(RED);
        }
    }

    // ------------------------------------------------------------------ özel görünümler

    /** Animasyonlu neon bağlantı halkası. Animasyon yalnızca setRunning(true) iken çalışır. */
    static class RingView extends View {
        private final float d;
        private final Paint trackP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint discP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint iconP = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF iconRect = new RectF();
        private final Path check = new Path();

        private ValueAnimator anim;
        private float phase = 0f;
        private float breathe = 0f;
        private boolean running = false;
        private State st = State.DISCONNECTED;
        private int color = SLATE;
        private float cx, cy, radius, glowRadius;

        RingView(Context c, float density) {
            super(c);
            d = density;
            trackP.setStyle(Paint.Style.STROKE);
            trackP.setStrokeWidth(9 * d);
            arcP.setStyle(Paint.Style.STROKE);
            arcP.setStrokeWidth(9 * d);
            arcP.setStrokeCap(Paint.Cap.ROUND);
            discP.setStyle(Paint.Style.FILL);
            iconP.setStyle(Paint.Style.STROKE);
            iconP.setStrokeCap(Paint.Cap.ROUND);
            iconP.setStrokeJoin(Paint.Join.ROUND);
            iconP.setStrokeWidth(4.5f * d);
            setClickable(true);
        }

        void setState(State s, int c) {
            st = s;
            color = c;
            rebuildShaders();
            restart();
            invalidate();
        }

        void setRunning(boolean r) {
            running = r;
            restart();
        }

        private void restart() {
            if (anim != null) {
                anim.removeAllUpdateListeners();
                anim.cancel();
                anim = null;
            }
            if (!running) return;
            long dur = st == State.CONNECTING ? 1100L : (st == State.CONNECTED ? 4000L : 7000L);
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(dur);
            anim.setInterpolator(new LinearInterpolator());
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator va) {
                    phase = (Float) va.getAnimatedValue();
                    breathe = 0.5f + 0.5f * (float) Math.sin(phase * 2 * Math.PI);
                    invalidate();
                }
            });
            anim.start();
        }

        private float sweepFor(State s) {
            return s == State.CONNECTING ? 270f : (s == State.CONNECTED ? 90f : 110f);
        }

        private int withAlpha(int c, int a) {
            return (c & 0x00FFFFFF) | (a << 24);
        }

        private void rebuildShaders() {
            if (cx == 0f && cy == 0f) return;
            float sw = sweepFor(st);
            float p = sw / 360f;
            arcP.setShader(new SweepGradient(cx, cy,
                    new int[]{withAlpha(color, 0), color, color},
                    new float[]{0f, p, 1f}));
            glowP.setShader(new RadialGradient(cx, cy, glowRadius,
                    new int[]{withAlpha(color, 120), withAlpha(color, 0)},
                    new float[]{0.55f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            cx = w / 2f;
            cy = h / 2f;
            glowRadius = Math.min(w, h) / 2f;
            radius = glowRadius - 16 * d;
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            rebuildShaders();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) {
                anim.removeAllUpdateListeners();
                anim.cancel();
                anim = null;
            }
            super.onDetachedFromWindow();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            restart();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (radius <= 0f) return;

            // Parıltı
            int ga = st == State.CONNECTED ? (int) (110 + 145 * breathe)
                    : (st == State.CONNECTING ? (int) (140 + 115 * breathe) : (int) (40 + 40 * breathe));
            glowP.setAlpha(Math.min(255, ga));
            canvas.drawCircle(cx, cy, glowRadius, glowP);

            // İç disk
            discP.setColor(0xFF050505);
            canvas.drawCircle(cx, cy, radius - 16 * d, discP);

            // Halka zemini
            if (st == State.CONNECTED) {
                trackP.setColor(withAlpha(color, (int) (150 + 80 * breathe)));
            } else {
                trackP.setColor(0xFF2A2A2A);
            }
            canvas.drawCircle(cx, cy, radius, trackP);

            // Dönen ışık
            canvas.save();
            canvas.rotate(phase * 360f, cx, cy);
            canvas.drawArc(rect, 0f, sweepFor(st), false, arcP);
            canvas.restore();

            // Simge
            iconP.setColor(st == State.DISCONNECTED ? MUTED : color);
            if (st == State.CONNECTING) {
                iconP.setAlpha((int) (110 + 145 * breathe));
            }
            float ir = radius * 0.30f;
            if (st == State.CONNECTED) {
                check.reset();
                check.moveTo(cx - ir * 0.8f, cy + ir * 0.05f);
                check.lineTo(cx - ir * 0.2f, cy + ir * 0.65f);
                check.lineTo(cx + ir * 0.85f, cy - ir * 0.6f);
                canvas.drawPath(check, iconP);
            } else {
                iconRect.set(cx - ir, cy - ir + ir * 0.15f, cx + ir, cy + ir + ir * 0.15f);
                canvas.drawArc(iconRect, 310f, 280f, false, iconP);
                canvas.drawLine(cx, cy - ir * 1.05f, cx, cy - ir * 0.1f, iconP);
            }
        }
    }

    /** Alt navigasyon simgeleri (0: ana sayfa, 1: modlar, 2: ayarlar). */
    static class IconView extends View {
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        IconView(Context c, int type, float density) {
            super(c);
            this.type = type;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(MUTED);
        }

        void setColor(int c) {
            p.setColor(c);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            canvas.save();
            canvas.scale(s, s);
            p.setStrokeWidth(1.9f);
            if (type == 0) {
                path.reset();
                path.moveTo(2.5f, 11.5f);
                path.lineTo(12f, 3.5f);
                path.lineTo(21.5f, 11.5f);
                canvas.drawPath(path, p);
                path.reset();
                path.moveTo(5f, 10f);
                path.lineTo(5f, 20f);
                path.lineTo(19f, 20f);
                path.lineTo(19f, 10f);
                canvas.drawPath(path, p);
                path.reset();
                path.moveTo(10f, 20f);
                path.lineTo(10f, 14f);
                path.lineTo(14f, 14f);
                path.lineTo(14f, 20f);
                canvas.drawPath(path, p);
            } else if (type == 1) {
                r.set(4f, 4f, 10f, 10f);
                canvas.drawRoundRect(r, 2f, 2f, p);
                r.set(14f, 4f, 20f, 10f);
                canvas.drawRoundRect(r, 2f, 2f, p);
                r.set(4f, 14f, 10f, 20f);
                canvas.drawRoundRect(r, 2f, 2f, p);
                r.set(14f, 14f, 20f, 20f);
                canvas.drawRoundRect(r, 2f, 2f, p);
            } else {
                canvas.drawCircle(12f, 12f, 5.5f, p);
                canvas.drawCircle(12f, 12f, 2f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0);
                    float cs = (float) Math.cos(a);
                    float sn = (float) Math.sin(a);
                    canvas.drawLine(12f + cs * 7.5f, 12f + sn * 7.5f,
                            12f + cs * 10f, 12f + sn * 10f, p);
                }
            }
            canvas.restore();
        }
    }
}
