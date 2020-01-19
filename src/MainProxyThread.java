package skj_proxy;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainProxyThread extends Thread {
    private Socket serverSocket = null;
    private OutputStream toServer = null;
    private InputStream fromServer = null;

    private Socket clientSocket = null;
    private InputStream fromClient = null;
    private OutputStream toClient = null;

    private volatile String remoteHost = "";
    private volatile int remotePort = 80;

    private String cacheDir = "";

    private volatile String siteUrl = "";
    private volatile String siteUrlForCaching = "";
    private volatile boolean implementedMethod = false;
    private volatile boolean cacheable = false;

    private volatile ArrayList<String> cachedSites = new ArrayList<String>();

    private String[] illegalWordsArray;

    public MainProxyThread(Socket clientSocket, String[] illegalWords, String cacheDir, skj_proxy.ProxyServer.Version version) {
        this.clientSocket = clientSocket;
        this.remotePort = 80;
        this.illegalWordsArray = illegalWords;
        this.cacheDir = cacheDir;

        this.start();
    }

    @Override
    public void run() {
        ArrayList<String> request = new ArrayList<String>();
        byte[] responseBuffer = new byte[4096];

        try {
            fromClient = clientSocket.getInputStream();
            toClient = clientSocket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            if (toClient != null && fromClient != null) {
                BufferedReader input = new BufferedReader(new InputStreamReader(fromClient, "UTF-8"));
                String requestLine = "";

                siteUrl = "";
                implementedMethod = false;
                cacheable = false;

                siteUrlForCaching = "";
                while (!(requestLine = input.readLine()).equals("")) {
                    Pattern encodingPattern = Pattern.compile("Accept-Encoding: (.*)");
                    Matcher encodingMatcher = encodingPattern.matcher(requestLine);
                    if(!(encodingMatcher.matches())) {
                        System.out.println(requestLine);
                        request.add(requestLine);
                    }

                    //getting remoteHost from request:
                    Pattern pattern = Pattern.compile("Host: (.+)");
                    Matcher matcher = pattern.matcher(requestLine);
                    if (matcher.matches()) {
                        Pattern pattern2 = Pattern.compile("(.+):443");
                        Matcher matcher2 = pattern2.matcher(matcher.group(1));
                        if (matcher2.matches()) {
                            remoteHost = matcher2.group(1);
                            remotePort = 443;
                        } else {
                            remoteHost = matcher.group(1);
                            remotePort = 80;
                        }
                    }

                    //getting siteUrl and setting flags:
                    if(!cacheable) {
                        Pattern siteUrlPattern1 = Pattern.compile("GET (.+) (.+)");
                        Matcher siteUrlMatcher1 = siteUrlPattern1.matcher(requestLine);
                        if (siteUrlMatcher1.matches()) {
                            siteUrl = siteUrlMatcher1.group(1);
                            implementedMethod = true;
                            cacheable = true;
                        }
                        Pattern siteUrlPattern2 = Pattern.compile("POST (.+) (.+)");
                        Matcher siteUrlMatcher2 = siteUrlPattern2.matcher(requestLine);
                        if (siteUrlMatcher2.matches()) {
                            siteUrl = siteUrlMatcher2.group(1);
                            implementedMethod = true;
                            cacheable = false;
                        }
                        Pattern siteUrlPattern3 = Pattern.compile("CONNECT (.+) (.+)");
                        Matcher siteUrlMatcher3 = siteUrlPattern3.matcher(requestLine);
                        if (siteUrlMatcher3.matches()) {
                            siteUrl = siteUrlMatcher3.group(1);
                            implementedMethod = true;
                            cacheable = false;
                        }
                    }

                    if(cacheable) {
                        siteUrlForCaching = siteUrl.substring(7, siteUrl.length());
                        siteUrlForCaching = siteUrlForCaching.replace("/", "\\");
                        Pattern questionMarkPattern = Pattern.compile("(.*)\\?(.*)");
                        Matcher questionMarkMatcher = questionMarkPattern.matcher(siteUrlForCaching);
                        if (questionMarkMatcher.matches()) {
                            siteUrlForCaching = questionMarkMatcher.group(1);
                        }
                        if ((siteUrlForCaching.charAt(siteUrlForCaching.length() - 1)) == '\\') {
                            siteUrlForCaching = siteUrlForCaching.concat("index");
                        }
                    }
                }
            }

            System.out.println("\n");

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        new Thread() {
            public void run() {
                loadCachedSitesList();

                try {
                    try {
                        serverSocket = new Socket(remoteHost, remotePort);
                        toServer = serverSocket.getOutputStream();
                        fromServer = serverSocket.getInputStream();

                        System.out.println("serverSocket and streams set successfully! " + remoteHost + " " + remotePort);
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    }

                    sendRequestToServer(request, toServer);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println("getting response and sending to client...");

                int bytesCount;
                String siteUrlForCachingInternal = siteUrlForCaching;
                boolean cacheableInternal = cacheable;
                String responseLine2 = "";
                boolean filtered = false;
                String responseLineNotFiltered = "";

                if (implementedMethod == true) {
                    try {
                        if (!(isSiteCached(siteUrlForCachingInternal))) {
                            // GETTING NON-CACHED PAGE:
                            ByteArrayOutputStream cacheInputStream = new ByteArrayOutputStream();

                            while ((bytesCount = fromServer.read(responseBuffer)) != -1) {
                                //FILTERING:
                                responseLine2 = new String(responseBuffer, StandardCharsets.UTF_8);
                                responseLineNotFiltered = responseLine2;
                                filtered = false;
                                for(int i = 0; i < illegalWordsArray.length; i++) {
                                    responseLine2 = responseLine2.replaceAll(illegalWordsArray[i], reverseString(illegalWordsArray[i]));
                                }
                                if(!(responseLine2.equals(responseLineNotFiltered)))
                                    filtered = true;

                                if(filtered == false)
                                    toClient.write(responseBuffer, 0, bytesCount);
                                else {
                                    byte[] responseLineFilteredByteArray = responseLine2.getBytes();
                                    toClient.write(responseLineFilteredByteArray, 0, bytesCount);
                                }
                                toClient.flush();

                                //adding page to cacheStream:
                                if(cacheableInternal) {
                                    cacheInputStream.write(responseBuffer, 0, bytesCount);
                                    cacheInputStream.flush();
                                }
                            }

                            if(cacheableInternal) {
                                //ADDING SITE TO CACHE:
                                cachedSites.add(siteUrlForCachingInternal);
                                addSiteToCacheByByteStream(siteUrlForCachingInternal, cacheInputStream);
                            }
                        } else {
                            // GETTING PAGE FROM CACHE:
                            File cachedPageFile = new File(getCachedPageDir(siteUrlForCachingInternal));
                            FileInputStream fis = new FileInputStream(cachedPageFile);
                            try {
                                while ((bytesCount = fis.read(responseBuffer)) != -1) {
                                    // FILTERING CACHED PAGE:
                                    responseLine2 = new String(responseBuffer, StandardCharsets.UTF_8);
                                    responseLineNotFiltered = responseLine2;
                                    filtered = false;
                                    for(int i = 0; i < illegalWordsArray.length; i++) {
                                        responseLine2 = responseLine2.replaceAll(illegalWordsArray[i], reverseString(illegalWordsArray[i]));
                                    }
                                    if(!(responseLine2.equals(responseLineNotFiltered)))
                                        filtered = true;

                                    if(filtered == false)
                                        toClient.write(responseBuffer, 0, bytesCount);
                                    else {
                                        byte[] responseLineFilteredByteArray = responseLine2.getBytes();
                                        toClient.write(responseLineFilteredByteArray, 0, bytesCount);
                                    }

                                    toClient.flush();
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        System.out.println("response successfully sent to client!\n===============================================\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            if (serverSocket != null)
                                serverSocket.close();
                            if (clientSocket != null)
                                clientSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    String notImplementedResponse = "HTTP/1.1 501 Not Implemented\r\n" + "\r\n";
                    byte[] responseNotImplemented = notImplementedResponse.getBytes(StandardCharsets.UTF_8);
                    try {
                        toClient.write(responseNotImplemented, 0, responseNotImplemented.length);
                        toClient.flush();
                    }catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    public String reverseString(String x) {
        StringBuilder sb = new StringBuilder(x);
        return sb.reverse().toString();
    }

    public String getCachedPageDir(String siteUrl) {
        int i = 0;
        for(String cachedPageUrl : cachedSites) {
            if(cachedPageUrl.equals(siteUrl)) {
                String s = cacheDir + "\\" + cachedPageUrl + ".tmp";
                return s;
            }
            i++;
        }
        return "";
    }

    public void addSiteToCacheByByteStream(String siteUrl, ByteArrayOutputStream cacheByteStream) throws IOException {
        try {
            File file = new File("cached_sites.txt");
            FileWriter writer = new FileWriter(file, true);
            writer.write(siteUrl + "\n");
            writer.flush();
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("///////// siteUrl added to cached_sites.txt!");
        try {
            File file = new File(cacheDir + "\\" + siteUrl + ".tmp");
            file.getParentFile().mkdirs();
            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(cacheDir + "\\" + siteUrl + ".tmp");
            int bytesCount;
            byte[] buffer = new byte[4096];
            ByteArrayInputStream cacheByteInputStream = new ByteArrayInputStream(cacheByteStream.toByteArray());
            while((bytesCount = cacheByteInputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesCount);
                fos.flush();
            }
            fos.close();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        }
        System.out.println("////////////// Site added to cache (tmp folder)!");
    }

    public boolean isSiteCached(String siteUrl) {
        for(String cachedSite : cachedSites)
            if(siteUrl.equals(cachedSite))
                return true;

        return false;
    }

    public void loadCachedSitesList() {
        cachedSites.clear();
        try {
            File file = new File("cached_sites.txt");
            Scanner scanner = new Scanner(file);
            String line = "";
            cachedSites.clear();
            while (scanner.hasNext()) {
                line = scanner.next();
                cachedSites.add(line);
            }
            scanner.close();
        } catch(FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void sendRequestToServer(ArrayList<String> request, OutputStream toServer) throws IOException {
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(toServer));

        for(String requestLine : request) {
            output.write(requestLine + "\r\n");
            output.flush();
        }

        output.write("\r\n");
        output.flush();

        System.out.println("request to server sent! =========");
    }
}