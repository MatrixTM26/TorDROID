package com.tordroid.util;

/**
 * TorConfig - Konfigurasi konstan untuk TorDROID
 * Menyimpan semua pengaturan port, timeout, dan path Tor
 */
public class TorConfig {

    // ── Port Settings ────────────────────────────────────────────────────────
    public static final int SOCKS_PORT = 9050; // SOCKS5 proxy port
    public static final int HTTP_PORT = 8118; // HTTP proxy port
    public static final int CONTROL_PORT = 9051; // Tor control port
    public static final int DNS_PORT = 5400; // DNS-through-Tor port
    public static final int TRANS_PORT = 9040; // Transparent proxy port

    // ── VPN Settings ─────────────────────────────────────────────────────────
    public static final String VPN_ADDRESS = "10.0.0.2";
    public static final int VPN_PREFIX = 24;
    public static final String VPN_ROUTE = "0.0.0.0";
    public static final String VPN_DNS = "10.0.0.1";
    public static final int VPN_MTU = 1500;

    // ── Tor Network ──────────────────────────────────────────────────────────
    public static final String TOR_HOST = "127.0.0.1";
    public static final int BOOTSTRAP_TIMEOUT_SEC = 120;
    public static final int CONNECT_RETRY_MAX = 5;
    public static final int CONNECT_RETRY_DELAY = 3000; // ms

    // ── Notification ─────────────────────────────────────────────────────────
    public static final int NOTIF_ID = 1337;
    public static final String NOTIF_CHANNEL_ID = "tordroid_channel";
    public static final String NOTIF_CHANNEL_NAME = "TorDROID VPN";

    // ── Intent Actions ───────────────────────────────────────────────────────
    public static final String ACTION_START = "com.tordroid.START";
    public static final String ACTION_STOP = "com.tordroid.STOP";
    public static final String ACTION_STATUS = "com.tordroid.STATUS";
    public static final String ACTION_NEWID = "com.tordroid.NEWIDENTITY";

    // ── Broadcast Extras ─────────────────────────────────────────────────────
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_IP = "exit_ip";

    // ── Tor torrc config template ─────────────────────────────────────────────
    public static String buildTorrcConfig(String dataDir) {
        return (
            "DataDirectory " +
            dataDir +
            "\n" +
            "SocksPort " +
            TOR_HOST +
            ":" +
            SOCKS_PORT +
            "\n" +
            "HTTPTunnelPort " +
            TOR_HOST +
            ":" +
            HTTP_PORT +
            "\n" +
            "ControlPort " +
            TOR_HOST +
            ":" +
            CONTROL_PORT +
            "\n" +
            "DNSPort " +
            TOR_HOST +
            ":" +
            DNS_PORT +
            "\n" +
            "TransPort " +
            TOR_HOST +
            ":" +
            TRANS_PORT +
            "\n" +
            "AutomapHostsOnResolve 1\n" +
            "AutomapHostsSuffixes .onion,.exit\n" +
            "GeoIPExcludeUnknown 1\n" +
            "ClientOnly 1\n" +
            "SafeSocks 1\n" +
            "TestSocks 1\n" +
            "WarnUnsafeSocks 1\n" +
            "NewCircuitPeriod 30\n" +
            "MaxCircuitDirtiness 600\n" +
            "EnforceDistinctSubnets 1\n" +
            "Log notice stdout\n" +
            "RunAsDaemon 0\n"
        );
    }
}
