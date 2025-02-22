/*
 * @(#) HttpResource.java
 *
 * Created on 03.10.2016 by Daniel Becker
 *
 *-----------------------------------------------------------------------
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *----------------------------------------------------------------------
 */

package de.quippy.javamod.io;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import de.quippy.javamod.system.Helpers;

import static java.lang.System.getLogger;


/**
 * @author Daniel Becker
 * @since 03.10.2016
 * This class will wrap around a URL as a http-resource
 * and read it. It will reflect on http headers and do a
 * reload if for instance "302 - moved"
 */
public class HttpResource implements Closeable {

    private static final Logger logger = getLogger(HttpResource.class.getName());

    private static final String HEADER_LOCATION = "LOCATION";
    private static final String HEADER_CONTENTTYPE = "CONTENT-TYPE";
    private static final String HEADER_CONTENTLENGTH = "CONTENT-LENGTH";

    private URL resource;
    private Socket socket;
    private boolean isHTTPS;
    private int port;
    private String path;

    private Map<String, String> resourceHeaders;
    private int httpCode;
    private URL newLocation;
    private String contentType;
    private long contentLength;

    private String user_agent = "Java HttpResource 1.0";
    private String accept_charset = "UTF-8";

    /**
     * Constructor for HttpResource
     *
     * @param resource
     */
    public HttpResource(URL resource) {
        super();
        initialize(resource);
    }

    private void initialize(URL resource) {
        this.resource = resource;
        String protocol = resource.getProtocol().toLowerCase();
        isHTTPS = protocol.equals("https");
        port = resource.getPort();
        if (port < 0) { // no port specified - so get default!
            if (isHTTPS)
                port = 443;
            else
                port = 80;
        }
        path = resource.getPath();
        if (path == null || path.isEmpty()) path = "/";
    }

    /**
     * Create the socket connection
     *
     * @since 03.10.2016
     */
    private void open() {
        try {
            socket = new Socket(resource.getHost(), port);
        } catch (IOException ex) {
            logger.log(Level.ERROR, "Connection to resource " + resource.toString() + " failed", ex);
        }
    }

    @Override
    public void close() throws IOException {
        if (socket != null) socket.close();
    }

    /**
     * @param user_agent the user_agent to set
     */
    public void setUser_agent(String user_agent) {
        this.user_agent = user_agent;
    }

    /**
     * @return the user_agent
     */
    public String getUser_agent() {
        return user_agent;
    }

    /**
     * @return the accept_charset
     */
    public String getAccept_charset() {
        return accept_charset;
    }

    /**
     * @param accept_charset the accept_charset to set
     */
    public void setAccept_charset(String accept_charset) {
        this.accept_charset = accept_charset;
    }

    /**
     * @return the resourceHeaders
     */
    public Map<String, String> getResourceHeaders() {
        return resourceHeaders;
    }

    /**
     * @return the httpCode
     */
    public int getHttpCode() {
        return httpCode;
    }

    public boolean isInformation() {
        return (httpCode >= 100 && httpCode < 200);
    }

    public boolean isOK() {
        return (httpCode >= 200 && httpCode < 300);
    }

    public boolean isMoved() {
        return (httpCode >= 300 && httpCode < 400);
    }

    public boolean isClientError() {
        return (httpCode >= 400 && httpCode < 500);
    }

    public boolean isServerError() {
        return (httpCode >= 500 && httpCode < 600);
    }

    /**
     * @return the contentType
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * @return the contentLength
     */
    public long getContentLength() {
        return contentLength;
    }

