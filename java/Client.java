import java.io.File;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.zip.Checksum;
import java.util.zip.CRC32;
import java.util.Random;
import java.util.Arrays;
import java.nio.file.Files;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;

public class Client {

    private static final int PACKETSIZE = 100;
    private static final int SOCKETTIMEOUT = 200;

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

    // checksumme
    public static byte[] checksumme_fkt(byte[] pack) {
        Checksum checksum = new CRC32();
        checksum.update(pack, 0, pack.length);
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
        return ByteBuffer.allocate(8).putLong(file.length() * 2).array();
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
        return startpacketMitCRC_bb;
    }

    public static boolean sessionnummercheck() {
        return Arrays.equals(sessionnummer, Arrays.copyOfRange(receivedBytes, 0, 2));
    }

    public static boolean packetnumbercheck() {
        return packetnummer == receivedBytes[2];
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Falsche Parameteranzahl!");
            System.exit(0);
        }

        System.out.println("Parameteranzahl: " + args.length);

        try {
            file = new File("./" + args[2]);
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
            IPAddress = InetAddress.getByName(args[0]);
        } catch (UnknownHostException e) {
            System.out.println("Fehler beim Lesen der Internetadresse!");
            e.printStackTrace();
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

        toSend[0] = ByteBuffer.allocate(sessionnummer.length + 1 + identification.length + filesize.length
                + filenamesize.length + filename.length);
        toSend[0] = startpacket_fkt();

        for (int i = 1; i < packetanzahl - 1; i++) {
            toSend[i] = ByteBuffer.allocate(PACKETSIZE + 3);
            System.out.println(i);
            toSend[i].put(sessionnummer);
            toSend[i].put(packetnummer);
            toSend[i].put(Arrays.copyOfRange(filecontent, i * PACKETSIZE, ((i + 1) * PACKETSIZE) - 1));
        }
        System.out.println(packetanzahl-1);
        toSend[packetanzahl - 1] = ByteBuffer.allocate((int) (file.length() % PACKETSIZE) + 7);
        toSend[packetanzahl - 1].put(sessionnummer);
        toSend[packetanzahl - 1].put(packetnummer);
        toSend[packetanzahl - 1].put(Arrays.copyOfRange(filecontent, (packetanzahl - 2) * PACKETSIZE, (int) file.length()));

        for (int i = 0; i < packetanzahl; i++) {
            try {
                System.out.println(new String (toSend[i].array(), "UTF-8"));
            } catch (UnsupportedEncodingException e){
                System.out.println("Encodingfehler!");
            }
            DatagramPacket packet_dp = new DatagramPacket(toSend[i].array(), toSend[i].array().length, IPAddress, Integer.parseInt(args[1]));
            try {
                clientSocket.send(packet_dp);
            } catch (IOException e) {
                System.out.println("Fehler beim Senden des Startpacketes!");
                e.printStackTrace();
            }
        }

        System.out.println("Anzahl zu sendende Packete: " + packetanzahl);

    }
}