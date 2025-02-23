package org.cagnulein.qzcompanionnordictracktreadmill;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Calendar;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/*
 * Linux command to send UDP:
 * #socat - UDP-DATAGRAM:192.168.1.255:8002,broadcast,sp=8002
 */
public class UDPListenerService extends Service {
    private static final String LOG_TAG = "QZ:UDPListenerService";

    static String UDP_BROADCAST = "UDPBroadcast";

    //Boolean shouldListenForUDPBroadcast = false;
    static DatagramSocket socket;

    static double lastReqSpeed;
    static int y1Speed;      //vertical position of slider at 2.0
    static double lastReqInclination = 0;
    static int y1Inclination;    //vertical position of slider at 0.0
    static double lastReqResistance = 0;
    static int y1Resistance;

    static long lastSwipeMs = 0;
    static double reqCachedSpeed = -1;
    static double reqCachedResistance = -1;
    static float reqCachcedInclination = -1;

    public enum _device {
        x11i,
        nordictrack_2950,
        other,
        proform_2000,
        s22i,
		tdf10,
		t85s,
        s40,
        exp7i,
        x32i,
    }

    private static _device device;

    private final ShellRuntime shellRuntime = new ShellRuntime();

    public static void setDevice(_device dev) {
        switch(dev) {
            case x11i:
                lastReqSpeed = 0;
                y1Speed = 600;      //vertical position of slider at 2.0
                y1Inclination = 557;    //vertical position of slider at 0.0
                break;
            case nordictrack_2950:
                lastReqSpeed = 2;
                y1Speed = 807;      //vertical position of slider at 2.0
                y1Inclination = 717;    //vertical position of slider at 0.0
                break;
            case proform_2000:
                lastReqSpeed = 2;
                y1Speed = 598;      //vertical position of slider at 2.0
                y1Inclination = 522;    //vertical position of slider at 0.0
                break;
            case s22i:
                lastReqResistance = 0;
                y1Resistance = 618;
                break;
            case tdf10:
                lastReqResistance = 1;
                y1Resistance = 604;
                break;				
            case t85s:
                lastReqSpeed = 0;
                y1Speed = 609;      //vertical position of slider at 2.0
                y1Inclination = 609;    //vertical position of slider at 0.0
            case s40:
                lastReqSpeed = 2;
                y1Speed = 482;      //vertical position of slider at 2.0
                y1Inclination = 490;    //vertical position of slider at 0.0
                break;
            case exp7i:
                lastReqSpeed = 0.5;
                y1Speed = 442;      //vertical position of slider at 2.0
                y1Inclination = 442;    //vertical position of slider at 0.0
                break;
            case x32i:
                lastReqSpeed = 0;
                y1Speed = 927;      //vertical position of slider at 2.0
                y1Inclination = 881;    //vertical position of slider at 0.0
            break;
            default:
                break;
        }
        device = dev;
    }

    private void listenAndWaitAndThrowIntent(InetAddress broadcastIP, Integer port) throws Exception {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        wakeLock.acquire();

        byte[] recvBuf = new byte[15000];
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket(port);
            socket.setBroadcast(true);
        }
        //socket.setSoTimeout(1000);
        DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
        Log.i(LOG_TAG, "Waiting for UDP broadcast");
        socket.receive(packet);

        String senderIP = packet.getAddress().getHostAddress();
        String message = new String(packet.getData()).trim();

        Log.i(LOG_TAG, "Got UDP broadcast from " + senderIP + ", message: " + message);