    /**
     * read all headers and set error code, content-type, ...
     *
     * @param headers
     * @since 03.10.2016
     */
    public void readHeadersFromMap(Map<String, List<String>> headers) throws IOException {
        resourceHeaders = new HashMap<>();
        for (String key : headers.keySet()) {
            List<String> values = headers.get(key);
            if (values == null) continue;

            String value = values.get(0);

            if (key == null) {
                StringTokenizer spaceTokenizer = new StringTokenizer(value, " ");
                String httpType = (spaceTokenizer.hasMoreTokens()) ? spaceTokenizer.nextToken() : null;
                String httpCodeString = (spaceTokenizer.hasMoreTokens()) ? spaceTokenizer.nextToken() : null;
                String httpStatusString = (spaceTokenizer.hasMoreTokens()) ? spaceTokenizer.nextToken() : null;
                if (httpCodeString != null) {
                    httpCode = Integer.parseInt(httpCodeString);
                    resourceHeaders.put(httpType, httpCodeString + " " + httpStatusString);
                }
            } else {
                String compare = key.toUpperCase();

                if (compare.startsWith(HEADER_LOCATION)) {
                    newLocation = Helpers.createURLfromString(value);
                } else if (compare.startsWith(HEADER_CONTENTLENGTH)) {
                    contentLength = Long.parseLong(value);
                } else if (compare.startsWith(HEADER_CONTENTTYPE)) {
                    contentType = value;
                }
                resourceHeaders.put(key, value);
            }
        }
    }

    /**
     * read all headers and set error code, content type, ...
     *
     * @param reader
     * @since 03.10.2016
     */
    public void readHeaders(BufferedReader reader) throws IOException {
        Map<String, List<String>> headers = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) break; // empty line: all headers read
            int pos = line.indexOf(':');
            if (pos > 0) {
                String keyWord = line.substring(0, pos);
                String value = line.substring(pos + 1).trim();
                if (value.toLowerCase().endsWith("<br>")) value = value.substring(0, value.length() - 4);
                List<String> list = new ArrayList<>();
                list.add(value);
                headers.put(keyWord, list);
            } else {
                List<String> list = new ArrayList<>();
                list.add(line);
                headers.put(null, list);
            }
        }
        readHeadersFromMap(headers);
    }

    /**
     * Read the resource - first read all headers and stay on the resource data itself
     *
     * @param additionalRequestHeaders
     * @param keep_alive
     * @return
     * @throws IOException
     * @since 03.10.2016
     */
    public InputStream getResource(Map<String, String> additionalRequestHeaders, boolean keep_alive) throws IOException {
        if (isHTTPS) {
            URLConnection con = resource.openConnection();
            con.addRequestProperty("user-agent", user_agent);
            con.addRequestProperty("Connection", (keep_alive) ? "keep_alive" : "close");
            for (String key : additionalRequestHeaders.keySet()) {
                if (key == null) continue;
                con.addRequestProperty(key, additionalRequestHeaders.get(key));
            }

            InputStream result = con.getInputStream();
            readHeadersFromMap(con.getHeaderFields());
            // if the resource moved, re-read at new location
            if (isMoved() && newLocation != null) {
                close();
                initialize(newLocation);
                open();
                return getResource(additionalRequestHeaders, keep_alive);
            } else
                return result;
        } else {
            StringBuilder getString = new StringBuilder()
                    .append("GET ").append(path).append(" HTTP/1.1\r\nHost: ").append(resource.getHost())
                    .append("\r\nuser-agent: ").append(user_agent).append("\r\nAccept: */*\r\nAccept-Charset: ").append(accept_charset)
                    .append("\r\n");
            for (String key : additionalRequestHeaders.keySet()) {
                getString.append(key).append(": ").append(additionalRequestHeaders.get(key)).append("\r\n");
            }
            getString.append("Connection: ").append((keep_alive) ? "keep_alive" : "close").append("\r\n\r\n");
            // now send this "get" command and open the input Stream
            open();
            OutputStream os = socket.getOutputStream();
            os.write(getString.toString().getBytes());
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), Helpers.CODING_M3U));
            readHeaders(reader);
            // if the resource moved, re-read at new location
            if (isMoved() && newLocation != null) {
                close();
                initialize(newLocation);
                open();
                return getResource(additionalRequestHeaders, keep_alive);
            } else
                return socket.getInputStream();
        }
    }
}
