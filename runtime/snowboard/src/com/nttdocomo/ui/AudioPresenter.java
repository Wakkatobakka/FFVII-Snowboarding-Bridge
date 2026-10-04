package com.nttdocomo.ui;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import com.wakka.snowboardbridge.BridgeLog;
import com.wakka.snowboardbridge.RuntimeHost;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Snowboarding's bounded MLD audio bridge. The original MLD stays game-owned;
 *  preconverted Type-0 MIDI mirrors provide Android-native playback. */
public class AudioPresenter extends MediaPresenter {
    private static final Map<String,File> CACHE=new HashMap<String,File>();
    private final int channel;
    private MediaSound sound;
    private MediaListener listener;
    private MediaPlayer introPlayer;
    private MediaPlayer loopPlayer;
    private volatile MediaPlayer currentPlayer;
    private int state=0;
    private float volume=1.0f;

    private AudioPresenter(int c){channel=c;}
    public static AudioPresenter getAudioPresenter(int channel){return new AudioPresenter(channel);}
    public void setMediaListener(MediaListener l){listener=l;}
    public void setSound(MediaSound s){sound=s;}

    public void setAttribute(int attribute,int value){
        if(attribute!=4)return;
        int clean=Math.max(0,Math.min(100,value));
        volume=clean/100.0f;
        applyVolume(introPlayer);
        applyVolume(loopPlayer);
    }

    public synchronized void play(){
        releasePlayers();
        if(sound==null || sound.name==null){
            state=0;
            BridgeLog.i("AUDIO","channel="+channel+" missing named MLD resource");
            return;
        }
        String base=baseName(sound.name);
        if(base==null){
            state=0;
            BridgeLog.i("AUDIO","channel="+channel+" unsupported sound="+sound.name);
            return;
        }
        try{
            File intro=cacheAsset("audio/"+base+".intro.mid");
            File loop=cacheAsset("audio/"+base+".loop.mid");
            loopPlayer=createPlayer(loop,true);
            introPlayer=createPlayer(intro,false);
            introPlayer.setNextMediaPlayer(loopPlayer);
            introPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
                public void onCompletion(MediaPlayer ignored){
                    synchronized(AudioPresenter.this){
                        if(state==1 && loopPlayer!=null)currentPlayer=loopPlayer;
                    }
                }
            });
            currentPlayer=introPlayer;
            state=1;
            introPlayer.start();
            BridgeLog.i("AUDIO","channel="+channel+" playing "+sound.name+" via MIDI bridge");
        }catch(Throwable e){
            state=0;
            releasePlayers();
            BridgeLog.e("AUDIO",e);
        }
    }

    public synchronized void pause(){
        MediaPlayer p=currentPlayer;
        try{if(p!=null && p.isPlaying())p.pause();}catch(Throwable e){BridgeLog.e("AUDIO PAUSE",e);}
        state=2;
    }

    public synchronized void restart(){
        MediaPlayer p=currentPlayer;
        try{if(p!=null){p.start();state=1;}}catch(Throwable e){BridgeLog.e("AUDIO RESTART",e);}
    }

    public synchronized void stop(){
        state=0;
        releasePlayers();
    }

    private MediaPlayer createPlayer(File file,boolean looping) throws Exception{
        MediaPlayer p=new MediaPlayer();
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        p.setDataSource(file.getAbsolutePath());
        p.setLooping(looping);
        p.prepare();
        applyVolume(p);
        return p;
    }

    private void applyVolume(MediaPlayer p){
        if(p==null)return;
        try{p.setVolume(volume,volume);}catch(Throwable ignored){}
    }

    private void releasePlayers(){
        MediaPlayer a=introPlayer,b=loopPlayer;
        currentPlayer=null;introPlayer=null;loopPlayer=null;
        release(a);
        if(b!=a)release(b);
    }

    private static void release(MediaPlayer p){
        if(p==null)return;
        try{p.setNextMediaPlayer(null);}catch(Throwable ignored){}
        try{p.stop();}catch(Throwable ignored){}
        try{p.release();}catch(Throwable ignored){}
    }

    private static String baseName(String resource){
        String n=resource.replace('\\','/');
        int slash=n.lastIndexOf('/');if(slash>=0)n=n.substring(slash+1);
        if(n.equalsIgnoreCase("elec_de_chocobo.mld"))return "elec_de_chocobo";
        if(n.equalsIgnoreCase("fanfare.mld"))return "fanfare";
        if(n.equalsIgnoreCase("yuki_ni_tozasarete.mld"))return "yuki_ni_tozasarete";
        return null;
    }

    private static synchronized File cacheAsset(String assetPath) throws Exception{
        File cached=CACHE.get(assetPath);
        if(cached!=null && cached.isFile())return cached;
        if(RuntimeHost.context==null)throw new IllegalStateException("RuntimeHost context unavailable");
        File dir=new File(RuntimeHost.context.getCacheDir(),"snowboard-midi");
        if(!dir.isDirectory() && !dir.mkdirs())throw new IllegalStateException("Cannot create audio cache");
        String fileName=assetPath.substring(assetPath.lastIndexOf('/')+1);
        File out=new File(dir,fileName);
        try(InputStream in=RuntimeHost.openGameAsset(assetPath);
            FileOutputStream fos=new FileOutputStream(out,false)){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)fos.write(buf,0,n);
        }
        CACHE.put(assetPath,out);
        return out;
    }
}
