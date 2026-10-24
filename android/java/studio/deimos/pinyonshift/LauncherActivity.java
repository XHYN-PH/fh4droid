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

public final class LauncherActivity extends Activity {
    private static final int ISO=310,SAVE_OUT=311,SAVE_IN=312,DRIVER_ZIP=313,PROBE=314;
    private TextView title,summary,note;private Button play;private boolean busy;
    private String pendingDriver;private boolean playAfterProbe;private AlertDialog progress;private FileOperation operation;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(state!=null){pendingDriver=state.getString("driver");playAfterProbe=state.getBoolean("play_after_probe");}
        build();FestivalUi.immersive(this);
        new Thread(()->{try{DriverStore.installBundled(this);}catch(IOException ignored){}},"BundledDrivers").start();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("driver",pendingDriver);state.putBoolean("play_after_probe",playAfterProbe);}
    @Override protected void onResume(){super.onResume();refresh();recovery();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);build();refresh();FestivalUi.immersive(this);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus){FestivalUi.immersive(this);refresh();}}
    private void build(){
        FrameLayout canvas=new FrameLayout(this);canvas.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=FestivalUi.column(this);int pad=FestivalUi.dp(this,24);root.setPadding(pad,FestivalUi.dp(this,18),pad,FestivalUi.dp(this,14));
        ScrollView page=new ScrollView(this);page.setFillViewport(true);page.addView(root);canvas.addView(page,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            root.setPadding(pad+safe.left,FestivalUi.dp(this,18)+safe.top,pad+safe.right,FestivalUi.dp(this,14)+safe.bottom);return insets;});
        TextView brand=FestivalUi.text(this,"FH4DROID  /  ANDROID",16,FestivalUi.TEXT);brand.setLetterSpacing(.12f);brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(brand,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout details=FestivalUi.column(this);details.setGravity(Gravity.BOTTOM);
        title=FestivalUi.text(this,"Ready to drive",getResources().getConfiguration().screenHeightDp<430?36:48,FestivalUi.TEXT);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        summary=FestivalUi.text(this,"",14,FestivalUi.MUTED);summary.setPadding(0,FestivalUi.dp(this,8),0,0);
        note=FestivalUi.text(this,"",13,0xffffbca5);note.setPadding(0,FestivalUi.dp(this,8),0,0);
        details.addView(title);details.addView(summary);details.addView(note);
        details.setPadding(0,FestivalUi.dp(this,28),0,0);root.addView(details,new LinearLayout.LayoutParams(-1,-2));
        boolean wide=getResources().getConfiguration().screenWidthDp>=600&&getResources().getConfiguration().fontScale<1.3f;
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);actions.setGravity(Gravity.CENTER_VERTICAL);
        play=FestivalUi.button(this,"▶  Play",true);play.setOnClickListener(v->play(false));actions.addView(play,new LinearLayout.LayoutParams(wide?0:-1,-2,wide?1:0));
        navButton(actions,"Settings",()->{
            if(GameFiles.running(this)){toast("Use the in-game menu to change settings while the game is running.");return;}
            MobileSettings.show(this,null,this::controls,this::drivers);
        });
        navButton(actions,"Game files",this::files);
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,-2);navParams.topMargin=FestivalUi.dp(this,18);root.addView(actions,navParams);
        GridLayout links=new GridLayout(this);links.setColumnCount(getResources().getConfiguration().screenWidthDp>=400?2:1);
        link(links,"Saves",this::saves);link(links,"Controls",this::controls);link(links,"Drivers",this::drivers);
        link(links,"Reports",this::reports);link(links,"GitHub ↗",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/XHYN-PH/fh4droid"))));
        root.addView(links,new LinearLayout.LayoutParams(-1,-2));
        setContentView(canvas);FestivalUi.enter(root);
    }
    private void navButton(LinearLayout row,String text,Runnable action){
        Button b=FestivalUi.button(this,text,false);boolean horizontal=row.getOrientation()==LinearLayout.HORIZONTAL;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(horizontal?0:-1,-2,horizontal?1:0);if(horizontal)p.leftMargin=FestivalUi.dp(this,10);else p.topMargin=FestivalUi.dp(this,10);row.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private void link(GridLayout row,String label,Runnable action){
        Button button=FestivalUi.button(this,label,false);button.setTextSize(14);
        GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=-2;p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        p.setMargins(FestivalUi.dp(this,4),FestivalUi.dp(this,8),FestivalUi.dp(this,4),0);row.addView(button,p);button.setOnClickListener(v->action.run());
    }
    private void refresh(){
        if(title==null)return;boolean ready=GameFiles.ready(this);
        title.setText(ready?"Ready to drive":"Your next drive awaits");play.setText(ready?"▶  Play":"Set up game");
        summary.setText(MobileSettings.summary(this));note.setText(ready?"Your own Forza Horizon Xbox 360 game files.":"Add your Forza Horizon Xbox 360 disc image or extracted folder.");
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
        FestivalUi.action(this,p,"Copy from PC over Wi-Fi",()->{if(canManage())startActivity(new Intent(this,SetupActivity.class).putExtra("setup",true));});
        FestivalUi.action(this,p,"Check installed game files",()->{if(canManage())work("Checking all game files. Large games can take several minutes…",()->GameFiles.verifyComplete(this),()->toast("All supported game files verified."));});
        FestivalUi.dialog(this,"Game files",p).show();
    }
    private void controls(){startActivity(new Intent(this,TouchEditorActivity.class));}
    private void saves(){
        LinearLayout p=FestivalUi.column(this);p.addView(FestivalUi.text(this,"Save-only backups for this base-game build. Quit the game before importing or restoring.",14,FestivalUi.MUTED));
        FestivalUi.action(this,p,"Export saves",()->{if(canManage())pick(SAVE_OUT,"application/zip",true);});
        FestivalUi.action(this,p,"Import saves",()->{if(canManage())new AlertDialog.Builder(this).setTitle("Import saves?").setMessage("Current saves are kept as a local backup before replacement.")
            .setNegativeButton("Cancel",null).setPositiveButton("Choose ZIP",(d,w)->pick(SAVE_IN,"application/zip",false)).show();});
        FestivalUi.action(this,p,"Restore previous saves",()->{if(canManage())new AlertDialog.Builder(this).setTitle("Restore previous saves?").setMessage("Your current saves are also kept before the restore.")
            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->work("Restoring saves…",()->SaveArchive.restore(new File(MobileConfig.base(this),"state")),()->toast("Previous saves restored."))).show();});
        FestivalUi.dialog(this,"Saves",p).show();
    }
    private void drivers(){
        LinearLayout p=FestivalUi.column(this);p.addView(FestivalUi.text(this,"Drivers are tested before activation and apply on the next launch. Gameplay still depends on your device. Only import packages from a source you trust.",14,FestivalUi.MUTED));
        for(String choice:DriverStore.choices(this))FestivalUi.action(this,p,choice.isEmpty()?"System driver":choice.equals("auto")?"Automatic":choice,()->{if(canManage())probe(choice,false);});
        FestivalUi.action(this,p,"Import driver ZIP",()->{if(canManage())pick(DRIVER_ZIP,"*/*",false);});FestivalUi.dialog(this,"Graphics driver",p).show();
    }
    private void reports(){
        LinearLayout p=FestivalUi.column(this);p.addView(FestivalUi.text(this,"Reports include device details, settings and recent logs. Review them before sharing.",14,FestivalUi.MUTED));
        FestivalUi.action(this,p,"Play with logs",()->play(true));ProblemReports.action(this,p,"Share latest problem report");
        FestivalUi.dialog(this,"Logs & troubleshooting",p).show();
    }
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
        try{JSONObject json=new JSONObject(report==null?"{}":report);getSharedPreferences("mobile",0).edit().putString("last_probe",report).apply();
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
