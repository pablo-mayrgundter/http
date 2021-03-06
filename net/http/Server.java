package net.http;

import util.Flags;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.net.Socket;
import java.net.ServerSocket;
import java.security.KeyStore;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.security.cert.X509Certificate;


/**
 * A simple multi-threaded HTTP file server.  Handles only GET
 * requests, serving files below the directory in which the server is
 * started.
 *
 * To run:
 *
 *   java net.http.Server
 *
 * or if you can't run on priviledged ports (&lt;1024), pick a high
 * one:
 *
 *   java -Dport=8080 net.http.Server
 *   java -Dport=8080 -Dssl=true net.http.Server
 *
 * Loadtest using acme.com's http_load:
 *
 *   > cat /proc/cpuinfo
 *   ... Intel(R) Xeon(R) CPU           X5679  @ 3.20GHz ...
 *   ... Intel(R) Xeon(R) CPU           X5679  @ 3.20GHz ...
 *   ... Intel(R) Xeon(R) CPU           X5679  @ 3.20GHz ...
 *   > dd if=/dev/zero of=10k.dat bs=1024 count=10
 *   > echo "http://localhost:8080/10k.dat" > test.url
 *   > ./http_load -p 10 -f 10000 test.url
 *   # Throwaway
 *   > ./http_load -p 10 -f 100000 test.url
 *   100000 fetches, 10 max parallel, 1.024e+09 bytes, in 34.9176 seconds
 *   10240 mean bytes/connection
 *   2863.88 fetches/sec, 2.93262e+07 bytes/sec
 *   msecs/connect: 0.0995282 mean, 2.717 max, 0.037 min
 *   msecs/first-response: 3.18724 mean, 202.23 max, 0.315 min
 *   HTTP response codes:
 *     code 200 -- 100000
 *
 * @author Pablo Mayrgundter
 */
public class Server {

  static final Logger logger = Logger.getLogger(Server.class.getName());

  static final Flags flags = new Flags(Server.class);
  static final int PORT = flags.get("port", "port", 80);
  static final boolean log = flags.get("log", "log", false);
  static final String indexFilename = flags.get("index", "index", "index.html");

  static ConcurrentLinkedDeque<Handler> idleHandlers = new ConcurrentLinkedDeque<Handler>();

  static final class Handler implements Runnable {

    final byte [] buf;
    final DateFormat dateFormat;
    OutputStream os;
    BufferedReader r;

    Handler() {
      buf = new byte[1024];
      dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
      dateFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
    }

    void handle(Socket s) throws IOException {
      assert debug("Handling connection: " + s);
      r = new BufferedReader(new InputStreamReader(s.getInputStream()));
      try {
        os = s.getOutputStream();
      } catch (IOException e) {
        r.close();
        throw e;
      }
    }

    public void run() {
      try {
        String line, hdr = "";
        while ((line = r.readLine()) != null) {
          if (line.trim().equals("")) {
            break;
          }
          hdr += line + "\n";
        }

        assert debug("Got header: " + hdr);
        if (hdr.startsWith("GET")) {
          String [] parts = hdr.split(" ");
          if (parts.length < 3) {
            System.out.println("Malformed header: " + hdr);
            responseHeaders("-", 400, null);
            return;
          }
          sendFile(parts[1]);
        }
      } catch (IOException e) {
        e.printStackTrace();
        logger.warning("Connection service failed: " + e);
        try {
          responseHeaders("-", 500, null);
        } catch (IOException ee) {
          ee.printStackTrace();
          logger.severe("Response 500 to client failed: " + ee);
        }
      } finally {
        try {
          r.close();
        } catch (IOException e) {
          logger.warning("Connection close failed: " + e);
        }
      }
      idleHandlers.push(this);
    }

    void responseHeaders(String path, int code, String mime, long ... contentLength) throws IOException {
      String msg = "HTTP/1.0 " + code + " ";
      switch(code) {
        case 200: msg += "OK\r\n"; break;
        case 302:
          msg += "Found\r\n";
          msg += "Location: " + path + "\r\n";
          break;
        case 400: msg += "Client error\r\n"; break;
        case 403: msg += "Forbidden\r\n"; break;
        case 404: msg += "File not found\r\n"; break;
        case 500: ;
        default: msg += "Server error\r\n"; break;
      }
      if (contentLength.length != 0) {
        msg += "Content-Length: " + contentLength[0] + "\r\n";
      }
      if (mime != null) {
        msg += "Content-Type: " + mime + "\r\n";
      }
      msg += "Cache-Control: private, max-age=0\r\n";
      msg += "Expires: -1\r\n";
      msg += "Server: yo\r\n";
      final String serveDate = dateFormat.format(new Date());
      msg += "Date: " + serveDate + "\r\n";
      msg += "\r\n";
      os.write(msg.getBytes());
      if (log) {
        System.out.printf("%s %d path(%s)\n%s\n\n", serveDate, code, path, msg);
      }
    }


