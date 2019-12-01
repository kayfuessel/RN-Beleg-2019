import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

class Server {

    public static int state = 0;
    public static final int STARTPACKET = 0;
    public static final int FIRSTDATAPACKET = 1;
    public static final int DATAPACKET = 2;
    public static final int LASTDATAPACKET = 3;

    public static byte[] sessionnummer;
    public static byte packetnummer;
    public static byte[] kennung = { 83, 84, 65, 82, 84 };
    public static byte[] kennung_received;
    public static long filesize;
    public static short filenamesize;
    public static byte[] filename;
    public static byte[] crc;
    public static int packetsize;
    public static byte[] received_b = new byte[1500];
    public static long packetanzahl;
    public static int packetcounter = 0;
    public static ByteBuffer filecontent;

    public static boolean startpaket(byte[] paket) {
        if ("START".equals(new String(Arrays.copyOfRange(paket, 3, 8)))) {
            return true;
        } else {
            return false;
        }
    }

    public static String filename_fkt(String filename) {
        File tmp = new File(filename);
        while (tmp.exists() && tmp.length() != 0) {
            String[] stringtmp = tmp.toString().split("\\.");
            tmp = new File(stringtmp[0] + "1." + stringtmp[1]);
        }
        return tmp.toString();
    }

    public static boolean sessionnummercheck(byte[] packet) {
        if (state == STARTPACKET) {
            return true;
        }
        return (packet[0] == sessionnummer[0] && packet[1] == sessionnummer[1]);
    }

    public static long packetanzahl_fkt(int packetsize, long filesize) {
        long tmp = filesize / packetsize;
        tmp++;
        if ((filesize % packetsize) > 0) {
            tmp++;
        }
        if ((packetsize - (filesize % packetsize)) < 7) {
            tmp++;
        }
        return tmp;
    }

    public static boolean crccheck(byte[] crc, byte[] field) {
        Checksum checksum = new CRC32();
        checksum.update(field, 0, field.length);
        System.out.println(new String(crc));
        System.out.println(new String(ByteBuffer.allocate(4).putInt((int) checksum.getValue()).array()));
        return Arrays.equals(crc, ByteBuffer.allocate(4).putInt((int) checksum.getValue()).array());
    }

    public static void startpacket_fkt(byte[] packet) {
        System.out.println(new String(packet));
        sessionnummer = new byte[2];
        sessionnummer = Arrays.copyOfRange(packet, 0, 2);
        System.out.println("Sessionnummer: " + new String(sessionnummer));
        packetnummer = packet[2];
        System.out.println("Packetnummer: " + packetnummer);
        kennung_received = new byte[5];
        kennung_received = Arrays.copyOfRange(packet, 3, 8);
        System.out.println("Kennung: " + new String(kennung_received));
        filesize = ByteBuffer.wrap(Arrays.copyOfRange(packet, 8, 16)).getLong();
        System.out.println("Dateigröße: " + filesize);
        filenamesize = ByteBuffer.wrap(Arrays.copyOfRange(packet, 16, 18)).getShort();
        System.out.println("Dateinamengröße: " + filenamesize);
        filename = new byte[filenamesize];
        filename = Arrays.copyOfRange(packet, 18, 18 + filenamesize);
        System.out.println("Dateiname: " + new String(filename));
        crc = new byte[4];
        crc = Arrays.copyOfRange(packet, 18 + filenamesize, 18 + filenamesize + 4);

        if (!crccheck(crc, Arrays.copyOfRange(packet, 0, 18 + filenamesize))) {
            System.out.println("Fehler bei der CRC im Startpacket!");
            System.out.print(new String(Arrays.copyOfRange(packet, 0, 18 + filenamesize)));
            System.exit(0);
        }
    }

    public static void firstdatapacket_fkt(byte[] packet) {

        for (int i = packet.length - 1; i > 0; i--) {
            if (packet[i] != 0) {
                packetsize = i - 2;
                break;
            }
        }
        System.out.println("Packetgröße: " + packetsize);
        packetanzahl = packetanzahl_fkt(packetsize, filesize);
        System.out.println("Packetanzahl: " + packetanzahl);
        filecontent = ByteBuffer.allocate((int) filesize);
    }

    public static void datapacket_fkt(byte[] packet) {

    }

    public static void lastdatapacket_fkt(byte[] packet) {
        int tmp = (int) filesize % packetsize;
        filecontent.put(Arrays.copyOfRange(packet, 3, tmp + 3));
        if (!(crccheck(Arrays.copyOfRange(packet, tmp+3, tmp+7), filecontent.array()))) {
            System.out.println("CRC-Fehler in der Datei!");
        } else {
            System.out.println("CRC Okay!");
        }

    }

    public static void answer(){
        System.out.println("hier");
        byte[] bb = ByteBuffer.allocate(3).put(sessionnummer).put(packetnummer).array();
        System.out.println(new String(bb));
        
        try {
        server_ds.send(new DatagramPacket(bb, bb.length));
        } catch (IOException e) {
            System.out.println("Fehler beim Senden des Antwortpacketes!");
            e.printStackTrace();
        }
        
    }

    public static DatagramSocket server_ds;
    public static DatagramPacket received_dp;

    public static void main(String args[]) {

        try {
            server_ds = new DatagramSocket(30303);
        } catch (SocketException e) {
            System.out.println("Fehler beim Erstellen des Sockets!");
        }
        received_dp = new DatagramPacket(received_b, received_b.length);

        while (true) {
            try {
                server_ds.receive(received_dp);
                System.out.println(packetcounter + ": " + new String(received_dp.getData()));
            } catch (IOException e) {
                System.out.println("Timeout!");
                continue;
            }
            if (received_dp.getData()[2] == packetnummer && sessionnummercheck(received_dp.getData())) {
                switch (state) {
                case STARTPACKET:
                    startpacket_fkt(received_dp.getData());
                    state = FIRSTDATAPACKET;
                    break;
                case FIRSTDATAPACKET:
                    firstdatapacket_fkt(received_dp.getData());
                    state = DATAPACKET;
                case DATAPACKET:
                    if (packetcounter < packetanzahl - 1) {
                        filecontent.put(Arrays.copyOfRange(received_dp.getData(), 3, packetsize + 3));
                        break;
                    } else {
                        state = LASTDATAPACKET;
                    }
                case LASTDATAPACKET:
                    lastdatapacket_fkt(received_dp.getData());
                    System.out.print(new String(filecontent.array()));
                    state = STARTPACKET;
                    break;
                }
                answer();
                packetcounter++;
                packetnummer ^= 1;
            }
        }

    }
}
