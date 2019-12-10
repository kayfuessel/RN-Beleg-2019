import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
    public static byte[] kennung = { 83, 116, 97, 114, 116 };
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
    public static int port_from_client;
    public static InetAddress addr;
    public static int port;
    public static FileChannel fc;
    public static File testfile;
    public static String finalname;
    public static float lossrate;
    public static int avrdelay;
    public static DatagramSocket server_ds;
    public static DatagramPacket received_dp;
    public static int losscounter = 0;

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
        return Arrays.equals(crc, ByteBuffer.allocate(4).putInt((int) checksum.getValue()).array());
    }

    public static void startpacket_fkt(byte[] packet) {
        try {
            System.out.println(new String(packet));
            sessionnummer = new byte[2];
            sessionnummer = Arrays.copyOfRange(packet, 0, 2);
            System.out.println("Sessionnummer: " + new String(sessionnummer));
            packetnummer = packet[2];
            System.out.println("Packetnummer: " + packetnummer);
            kennung_received = new byte[5];
            kennung_received = Arrays.copyOfRange(packet, 3, 8);
            if (!Arrays.equals(kennung_received, kennung)) {
                System.out.println(new String(kennung_received));
                System.out.println("Falsche Kennung");
                System.exit(-1);
            }
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
                System.exit(-1);
            }
        } catch (Exception e) {
            System.out.println("Fehler im Startpacket!");
            reset();
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

    public static String test_filename_fkt() {
        testfile = new File( System.getProperty("user.dir") + "/" + new String(filename));
        String str = new String("/");
        for (int i = 1; i < testfile.getAbsolutePath().split("/").length-2; i++){
            str = new String (str + "/" + testfile.getAbsolutePath().split("/")[i]);
        }
        
        testfile = new File(str + "/" + new String(filename));

        finalname = new String(filename);
        while (testfile.exists()) {
            String[] tmp = testfile.getAbsolutePath().split("\\.");
            testfile = new File(tmp[0] + "1." + tmp[1]);
        }
        return testfile.getAbsolutePath();
    }

    public static void lastdatapacket_fkt(byte[] packet) {
        int tmp = (int) filesize % packetsize;
        filecontent.put(Arrays.copyOfRange(packet, 3, tmp + 3));

        test_filename_fkt();

        try {
            fc = new FileOutputStream(test_filename_fkt()).getChannel();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            fc.write(ByteBuffer.wrap(filecontent.array()));
            fc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!(crccheck(Arrays.copyOfRange(packet, tmp + 3, tmp + 7), filecontent.array()))) {
            System.out.println("CRC-Fehler in der Datei!");
            System.exit(-1);
        }
    }

    public static void answer() {
        byte[] bb = ByteBuffer.allocate(3).put(sessionnummer).put(packetnummer).array();
        try {
            server_ds.send(new DatagramPacket(bb, 3, addr, port_from_client));
        } catch (IOException e) {
            System.out.println("Fehler beim Senden des Antwortpacketes!");
            e.printStackTrace();
        }
        System.out.println((packetcounter + 1) + "/" + (packetanzahl));
        if (state==LASTDATAPACKET){
            System.out.println("FINISHED!!!");
            reset();
        }

    }

    public static boolean losscheck() {
        int rndm = Math.abs(ThreadLocalRandom.current().nextInt());
        rndm %= 100;
        rndm++;
        int loss = (int) (lossrate * 100);
        return (rndm > loss);
    }

    public static void reset() {
        state = STARTPACKET;
        sessionnummer = null;
        packetnummer = (byte)1;
        kennung_received = null;
        filesize = 0;
        filenamesize = 0;
        filename = null;
        crc = null;
        packetsize = 0;
        received_b = new byte[1500];
        packetanzahl = 0;
        packetcounter = -1;
        filecontent =  ByteBuffer.allocate(0);
        port_from_client = 0;
        losscounter = 0;
    }

    public static void main(String args[]) {

        switch (args.length){
            case 3:
                lossrate = Float.parseFloat(args[1]);
                avrdelay = Integer.parseInt(args[2]);
                port = Integer.parseInt(args[0]);
                break;
            case 1:
                port = Integer.parseInt(args[0]);
                lossrate = 0;
                avrdelay = 0;
                break;
            default:
                System.out.println("Falsche Parameteranzahl!");
                System.exit(-1);
        }

        try {
            server_ds = new DatagramSocket(Integer.parseInt(args[0]));
            server_ds.setSoTimeout(1000);
        } catch (SocketException e) {
            System.out.println("Fehler beim Erstellen des Sockets!");
        }

        while (true) {
            received_dp = new DatagramPacket(received_b, received_b.length);
            try {
                server_ds.receive(received_dp);
            } catch (IOException e) {
                if (++losscounter == 10 && state != STARTPACKET){
                    System.out.println("RESET!");
                    reset();
                }
                continue;
            }
            if (received_dp.getData()[2] == packetnummer && sessionnummercheck(received_dp.getData()) && losscheck()) {
                losscounter = 0;
                switch (state) {
                case STARTPACKET:
                    startpacket_fkt(received_dp.getData());
                    addr = received_dp.getAddress();
                    port_from_client = received_dp.getPort();
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
                    break;
                }
                if (avrdelay != 0) {
                    int rand_int1 = Math.abs(ThreadLocalRandom.current().nextInt());
                    rand_int1 %= (avrdelay * 2);
                    rand_int1++;
                    try {
                        TimeUnit.MILLISECONDS.sleep(rand_int1);
                    } catch (InterruptedException ex) {
                        System.out.println("InterruptException");
                    }
                }

                answer();
                packetcounter++;
                packetnummer ^= 1;
            }
        }
    }
}