    String getMimeAndCharset(String filename) {
      final String [] parts = filename.split("\\.");
      String type = "application/octet";
      if (parts.length > 0) {
        final String ftype = parts[parts.length - 1];
        if (ftype.matches("(png|jpg|jpeg|gif|ico)")) {
          type = "image/" + ftype;
        } else if (ftype.matches("(html|xml|txt|css)")) {
          type = "text/" + ftype + "; charset=UTF-8";
        } else if (ftype.matches("(js|mjs)")) {
          type = "text/javascript" + "; charset=UTF-8";
        } else if (ftype.matches("json")) {
          // https://www.ietf.org/rfc/rfc4627.txt
          type = "application/json" + "; charset=UTF-8";
        } else if (ftype.matches("wasm")) {
          // https://webassembly.org/docs/web/
          type = "application/wasm";
        }
      }
      return type;
    }

    void sendFile(String filename) throws IOException {
      assert debug("sendFile: " + filename);
      int code = 200;
      String mime;
      File serveFile;
      if (filename.endsWith("/")) {
        String maybeIndex = filename + indexFilename;
        serveFile = new File("./" + maybeIndex);
        if (!serveFile.exists()) {
          System.out.println(serveFile + " doesn't exist");
          responseHeaders(filename, 403, null);
          return;
        }
        filename = maybeIndex;
        mime = getMimeAndCharset(filename);
        System.out.printf("mime(%s), filename(%s): ", mime, filename);
        code = 302;
      } else {
        filename = translateFilename(filename);
        mime = getMimeAndCharset(filename);
        System.out.println("MIME: " + mime);
        serveFile = new File(filename);
      }
      if (!serveFile.exists()) {
          responseHeaders(filename, 404, mime, 0);
          return;
      }
      RandomAccessFile raf = null;
      try {
        raf = new RandomAccessFile(serveFile, "r");
      } catch (IOException e) {
        responseHeaders(filename, 500, mime, 0);
        return;
      }
      final long fileLen = raf.length();
      try (FileChannel fc = raf.getChannel();
           WritableByteChannel wbc = Channels.newChannel(os)) {
          responseHeaders(filename, code, mime, fileLen);
        long sentLen = 0;
        while (sentLen < fileLen) {
          sentLen += fc.transferTo(sentLen, fileLen - sentLen, wbc);
        }
      }
    }

    String translateFilename(String filename) {
      if (filename.startsWith("/")) {
        filename = filename.substring(1);
      }
      while (filename.startsWith("../")) {
        filename = filename.substring(3);
      }
      return filename;
    }
  }

  final ServerSocketFactory serverSocketFactory;
  Server(ServerSocketFactory serverSocketFactory) {
    this.serverSocketFactory = serverSocketFactory;
  }

  public void run() throws IOException {
    // Bind
    final ServerSocket ss = serverSocketFactory.createServerSocket(PORT);
    Socket socket;
    while ((socket = ss.accept()) != null) {
      assert debug("Spawning worker thread: " + socket);
      Handler h;
      if (idleHandlers.isEmpty()) {
        h = new Handler();
      } else {
        h = idleHandlers.pop();
      }
      try {
        h.handle(socket);
        new Thread(h).start();
      } catch (IOException e) {
        logger.warning(e.getMessage());
      }
    }
  }

  // https://docs.oracle.com/javase/10/security/sample-code-illustrating-secure-socket-connection-client-and-server.htm
  static ServerSocketFactory createSslServerSocketFactory(String keystoreFilename) throws Exception {
    final SSLContext ctx = SSLContext.getInstance("TLS");
    final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    final KeyStore ks = KeyStore.getInstance("JKS");
    final char [] passphrase = flags.get("keystorePassphrase", "passphrase").toCharArray();
    ks.load(new FileInputStream(keystoreFilename), passphrase);
    kmf.init(ks, passphrase);
    ctx.init(kmf.getKeyManagers(), null, null);
    return ctx.getServerSocketFactory();
  }

  public static void main(String [] args) throws Exception {
    new Server(flags.get("ssl", "ssl", false) ?
               createSslServerSocketFactory(flags.get("keystore"))
               : ServerSocketFactory.getDefault()).run();
  }

  static final boolean debug(String s) {
    logger.info(s);
    return true;
  }
}
