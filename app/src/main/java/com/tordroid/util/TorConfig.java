package com.tordroid.util;

// Constants and configuration values for TorDROID
public class TorConfig {

    // Port settings
    public static final int SocksPort = 9050;
    public static final int HttpPort = 8118;
    public static final int ControlPort = 9051;
    public static final int DnsPort = 5400;
    public static final int TransPort = 9040;

    // VPN interface settings
    public static final String VpnAddress = "10.0.0.2";
    public static final int VpnPrefix = 24;
    public static final String VpnRoute = "0.0.0.0";
    public static final String VpnDns = "10.0.0.1";
    public static final int VpnMtu = 1500;

    // Tor network settings
    public static final String TorHost = "127.0.0.1";
    public static final int BootstrapTimeoutSec = 120;
    public static final int ConnectRetryMax = 5;
    public static final int ConnectRetryDelayMs = 3000;

    // Notification settings
    public static final int NotificationId = 1337;
    public static final String NotificationChannelId = "tordroid_channel";
    public static final String NotificationChannelName = "TorDROID VPN";

    // Intent action strings
    public static final String ActionStart = "com.tordroid.START";
    public static final String ActionStop = "com.tordroid.STOP";
    public static final String ActionStatus = "com.tordroid.STATUS";
    public static final String ActionNewIdentity = "com.tordroid.NEWIDENTITY";

    // Broadcast intent extra keys
    public static final String ExtraStatus = "status";
    public static final String ExtraProgress = "progress";
    public static final String ExtraMessage = "message";
    public static final String ExtraIp = "exit_ip";

    // Build the torrc configuration file content
    public static String BuildTorrcConfig(String DataDir) {
        return "DataDirectory " + DataDir + "\n"
            + "SocksPort " + TorHost + ":" + SocksPort + "\n"
            + "HTTPTunnelPort " + TorHost + ":" + HttpPort + "\n"
            + "ControlPort " + TorHost + ":" + ControlPort + "\n"
            + "DNSPort " + TorHost + ":" + DnsPort + "\n"
            + "TransPort " + TorHost + ":" + TransPort + "\n"
            + "AutomapHostsOnResolve 1\n"
            + "AutomapHostsSuffixes .onion,.exit\n"
            + "GeoIPExcludeUnknown 1\n"
            + "ClientOnly 1\n"
            + "SafeSocks 1\n"
            + "TestSocks 1\n"
            + "WarnUnsafeSocks 1\n"
            + "NewCircuitPeriod 30\n"
            + "MaxCircuitDirtiness 600\n"
            + "EnforceDistinctSubnets 1\n"
            + "Log notice stdout\n"
            + "RunAsDaemon 0\n";
    }
}
