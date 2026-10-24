package org.qingyue.rope;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.qingyue.rope.core.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Small platform-native UI; training logic and persistence formats live outside this activity. */
public final class MainActivity extends Activity {
    private static final int ORANGE=0xffff6b35,BLUE=0xff4c9bff,TEAL=0xff31bfd3,PURPLE=0xff9970f5;
    private static final int INK=0xff20242d,GRAY=0xff7b8190,BG=0xfff7f8fa,WHITE=Color.WHITE;
    private enum Screen { HOME,CONFIG,TRAINING,HISTORY,RESULT }
    private Screen screen=Screen.HOME;
    private LocalStore store;
    private OfflineSpeaker speaker;
    private SharedPreferences preferences;
    private SessionEngine engine;
    private Plan selectedPlan=new Plan(Plan.Mode.FREE,60,15,3);
    private CameraController camera;
    private final JumpDetector detector=new JumpDetector();
    private final NumericDiagnostics diagnostics=new NumericDiagnostics();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastCheckpoint,lastFrameAt,cameraOpenedAt,bodyLostAt;
    private boolean visible,front=true,resuming,finalSaved;
    private int cameraGeneration,reportEvery=10,lastCountdown=-1;
    private SessionEngine.Phase lastPhase;
    private String cameraMessage="请打开相机，站稳后开始";
    private PosePreview preview;
    private TextView timerText,timerLabel,countText,phaseText,centerText,feedbackText,voiceText;
    private Button primaryButton;
    private LinearLayout content;
    private final Runnable ticker=new Runnable(){public void run(){
        if(visible){advance();handler.postDelayed(this,100);}
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);preferences=getSharedPreferences("settings",MODE_PRIVATE);
        reportEvery=preferences.getInt("reportEvery",10);if(reportEvery!=1&&reportEvery!=10&&reportEvery!=20)reportEvery=10;
        front=preferences.getBoolean("front",true);store=new LocalStore(this);
        speaker=new OfflineSpeaker(this,preferences.getBoolean("voice",true),()->runOnUiThread(()->{
            if(screen==Screen.TRAINING)updateTraining();
        }));
        try {engine=store.recover(now());}catch(IOException error){cameraMessage="恢复数据异常，原记录已保留："+error.getMessage();}
        if(engine!=null){selectedPlan=engine.plan;
            if(engine.phase()==SessionEngine.Phase.FINISHED)finishAndSave();
            else {cameraMessage="上次训练已恢复，点击继续后站稳";showTraining();}
        }else showHome();
    }
    @Override public void onResume(){super.onResume();visible=true;handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override public void onPause(){
        visible=false;handler.removeCallbacks(ticker);
        if(engine!=null&&engine.phase()!=SessionEngine.Phase.FINISHED){engine.pause(now());checkpoint(true);}
        resuming=false;closeCamera();if(speaker!=null)speaker.stop();
        if(screen==Screen.TRAINING){cameraMessage="离开应用已自动暂停，点击继续恢复";updateTraining();}
        super.onPause();
    }
    @Override public void onDestroy(){closeCamera();if(speaker!=null)speaker.close();super.onDestroy();}
    private long now(){return SystemClock.elapsedRealtime();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private GradientDrawable bg(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setIncludeFontPadding(false);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));return t;
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private Button button(String label,int color,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(16);b.setTextColor(color==WHITE?INK:WHITE);b.setAllCaps(false);
        b.setMinHeight(dp(52));b.setMinimumHeight(dp(52));b.setPadding(dp(14),dp(6),dp(14),dp(6));b.setBackground(bg(color,14));
        b.setOnClickListener(v->action.run());return b;
    }
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(bg(WHITE,16));return c;}
    private void cardAdd(LinearLayout parent,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);parent.addView(child,lp);}
    private void base(Screen page,String title,boolean bottomNav) {
        screen=page;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=column();root.setBackgroundColor(BG);root.setFitsSystemWindows(true);
        LinearLayout header=row();header.setPadding(dp(16),dp(16),dp(12),dp(14));
        if(page!=Screen.HOME&&page!=Screen.HISTORY){Button back=button("‹",WHITE,this::goBack);header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        TextView heading=text(title,page==Screen.HOME?27:23,INK,true);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(page==Screen.HOME){Icons settings=new Icons(this,Icons.Kind.SETTINGS,INK,false);settings.setContentDescription("设置");settings.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);settings.setOnClickListener(v->showSettings());header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));}
        root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(dp(16),dp(4),dp(16),dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(bottomNav){LinearLayout nav=row();nav.setPadding(dp(20),dp(8),dp(20),dp(8));nav.setBackgroundColor(WHITE);
            nav.addView(navItem("训练",Icons.Kind.ROPE,page==Screen.HOME,()->showHome()),new LinearLayout.LayoutParams(0,dp(58),1));
            nav.addView(navItem("记录",Icons.Kind.HISTORY,page==Screen.HISTORY,()->showHistory(0)),new LinearLayout.LayoutParams(0,dp(58),1));root.addView(nav);}
        setContentView(root);
    }
    private View navItem(String label,Icons.Kind kind,boolean selected,Runnable action){
        LinearLayout item=column();item.setGravity(Gravity.CENTER);Icons icon=new Icons(this,kind,selected?ORANGE:GRAY,false);
        item.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(32)));TextView t=text(label,12,selected?ORANGE:GRAY,selected);item.addView(t);item.setOnClickListener(v->action.run());item.setContentDescription(label);return item;
    }
    private void showHome(){
        closeCamera();engine=null;base(Screen.HOME,"轻跃跳绳",true);
        TextView date=text(new SimpleDateFormat("M月d日 EEEE",Locale.CHINA).format(new Date()),13,GRAY,false);content.addView(date);space(content,18);
        LinearLayout hero=card();hero.setBackground(bg(0xffffeee5,18));
        TextView tag=text("专 注 每 一 跃",12,ORANGE,true);hero.addView(tag);space(hero,12);
        hero.addView(text("每一次轻跃，都算数。",25,INK,true));space(hero,10);hero.addView(text("端侧识别 · 离线训练 · 本地记录",13,GRAY,false));space(hero,18);
        LinearLayout stats=row();long count=0,time=0;for(WorkoutRecord r:store.history())if(sameDay(r.startedAt,System.currentTimeMillis())){count+=r.total;time+=r.workMs;}
        stats.addView(stat(Long.toString(count),"今日跳跃 / 个",ORANGE),new LinearLayout.LayoutParams(0,-2,1));
        stats.addView(stat(clock(time),"今日运动 / 分:秒",INK),new LinearLayout.LayoutParams(0,-2,1));hero.addView(stats);cardAdd(content,hero);
        if(store.blocked()){LinearLayout error=card();error.addView(text("本地记录读取异常",17,ORANGE,true));space(error,8);error.addView(text("原文件已保留，训练暂不可写入。"+store.error(),14,GRAY,false));cardAdd(content,error);}
        section("开始运动");
        LinearLayout first=row();first.addView(trainingTile("自由跳绳","随时开始，自由结束",Icons.Kind.ROPE,BLUE,()->configure(Plan.Mode.FREE)),tileParams(0));
        first.addView(trainingTile("定时跳绳","给自己一个小目标",Icons.Kind.TIMER,ORANGE,()->configure(Plan.Mode.TIMED)),tileParams(1));cardAdd(content,first);
        LinearLayout second=row();second.addView(trainingTile("分组训练","运动与休息有节奏",Icons.Kind.REST,PURPLE,()->configure(Plan.Mode.INTERVAL)),tileParams(0));
        second.addView(trainingTile("训练记录","看看每一天的进步",Icons.Kind.HISTORY,TEAL,()->showHistory(0)),tileParams(1));cardAdd(content,second);
        section("最近训练");
        if(store.history().isEmpty())empty("还没有训练记录","从一次轻松的跳绳开始吧");
        else for(int i=0;i<Math.min(2,store.history().size());i++)cardAdd(content,recordRow(store.history().get(i)));
        TextView note=text("仅相机权限 · 不上传、不录像\n计数识别双脚跳跃，无法判断绳是否实际过脚。",12,GRAY,false);note.setLineSpacing(dp(4),1);space(content,6);content.addView(note);
    }
    private LinearLayout.LayoutParams tileParams(int side){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(154),1);if(side==0)p.rightMargin=dp(6);else p.leftMargin=dp(6);return p;}
    private View trainingTile(String title,String subtitle,Icons.Kind icon,int color,Runnable action){
        LinearLayout c=card();c.setPadding(dp(16),dp(16),dp(12),dp(16));c.addView(new Icons(this,icon,color,true),new LinearLayout.LayoutParams(dp(44),dp(44)));space(c,15);c.addView(text(title,19,INK,true));space(c,7);c.addView(text(subtitle,11,GRAY,false));c.setOnClickListener(v->action.run());return c;
    }
    private View stat(String value,String label,int color){LinearLayout col=column();col.addView(text(value,32,color,true));space(col,7);col.addView(text(label,11,GRAY,false));return col;}
    private void section(String title){space(content,10);content.addView(text(title,19,INK,true));space(content,14);}
    private void empty(String title,String subtitle){LinearLayout c=card();c.setGravity(Gravity.CENTER);space(c,15);c.addView(new Icons(this,Icons.Kind.ROPE,0xffc6ccd6,false),new LinearLayout.LayoutParams(dp(54),dp(54)));space(c,13);c.addView(text(title,16,INK,true));space(c,9);c.addView(text(subtitle,13,GRAY,false));space(c,15);cardAdd(content,c);}

    private void configure(Plan.Mode mode){
        if(store.blocked()){toast("本地原记录受保护，暂不能开始训练");return;}
        try{selectedPlan=new Plan(mode,preferences.getInt(mode==Plan.Mode.INTERVAL?"intervalWork":"timedWork",60),preferences.getInt("rest",15),Math.max(2,preferences.getInt("rounds",3)));}
        catch(IllegalArgumentException error){selectedPlan=new Plan(mode,60,15,3);}
        showConfig();
    }
    private void showConfig(){
        base(Screen.CONFIG,selectedPlan.title(),false);LinearLayout intro=card();LinearLayout label=row();
        label.addView(new Icons(this,selectedPlan.mode==Plan.Mode.FREE?Icons.Kind.ROPE:selectedPlan.mode==Plan.Mode.TIMED?Icons.Kind.TIMER:Icons.Kind.REST,ORANGE,true),new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView slogan=text(selectedPlan.mode==Plan.Mode.FREE?"找到自己的节奏":"轻松设定，专心跳绳",20,INK,true);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(0,-2,1);sl.leftMargin=dp(14);label.addView(slogan,sl);intro.addView(label);space(intro,18);
        intro.addView(text("将手机固定放好，保持约2～3米距离。\n单人、正面、全身入镜，双脚同步轻跳。",15,GRAY,false));cardAdd(content,intro);
        if(selectedPlan.mode!=Plan.Mode.FREE)parameter("每组运动",selectedPlan.workSeconds,"秒",new int[]{30,60,90,120,180,300},value->{selectedPlan=new Plan(selectedPlan.mode,value,selectedPlan.restSeconds,selectedPlan.rounds);showConfig();});
        if(selectedPlan.mode==Plan.Mode.INTERVAL){
            parameter("组间休息",selectedPlan.restSeconds,"秒",new int[]{10,15,20,30,45,60},value->{selectedPlan=new Plan(selectedPlan.mode,selectedPlan.workSeconds,value,selectedPlan.rounds);showConfig();});
            parameter("训练组数",selectedPlan.rounds,"组",new int[]{2,3,5,8,10},value->{selectedPlan=new Plan(selectedPlan.mode,selectedPlan.workSeconds,selectedPlan.restSeconds,value);showConfig();});
        }
        LinearLayout privacy=card();privacy.addView(text("相机只用于此刻计数",17,INK,true));space(privacy,10);TextView body=text("画面只在设备内存中处理，不上传、不保存视频。仅支持单人，无法可靠检测旁人；多人进入请手动暂停。休息不计数；离开应用或离开画面3秒自动暂停。重新进入后，需点击继续。",14,GRAY,false);body.setLineSpacing(dp(5),1);privacy.addView(body);cardAdd(content,privacy);
        LinearLayout voice=card();voice.addView(text("语音报数",17,INK,true));space(voice,8);voice.addView(text(speaker.status()+"\n当前每"+reportEvery+"个报数，可在设置调整。",14,GRAY,false));cardAdd(content,voice);
        Button begin=button("开启相机，开始准备",ORANGE,()->{
            if(selectedPlan.mode==Plan.Mode.INTERVAL)preferences.edit().putInt("intervalWork",selectedPlan.workSeconds).putInt("rest",selectedPlan.restSeconds).putInt("rounds",selectedPlan.rounds).apply();
            else if(selectedPlan.mode==Plan.Mode.TIMED)preferences.edit().putInt("timedWork",selectedPlan.workSeconds).apply();
            if(hasCameraPermission()){showTraining();openCamera();}else requestPermissions(new String[]{Manifest.permission.CAMERA},41);
        });content.addView(begin,new LinearLayout.LayoutParams(-1,dp(56)));space(content,12);
        content.addView(text("首次打开需你允许相机权限。设备没有离线中文语音时，训练仍可静音进行。",12,GRAY,false));
    }
    private interface IntChoice{void choose(int value);}
    private void parameter(String title,int current,String unit,int[] choices,IntChoice picked){
        LinearLayout c=card();LinearLayout top=row();top.addView(text(title,16,INK,true),new LinearLayout.LayoutParams(0,-2,1));top.addView(text(current+" "+unit,20,ORANGE,true));c.addView(top);space(c,15);
        for(int offset=0;offset<choices.length;offset+=3){LinearLayout r=row();for(int i=offset;i<Math.min(offset+3,choices.length);i++){
            int value=choices[i];Button b=button(value+unit,value==current?ORANGE:BG,()->picked.choose(value));if(value!=current)b.setTextColor(INK);b.setTextSize(14);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(b,p);
        }c.addView(r);}cardAdd(content,c);
    }
    private boolean hasCameraPermission(){return checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);if(request!=41)return;
        if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED){resuming=engine!=null&&engine.phase()==SessionEngine.Phase.PAUSED;showTraining();openCamera();}
        else new AlertDialog.Builder(this).setTitle("相机权限未开启").setMessage("自动计数需要相机。你可以重新允许权限；若系统不再弹出授权，请到应用设置手动开启。")
                .setNegativeButton("稍后",null).setPositiveButton("应用设置",(d,w)->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())))).show();
    }

    private void showTraining(){
        screen=Screen.TRAINING;getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(0);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);root.setFitsSystemWindows(true);
        preview=new PosePreview(this);root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=column();top.setPadding(dp(16),dp(10),dp(16),0);LinearLayout toolbar=row();
        Button back=button("‹",0x9920242d,this::goBack);toolbar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView title=text(selectedPlan.title(),16,WHITE,true);title.setGravity(Gravity.CENTER);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Icons swap=new Icons(this,Icons.Kind.SWITCH,WHITE,false);swap.setContentDescription("切换前后摄像头");swap.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);swap.setOnClickListener(v->{
            if(engine!=null&&engine.phase()!=SessionEngine.Phase.PAUSED)pauseTraining("切换摄像头，训练已暂停");front=!front;preferences.edit().putBoolean("front",front).apply();
            if(engine==null){closeCamera();openCamera();}else{cameraMessage="摄像头已切换，点击继续";updateTraining();}
        });toolbar.addView(swap,new LinearLayout.LayoutParams(dp(48),dp(48)));top.addView(toolbar);space(top,12);
        LinearLayout counters=row();LinearLayout timerBox=column();timerBox.setPadding(dp(12),dp(8),dp(12),dp(8));timerBox.setBackground(bg(ORANGE,10));timerLabel=text("运动计时",11,WHITE,true);timerBox.addView(timerLabel);space(timerBox,4);timerText=text("00:00",34,WHITE,true);timerBox.addView(timerText);counters.addView(timerBox,new LinearLayout.LayoutParams(-2,-2));
        View spacer=new View(this);counters.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        LinearLayout countBox=column();countBox.setGravity(Gravity.RIGHT);countBox.setPadding(dp(16),dp(8),dp(16),dp(8));GradientDrawable countBg=bg(0xe620242d,10);countBg.setStroke(dp(2),WHITE);countBox.setBackground(countBg);countBox.addView(text("累计 / 个",11,WHITE,true));space(countBox,4);countText=text("0",44,WHITE,true);countBox.addView(countText);counters.addView(countBox);top.addView(counters);space(top,12);
        phaseText=text("准备入镜",14,WHITE,true);phaseText.setPadding(dp(12),dp(8),dp(12),dp(8));phaseText.setBackground(bg(0xb320242d,20));top.addView(phaseText);root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        centerText=text("",30,WHITE,true);centerText.setGravity(Gravity.CENTER);centerText.setPadding(dp(24),dp(8),dp(24),dp(8));FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);root.addView(centerText,center);
        LinearLayout bottom=column();bottom.setPadding(dp(16),dp(12),dp(16),dp(16));bottom.setBackground(bg(0xe6141820,20));
        feedbackText=text("正在加载本地模型…",14,WHITE,false);feedbackText.setGravity(Gravity.CENTER);feedbackText.setMinHeight(dp(48));feedbackText.setOnLongClickListener(v->{showDiagnostics();return true;});bottom.addView(feedbackText);space(bottom,10);
        LinearLayout controls=row();LinearLayout speech=column();speech.setGravity(Gravity.CENTER);speech.addView(new Icons(this,Icons.Kind.VOICE,WHITE,false),new LinearLayout.LayoutParams(dp(32),dp(32)));voiceText=text("语音开",12,WHITE,false);speech.addView(voiceText);speech.setContentDescription("切换语音报数");speech.setOnClickListener(v->{speaker.enable(!speaker.enabled());preferences.edit().putBoolean("voice",speaker.enabled()).apply();updateTraining();});controls.addView(speech,new LinearLayout.LayoutParams(dp(66),dp(60)));
        primaryButton=button("打开相机",ORANGE,this::primaryAction);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(56),1);pp.setMargins(dp(8),0,dp(8),0);controls.addView(primaryButton,pp);
        Button end=button("结束",0xff333a46,this::confirmEnd);end.setTextSize(14);controls.addView(end,new LinearLayout.LayoutParams(dp(68),dp(56)));bottom.addView(controls);space(bottom,10);
        TextView local=text("画面仅在本机处理 · 不保存视频",11,0xffb2b9c7,false);local.setGravity(Gravity.CENTER);bottom.addView(local);
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));setContentView(root);updateTraining();
    }
    private void primaryAction(){
        if(engine==null){
            if(camera==null){openCamera();return;}
            if(!detector.ready()){toast("请全身入镜并站稳片刻");return;}
            engine=new SessionEngine(selectedPlan,System.currentTimeMillis());engine.start(now());lastPhase=null;lastCountdown=-1;
            if(!checkpoint(true)){pauseTraining("本地保存失败，请重试后继续");return;}
            updateTraining();return;
        }
        if(engine.phase()==SessionEngine.Phase.PAUSED){
            if(resuming)return;resuming=true;cameraMessage="站稳后自动继续，请保持全身入镜";openCamera();updateTraining();
        }else pauseTraining("已暂停，点击继续恢复");
    }
    private void openCamera(){
        if(!hasCameraPermission()){resuming=false;requestPermissions(new String[]{Manifest.permission.CAMERA},41);return;}
        closeCamera();detector.reset();bodyLostAt=0;cameraOpenedAt=now();lastFrameAt=cameraOpenedAt;cameraMessage=resuming?"站稳后自动继续，请保持全身入镜":"请全身入镜，站稳约1秒";
        int generation=++cameraGeneration;
        int displayRotation=getWindowManager().getDefaultDisplay().getRotation()*90;
        camera=new CameraController(this,front,displayRotation,new CameraController.Listener(){
            public void frame(android.graphics.Bitmap bitmap,Pose pose,boolean mirrored){
                if(generation!=cameraGeneration||screen!=Screen.TRAINING||!visible)return;
                long time=now();lastFrameAt=time;preview.setFrame(bitmap,pose,mirrored);advance();if(screen!=Screen.TRAINING)return;
                if(engine!=null&&engine.phase()==SessionEngine.Phase.WORK&&time-pose.capturedAtMs>500){pauseTraining("图像处理延迟超过500毫秒，已暂停以避免漏计");return;}
                boolean allow=engine==null||engine.phase()==SessionEngine.Phase.WORK||engine.phase()==SessionEngine.Phase.COUNTDOWN||engine.phase()==SessionEngine.Phase.REST||resuming;
                boolean jump=allow&&detector.update(pose);
                if(resuming&&detector.ready()) {engine.resume(time);detector.discardCycle();resuming=false;lastPhase=engine.phase();checkpoint(true);speaker.say("继续训练");}
                if(engine!=null&&jump&&engine.jump(time,pose.capturedAtMs)){checkpoint(true);if(engine.total()%reportEvery==0)speaker.say(engine.total()+"个");}
                PoseQuality quality=PoseQuality.assess(pose);
                if(preferences.getBoolean("diagnostics",false))diagnostics.sample(pose,detector,engine==null?0:engine.phase().ordinal(),engine==null?0:engine.total());
                if(engine!=null&&engine.phase()==SessionEngine.Phase.WORK&&!quality.personVisible){
                    if(bodyLostAt==0)bodyLostAt=time;
                    if(time-bodyLostAt>=3000){pauseTraining("离开画面超过3秒，已自动暂停");return;}
                }else bodyLostAt=0;
                cameraMessage=recognitionMessage(quality);
                if(engine!=null&&engine.phase()==SessionEngine.Phase.REST)cameraMessage="组间休息中，跳跃不计入次数";
                updateTraining();
            }
            public void error(String message){if(generation!=cameraGeneration)return;pauseTraining(message);}
        });
        updateTraining();
    }
    private String recognitionMessage(PoseQuality quality){
        if(quality.reason==PoseQuality.NO_TORSO)return "未找到躯干：请正面对准镜头，让肩和髋进入画面，增加照明";
        if(quality.reason==PoseQuality.FEET_MISSING)return "已找到人物：请让双脚入镜、减少脚部遮挡，避免逆光";
        if(quality.reason==PoseQuality.TOO_SMALL)return "已找到人物：请稍靠近相机，让肩和双脚更清晰";
        if(quality.reason==PoseQuality.NOT_UPRIGHT)return "已找到人物：请直立，让相机能同时看到肩、髋和双脚";
        if(quality.reason==PoseQuality.CAMERA_MOVING||detector.status()==JumpDetector.Status.MOVING)return "检测到画面或位置移动：请固定手机并回到原位站稳";
        if(detector.status()==JumpDetector.Status.OCCLUDED)return "短暂遮挡：本次未完成跳跃不计数，双脚落地后继续";
        return detector.ready()?"识别就绪，保持双脚同步轻跳":"已找到人物：请双脚落地站稳约1秒，正在校准";
    }
    private void closeCamera(){cameraGeneration++;if(camera!=null){camera.close();camera=null;}detector.reset();if(preview!=null)preview.clear();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
    private void pauseTraining(String reason){
        if(engine!=null&&engine.phase()!=SessionEngine.Phase.FINISHED){engine.pause(now());checkpoint(true);}
        resuming=false;closeCamera();speaker.stop();cameraMessage=reason;updateTraining();
    }
    private boolean checkpoint(boolean force){
        if(engine==null)return true;long time=now();if(!force&&time-lastCheckpoint<1000)return true;
        try{store.checkpoint(engine);lastCheckpoint=time;return true;}
        catch(IOException error){cameraMessage="本地写入失败，尚未保存。请保留本次训练后重试。";if(force)toast(cameraMessage);return false;}
    }
    private void advance(){
        if(screen==Screen.TRAINING&&camera!=null&&now()-lastFrameAt>2500&&now()-cameraOpenedAt>2500){pauseTraining("相机画面已中断，请重试打开相机");return;}
        if(engine==null||screen!=Screen.TRAINING){if(screen==Screen.TRAINING)updateTraining();return;}
        long time=now();engine.tick(time);SessionEngine.Phase current=engine.phase();
        if(current==SessionEngine.Phase.FINISHED){finishAndSave();return;}
        if(current!=lastPhase){if(current==SessionEngine.Phase.WORK)detector.discardCycle();else detector.reset();lastPhase=current;lastCountdown=-1;checkpoint(true);
            if(current==SessionEngine.Phase.REST)speaker.say("本组结束，休息"+selectedPlan.restSeconds+"秒");
            if(current==SessionEngine.Phase.WORK)speaker.say("第"+engine.round()+"组，开始");
        }
        if(current==SessionEngine.Phase.COUNTDOWN){int remaining=(int)((engine.remainingMs()+999)/1000);if(remaining!=lastCountdown){lastCountdown=remaining;speaker.say(Integer.toString(remaining));}}
        if(camera!=null&&time-lastFrameAt>2500&&time-cameraOpenedAt>2500){pauseTraining("相机画面已中断，训练已暂停");return;}
        if(current!=SessionEngine.Phase.PAUSED&&!checkpoint(false)){pauseTraining("本地保存异常，训练已暂停，请重试");return;}
        updateTraining();
    }
    private void updateTraining(){
        if(screen!=Screen.TRAINING||timerText==null)return;
        SessionEngine.Phase phase=engine==null?SessionEngine.Phase.READY:engine.phase();
        countText.setText(engine==null?"0":Integer.toString(engine.total()));
        long shown=engine==null?0:phase==SessionEngine.Phase.REST||(phase==SessionEngine.Phase.PAUSED&&engine.pausedFrom()==SessionEngine.Phase.REST)?engine.remainingMs():engine.workMs();
        timerText.setText(clock(shown));
        timerLabel.setText(phase==SessionEngine.Phase.REST||(phase==SessionEngine.Phase.PAUSED&&engine.pausedFrom()==SessionEngine.Phase.REST)?"休息倒计时":"运动计时");
        String stage=phase==SessionEngine.Phase.READY?"准备入镜":phase==SessionEngine.Phase.COUNTDOWN?"准备开始":phase==SessionEngine.Phase.REST?"休息 · 不计数":phase==SessionEngine.Phase.PAUSED?"已暂停":"正在运动";
        if(selectedPlan.mode==Plan.Mode.INTERVAL&&engine!=null)stage+="  "+engine.round()+" / "+selectedPlan.rounds+"组";
        if(engine!=null&&phase==SessionEngine.Phase.WORK&&selectedPlan.mode!=Plan.Mode.FREE)stage+="  剩余 "+clockCeil(engine.remainingMs());
        phaseText.setText(stage);
        centerText.setText(phase==SessionEngine.Phase.COUNTDOWN?Long.toString((engine.remainingMs()+999)/1000):phase==SessionEngine.Phase.REST?"休息一下\n"+clockCeil(engine.remainingMs()):phase==SessionEngine.Phase.PAUSED?(resuming?"全身入镜，站稳后继续":"训练已暂停"):phase==SessionEngine.Phase.READY&&camera==null?"准备好，轻轻一跃":"");
        centerText.setTextSize(phase==SessionEngine.Phase.COUNTDOWN?80:28);
        feedbackText.setText(cameraMessage+(phase==SessionEngine.Phase.WORK&&!detector.ready()?"\n未稳定识别时不计数，计时继续":""));
        voiceText.setText(!speaker.enabled()?"语音关":speaker.ready()?"语音开":"语音不可用");
        primaryButton.setEnabled(!resuming);
        primaryButton.setAlpha(resuming?.6f:1);
        primaryButton.setText(phase==SessionEngine.Phase.READY?(camera==null?"打开相机":detector.ready()?"开始训练":"等待计数就绪"):phase==SessionEngine.Phase.PAUSED?(resuming?"正在校准…":"继续训练"):"暂停");
        if(camera!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void confirmEnd(){
        if(engine==null){closeCamera();showConfig();return;}
        pauseTraining("已暂停，可以继续或结束保存");
        new AlertDialog.Builder(this).setTitle("结束本次训练？").setMessage("当前 "+engine.total()+" 个，运动 "+clock(engine.workMs())+"。结束后保存到本地记录。")
                .setNegativeButton("保持暂停",null).setPositiveButton("结束并保存",(dialog,which)->{engine.finish(now());finishAndSave();}).show();
    }
    private void finishAndSave(){
        closeCamera();speaker.stop();finalSaved=false;
        try {store.commit(engine);finalSaved=true;speaker.say("训练结束，共"+engine.total()+"个");}
        catch(IOException error){cameraMessage="本地保存失败，请点击重试。不要卸载或清除应用数据。";}
        showResult();
    }
    private void showResult(){
        base(Screen.RESULT,finalSaved?"本次训练已保存":"训练等待保存",false);
        LinearLayout result=card();result.setGravity(Gravity.CENTER);result.addView(new Icons(this,Icons.Kind.ROPE,ORANGE,true),new LinearLayout.LayoutParams(dp(60),dp(60)));space(result,20);
        result.addView(text(Integer.toString(engine.total()),64,ORANGE,true));space(result,8);result.addView(text("累计跳跃 / 个",15,GRAY,false));space(result,25);
        LinearLayout stats=row();stats.addView(stat(clock(engine.workMs()),"运动时长",INK),new LinearLayout.LayoutParams(0,-2,1));stats.addView(stat(clock(engine.restMs()),"休息时长",INK),new LinearLayout.LayoutParams(0,-2,1));result.addView(stats);space(result,20);
        result.addView(text(engine.plan.title()+" · "+(engine.completed()?"计划完成":"手动结束"),14,GRAY,false));cardAdd(content,result);
        if(engine.plan.mode==Plan.Mode.INTERVAL){int[] counts=engine.roundCounts();long[] work=engine.roundWork();for(int i=0;i<counts.length;i++){LinearLayout c=card();c.addView(text("第 "+(i+1)+" 组     "+counts[i]+" 个  ·  "+clock(work[i]),16,INK,false));cardAdd(content,c);}}
        if(!finalSaved){content.addView(text(cameraMessage,15,ORANGE,true));space(content,15);content.addView(button("重试保存",ORANGE,this::finishAndSave),new LinearLayout.LayoutParams(-1,dp(56)));}
        else {content.addView(button("完成，返回训练",ORANGE,this::showHome),new LinearLayout.LayoutParams(-1,dp(56)));space(content,12);content.addView(button("查看本地记录",WHITE,()->{engine=null;showHistory(0);}),new LinearLayout.LayoutParams(-1,dp(52)));}
    }

    private void showHistory(int filter){showHistory(filter,100);}
    private void showHistory(int filter,int limit){
        base(Screen.HISTORY,"训练记录",true);LinearLayout strip=row();String[] names={"全部","今天","昨天","近7天"};
        for(int i=0;i<names.length;i++){int n=i;Button b=button(names[i],filter==i?ORANGE:WHITE,()->showHistory(n));if(filter!=i)b.setTextColor(GRAY);b.setTextSize(13);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(2),0,dp(2),0);strip.addView(b,p);}cardAdd(content,strip);
        List<WorkoutRecord> records=new ArrayList<>();long today=dayStart(System.currentTimeMillis());Calendar yesterday=Calendar.getInstance();yesterday.setTimeInMillis(today);yesterday.add(Calendar.DAY_OF_YEAR,-1);Calendar week=Calendar.getInstance();week.setTimeInMillis(today);week.add(Calendar.DAY_OF_YEAR,-6);
        for(WorkoutRecord r:store.history())if(filter==0||(filter==1&&r.startedAt>=today)||(filter==2&&r.startedAt>=yesterday.getTimeInMillis()&&r.startedAt<today)||(filter==3&&r.startedAt>=week.getTimeInMillis()))records.add(r);
        long jumps=0,time=0;for(WorkoutRecord r:records){jumps+=r.total;time+=r.workMs;}
        LinearLayout summary=card();summary.addView(text(records.size()+" 次训练  ·  "+jumps+" 个跳跃",20,INK,true));space(summary,9);summary.addView(text("运动合计 "+clock(time)+"  ·  数据仅保存在本机",13,GRAY,false));cardAdd(content,summary);
        if(records.isEmpty())empty("这段时间还没有记录","完成训练后会自动保存在这里");
        String day="";int rendered=0;
        for(WorkoutRecord r:records){if(rendered++>=limit)break;String next=new SimpleDateFormat("yyyy年M月d日",Locale.CHINA).format(new Date(r.startedAt));if(!day.equals(next)){section(next);day=next;}cardAdd(content,recordRow(r));}
        if(records.size()>limit)content.addView(button("再显示100次训练",WHITE,()->showHistory(filter,limit+100)),new LinearLayout.LayoutParams(-1,dp(52)));
    }
    private View recordRow(WorkoutRecord record){
        LinearLayout c=card();LinearLayout r=row();r.addView(new Icons(this,record.plan.mode==Plan.Mode.INTERVAL?Icons.Kind.REST:record.plan.mode==Plan.Mode.TIMED?Icons.Kind.TIMER:Icons.Kind.ROPE,record.plan.mode==Plan.Mode.INTERVAL?PURPLE:BLUE,true),new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout info=column();info.addView(text(record.plan.title(),16,INK,true));space(info,7);info.addView(text(new SimpleDateFormat("M/d HH:mm",Locale.CHINA).format(new Date(record.startedAt))+"  ·  "+clock(record.workMs),12,GRAY,false));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(12);r.addView(info,ip);r.addView(text(record.total+" 个",22,ORANGE,true));c.addView(r);c.setOnClickListener(v->recordDetail(record));return c;
    }
    private void recordDetail(WorkoutRecord r){
        StringBuilder body=new StringBuilder(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(r.startedAt)));
        body.append("\n\n跳跃：").append(r.total).append(" 个\n运动：").append(clock(r.workMs)).append("\n休息：").append(clock(r.restMs)).append("\n状态：").append(r.completed?"计划完成":"手动结束");
        if(r.plan.mode==Plan.Mode.INTERVAL)for(int i=0;i<r.counts.length;i++)body.append("\n第 ").append(i+1).append(" 组：").append(r.counts[i]).append(" 个 / ").append(clock(r.roundWork[i]));
        new AlertDialog.Builder(this).setTitle(r.plan.title()).setMessage(body.toString()).setPositiveButton("知道了",null).show();
    }
    private void showSettings(){
        LinearLayout box=column();box.setPadding(dp(22),dp(10),dp(22),dp(14));CheckBox voice=new CheckBox(this);voice.setText("启用离线语音报数");voice.setChecked(speaker.enabled());voice.setOnCheckedChangeListener((b,checked)->{speaker.enable(checked);preferences.edit().putBoolean("voice",checked).apply();});box.addView(voice);
        TextView status=text(speaker.status()+"。不会自动下载语音包。",13,GRAY,false);box.addView(status);space(box,16);box.addView(text("报数间隔",15,INK,true));
        RadioGroup group=new RadioGroup(this);for(int value:new int[]{1,10,20}){RadioButton radio=new RadioButton(this);radio.setId(value);radio.setText(value==1?"每个报数（可能跟不上较快节奏）":"每"+value+"个报数");group.addView(radio);}group.check(reportEvery);group.setOnCheckedChangeListener((g,id)->{reportEvery=id;preferences.edit().putInt("reportEvery",id).apply();});box.addView(group);space(box,15);
        CheckBox diagnostic=new CheckBox(this);diagnostic.setText("本地数值诊断（仅内存，最多1分钟）");diagnostic.setChecked(preferences.getBoolean("diagnostics",false));diagnostic.setOnCheckedChangeListener((b,checked)->{preferences.edit().putBoolean("diagnostics",checked).apply();diagnostics.clear();});box.addView(diagnostic);
        Button trace=button("查看数值诊断",BG,this::showDiagnostics);trace.setTextColor(INK);box.addView(trace);space(box,12);
        Button about=button("隐私说明与开源许可",BG,this::showAbout);about.setTextColor(INK);box.addView(about);space(box,12);
        Button clear=button("清除本地训练历史",BG,()->new AlertDialog.Builder(this).setTitle("清除全部历史？").setMessage("此操作仅删除本机已完成的训练记录，删除后无法恢复。")
                .setNegativeButton("取消",null).setPositiveButton("确认清除",(d,w)->{try{store.clearHistory();toast("本地训练历史已清除");showHome();}catch(IOException e){toast("清除失败，原记录已保留");}}).show());clear.setTextColor(ORANGE);box.addView(clear);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);new AlertDialog.Builder(this).setTitle("训练设置").setView(scroll).setPositiveButton("完成",null).show();
    }
    private void showDiagnostics(){
        String data=diagnostics.csv();ScrollView scroll=new ScrollView(this);TextView view=text("只含肩、髋、踝分数、人物获取和计数就绪标记、相对时间及图像尺寸等数值。不会保存图像或视频，不自动上传。关闭诊断或退出进程会清空。\nstatus: 0未找到 1校准 2跟踪 3遮挡 4移动\nreason: 0正常 1躯干缺失 2脚部缺失 3人物过小 4非直立 5相机移动\n\n"+data,11,INK,false);view.setPadding(dp(18),dp(12),dp(18),dp(12));view.setTextIsSelectable(true);scroll.addView(view);
        new AlertDialog.Builder(this).setTitle("本地数值诊断 · "+diagnostics.size()+"帧").setView(scroll).setNegativeButton("关闭",null).setPositiveButton("复制数值",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("轻跃数值诊断",data));toast("数值已复制，由你选择是否分享");}).show();
    }
    private void showAbout(){
        String license="";try(InputStream stream=getAssets().open("LICENSE-2.0.txt");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=stream.read(b))>=0)out.write(b,0,n);license=out.toString("UTF-8");}catch(IOException ignored){}
        ScrollView scroll=new ScrollView(this);TextView body=text("轻跃跳绳 0.1.2 · 开源 MVP\n\n只申请相机权限。不含广告、登录、社交、会员或分析追踪；不申请网络、录音、位置和存储权限。画面仅用于本机姿态识别，不上传、不写入录像；退出或暂停释放相机。训练计数和时长保存在应用私有目录，禁用系统备份，卸载或清除数据会删除记录。语音仅使用系统已安装的离线中文声音。\n\n算法识别双脚的起落，不能证明绳圈通过，也不适用于交替脚或双摇验证。请固定手机，确保光线与单人全身入镜。计数准确率尚未经真人测试。\n\n原创应用代码和图标：Apache-2.0。TensorFlow Lite 与 MoveNet：Apache-2.0，Google/TensorFlow Authors。UI研究参考天天跳绳的小米官方截图，未使用其Logo或素材。完整依赖与模型来源见源码 THIRD_PARTY_NOTICES.md。\n\n此MVP使用公开的AOSP测试签名，只用于测试验收；正式分发需使用所有者提供的签名。\n\n"+license,13,INK,false);body.setPadding(dp(22),dp(12),dp(22),dp(18));scroll.addView(body);new AlertDialog.Builder(this).setTitle("隐私与开源").setView(scroll).setPositiveButton("关闭",null).show();
    }
    private long dayStart(long epoch){Calendar c=Calendar.getInstance();c.setTimeInMillis(epoch);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    private boolean sameDay(long a,long b){return dayStart(a)==dayStart(b);}
    private String clock(long ms){long sec=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d",sec/60,sec%60);}
    private String clockCeil(long ms){return clock(((ms+999)/1000)*1000);}
    private void goBack(){
        if(screen==Screen.TRAINING){confirmEnd();return;}
        if(screen==Screen.RESULT&&!finalSaved){toast("请先重试保存，以免丢失本次训练");return;}
        showHome();
    }
    @Override public void onBackPressed(){if(screen==Screen.HOME)super.onBackPressed();else goBack();}
}
