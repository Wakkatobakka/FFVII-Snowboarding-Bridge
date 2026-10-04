package com.nttdocomo.net;
public final class URLEncoder { public static String encode(String s){try{return java.net.URLEncoder.encode(s,"UTF-8");}catch(Exception e){return s;}} }
