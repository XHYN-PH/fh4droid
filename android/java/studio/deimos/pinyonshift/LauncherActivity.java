package studio.deimos.pinyonshift;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import org.json.JSONObject;

public final class LauncherActivity extends Activity implements FestivalUi.PanelHost {
    private final ArrayDeque<PanelPage> panels=new ArrayDeque<>();
    private FrameLayout rightPane;private float builtScale;private boolean sideBySide;
    private static final class PanelPage{final String title;final View body;PanelPage(String title,View body){this.title=title;this.body=body;}}
    private static final int ISO=310,SAVE_OUT=311,SAVE_IN=312,DRIVER_ZIP=313,PROBE=314;
    private TextView title,summary,note,versionFooter;private Button play;private boolean busy;
    private String pendingDriver;private boolean playAfterProbe;private AlertDialog progress;private FileOperation operation;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);UiPreferences.load(this);if(state!=null){pendingDriver=state.getString("driver");playAfterProbe=state.getBoolean("play_after_probe");}
        build();FestivalUi.immersive(this);
        new Thread(()->{try{DriverStore.installBundled(this);}catch(IOException ignored){}},"BundledDrivers").start();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("driver",pendingDriver);state.putBoolean("play_after_probe",playAfterProbe);}
    @Override protected void onResume(){super.onResume();UiPreferences.load(this);if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();refresh();recovery();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);build();refresh();FestivalUi.immersive(this);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus){UiPreferences.load(this);if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();FestivalUi.immersive(this);refresh();if(rightPane!=null)FestivalUi.refreshTree(rightPane);}}
    private void build(){
        builtScale=FestivalUi.scale(this);
        FrameLayout canvas=new FrameLayout(this);canvas.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
        boolean wide=getResources().getConfiguration().screenWidthDp>=650&&getResources().getConfiguration().fontScale<1.5f;
        sideBySide=wide;int pad=FestivalUi.dp(this,18);LinearLayout row=new LinearLayout(this);row.setPadding(pad,pad,pad,FestivalUi.dp(this,64));canvas.addView(row,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout menu=FestivalUi.column(this);menu.setGravity(Gravity.BOTTOM);menu.setPadding(0,FestivalUi.dp(this,12),wide?pad:0,0);scroll.addView(menu);row.addView(scroll,new LinearLayout.LayoutParams(0,-1,wide?.43f:1));
        title=FestivalUi.text(this,"Ready to drive",13,FestivalUi.MUTED);menu.addView(title);
        ImageView logo=new ImageView(this);logo.setContentDescription("FH4Droid logo");logo.setScaleType(ImageView.ScaleType.FIT_START);logo.setAdjustViewBounds(true);
        try(InputStream in=getAssets().open("menu-logo.png")){logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));}catch(IOException error){logo.setContentDescription("FH4Droid");}
        logo.setBackgroundColor(android.graphics.Color.TRANSPARENT);menu.addView(logo,new LinearLayout.LayoutParams(-1,FestivalUi.dp(this,getResources().getConfiguration().screenHeightDp<380?70:96)));
        summary=FestivalUi.text(this,"",13,FestivalUi.MUTED);summary.setPadding(0,FestivalUi.dp(this,4),0,FestivalUi.dp(this,6));menu.addView(summary);
        note=FestivalUi.text(this,"",12,0xffffbca5);note.setPadding(0,0,0,FestivalUi.dp(this,8));menu.addView(note);
        play=FestivalUi.button(this,"Play",true);play.setOnClickListener(v->play(false));menu.addView(play,new LinearLayout.LayoutParams(-1,-2));
        FestivalUi.action(this,menu,"Settings",()->{if(GameFiles.running(this)){toast("Open settings with Android Back while playing.");return;}openRoot();MobileSettings.show(this,null,this::controls,this::drivers);});
        FestivalUi.action(this,menu,"Controls",this::controls);
        HorizontalScrollView links=new HorizontalScrollView(this);links.setHorizontalScrollBarEnabled(false);LinearLayout shortcuts=new LinearLayout(this);links.addView(shortcuts);menu.addView(links);
        smallLink(shortcuts,"Game files",()->{openRoot();files();});smallLink(shortcuts,"Saves",()->{openRoot();saves();});smallLink(shortcuts,"More",()->{openRoot();more();});
        rightPane=new FrameLayout(this);if(wide){LinearLayout.LayoutParams pane=new LinearLayout.LayoutParams(0,-1,.57f);pane.topMargin=FestivalUi.dp(this,40);row.addView(rightPane,pane);}else{FrameLayout.LayoutParams pane=new FrameLayout.LayoutParams(-1,-1);pane.setMargins(pad,FestivalUi.dp(this,54),pad,FestivalUi.dp(this,54));canvas.addView(rightPane,pane);}
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(pad,0,pad,0);
        HorizontalScrollView socials=new HorizontalScrollView(this);socials.setHorizontalScrollBarEnabled(false);
        LinearLayout social=new LinearLayout(this);social.setGravity(Gravity.CENTER_VERTICAL);socials.addView(social);
        smallLink(social,"YouTube",()->external("https://youtube.com/@xhyn_ph"));footerSeparator(social);
        smallLink(social,"Telegram",()->external("https://t.me/xhynports"));footerSeparator(social);
        smallLink(social,"GitHub",()->external("https://github.com/XHYN-PH/fh4droid"));
        footer.addView(socials,new LinearLayout.LayoutParams(0,-2,.62f));
        versionFooter=FestivalUi.text(this,installedVersion(),12,FestivalUi.MUTED);versionFooter.setTextSize(12);
        versionFooter.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);versionFooter.setMaxLines(3);versionFooter.setPadding(FestivalUi.dp(this,8),0,0,0);
        footer.addView(versionFooter,new LinearLayout.LayoutParams(0,-2,.38f));
        FrameLayout.LayoutParams footerParams=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);footerParams.bottomMargin=FestivalUi.dp(this,8);canvas.addView(footer,footerParams);
        final android.graphics.Insets[] safeInsets={android.graphics.Insets.NONE};
        Runnable fitFooter=()->{
            android.graphics.Insets safe=safeInsets[0];int bottom=Math.max(FestivalUi.dp(this,64),footer.getHeight()+FestivalUi.dp(this,16))+safe.bottom;
            row.setPadding(pad+safe.left,pad+safe.top,pad+safe.right,bottom);
            footer.setPadding(pad+safe.left,0,pad+safe.right,0);footerParams.bottomMargin=FestivalUi.dp(this,8)+safe.bottom;footer.setLayoutParams(footerParams);
            if(!wide){FrameLayout.LayoutParams pane=(FrameLayout.LayoutParams)rightPane.getLayoutParams();pane.setMargins(pad+safe.left,FestivalUi.dp(this,54)+safe.top,pad+safe.right,bottom);rightPane.setLayoutParams(pane);}
        };
        footer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(l!=ol||t!=ot||r!=or||b!=ob)fitFooter.run();});
        canvas.setOnApplyWindowInsetsListener((v,insets)->{safeInsets[0]=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());fitFooter.run();return insets;});
        setContentView(canvas);renderPanel();FestivalUi.enter(menu);
    }
    private void openRoot(){panels.clear();renderPanel();}
    @Override public void showPanel(String title,View body){if(rightPane!=null)FestivalUi.rememberTree(rightPane);panels.push(new PanelPage(title,body));renderPanel();}
    @Override public void replacePanel(String title,View body){if(!panels.isEmpty())panels.pop();showPanel(title,body);}
    @Override public void backPanel(){if(!panels.isEmpty())panels.pop();if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();else renderPanel();}
    private void renderPanel(){if(rightPane==null)return;FestivalUi.rememberTree(rightPane);rightPane.removeAllViews();rightPane.setVisibility(panels.isEmpty()&&!sideBySide?View.GONE:View.VISIBLE);if(!panels.isEmpty()){PanelPage page=panels.peek();View panel=FestivalUi.panel(this,page.title,page.body,this::backPanel);rightPane.addView(panel,new FrameLayout.LayoutParams(-1,-1));FestivalUi.slideIn(panel);}}
    @Override public void onBackPressed(){if(!panels.isEmpty())backPanel();else super.onBackPressed();}
    private TextView smallLink(LinearLayout row,String label,Runnable action){
        TextView link=FestivalUi.text(this,label,13,FestivalUi.MUTED);link.setTextSize(13);link.setGravity(Gravity.CENTER);
        link.setMinHeight(FestivalUi.dp(this,48));link.setPadding(FestivalUi.dp(this,10),0,FestivalUi.dp(this,10),0);link.setFocusable(true);
        android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},FestivalUi.surface(this,0x33ffffff,8));
        background.addState(new int[]{},FestivalUi.surface(this,android.graphics.Color.TRANSPARENT,8));
        link.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),background,FestivalUi.surface(this,android.graphics.Color.WHITE,8)));
        link.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName("android.widget.Button");}});
        row.addView(link,new LinearLayout.LayoutParams(-2,-2));link.setOnClickListener(v->action.run());return link;
    }
    private void footerSeparator(LinearLayout row){TextView separator=FestivalUi.text(this,"·",13,FestivalUi.MUTED);separator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(separator);}
    private String installedVersion(){
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);return "v"+(info.versionName==null?"Unknown":info.versionName)+" · build "+info.getLongVersionCode();}
        catch(android.content.pm.PackageManager.NameNotFoundException error){return "Version unavailable";}
    }
    private void external(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException error){toast("No browser is available.");}}
    private void more(){LinearLayout body=FestivalUi.column(this);FestivalUi.action(this,body,"Graphics drivers",this::drivers);FestivalUi.action(this,body,"Achievements",()->AchievementsPage.show(this,null));FestivalUi.action(this,body,"Cheats · separate profile",()->TrainerPage.show(this,null));ProblemReports.action(this,body,"Report a problem");FestivalUi.action(this,body,"About",this::about);FestivalUi.present(this,"More",body);}
    private void about(){LinearLayout body=FestivalUi.column(this);body.addView(FestivalUi.text(this,installedVersion(),16,FestivalUi.ORANGE));body.addView(FestivalUi.text(this,"FH4Droid is an unofficial Android fan port. Supply your own supported Forza Horizon game files. Local achievements do not synchronize with Xbox Live.",14,FestivalUi.TEXT));FestivalUi.action(this,body,"Control artwork credits",this::controlArtworkCredits);FestivalUi.present(this,"About FH4Droid",body);}
    private void controlArtworkCredits(){
        LinearLayout body=FestivalUi.column(this);
        FestivalUi.action(this,body,"Lucide icon library",()->external("https://lucide.dev/"));
        try(InputStream in=getResources().openRawResource(getResources().getIdentifier("control_artwork_license","raw",getPackageName()));ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];for(int n;(n=in.read(buffer))!=-1;)bytes.write(buffer,0,n);
            body.addView(FestivalUi.text(this,new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),13,FestivalUi.MUTED));
        }catch(Exception error){body.addView(FestivalUi.text(this,"Lucide icons are used under the ISC license, with applicable Feather icons under the MIT license.",13,FestivalUi.MUTED));}
        FestivalUi.present(this,"Control artwork",body);
    }
    void startDiagnosticRun(){play(true);}
    private void navButton(LinearLayout row,String text,Runnable action){
        Button b=FestivalUi.button(this,text,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);if(row.getChildCount()>0)p.leftMargin=FestivalUi.dp(this,8);row.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private void link(GridLayout row,String label,Runnable action){
        Button button=FestivalUi.button(this,label,false);button.setTextSize(14);
        GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=-2;p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        p.setMargins(FestivalUi.dp(this,4),FestivalUi.dp(this,8),FestivalUi.dp(this,4),0);row.addView(button,p);button.setOnClickListener(v->action.run());
    }
    private void refresh(){
        if(title==null)return;boolean ready=GameFiles.ready(this);
        title.setText(ready?"Ready to drive":"Your next drive awaits");play.setText(ready?"Play":"Set up game");
        summary.setText(MobileSettings.summary(this));if(versionFooter!=null)versionFooter.setText(installedVersion()+"\n"+(GameFiles.running(this)?"Game running":ready?"Game files ready":"Setup required"));note.setText(ready?"Your own Forza Horizon Xbox 360 game files.":"Add your Forza Horizon Xbox 360 disc image or extracted folder.");
        if(GameSession.interrupted(this)>0)note.setText("The last game stopped unexpectedly. Try the system driver or share a report.");
    }
    private void recovery(){
        long interrupted=GameSession.interrupted(this);
        if(interrupted==0||busy||getSharedPreferences("mobile",0).getLong("recovery_seen",0)==interrupted)return;
        getSharedPreferences("mobile",0).edit().putLong("recovery_seen",interrupted).apply();
        new AlertDialog.Builder(this).setTitle("The last game stopped unexpectedly")
            .setMessage("This may be a driver problem, a game crash or Android freeing memory. A report can help identify the cause.")
            .setNegativeButton("Back",null).setNeutralButton("Share report",(d,w)->ProblemReports.share(this))
            .setPositiveButton("Try system driver",(d,w)->{try{MobileConfig.put(this,"android_gpu_driver","\"\"");play(false);}
                catch(IOException e){error(e.getMessage());}}).show();
    }
    private boolean canManage(){if(busy)return false;if(GameFiles.running(this)){toast("Quit the game before changing files, saves or drivers.");return false;}return true;}
    private void work(String message,Task task,Runnable success){
        if(busy)return;busy=true;LinearLayout p=FestivalUi.column(this);TextView detail=FestivalUi.text(this,message,16,FestivalUi.TEXT);p.addView(detail);
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setIndeterminate(true);p.addView(bar);
        operation=new FileOperation((name,done,total)->runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
            detail.setText(name+"\n"+(done/(1024*1024))+" / "+Math.max(1,total/(1024*1024))+" MB");bar.setIndeterminate(total<=0);if(total>0)bar.setProgress((int)Math.min(1000,done*1000/total));}));
        final FileOperation current=operation;
        progress=new AlertDialog.Builder(this).setTitle("Please wait").setView(p).setNegativeButton("Cancel",null).create();progress.setCancelable(false);progress.show();
        progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{current.cancel();v.setEnabled(false);detail.setText("Stopping after the current read. Completed files are kept.");});
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(()->{try(FileOperation scope=current.attach()){task.run();runOnUiThread(()->{endWork();if(isFinishing()||isDestroyed())return;success.run();refresh();});}
            catch(Exception e){runOnUiThread(()->{endWork();if(isFinishing()||isDestroyed())return;if(current.isCancelled()||e instanceof InterruptedIOException)toast(FileOperation.cancelMessage());else error(e.getMessage());});}},"FH4DroidFiles").start();
    }
    private void endWork(){busy=false;operation=null;if(!isDestroyed())getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(progress!=null){progress.dismiss();progress=null;}}
    interface Task{void run()throws Exception;}
    private void play(boolean logging){
        if(busy)return;if(!GameFiles.ready(this)){files();return;}
        if(GameFiles.running(this)){startGame(logging);return;}
        getSharedPreferences("mobile",0).edit().putBoolean("next_debug",logging).apply();
        work("Checking the game executables…",()->GameFiles.verify(this),()->probe(MobileConfig.get(this,"android_gpu_driver","auto"),true));
    }
    private void startGame(boolean logging){
        Intent i=new Intent(this,PinyonShiftActivity.class);i.putExtra("debug_report",logging);
        String effective=getSharedPreferences("mobile",0).getString("verified_driver","");
        i.putExtra("verified_driver",effective);
        i.putExtra("env.PINYON_SHIFT_ANDROID_LAUNCHER","1");startActivity(i);
    }
    private void files(){
        LinearLayout p=FestivalUi.column(this);p.addView(FestivalUi.text(this,"Use your own Xbox 360 copy of Forza Horizon (2012). This build uses base-game executables; TU4 saves require a separate TU4 build.",14,FestivalUi.MUTED));
        FestivalUi.action(this,p,"Choose game ISO",()->{if(canManage())pick(ISO,"*/*",false);});
        FestivalUi.action(this,p,"Choose extracted game folder",()->{if(canManage())startActivity(new Intent(this,SetupActivity.class).putExtra("setup",true).putExtra("choose_folder",true));});
        FestivalUi.action(this,p,"Check installed game files",()->{if(canManage())work("Checking all game files. Large games can take several minutes…",()->GameFiles.verifyComplete(this),()->toast("All supported game files verified."));});
        FestivalUi.present(this,"Game files",p);
    }
    private void controls(){startActivity(new Intent(this,TouchEditorActivity.class));}
    private void saves(){
        LinearLayout p=FestivalUi.column(this);p.addView(FestivalUi.text(this,"Backups cover the normal base-game profile. The trainer uses a separate modded profile, which is not included here. Quit the game before importing or restoring.",14,FestivalUi.MUTED));
        FestivalUi.action(this,p,"Export saves",()->{if(canManage())pick(SAVE_OUT,"application/zip",true);});
        FestivalUi.action(this,p,"Import saves",()->{if(canManage())new AlertDialog.Builder(this).setTitle("Import saves?").setMessage("Current saves are kept as a local backup before replacement.")
            .setNegativeButton("Cancel",null).setPositiveButton("Choose ZIP",(d,w)->pick(SAVE_IN,"application/zip",false)).show();});
        FestivalUi.action(this,p,"Restore previous saves",()->{if(canManage())new AlertDialog.Builder(this).setTitle("Restore previous saves?").setMessage("Your current saves are also kept before the restore.")
            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->work("Restoring saves…",()->SaveArchive.restore(new File(MobileConfig.base(this),"state")),()->toast("Previous saves restored."))).show();});
        FestivalUi.present(this,"Saves",p);
    }
    void openDrivers(){drivers();}
    private void drivers(){
        FestivalUi.present(this,"Graphics drivers",driverPage());
    }
    private View driverPage(){
        LinearLayout body=FestivalUi.column(this);body.addView(FestivalUi.text(this,"Test a driver before selecting it. Quit the game before importing or removing drivers.",13,FestivalUi.MUTED));
        String current=MobileConfig.get(this,"android_gpu_driver","auto");
        for(String choice:DriverStore.choices(this)){
            LinearLayout row=new LinearLayout(this);body.addView(row);
            Button choose=FestivalUi.button(this,(current.equals(choice)?"Selected · ":"")+DriverStore.displayName(this,choice),false);row.addView(choose,new LinearLayout.LayoutParams(0,-2,1));choose.setOnClickListener(v->{if(canManage())probe(choice,false);});
            if(choice.startsWith("imported-")){
                Button trash=FestivalUi.button(this,"Delete",false);row.addView(trash);trash.setContentDescription("Delete "+DriverStore.displayName(this,choice));
                trash.setOnClickListener(v->{if(!canManage())return;new AlertDialog.Builder(this).setTitle("Delete imported driver?").setMessage(DriverStore.displayName(this,choice)+(current.equals(choice)?" will be removed. The system driver will be selected.":" will be removed. Your selected driver is kept."))
                    .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->work("Removing driver…",()->DriverStore.removeImported(this,choice),()->{replacePanel("Graphics drivers",driverPage());refresh();})).show();});
            }
        }
        FestivalUi.action(this,body,"Import driver ZIP",()->{if(canManage())pick(DRIVER_ZIP,"application/zip",false);});
        DriverStore.addReport(this,body,AdrenoSettings.reportDetails(this));return body;
    }
    private void reports(){ProblemReports.share(this);}
    private void probe(String choice,boolean launch){
        pendingDriver=choice;playAfterProbe=launch;startActivityForResult(new Intent(this,DriverProbeActivity.class).putExtra("driver",choice),PROBE);
    }
    private void pick(int request,String mime,boolean create){
        Intent i=new Intent(create?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);
        if(create)i.putExtra(Intent.EXTRA_TITLE,"FH4Droid-saves.zip");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,request);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PROBE){probeResult(result,data);return;}
        if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(!canManage())return;
        if(request==ISO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException e){error("This file provider cannot keep access. Choose an ISO in local storage.");return;}
            work("Inspecting your disc and verifying the game…",()->installIso(uri),()->toast("Disc image ready."));}
        else if(request==SAVE_OUT)work("Exporting saves…",()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Cannot create backup.");SaveArchive.export(new File(MobileConfig.base(this),"state/user"),out);}},()->toast("Saves exported."));
        else if(request==SAVE_IN)work("Importing saves…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open backup.");SaveArchive.importInto(new File(MobileConfig.base(this),"state"),in);}},()->toast("Saves imported."));
        else if(request==DRIVER_ZIP)work("Importing driver…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open driver ZIP.");pendingDriver=DriverStore.importZip(this,in);}},()->probe(pendingDriver,false));
    }
    private void probeResult(int result,Intent data){
        boolean launch=playAfterProbe;playAfterProbe=false;
        String report=result==RESULT_OK&&data!=null?data.getStringExtra("report"):null;
        try{AdrenoSettings.record(this,pendingDriver,report==null?"{}":report);JSONObject json=new JSONObject(report==null?"{}":report);getSharedPreferences("mobile",0).edit().putString("last_probe",report).apply();
            if(!json.optBoolean("ok",false))throw new IOException(json.optString("error","The graphics driver test stopped unexpectedly."));
            MobileConfig.put(this,"android_gpu_driver",MobileConfig.quote(pendingDriver==null?"":pendingDriver));
            getSharedPreferences("mobile",0).edit().putString("verified_driver",json.optString("effective_driver","")).commit();
            if(launch)startGame(getSharedPreferences("mobile",0).getBoolean("next_debug",false));else toast("Driver passed and selected for the next launch.");
        }catch(Exception e){
            new AlertDialog.Builder(this).setTitle("Graphics driver could not start").setMessage(e.getMessage()+"\nTry the device's system driver.")
                .setNegativeButton("Back",null).setPositiveButton("Test system driver",(d,w)->probe("",launch)).show();
        }
        refresh();
    }
    private void installIso(Uri uri)throws Exception{
        File base=MobileConfig.base(this),game=new File(base,"game/base");game.getParentFile().mkdirs();File stage=Files.createTempDirectory(game.getParentFile().toPath(),".iso-inspect-").toFile();
        try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r")){
            if(fd==null)throw new IOException("Cannot open this ISO.");
            try(FileInputStream in=new FileInputStream(fd.getFileDescriptor())){
                DiscImage disc=new DiscImage(in.getChannel());
                for(GameFiles.Expected expected:GameFiles.catalog(this).values()){
                    DiscImage.Entry entry=disc.files.get(expected.path.toLowerCase(Locale.ROOT));
                    if(entry==null||entry.size!=expected.size)throw new IOException("Missing or unexpected "+expected.path+" in this disc image.");
                }
                for(Map.Entry<String,String> e:GameFiles.EXECUTABLES.entrySet())disc.copy(e.getKey(),new File(stage,e.getKey()),e.getValue());
                // Only the host UI and mixer need small local support files.
                // Guest game data continues to be read directly from the ISO.
                for(String path:new String[]{"media/ui/Fonts.zip","media/ui/textures/Horizon.zip","media/audio/AudioEngineConfig.xml","media/audio/AudioMixerSnapshots.xml"}){
                    GameFiles.Expected expected=GameFiles.catalog(this).get(path.toLowerCase(Locale.ROOT));
                    disc.copy(path,new File(stage,path),expected.hash);
                }
            }
            FileOperation.check();
            File incomplete=new File(base,"game/.copy-incomplete");incomplete.createNewFile();
            game.mkdirs();for(String name:GameFiles.EXECUTABLES.keySet())Files.move(new File(stage,name).toPath(),new File(game,name).toPath(),StandardCopyOption.REPLACE_EXISTING);
            for(String path:new String[]{"media/ui/Fonts.zip","media/ui/textures/Horizon.zip","media/audio/AudioEngineConfig.xml","media/audio/AudioMixerSnapshots.xml"}){
                File to=new File(game,path);to.getParentFile().mkdirs();Files.move(new File(stage,path).toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            // Persist only after all supported executables have been checked.
            getSharedPreferences("mobile",0).edit().putString("iso_uri",uri.toString()).commit();
            new File(base,"game/.copy-incomplete").delete();
        }finally{SaveArchive.delete(stage);}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void error(String message){new AlertDialog.Builder(this).setTitle("FH4Droid").setMessage(message==null?"The operation could not finish.":message).setPositiveButton("Back",null).show();}
    @Override protected void onDestroy(){if(operation!=null)operation.cancel();if(progress!=null)progress.dismiss();super.onDestroy();}
}
