package org.qingyue.rope;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.media.AudioAttributes;
import java.util.Locale;

/** Never speaks until an explicitly offline Chinese voice has been selected. */
public final class OfflineSpeaker {
    private TextToSpeech tts;
    private boolean ready,enabled;
    private String status="正在检查离线中文语音…";
    private final Runnable changed;
    public OfflineSpeaker(Context context,boolean enabled,Runnable changed) {
        this.enabled=enabled;this.changed=changed;
        tts=new TextToSpeech(context,result->{
            if(result!=TextToSpeech.SUCCESS){status="离线中文语音不可用，可静音训练";changed.run();return;}
            Voice selected=null;
            try {
                if(tts.getVoices()!=null)for(Voice voice:tts.getVoices()) {
                    if(!voice.isNetworkConnectionRequired()&&"zh".equals(voice.getLocale().getLanguage())) {
                        if(selected==null||"CN".equals(voice.getLocale().getCountry()))selected=voice;
                    }
                }
                if(selected!=null&&tts.setVoice(selected)==TextToSpeech.SUCCESS) {
                    tts.setSpeechRate(1.05f);
                    tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    ready=true;status="离线中文语音已就绪";
                } else status="设备未装离线中文语音，可静音训练";
            } catch(RuntimeException error){status="离线中文语音不可用，可静音训练";}
            changed.run();
        });
    }
    public void enable(boolean on){enabled=on;if(!on)stop();}
    public boolean enabled(){return enabled;}
    public boolean ready(){return ready;}
    public String status(){return status;}
    public void say(String text){if(enabled&&ready&&tts!=null)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"qingyue");}
    public void stop(){if(tts!=null)tts.stop();}
    public void close(){ready=false;if(tts!=null){tts.stop();tts.shutdown();tts=null;}}
}