        Log.i(LOG_TAG, message);
        String[] amessage = message.split(";");
        if(device == _device.s22i || device == _device.tdf10) {
            if (amessage.length > 0) {
                String rResistance = amessage[0];
                double reqResistance = Double.parseDouble(rResistance);
                reqResistance = Math.round((reqResistance) * 10) / 10.0;
                Log.i(LOG_TAG, "requestResistance: " + reqResistance + " " + lastReqResistance);

                if (lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis()) {
                    if (reqResistance != -1 && lastReqResistance != reqResistance || reqCachedResistance != -1) {
                        if (reqCachedResistance != -1) {
                            reqResistance = reqCachedResistance;
                        }
                        int x1 = 0;
                        int y2 = 0;
                        if (device == _device.s22i) {
                            x1 = 75;
                            y2 = (int) (616.18 - (17.223 * reqResistance));
                        } else if (device == _device.tdf10) {
							x1 = 1205;
                            y2 = (int) (619.91 - (15.913 * reqResistance));
						}

                        String command = "input swipe " + x1 + " " + y1Resistance + " " + x1 + " " + y2 + " 200";
                        MainActivity.sendCommand(command);
                        Log.i(LOG_TAG, command);

                        if (device == _device.s22i || device == _device.tdf10)
                            y1Resistance = y2;  //set new vertical position of speed slider
                        lastReqResistance = reqResistance;
                        lastSwipeMs = Calendar.getInstance().getTimeInMillis();
                        reqCachedResistance = -1;
                    }
                } else {
                    reqCachedResistance = reqResistance;
                }
            }
        } else {
            if (amessage.length > 0) {
                String rSpeed = amessage[0];
                double reqSpeed = Double.parseDouble(rSpeed);
                reqSpeed = Math.round((reqSpeed) * 10) / 10.0;
                if(device == _device.exp7i) {
                    reqSpeed = reqSpeed * 0.621371; // km to miles
                }
                Log.i(LOG_TAG, "requestSpeed: " + reqSpeed + " " + lastReqSpeed);

                if (lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis()) {
                    if (reqSpeed != -1 && lastReqSpeed != reqSpeed || reqCachedSpeed != -1) {
                        if (reqCachedSpeed != -1) {
                            reqSpeed = reqCachedSpeed;
                        }
                        int x1 = 0;
                        int y2 = 0;
                        if (device == _device.x11i) {
                            x1 = 1207;
                            y2 = (int) (621.997 - (21.785 * reqSpeed));
                        } else if (device == _device.x32i) {
                            x1 = 1845;
                            y2 = (int) (978.8794 - (25.9811 * reqSpeed));
						} else if (device == _device.t85s) {
                            x1 = 1207;
                            y2 = (int) (629.81 - (20.81 * reqSpeed));
                        } else if (device == _device.s40) {
                            x1 = 949;
                            y2 = (int) (507 - (12.5 * reqSpeed));
                        } else if (device == _device.exp7i) {
                            x1 = 950;
                            y2 = (int) (453.014 - (22.702 * reqSpeed));
                        } else if (device == _device.nordictrack_2950) {
                            x1 = 1845;     //middle of slider
                            y1Speed = 807 - (int) ((QZService.lastSpeedFloat - 1) * 29.78);
                            //set speed slider to target position
                            y2 = y1Speed - (int) ((reqSpeed - QZService.lastSpeedFloat) * 29.78);
                        } else if (device == _device.proform_2000) {
                            x1 = 1205;     //middle of slider
                            y2 = (int) ((-19.921 * reqSpeed) + 631.03);
                        }

                        String command = "input swipe " + x1 + " " + y1Speed + " " + x1 + " " + y2 + " 200";
                        MainActivity.sendCommand(command);
                        Log.i(LOG_TAG, command);

                        if (device == _device.x11i || device == _device.proform_2000 || device == _device.t85s || device == _device.s40 || device == _device.exp7i || device == _device.x32i)
                            y1Speed = y2;  //set new vertical position of speed slider
                        lastReqSpeed = reqSpeed;
                        lastSwipeMs = Calendar.getInstance().getTimeInMillis();
                        reqCachedSpeed = -1;
                    }
                } else {
                    reqCachedSpeed = reqSpeed;
                }
            }

            if (amessage.length > 1 && lastSwipeMs + 500 < Calendar.getInstance().getTimeInMillis()) {
                String rInclination = amessage[1];
                double reqInclination = roundToHalf(Double.parseDouble(rInclination));
                Log.i(LOG_TAG, "requestInclination: " + reqInclination + " " + lastReqInclination);
                if (reqInclination != -100 && lastReqInclination != reqInclination) {
                    int x1 = 0;
                    int y2 = 0;
                    if (device == _device.x11i) {
                        x1 = 75;
                        y2 = (int) (565.491 - (8.44 * reqInclination));
                    } else if (device == _device.x32i) {
                        x1 = 74;
                        y2 = (int) (881.3421 - (11.8424 * reqInclination));
					} else if (device == _device.t85s) {
                        x1 = 75;
                        y2 = (int) (609 - (36.417 * reqInclination));
                    } else if (device == _device.s40) {
                        x1 = 75;
                        y2 = (int) (490 - (21.4 * reqInclination));
                    } else if (device == _device.exp7i) {
                        x1 = 74;
                        y2 = (int) (441.813 - (21.802 * reqInclination));
                    } else if (device == _device.nordictrack_2950) {
                        x1 = 75;     //middle of slider
                        y1Inclination = 807 - (int) ((QZService.lastInclinationFloat + 3) * 29.9);
                        //set speed slider to target position
                        y2 = y1Inclination - (int) ((reqInclination - QZService.lastInclinationFloat) * 29.9);
                    } else if (device == _device.proform_2000) {
                        x1 = 79;
                        y2 = (int) ((-21.804 * reqInclination) + 520.11);
                    }

                    String command = " input swipe " + x1 + " " + y1Inclination + " " + x1 + " " + y2 + " 200";
                    MainActivity.sendCommand(command);
                    Log.i(LOG_TAG, command);

                    if (device == _device.x11i || device == device.proform_2000 || device == device.t85s || device == device.s40 || device == device.exp7i || device == _device.x32i)
                        y1Inclination = y2;  //set new vertical position of inclination slider
                    lastReqInclination = reqInclination;
                    lastSwipeMs = Calendar.getInstance().getTimeInMillis();
                }
            }
        }

        broadcastIntent(senderIP, message);
        socket.close();
    }

    private double roundToHalf(double d) {
        return Math.round(d * 2) / 2.0;
    }

    private void broadcastIntent(String senderIP, String message) {
        Intent intent = new Intent(UDPListenerService.UDP_BROADCAST);
        intent.putExtra("sender", senderIP);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    Thread UDPBroadcastThread;

    InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager)    getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = null;
        try {
            dhcp = wifi.getDhcpInfo();
            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++)
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            Log.e(LOG_TAG, "IOException: " + e.getMessage());
        }
        byte[] quads = new byte[4];
        return InetAddress.getByAddress(quads);
    }

    void startListenForUDPBroadcast() {
        UDPBroadcastThread = new Thread(new Runnable() {
            public void run() {
                try {
                    InetAddress broadcastIP = getBroadcastAddress();
                    Integer port = 8003;
                    while (shouldRestartSocketListen) {
                        listenAndWaitAndThrowIntent(broadcastIP, port);
                    }
                    //if (!shouldListenForUDPBroadcast) throw new ThreadDeath();
                } catch (Exception e) {
                    Log.i(LOG_TAG, "no longer listening for UDP broadcasts cause of error " + e.getMessage());
                }
            }
        });
        UDPBroadcastThread.start();
    }

    private Boolean shouldRestartSocketListen=true;

    void stopListen() {
        shouldRestartSocketListen = false;
        socket.close();
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDestroy() {
        stopListen();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        shouldRestartSocketListen = true;
        startListenForUDPBroadcast();
        Log.i(LOG_TAG, "Service started");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
