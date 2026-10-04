package com.nttdocomo.io;
import java.io.*;
public interface HttpConnection extends javax.microedition.io.ContentConnection {
    void connect() throws IOException;
    int getResponseCode() throws IOException;
    InputStream openInputStream() throws IOException;
    OutputStream openOutputStream() throws IOException;
    void setRequestMethod(String method) throws IOException;
    void setRequestProperty(String key,String value) throws IOException;
    void close() throws IOException;
}
