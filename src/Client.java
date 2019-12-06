import java.io.File;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.zip.Checksum;
import java.util.zip.CRC32;
import java.util.Random;
import java.util.Arrays;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.net.URL;

public class Client {

    private static final int PACKETSIZE = 1250;
    private static final int SOCKETTIMEOUT = 1000;

    public static File file;
    public static byte[] filecontent;
    public static InetAddress IPAddress;
    public static DatagramSocket clientSocket;
    public static byte[] packet;
    public static ByteBuffer ackPacket_bb;
    public static int timeout;
    public static int fehlercounter = 0;
    public static int packetcounter = 0;
    public static int packetanzahl;
    public static byte[] sessionnummer;
    public static byte packetnummer = 0;
    public static byte[] identification = { 83, 84, 65, 82, 84 }; // identification says "START"
    public static byte[] filesize;
    public static byte[] filename;
    public static byte[] filenamesize;
    public static ByteBuffer startpacket;
    public static byte[] receivedBytes;
    public static ByteBuffer[] toSend;
    public static DatagramPacket packet_dp;
    public static DatagramPacket received_dp;
    public static int losscount = 0;
    public static long timeonepacket1;
    public static long timeonepacket2;
    public static long timefromstart;

    // checksumme
    public static byte[] checksumme_fkt(byte[] pack) {
        Checksum checksum = new CRC32();
        checksum.update(pack, 0, pack.length);
        System.out.println("CRC: " + new String(ByteBuffer.allocate(4).putInt((int) checksum.getValue()).array()));
        return ByteBuffer.allocate(4).putInt((int) checksum.getValue()).array();
    }

    // generiert 2 zufällige bytes
    public static byte[] sessionnummer_fkt() {
        byte[] bytetmp = new byte[2];
        Random zufall = new Random();
        zufall.nextBytes(bytetmp);
        return bytetmp;
    }

    public static byte[] filesize_fkt() {
        if (file.length() * 16 > Math.pow(2, 32)) {
            System.out.println("Datei zu gross!");
            System.exit(0);
        }
        return ByteBuffer.allocate(8).putLong(file.length()).array();
    }

    public static byte[] filename_fkt() {
        String[] stringtmp = file.toString().split("/");
        byte[] bytetmp = stringtmp[stringtmp.length - 1].getBytes();
        if (bytetmp.length > 256) {
            System.out.println("Dateiname zu lang!");
            System.exit(0);
        } else {
            return bytetmp;
        }
        return bytetmp;
    }

    public static int packetanzahl_fkt() {
        int tmp = (int) file.length() / PACKETSIZE;
        tmp++;
        if ((file.length() % PACKETSIZE) > 0) {
            tmp++;
        }
        if ((PACKETSIZE - (file.length() % PACKETSIZE)) < 7) {
            tmp++;
        }
        return tmp;
    }

    public static ByteBuffer startpacket_fkt() {
        int startpacket_size = sessionnummer.length + 1 + identification.length + filesize.length + filenamesize.length
                + filename.length;
        ByteBuffer startpacketOhneCRC_bb = ByteBuffer.allocate(startpacket_size);
        startpacketOhneCRC_bb.put(sessionnummer);
        startpacketOhneCRC_bb.put(packetnummer);
        startpacketOhneCRC_bb.put(identification);
        startpacketOhneCRC_bb.put(filesize);
        startpacketOhneCRC_bb.put(filenamesize);
        startpacketOhneCRC_bb.put(filename);

        // reallocate ByteBuffer and add CRC32
        ByteBuffer startpacketMitCRC_bb = ByteBuffer.allocate(startpacketOhneCRC_bb.array().length + 4);
        startpacketMitCRC_bb.put(startpacketOhneCRC_bb.array());
        startpacketMitCRC_bb.put(checksumme_fkt(startpacketOhneCRC_bb.array()));
        System.out.println(new String(startpacketOhneCRC_bb.array()));
        return startpacketMitCRC_bb;
    }

    public static boolean sessionnummercheck() {
        return Arrays.equals(sessionnummer, Arrays.copyOfRange(receivedBytes, 0, 2));
    }

    public static boolean packetnumbercheck() {
        return packetnummer == receivedBytes[2];
    }

