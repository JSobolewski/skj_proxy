package skj_proxy;
import skj_proxy.MainProxyThread;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;

public class ProxyServer {
    public static enum Version {
        LIGHT, HEAVY
    }

    public static void main(String[] args) throws IOException {
        args = readArgsFromFile(args[0]);

        String[] firstArg = args[0].split("=");
        int port = Integer.parseInt(firstArg[1]);

        String[] secondArg = args[1].split("=");
        String[] illegalWords = secondArg[1].split(";");

        String[] thirdArg = args[2].split("=");
        String cacheDir = thirdArg[1];
        cacheDir = cacheDir.replace("\"", "");

        System.out.println("########## Java Proxy Server by Jakub Sobolewski (s19300) ##########\n");

        System.out.println("host: localhost \n"
                + "port: " + port + "\n"
                + "illegalWords: " + Arrays.toString(illegalWords) + "\n"
                + "cacheDir: " + cacheDir + "\n");

        Version version = Version.HEAVY;

        ServerSocket welcomeSocket = new ServerSocket(port);

        while(true) {
            new MainProxyThread(welcomeSocket.accept(), illegalWords, cacheDir, version);
        }
    }

    public static String[] readArgsFromFile(String filename) throws IOException {
        String[] args = new String[3];
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        int i = 0;
        while(i < args.length) {
            args[i] = reader.readLine();
            i++;
        }
        reader.close();

        return args;
    }
}