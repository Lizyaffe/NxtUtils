import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class NxtServer {


    public static void main(String[] args) {
        NxtServer server = new NxtServer();
        server.start();
    }

    private void start() {
        try {
            // load server configuration.
            String jettyConfigFile = "etc/jetty.xml";
            InputStream is = ClassLoader.getSystemResourceAsStream(jettyConfigFile);
            if (is == null) {
                throw new IllegalStateException("Failed to find Jetty configuration file - " + jettyConfigFile);
            }
            Nxt.logMessage("Nxt server started");
            Server server = new Server();
            XmlConfiguration configuration = new XmlConfiguration(is);
            configuration.configure(server);
            Handler handler = server.getHandler();
            configureHandlers(handler);

            // configure the HTTP/S  server.
            ServerConnector httpConnector = new ServerConnector(server);
            httpConnector.setPort(7874);
            server.addConnector(httpConnector);

             // SSL Context Factory
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath("etc/keystore");
            sslContextFactory.setKeyStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
            sslContextFactory.setKeyManagerPassword("OBF:1u2u1wml1z7s1z7a1wnl1u2g");
            sslContextFactory.setTrustStorePath("etc/keystore");
            sslContextFactory.setTrustStorePassword("OBF:1vny1zlo1x8e1vnw1vn61x8g1zlu1vn4");
            sslContextFactory.setExcludeCipherSuites(
                    "SSL_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                    "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                    "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

            // SSL HTTP Configuration
            HttpConfiguration http_config = new HttpConfiguration();
            http_config.setSecureScheme("https");
            http_config.setSecurePort(8443);
            http_config.setOutputBufferSize(32768);
            http_config.setRequestHeaderSize(8192);
            http_config.setResponseHeaderSize(8192);
            http_config.setSendServerVersion(true);
            http_config.setSendDateHeader(false);
            HttpConfiguration https_config = new HttpConfiguration(http_config);
            https_config.addCustomizer(new SecureRequestCustomizer());

            // SSL Connector
            ServerConnector sslConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory,"http/1.1"),
                    new HttpConnectionFactory(https_config));
            sslConnector.setPort(7875);
            server.addConnector(sslConnector);
            server.start();
            server.join();
        } catch (Exception e) {
            Nxt.logMessage("Startup problem", e);
            throw new IllegalStateException(e);
        }
    }

    private void configureHandlers(Handler handler) {
        HandlerCollection handlersCollection = (HandlerCollection)handler;
        Handler[] handlers = handlersCollection.getHandlers();

        ContextHandlerCollection contexts = (ContextHandlerCollection)handlers[0];
        ServletContextHandler defaultContext = new ServletContextHandler(contexts, "/", ServletContextHandler.SESSIONS);

        // configure the default web server servlet
        Properties properties = new Properties();
        try {
            properties.load(ClassLoader.getSystemResourceAsStream("nxt.properties"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        List<?> propertyList = Collections.list(properties.propertyNames());
        ServletHolder nxtServletHolder = new ServletHolder(new Nxt());
        for (Object key : propertyList) {
            String propertyName = (String)key;
            nxtServletHolder.setInitParameter(propertyName, properties.getProperty(propertyName));
        }
        defaultContext.addServlet(nxtServletHolder, "/nxt/*");
        ServletHolder defaultServletHolder = new ServletHolder(new DefaultServlet());
        defaultServletHolder.setInitParameter("resourceBase", "webapps/root");
        defaultContext.addServlet(defaultServletHolder, "/*");
        contexts.addHandler(defaultContext);

        NCSARequestLog requestLog = new NCSARequestLog("logs/access_yyyy_mm_dd.log");
        int days = 14;
        requestLog.setRetainDays(days);
        requestLog.setAppend(true);
        requestLog.setExtended(true);
        requestLog.setLogCookies(true);
        requestLog.setLogServer(true);
        requestLog.setLogLatency(true);
        requestLog.setPreferProxiedForAddress(true);
        requestLog.setLogTimeZone(System.getProperty("user.timezone"));
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        requestLogHandler.setRequestLog(requestLog);
        handlersCollection.addHandler(requestLogHandler);
        Nxt.logMessage(String.format("Access log name: %s", requestLog.getFilename()));
    }


}