    public static byte[] ladebalken(int val){
        int tmpi = (int) Math.ceil(((float)  val / (float) packetanzahl) * 50);
        int percentage = (int) Math.ceil(((float)  val / (float) packetanzahl) * 100);
        String tmps;
        if(percentage < 10){
            tmps = new String("  " + percentage + "% [");
        } else if (percentage < 100){
            tmps = new String(" " + percentage + "% [");
        } else {
            tmps = new String(percentage + "% [");
        }
        for (int i = 0; i < tmpi; i++){
            tmps = new String(tmps + "#");
        }
        for (int i = 0; i< (50-tmpi);i++){
            tmps = new String(tmps + "-");
        }
        tmps = new String(tmps + "] ");
        float tmpf = (float)PACKETSIZE/(float)(timeonepacket2 - timeonepacket1 +1);
        tmps = new String(tmps + (int) Math.ceil(tmpf) + "kb/s");

        if (val != (packetanzahl-1)){
            tmps = new String(tmps + "\r");
        }
        else {
            tmps = new String(tmps + "\n" + "Es wurden " + packetanzahl + " Packete mit einer Durchschnittsgeschwindigkeit von " 
            + (int)(file.length() / (timeonepacket2-timefromstart)) + "kb/s in " + ((timeonepacket2 - timefromstart)/1000) + "s übertragen!\n");
        }
        System.out.print("\033[2K");
        return tmps.getBytes();
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Falsche Parameteranzahl!");
            System.exit(0);
        }

        try {
            file = new File(args[2]);
        } catch (NullPointerException e) {
            System.out.println("Fehler beim Öffnen der Datei!");
            e.printStackTrace();
        }

        // !Namen in die Ausgabe
        if (!file.exists()) {
            System.out.println("Datei existiert nicht!");
            System.exit(0);
        }

        try {
            filecontent = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            System.out.println("Fehler beim Lesen des Dateiinhaltes!");
            e.printStackTrace();
        }

        try {
            IPAddress = InetAddress.getByName(new URL(args[0]).getHost());
        } catch (UnknownHostException e) {
            System.out.println("Fehler beim Lesen der Internetadresse!");
            e.printStackTrace();
        } catch (MalformedURLException e) {
            System.out.println("Fehlerhafte Addresse!");
            System.exit(-1);
        }

        packet = new byte[PACKETSIZE];

        try {
            clientSocket = new DatagramSocket();
        } catch (SocketException e) {
            System.out.println("Fehler beim Öffnen des Sockets!");
            e.printStackTrace();
        }

        try {
            clientSocket.setSoTimeout(SOCKETTIMEOUT);
        } catch (SocketException e) {
            System.out.println("Fehler beim Setzen des Socket Timeouts!");
            e.printStackTrace();
        }

        sessionnummer = sessionnummer_fkt();

        filesize = filesize_fkt();

        filename = filename_fkt();

        filenamesize = ByteBuffer.allocate(2).putShort((short) filename.length).array();

        packetanzahl = packetanzahl_fkt();

        toSend = new ByteBuffer[packetanzahl];

        toSend[0] = ByteBuffer.allocate(sessionnummer.length + 1 
        + identification.length + filesize.length
                + filenamesize.length + filename.length + 4);
        toSend[0] = startpacket_fkt();

        for (int i = 1; i < packetanzahl - 1; i++) {
            toSend[i] = ByteBuffer.allocate(PACKETSIZE + 3);
            toSend[i].put(sessionnummer);
            packetnummer ^= 1;
            toSend[i].put(packetnummer);
            toSend[i].put(Arrays.copyOfRange(filecontent, (i-1) * PACKETSIZE, i * PACKETSIZE));
        }
        toSend[packetanzahl - 1] = ByteBuffer.allocate((int) (file.length() % PACKETSIZE) + 7);
        toSend[packetanzahl - 1].put(sessionnummer);
        packetnummer ^= 1;
        toSend[packetanzahl - 1].put(packetnummer);
        toSend[packetanzahl - 1].put(Arrays.copyOfRange(filecontent, (packetanzahl - 2) * PACKETSIZE, (int) file.length()));
        toSend[packetanzahl - 1].put(checksumme_fkt(filecontent));

        System.out.println("Anzahl zu sendende Packete: " + packetanzahl);

        for (int i = 0; i < packetanzahl; i++) {
            packet_dp = new DatagramPacket(toSend[i].array(), toSend[i].array().length, IPAddress, Integer.parseInt(args[1]));
            timeonepacket1 = System.currentTimeMillis();
            try {
                clientSocket.send(packet_dp);
            } catch (IOException e) {
                System.out.println("Fehler beim Senden des Packetes Nr.: " + i + "!");
                e.printStackTrace();
            }

            byte[] bb = new byte[3];
            received_dp = new DatagramPacket(bb, bb.length);

            try {
                clientSocket.receive(received_dp);
            } catch (IOException e) {
                i--;
                if (++losscount == 10){
                    System.out.println("\n10 fehlerhafte Packete hintereinander!");
                    System.exit(-1);
                }
                continue;
            }
            timeonepacket2 = System.currentTimeMillis();
            if(i==0){
                timefromstart = timeonepacket1;
            }

            losscount = 0;
            ByteBuffer bbb = ByteBuffer.allocate(200).put(ladebalken(i));
            System.out.print(new String(bbb.array()));
        }
    }
}