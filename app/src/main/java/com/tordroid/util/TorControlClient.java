package com.tordroid.util;

import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

// Communicates with the Tor daemon via the Control Port using TCP
public class TorControlClient {

    private static final String Tag = "TorControlClient";

    private Socket Socket;
    private BufferedReader Reader;
    private BufferedWriter Writer;
    private boolean Connected = false;

    // Opens a TCP connection to the Tor Control port
    public boolean Connect() {
        try {
            Socket = new java.net.Socket(TorConfig.TorHost, TorConfig.ControlPort);
            Socket.setSoTimeout(5000);
            Reader = new BufferedReader(new InputStreamReader(Socket.getInputStream()));
            Writer = new BufferedWriter(new OutputStreamWriter(Socket.getOutputStream()));
            Connected = true;
            Log.d(Tag, "Connected to Tor Control port");
            return true;
        } catch (IOException e) {
            Log.e(Tag, "Failed to connect to Control port: " + e.getMessage());
            Connected = false;
            return false;
        }
    }

    // Authenticates with the Tor Control port using an empty password
    public boolean Authenticate() {
        return Authenticate("");
    }

    // Authenticates with the Tor Control port using the given password
    public boolean Authenticate(String Password) {
        try {
            String Command = Password.isEmpty() ? "AUTHENTICATE\r\n" : "AUTHENTICATE \"" + Password + "\"\r\n";
            SendCommand(Command);
            String Response = Reader.readLine();
            Log.d(Tag, "Auth response: " + Response);
            return Response != null && Response.startsWith("250");
        } catch (IOException e) {
            Log.e(Tag, "Authentication failed: " + e.getMessage());
            return false;
        }
    }

    // Requests a new Tor identity (new exit node / new circuit)
    public boolean NewIdentity() {
        try {
            SendCommand("SIGNAL NEWNYM\r\n");
            String Response = Reader.readLine();
            Log.d(Tag, "NEWNYM response: " + Response);
            return Response != null && Response.startsWith("250");
        } catch (IOException e) {
            Log.e(Tag, "NewIdentity failed: " + e.getMessage());
            return false;
        }
    }

    // Sends the CLEARDNSCACHE signal to Tor
    public boolean ClearDnsCache() {
        try {
            SendCommand("SIGNAL CLEARDNSCACHE\r\n");
            String Response = Reader.readLine();
            return Response != null && Response.startsWith("250");
        } catch (IOException e) {
            return false;
        }
    }

    // Returns the IP address of the current Tor exit node
    public String GetExitNodeIp() {
        try {
            SendCommand("GETINFO address\r\n");
            String Response = Reader.readLine();
            if (Response != null && Response.startsWith("250-address=")) {
                return Response.substring("250-address=".length()).trim();
            }
        } catch (IOException e) {
            Log.e(Tag, "Failed to get exit IP: " + e.getMessage());
        }
        return null;
    }

    // Returns the current Tor circuit status as a string
    public String GetCircuitStatus() {
        try {
            SendCommand("GETINFO circuit-status\r\n");
            StringBuilder Builder = new StringBuilder();
            String Line;
            while ((Line = Reader.readLine()) != null) {
                if (Line.startsWith("250 ")) break;
                Builder.append(Line).append("\n");
            }
            return Builder.toString();
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    // Returns the current Tor bootstrap phase info string
    public String GetBootstrapStatus() {
        try {
            SendCommand("GETINFO status/bootstrap-phase\r\n");
            String Response = Reader.readLine();
            Reader.readLine(); // consume trailing "250 OK"
            return Response;
        } catch (IOException e) {
            return null;
        }
    }

    // Sends a raw command string to the Tor Control port
    private void SendCommand(String Command) throws IOException {
        Writer.write(Command);
        Writer.flush();
    }

    // Closes the Control port connection
    public void Disconnect() {
        try {
            if (Socket != null && !Socket.isClosed()) {
                SendCommand("QUIT\r\n");
                Socket.close();
            }
        } catch (IOException Ignored) {}
        Connected = false;
    }

    public boolean IsConnected() {
        return Connected && Socket != null && !Socket.isClosed();
    }
}
