import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

class Server {

    public static int state;
    public static final int FIRSTPACKET = 0;
    public static final int DATAPACKET = 1;
    public static final int LASTPACKET = 2;

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

    public static DatagramSocket server_ds;

    public static void main(String args[]) {

        byte[] received_b = new byte[1500];
        
        try {
        server_ds = new DatagramSocket(30303);
        } catch (SocketException e) {
            System.out.println("Fehler beim Erstellen des Sockets!");
        }
        DatagramPacket received_dp = new DatagramPacket(received_b, received_b.length);

        while (true) {
            try {
                server_ds.receive(received_dp);
            } catch (IOException e) {
                continue;
            }

            System.out.println(received_dp.getData());
        }

    }
}
