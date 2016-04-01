package fr.jeremiegarcia.metaosc;

import android.os.StrictMode;
import android.util.Log;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by jgarcia on 27/03/16.
 */
public class OSCManager {

    //OSC
    private static String ip = "127.0.0.1";
    private static int port = 1234;
    private static OSCPortOut outPort;


    public static void sendOscMessage(OSCMessage mess) {
        //OSC methods
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        if (outPort == null) {
                updatePort();
        }

        if(outPort!=null) {
            try {
                outPort.send(mess);
            } catch (IOException e) {
                Log.e("OSC", "sendOscMessage failed: " + e.getCause());
                e.printStackTrace();
            }
        }
    }

    public static void setIp(String ip) {
        if(!ip.equals(OSCManager.ip)){
            OSCManager.ip = ip;
            updatePort();
        }
    }

    public static void setPort(int port) {
        if(port != OSCManager.port) {
            OSCManager.port = port;
            updatePort();
        }
    }

    public static void updatePort() {
        Log.i("OSC", "Updating OSCport to IP: " + ip + " port: " + port);
        if (outPort != null) {
            outPort.close();
        }

        try {
            outPort = new OSCPortOut(InetAddress.getByName(OSCManager.ip), OSCManager.port);
            Log.i("OSC", "OSCPort successfully updated");
        } catch (SocketException e1) {
            e1.printStackTrace();
        } catch (UnknownHostException e1) {
            e1.printStackTrace();
        }
    }


    public static int getPort(){
        return port;
    }

    public static String getIp(){
        return ip;
    }
}
