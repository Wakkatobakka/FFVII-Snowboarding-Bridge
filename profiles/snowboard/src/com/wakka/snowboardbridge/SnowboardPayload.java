package com.wakka.snowboardbridge;

import android.content.Context;
import dalvik.system.InMemoryDexClassLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Public-release payload boundary. Original game data lives only in app-private storage. */
public final class SnowboardPayload {
    private static final String DIR="ffvii-snowboarding-payload";
    private static volatile ClassLoader gameLoader;
    private SnowboardPayload() {}

    private static final class Expected {
        final long size; final String sha;
        Expected(long size,String sha){this.size=size;this.sha=sha;}
    }

    private static Map<String,Expected> expected(Context c) throws IOException {
        Properties p=new Properties();
        try(InputStream in=c.getAssets().open("expected-payload.properties")){p.load(in);}
        Map<String,Expected> out=new HashMap<String,Expected>();
        for(String key:p.stringPropertyNames()){
            if(!key.startsWith("entry.")||!key.endsWith(".sha256"))continue;
            String name=key.substring(6,key.length()-7);
            String size=p.getProperty("entry."+name+".size");
            if(size==null)throw new IOException("Missing expected size for "+name);
            out.put(name,new Expected(Long.parseLong(size.trim()),p.getProperty(key).trim().toLowerCase()));
        }
        if(out.isEmpty())throw new IOException("Payload allowlist is empty");
        return out;
    }

    public static File dir(Context c){return new File(c.getFilesDir(),DIR);}

    public static boolean ready(Context c){
        try{verifyDir(c,dir(c));return true;}catch(Exception e){return false;}
    }

    public static String readiness(Context c){
        if(ready(c))return "GAME DATA · IMPORTED / VERIFIED";
        return "GAME DATA REQUIRED · USE IMPORT GAME DATA";
    }

    public static String importZip(Context c,InputStream source) throws Exception {
        Map<String,Expected> allow=expected(c);
        File base=c.getFilesDir();
        File temp=new File(base,DIR+".import-"+UUID.randomUUID().toString());
        if(!temp.mkdirs())throw new IOException("Cannot create import staging folder");
        Set<String> seen=new HashSet<String>();
        try{
            try(ZipInputStream zin=new ZipInputStream(source)){
                ZipEntry ze;
                while((ze=zin.getNextEntry())!=null){
                    if(ze.isDirectory()){zin.closeEntry();continue;}
                    String name=ze.getName().replace('\\','/');
                    if(name.startsWith("/")||name.contains("../")||name.equals(".."))throw new IOException("Unsafe ZIP entry: "+name);
                    Expected ex=allow.get(name);
                    if(ex==null)throw new IOException("Unexpected payload file: "+name);
                    if(!seen.add(name))throw new IOException("Duplicate payload file: "+name);
                    File out=new File(temp,name);
                    File parent=out.getParentFile(); if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create payload folder");
                    long count=0; byte[] buf=new byte[8192];
                    try(FileOutputStream fos=new FileOutputStream(out)){
                        int n;while((n=zin.read(buf))!=-1){count+=n;if(count>ex.size)throw new IOException(name+" is larger than expected");fos.write(buf,0,n);}
                        fos.getFD().sync();
                    }
                    if(count!=ex.size)throw new IOException(name+" size mismatch");
                    String got=sha256(out);if(!ex.sha.equals(got))throw new IOException(name+" SHA-256 mismatch");
                    zin.closeEntry();
                }
            }
            if(seen.size()!=allow.size()){
                List<String> missing=new ArrayList<String>();for(String n:allow.keySet())if(!seen.contains(n))missing.add(n);
                Collections.sort(missing);throw new IOException("Payload is incomplete; missing: "+join(missing));
            }
            verifyDir(c,temp);
            File live=dir(c),backup=new File(base,DIR+".backup");
            deleteTree(backup);
            if(live.exists()&&!live.renameTo(backup))throw new IOException("Cannot stage existing payload for replacement");
            if(!temp.renameTo(live)){
                if(backup.exists())backup.renameTo(live);
                throw new IOException("Cannot activate imported payload");
            }
            deleteTree(backup);gameLoader=null;
            return "FFVII Snowboarding game data imported / verified ("+allow.size()+" files)";
        }catch(Exception e){deleteTree(temp);throw e;}
    }

    private static void verifyDir(Context c,File root) throws Exception {
        if(!root.isDirectory())throw new IOException("Payload folder is missing");
        Map<String,Expected> allow=expected(c);
        for(Map.Entry<String,Expected> item:allow.entrySet()){
            File f=new File(root,item.getKey()); Expected ex=item.getValue();
            if(!f.isFile())throw new IOException("Missing "+item.getKey());
            if(f.length()!=ex.size)throw new IOException(item.getKey()+" size mismatch");
            if(!ex.sha.equals(sha256(f)))throw new IOException(item.getKey()+" SHA-256 mismatch");
        }
    }

    public static InputStream openAsset(Context c,String name) throws IOException {
        String clean=name.replace('\\','/');while(clean.startsWith("/"))clean=clean.substring(1);
        if(clean.contains("../"))throw new IOException("Unsafe asset path");
        File direct=new File(dir(c),clean);
        if(direct.isFile())return new FileInputStream(direct);
        File jar=new File(dir(c),"game.jar");
        if(!jar.isFile())throw new IOException("Game JAR is not imported");
        try(ZipFile zip=new ZipFile(jar)){
            ZipEntry entry=zip.getEntry(clean);if(entry==null)throw new IOException("Missing game resource: "+clean);
            try(InputStream in=zip.getInputStream(entry)){return new ByteArrayInputStream(readAll(in,(int)Math.min(Integer.MAX_VALUE,entry.getSize())));}
        }
    }

    public static synchronized Class<?> gameClass(Context c) throws Exception {
        if(!ready(c))throw new IOException("Game payload is not imported / verified");
        if(gameLoader==null){
            File dex=new File(dir(c),"game.dex");
            byte[] bytes;try(InputStream in=new FileInputStream(dex)){bytes=readAll(in,(int)dex.length());}
            gameLoader=new InMemoryDexClassLoader(ByteBuffer.wrap(bytes),SnowboardPayload.class.getClassLoader());
        }
        return Class.forName("SnowBoard",true,gameLoader);
    }

    private static byte[] readAll(InputStream in,int hint) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(Math.max(1024,hint));byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();
    }
    private static String sha256(File f) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[8192];
        try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
        StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format("%02x",x&255));return s.toString();
    }
    private static void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)deleteTree(k);}f.delete();}
    private static String join(List<String> names){StringBuilder s=new StringBuilder();for(String n:names){if(s.length()>0)s.append(", ");s.append(n);}return s.toString();}
}
