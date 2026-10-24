package xyz.valeh.vaxodpi;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** VaxoDPI'nin kendi SOCKS5 + DPI atlatma motoru. */
final class VaxoProxy {
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running;
    private int mode;

    private static volatile java.io.File store;

    static void setStore(java.io.File f) {
        store = f;
        BLOCKED.clear();
        try {
            if (f != null && f.exists()) {
                for (String line : new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        "UTF-8").split("\n")) {
                    if (!line.trim().isEmpty()) BLOCKED.add(line.trim());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void saveBlocked() {
        try {
            java.io.File f = store;
            if (f == null) return;
            if (BLOCKED.size() > 500) BLOCKED.clear();
            StringBuilder sb = new StringBuilder();
            for (String k : BLOCKED) sb.append(k).append('\n');
            java.nio.file.Files.write(f.toPath(), sb.toString().getBytes("UTF-8"));
        } catch (Throwable ignored) {
        }
    }

    static void resetBlocked() {
        BLOCKED.clear();
        saveBlocked();
    }
    private static final java.util.Set<String> BLOCKED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    void start(int port, int mode) throws IOException {
        this.mode = mode;
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), port), 128);
        pool = Executors.newCachedThreadPool();
        running = true;
        pool.execute(() -> {
            while (running) {
                try {
                    final Socket c = server.accept();
                    pool.execute(() -> handle(c));
                } catch (IOException e) {
                    if (!running || server.isClosed()) return;
                }
            }
        });
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
        if (pool != null) pool.shutdownNow();
    }

    private static void reply(OutputStream o, int code) throws IOException {
        o.write(new byte[]{5, (byte) code, 0, 1, 0, 0, 0, 0, 0, 0});
        o.flush();
    }

    private void handle(Socket c) {
        Socket r = null;
        try {
            c.setTcpNoDelay(true);
            DataInputStream in = new DataInputStream(c.getInputStream());
            OutputStream out = c.getOutputStream();

            if (in.readUnsignedByte() != 5) { c.close(); return; }
            int nm = in.readUnsignedByte();
            in.readFully(new byte[nm]);
            out.write(new byte[]{5, 0});
            out.flush();

            if (in.readUnsignedByte() != 5) { c.close(); return; }
            int cmd = in.readUnsignedByte();
            in.readUnsignedByte();
            int atyp = in.readUnsignedByte();
            String host = null;
            byte[] raw = null;
            if (atyp == 1) {
                raw = new byte[4]; in.readFully(raw);
            } else if (atyp == 4) {
                raw = new byte[16]; in.readFully(raw);
            } else if (atyp == 3) {
                int l = in.readUnsignedByte();
                byte[] nb = new byte[l]; in.readFully(nb);
                host = new String(nb, "UTF-8");
            } else { c.close(); return; }
            int port = in.readUnsignedShort();

            if (cmd == 3) { udp(c, in, out); return; }
            if (cmd != 1) { reply(out, 7); c.close(); return; }

            InetAddress target;
            try {
                target = host != null ? InetAddress.getByName(host)
                                      : InetAddress.getByAddress(raw);
            } catch (IOException e) { reply(out, 4); c.close(); return; }

            r = new Socket();
            r.setTcpNoDelay(true);
            try {
                r.connect(new InetSocketAddress(target, port), 10000);
            } catch (IOException e) { reply(out, 5); c.close(); r.close(); return; }
            reply(out, 0);

            byte[] first = new byte[16384];
            int n = in.read(first);
            if (n < 0) { c.close(); r.close(); return; }
            OutputStream ro = r.getOutputStream();
            if (port == 443 && n > 5 && (first[0] & 0xff) == 0x16) {
                String key = target.getHostAddress();
                if (BLOCKED.contains(key)) {
                    desync(r, ro, first, n);
                } else {
                    ro.write(first, 0, n);
                    ro.flush();
                    byte[] resp = new byte[65536];
                    int got = -1;
                    r.setSoTimeout(2500);
                    try {
                        got = r.getInputStream().read(resp);
                    } catch (IOException e) {
                        got = -1;
                    }
                    if (got > 0) {
                        r.setSoTimeout(0);
                        out.write(resp, 0, got);
                        out.flush();
                    } else {
                        BLOCKED.add(key);
                        saveBlocked();
                        try { r.close(); } catch (Throwable ignored) {}
                        r = new Socket();
                        r.setTcpNoDelay(true);
                        r.connect(new InetSocketAddress(target, port), 10000);
                        ro = r.getOutputStream();
                        desync(r, ro, first, n);
                    }
                }
            } else {
                ro.write(first, 0, n);
            }
            ro.flush();

            final Socket rs = r, cs = c;
            pool.execute(() -> pipe(rs, cs));
            pipe(cs, rs);
        } catch (Throwable e) {
            try { c.close(); } catch (Throwable ignored) {}
            try { if (r != null) r.close(); } catch (Throwable ignored) {}
        }
    }

