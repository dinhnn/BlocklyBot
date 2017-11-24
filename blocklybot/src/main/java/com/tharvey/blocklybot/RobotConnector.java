package com.tharvey.blocklybot;

import android.graphics.Color;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Created by dinhnn on 11/24/17.
 */

public class RobotConnector implements Runnable {
    enum ConnectStatus {
        CONNECTING(Color.YELLOW), CONNECTED(Color.GREEN), DISCONNECTED(Color.RED);
        int color;

        ConnectStatus(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    public interface RobotConnectorListener {
        void onStatusChanged(ConnectStatus status);

        void onCommandReceived(int command, String arg);
    }

    public final static int RECEIVE_IR = 'i';
    public final static int RECEIVE_NEAR = 'n';
    public final static int SEND_IR = 's';
    public final static int WHEEL = 'w';
    public final static int PING = 'p';
    private String host;
    private int port = 8888;
    private int instance = 0;
    private Thread thread;
    private RobotConnectorListener listener;
    private ConnectStatus status;

    public ConnectStatus getStatus() {
        return status;
    }

    public RobotConnector(RobotConnectorListener listener) {
        this.listener = listener;
        status = ConnectStatus.DISCONNECTED;
    }

    public void connect(String host) {
        int pos = host.indexOf(':');
        instance = 0;
        if (pos > 0) {
            this.port = Integer.parseInt(host.substring(pos + 1));
            this.host = host.substring(0, pos);
        } else {
            this.host = host;
        }
        connect();
    }

    public void disconnect() {
        instance = 0;
    }

    private void connect() {
        listener.onStatusChanged(status = ConnectStatus.CONNECTING);
        instance++;
        thread = new Thread(this);
        thread.start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int current = instance;
                try {
                    while (current == instance) {
                        Thread.sleep(5000);
                        send(PING);
                    }
                }catch (InterruptedException e){

                }
            }
        }).start();
    }

    PrintWriter out;

    public void send(int command, String arg) {
        if (out != null) {
            synchronized (out) {
                out.println((char) command + arg);
                out.flush();
            }
        }
    }
    public void send(int command) {
        if (out != null) {
            synchronized (out) {
                out.println(Character.toString((char) command));
                out.flush();
            }
        }
    }
    @Override
    public void run() {
        Socket socket = null;
        int current = instance;
        BufferedReader in = null;
        PrintWriter out = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port),3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            this.out = out;
            listener.onStatusChanged(status = ConnectStatus.CONNECTED);
            while (current == instance) {
                String line = in.readLine();
                if (line.isEmpty()) continue;
                listener.onCommandReceived(line.charAt(0), line.substring(1));
            }
        } catch (SocketTimeoutException | IllegalArgumentException |ConnectException e) {
            listener.onStatusChanged(status = ConnectStatus.DISCONNECTED);
        } catch (Exception e) {
            if (current == instance) {
                connect();
            } else if (instance == 0) {
                this.out = null;
                listener.onStatusChanged(status = ConnectStatus.DISCONNECTED);
            }
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException e) {
                }
            if (out != null)
                out.close();
            if (socket != null)
                try {
                    socket.close();
                } catch (IOException e) {
                }
        }
    }
}