    private static void pipe(Socket from, Socket to) {
        byte[] buf = new byte[65536];
        try {
            InputStream i = from.getInputStream();
            OutputStream o = to.getOutputStream();
            int n;
            while ((n = i.read(buf)) >= 0) {
                o.write(buf, 0, n);
                o.flush();
            }
        } catch (Throwable ignored) {
        } finally {
            try { from.close(); } catch (Throwable ignored) {}
            try { to.close(); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------ DPI atlatma

    private static void setTtl(Socket s, int ttl) {
        ParcelFileDescriptor p = null;
        try {
            p = ParcelFileDescriptor.fromSocket(s);
            try { Os.setsockoptInt(p.getFileDescriptor(),
                    OsConstants.IPPROTO_IP, 2, ttl); } catch (Throwable ignored) {}
            try { Os.setsockoptInt(p.getFileDescriptor(),
                    OsConstants.IPPROTO_IPV6, 16, ttl); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        } finally {
            try { if (p != null) p.close(); } catch (Throwable ignored) {}
        }
    }

    private static int findSni(byte[] b, int n) {
        try {
            int p = 5;
            if (b[p] != 1) return -1;
            p += 4 + 2 + 32;
            p += 1 + (b[p] & 0xff);
            p += 2 + (((b[p] & 0xff) << 8) | (b[p + 1] & 0xff));
            p += 1 + (b[p] & 0xff);
            int ext = ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
            p += 2;
            int end = Math.min(n, p + ext);
            while (p + 4 <= end) {
                int type = ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff);
                int len = ((b[p + 2] & 0xff) << 8) | (b[p + 3] & 0xff);
                p += 4;
                if (type == 0) return (p + 5 <= end) ? p + 5 : -1;
                p += len;
            }
        } catch (RuntimeException ignored) {
        }
        return -1;
    }

    private void desync(Socket r, OutputStream o, byte[] b, int n) throws IOException {
        int sni = findSni(b, n);
        byte[] d = b;
        int len = n;

        // TLS kaydını SNI'nın hemen sonrasında iki kayda böl.
        int recLen = ((b[3] & 0xff) << 8) | (b[4] & 0xff);
        if (recLen + 5 == n) {
            int pos = (sni > 5 ? sni - 5 : 1) + 1;
            if (pos >= 1 && pos < recLen) {
                d = new byte[n + 5];
                System.arraycopy(b, 0, d, 0, 3);
                d[3] = (byte) (pos >> 8); d[4] = (byte) pos;
                System.arraycopy(b, 5, d, 5, pos);
                int q = 5 + pos;
                System.arraycopy(b, 0, d, q, 3);
                int rest = recLen - pos;
                d[q + 3] = (byte) (rest >> 8); d[q + 4] = (byte) rest;
                System.arraycopy(b, 5 + pos, d, q + 5, rest);
                len = n + 5;
            }
        }

        int c1 = 1;
        int c2 = (sni > 1 && sni + 1 < len) ? sni + 1 : -1;

        if (mode == 0 || mode == 2) {
            int cut = (mode == 2 && c2 > 0) ? c2 : c1;
            setTtl(r, 1);
            o.write(d, 0, cut);
            o.flush();
            try { Thread.sleep(5); } catch (InterruptedException ignored) {}
            setTtl(r, 64);
            o.write(d, cut, len - cut);
        } else {
            o.write(d, 0, c1);
            o.flush();
            if (c2 > 0) {
                o.write(d, c1, c2 - c1);
                o.flush();
                o.write(d, c2, len - c2);
            } else {
                o.write(d, c1, len - c1);
            }
        }
    }

    // ------------------------------------------------------------ UDP ASSOCIATE

    private void udp(Socket c, DataInputStream in, OutputStream out) throws IOException {
        final DatagramSocket relay = new DatagramSocket(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0));
        final DatagramSocket up = new DatagramSocket();
        int lp = relay.getLocalPort();
        out.write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, (byte) (lp >> 8), (byte) lp});
        out.flush();

        final InetSocketAddress[] client = new InetSocketAddress[1];

        pool.execute(() -> {
            byte[] buf = new byte[65535];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                while (true) {
                    p.setLength(buf.length);
                    relay.receive(p);
                    client[0] = new InetSocketAddress(p.getAddress(), p.getPort());
                    if (buf[2] != 0) continue;
                    int atyp = buf[3];
                    int off = 4;
                    InetAddress dst;
                    if (atyp == 1) {
                        byte[] a = new byte[4];
                        System.arraycopy(buf, off, a, 0, 4); off += 4;
                        dst = InetAddress.getByAddress(a);
                    } else if (atyp == 4) {
                        byte[] a = new byte[16];
                        System.arraycopy(buf, off, a, 0, 16); off += 16;
                        dst = InetAddress.getByAddress(a);
                    } else if (atyp == 3) {
                        int l = buf[off] & 0xff; off++;
                        dst = InetAddress.getByName(new String(buf, off, l, "UTF-8"));
                        off += l;
                    } else continue;
                    int dport = ((buf[off] & 0xff) << 8) | (buf[off + 1] & 0xff);
                    off += 2;
                    up.send(new DatagramPacket(buf, off, p.getLength() - off, dst, dport));
                }
            } catch (Throwable ignored) {
            }
        });

        pool.execute(() -> {
            byte[] buf = new byte[65535];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                while (true) {
                    p.setLength(buf.length);
                    up.receive(p);
                    InetSocketAddress cl = client[0];
                    if (cl == null) continue;
                    byte[] a = p.getAddress().getAddress();
                    int hl = 4 + a.length + 2;
                    byte[] o = new byte[hl + p.getLength()];
                    o[3] = (byte) (a.length == 4 ? 1 : 4);
                    System.arraycopy(a, 0, o, 4, a.length);
                    o[4 + a.length] = (byte) (p.getPort() >> 8);
                    o[5 + a.length] = (byte) p.getPort();
                    System.arraycopy(buf, 0, o, hl, p.getLength());
                    relay.send(new DatagramPacket(o, o.length, cl));
                }
            } catch (Throwable ignored) {
            }
        });

        try {
            while (in.read() >= 0) { }
        } catch (IOException ignored) {
        } finally {
            relay.close();
            up.close();
            try { c.close(); } catch (Throwable ignored) {}
        }
    }
}
