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
    private RacingLauncherView launcher;private RacingLauncherView.Model launcherModel;private float builtScale;
    private String restoredPage="home",restoredFocus="play",restoredHomeFocus="play";
    /** A page may carry the code that builds it, so it can refresh after files, saves or drivers change. */
    private static final class PanelPage{
        final String title;final View body;final java.util.function.Supplier<View> builder;
        PanelPage(String title,View body){this(title,body,null);}
        PanelPage(String title,View body,java.util.function.Supplier<View> builder){this.title=title;this.body=body;this.builder=builder;}
    }
    private static final int ISO=310,SAVE_OUT=311,SAVE_IN=312,DRIVER_ZIP=313,PROBE=314,PROFILE_PIC=315,XENIA_IN=316;
    private boolean busy;
    private String pendingDriver,pendingSaveScope=SaveArchive.BOTH;private boolean playAfterProbe;private AlertDialog progress;private FileOperation operation;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);UiPreferences.load(this);if(state!=null){pendingDriver=state.getString("driver");playAfterProbe=state.getBoolean("play_after_probe");pendingSaveScope=state.getString("save_scope",SaveArchive.BOTH);restoredPage=state.getString("launcher_page","home");restoredFocus=state.getString("launcher_focus","play");restoredHomeFocus=state.getString("launcher_home_focus","play");}
        build();FestivalUi.immersive(this);
        new Thread(()->{try{DriverStore.installBundled(this);}catch(IOException ignored){}},"BundledDrivers").start();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("driver",pendingDriver);state.putBoolean("play_after_probe",playAfterProbe);state.putString("save_scope",pendingSaveScope);if(launcher!=null){state.putString("launcher_page",launcher.page());state.putString("launcher_focus",launcher.navigationFocus());state.putString("launcher_home_focus",launcher.homeFocus());}}
    @Override protected void onResume(){super.onResume();UiPreferences.load(this);if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();refresh();rebuildTop();recovery();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);build();refresh();FestivalUi.immersive(this);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus){UiPreferences.load(this);if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();FestivalUi.immersive(this);refresh();if(launcher!=null)FestivalUi.refreshTree(launcher);}}
    private android.graphics.Bitmap artwork(String name,int sample){
        try(InputStream input=getAssets().open(name)){
            android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inSampleSize=sample;
            return android.graphics.BitmapFactory.decodeStream(input,null,options);
        }catch(IOException error){android.util.Log.w("FH4Droid","Menu artwork unavailable: "+name,error);return null;}
    }
    private void build(){
        if(launcher!=null){restoredPage=launcher.page();restoredFocus=launcher.navigationFocus();restoredHomeFocus=launcher.homeFocus();FestivalUi.rememberTree(launcher);}
        builtScale=FestivalUi.scale(this);launcherModel=new RacingLauncherView.Model();
        launcher=new RacingLauncherView(this,RacingLauncherView.Design.GRID,launcherModel,
            artwork("menu-background.png",1),artwork("menu-logo.png",4),this::launcherAction);
        launcher.restoreHomeFocus(restoredHomeFocus);launcher.restoreNavigation(restoredPage,restoredFocus);
        launcher.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Rect safe=PlatformCompat.safeInsets(insets);view.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});
        setContentView(launcher);renderPanel(false);refresh();
    }
    private void launcherAction(String action){
        switch(action){
            case "play":case "resume":play(false);break;
            case "choose-game":case "choose-game-files":files();break;
            case "verify":if(canManage())work("Checking all game files…",()->GameFiles.verifyComplete(this),()->toast("All supported game files verified."));break;
            case "storage":openPage("Storage",this::storagePage);break;
            case "touch-layout":controls();break;
            case "controller":settingPage("Controller");break;
            case "tilt":settingPage("Phone");break;
            case "graphics":settingPage("Graphics");break;
            case "audio":settingPage("Audio");break;
            case "control-settings":settingPage("Controls");break;
            case "interface":settingPage("Interface");break;
            case "gameplay":settingPage("Gameplay");break;
            case "advanced":settingPage("Advanced");break;
            case "drivers":drivers();break;
            case "saves":saves();break;
            case "mods":if(canManage())startActivity(new Intent(this,ContentActivity.class));break;
            case "edit-profile":ProfilePage.show(this,()->{if(canManage())pick(PROFILE_PIC,"image/*",false);},this::saves);break;
            case "achievements":AchievementsPage.show(this,null);break;
            case "reports":reports();break;
            case "about":about();break;
            case "youtube":external("https://youtube.com/@xhyn_ph");break;
            case "telegram":external("https://t.me/xhynports");break;
            case "github":external("https://github.com/XHYN-PH/fh4droid");break;
            case "back":onBackPressed();break;
        }
    }
    private void settingPage(String page){
        if(GameFiles.running(this)){toast("Open settings with Android Back while playing.");return;}
        SettingsHub.page(this,page,null,this::controls);
    }
    @Override public void showPanel(String title,View body){
        if(launcher!=null)FestivalUi.rememberTree(launcher);
        panels.push(new PanelPage(title,body));renderPanel(false);
    }
    @Override public void replacePanel(String title,View body){if(!panels.isEmpty())panels.pop();showPanel(title,body);}
    @Override public void backPanel(){
        if(!panels.isEmpty())panels.pop();
        if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();else renderPanel(true);
    }
    private void renderPanel(boolean back){
        if(launcher==null)return;
        if(panels.isEmpty())launcher.clearContent(back);
        else{PanelPage page=panels.peek();launcher.showContent(page.title,page.body,back);}
    }
    private void openPage(String title,java.util.function.Supplier<View> builder){
        if(launcher!=null)FestivalUi.rememberTree(launcher);
        panels.push(new PanelPage(title,builder.get(),builder));renderPanel(false);
    }
    /** Refresh data after a picker or driver probe without losing the page's position. */
    private void rebuildTop(){
        if(launcher==null||panels.isEmpty()||panels.peek().builder==null)return;
        PanelPage top=panels.pop();FestivalUi.rememberTree(launcher);View body=top.builder.get();
        FestivalUi.transferPageState(top.body,body);panels.push(new PanelPage(top.title,body,top.builder));renderPanel(false);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(ControllerPrompts.handle(this,getWindow(),event.getKeyCode(),event,this::onBackPressed))return true;
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed(){if(!panels.isEmpty())backPanel();else if(launcher==null||!launcher.navigateBack())super.onBackPressed();}
    private String installedVersion(){
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);return "v"+(info.versionName==null?"Unknown":info.versionName)+" · build "+info.getLongVersionCode();}
        catch(android.content.pm.PackageManager.NameNotFoundException error){return "Version unavailable";}
    }
    private void external(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException error){toast("No browser is available.");}}
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
    private void refresh(){
        if(launcherModel==null)return;
        launcherModel.ready=GameFiles.ready(this);launcherModel.running=GameFiles.running(this);launcherModel.busy=busy;
        Map<String,String> settings=MobileConfig.read(this);
        String scale=settings.getOrDefault("fh1_render_scale_percent","100");
        String fps=settings.getOrDefault("pinyon_shift_fh1_render_fps_limit","30");
        launcherModel.graphics=scale+"% resolution · "+(fps.equals("0")?"Uncapped":fps+" FPS target");
        launcherModel.version=installedVersion();
        launcherModel.notice=GameSession.interrupted(this)>0?"Last session stopped unexpectedly. Open More → Problem reports for help.":"";
        launcher.update();
    }
    private void recovery(){
        long interrupted=GameSession.interrupted(this);
        if(interrupted==0||busy||getSharedPreferences("mobile",0).getLong("recovery_seen",0)==interrupted)return;
        getSharedPreferences("mobile",0).edit().putLong("recovery_seen",interrupted).apply();
        new AlertDialog.Builder(this).setTitle("The last game stopped unexpectedly")
            .setMessage("This may be a driver problem, a game crash or Android freeing memory. A report can help identify the cause.")
            .setNegativeButton("Back",null).setNeutralButton("Share report",(d,w)->ProblemReports.share(this))
            .setPositiveButton("Recovery options",(d,w)->{String[] choices=GraphicsProfilePage.lastGood(this).isFile()?new String[]{"Use system driver","Restore last working graphics"}:new String[]{"Use system driver"};FestivalUi.choose(this,"Recovery",choices,-1,index->{try{if(index==1)GraphicsProfilePage.restoreForRecovery(this);else MobileConfig.put(this,"android_gpu_driver","\"\"");toast("Recovery settings saved. Tap Play to try them.");}catch(IOException e){error(e.getMessage());}});}).show();
    }
    private boolean canManage(){if(busy)return false;if(GameFiles.running(this)){toast("Quit the game before changing files, saves or drivers.");return false;}return true;}
    private void work(String message,Task task,Runnable success){
        if(busy)return;busy=true;refresh();LinearLayout p=FestivalUi.column(this);TextView detail=FestivalUi.text(this,message,16,FestivalUi.TEXT);p.addView(detail);
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
    private void endWork(){busy=false;refresh();operation=null;if(!isDestroyed())getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(progress!=null){progress.dismiss();progress=null;}if(!isDestroyed()&&!isFinishing())rebuildTop();}
    interface Task{void run()throws Exception;}
    private void play(boolean logging){
        if(busy)return;if(!GameFiles.ready(this)){files();return;}
        if(GameFiles.running(this)){startGame(logging);return;}
        getSharedPreferences("mobile",0).edit().putBoolean("next_debug",logging).apply();
        work("Checking the game executables…",()->{ContentFiles.recoverForLaunch(this);GameFiles.verify(this);},()->probe(MobileConfig.get(this,"android_gpu_driver","auto"),true));
    }
    private void startGame(boolean logging){
        Intent i=new Intent(this,PinyonShiftActivity.class);i.putExtra("debug_report",logging);
        String effective=getSharedPreferences("mobile",0).getString("verified_driver","");
        i.putExtra("verified_driver",effective);
        i.putExtra("env.PINYON_SHIFT_ANDROID_LAUNCHER","1");startActivity(i);
    }
    private void files(){openPage("Game files",this::filesPage);}
    private View filesPage(){
        LinearLayout p=FestivalUi.column(this);boolean ready=GameFiles.ready(this);boolean partial=new File(MobileConfig.base(this),"game/.copy-incomplete").exists();
        FestivalUi.listRow(this,p,"file","Choose game ISO","Select the Forza Horizon game ISO file.",()->{if(canManage())pick(ISO,"*/*",false);});
        FestivalUi.listRow(this,p,"folder","Choose extracted folder","Select the extracted game folder instead.",()->{if(canManage())startActivity(new Intent(this,SetupActivity.class).putExtra("setup",true).putExtra("choose_folder",true));});
        FestivalUi.statusCard(this,p,"download","Installation status",
            ready?"Game files ready to play.":partial?"Import incomplete. Choose the same source again to resume.":"No game files installed yet.",
            ready?100:0,ready?"Game detected · Tap to verify all files":"No game detected",
            ()->{if(canManage())work("Checking all game files. Large games can take several minutes…",()->GameFiles.verifyComplete(this),()->toast("All supported game files verified."));});
        TextView help=FestivalUi.text(this,"Use your own Xbox 360 copy of Forza Horizon (2012). This build uses base-game executables; TU4 saves require a separate TU4 build.",13,FestivalUi.MUTED);help.setPadding(FestivalUi.dp(this,4),FestivalUi.dp(this,10),FestivalUi.dp(this,4),0);p.addView(help);
        return p;
    }
    private void controls(){startActivity(new Intent(this,TouchEditorActivity.class));}
    private View storagePage(){
        LinearLayout body=FestivalUi.column(this);
        long free=MobileConfig.base(this).getUsableSpace();
        FestivalUi.card(this,body,"Game storage",free>0?android.text.format.Formatter.formatFileSize(this,free)+" available on this storage volume.":"Storage availability is reported in Android settings.");
        body.addView(FestivalUi.text(this,"Disc images are read from their selected location. Extracted folders are copied into app storage. Keep save backups before clearing app data in Android.",14,FestivalUi.MUTED));
        FestivalUi.listRow(this,body,"folder","Game files","Choose or check your installation.",this::files);
        FestivalUi.action(this,body,"Open Android storage settings",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS));}catch(ActivityNotFoundException error){toast("Storage settings are not available on this device.");}});
        return body;
    }
    private void saves(){openPage("Saves",this::savesPage);}
    private View savesPage(){
        LinearLayout p=FestivalUi.column(this);
        String[] scopes={SaveArchive.NORMAL,SaveArchive.MODDED,SaveArchive.BOTH};int selected=Arrays.asList(scopes).indexOf(pendingSaveScope);if(selected<0)selected=2;
        FestivalUi.choiceGroup(this,p,"Save type","Choose which saves to manage.",new String[]{"save","wrench","layers"},new String[]{"Normal saves","Modded saves","Both"},new String[]{"Standard game progress.","Modified game progress.","Manage both types."},selected,index->pendingSaveScope=scopes[index]);
        FestivalUi.tileRow(this,p,
            FestivalUi.tile(this,"upload","Export saves","Export a ZIP for backup.",()->{if(canManage())pick(SAVE_OUT,"application/zip",true);}),
            FestivalUi.tile(this,"download","Restore saves","Restore from a backup ZIP.",()->{if(canManage())new AlertDialog.Builder(this).setTitle("Import saves?").setMessage("Current saves are kept as a local backup before replacement.")
                .setNegativeButton("Cancel",null).setPositiveButton("Choose ZIP",(d,w)->pick(SAVE_IN,"application/zip",false)).show();}));
        FestivalUi.listRow(this,p,"user","Import a Xenia profile","Add a base-game Xenia save from a ZIP.",()->{if(canManage())new AlertDialog.Builder(this).setTitle("Import a base-game Xenia save").setMessage("Choose a ZIP containing content/<XUID>/4D5309C9 with the original folders. Imports beside your current normal save and selects that original XUID for next launch. Existing profiles are kept, settings are backed up, and title-update-v4 saves are refused. Modded mode keeps using its separate save tree.").setNegativeButton("Cancel",null).setPositiveButton("Choose ZIP",(d,w)->pick(XENIA_IN,"application/zip",false)).show();});
        FestivalUi.listRow(this,p,"save","Restore previous saves","Go back to the copy kept before the last change.",()->{if(canManage())new AlertDialog.Builder(this).setTitle("Restore previous saves?").setMessage("Your current saves are also kept before the restore.")
            .setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->work("Restoring saves…",()->SaveArchive.restore(new File(MobileConfig.base(this),"state")),()->toast("Previous saves restored."))).show();});
        TextView help=FestivalUi.text(this,"The save type applies to exports. Imports and restores keep normal and modded profiles separate and retain a rollback copy. Quit the game before changing saves.",13,FestivalUi.MUTED);help.setPadding(FestivalUi.dp(this,4),FestivalUi.dp(this,10),FestivalUi.dp(this,4),0);p.addView(help);
        TextView next=FestivalUi.text(this,"Next launch: "+LocalProfile.configuredProfile(MobileConfig.read(this))+"\n"+LocalProfile.backupStatus(MobileConfig.read(this)),13,FestivalUi.ORANGE);next.setPadding(FestivalUi.dp(this,4),FestivalUi.dp(this,6),FestivalUi.dp(this,4),0);p.addView(next);
        return p;
    }
    void openDrivers(){drivers();}
    private void drivers(){openPage("Graphics drivers",this::driverPage);}
    private View driverPage(){
        LinearLayout body=FestivalUi.column(this);
        TextView intro=FestivalUi.text(this,"Mali and Xclipse: start with your system driver. Turnip is for compatible Adreno GPUs. Test a driver before selecting it. Quit the game before importing or removing drivers.",13,FestivalUi.MUTED);
        intro.setPadding(FestivalUi.dp(this,4),0,FestivalUi.dp(this,4),FestivalUi.dp(this,2));body.addView(intro);
        String current=MobileConfig.get(this,"android_gpu_driver","auto");
        for(String choice:DriverStore.choices(this)){
            boolean system=choice.isEmpty(),auto=choice.equals("auto"),imported=choice.startsWith("imported-");
            String name=system?"System Vulkan driver":DriverStore.displayName(this,choice);
            String detail=system?"Use the device's built-in Vulkan driver.":auto?"Let the app pick the best driver for your GPU.":imported?"Use a custom driver file (e.g. Turnip, Mesa).":"Driver bundled with the app, for compatible Adreno GPUs.";
            FestivalUi.radioRow(this,body,system||auto?"chip":"file",name,detail,current.equals(choice),()->{if(canManage())probe(choice,false);});
        }
        FestivalUi.tileRow(this,body,
            FestivalUi.tile(this,"download","Import driver","Select a driver file",()->{if(canManage())pick(DRIVER_ZIP,"application/zip",false);}),
            FestivalUi.tile(this,"flask","Test driver","Check compatibility",()->{if(canManage())probe(current,false);}),
            FestivalUi.tile(this,"trash","Remove driver","Clear imported driver",()->{if(canManage())removeDriver();}));
        DriverStore.addReport(this,body,AdrenoSettings.reportDetails(this));return body;
    }
    private void removeDriver(){
        List<String> imported=new ArrayList<>();for(String choice:DriverStore.choices(this))if(choice.startsWith("imported-"))imported.add(choice);
        if(imported.isEmpty()){toast("No imported driver to remove.");return;}
        if(imported.size()==1){confirmRemoveDriver(imported.get(0));return;}
        String[] labels=new String[imported.size()];for(int i=0;i<labels.length;i++)labels[i]=DriverStore.displayName(this,imported.get(i));
        FestivalUi.choose(this,"Remove which driver?",labels,-1,index->confirmRemoveDriver(imported.get(index)));
    }
    private void confirmRemoveDriver(String choice){
        String current=MobileConfig.get(this,"android_gpu_driver","auto");
        new AlertDialog.Builder(this).setTitle("Delete imported driver?").setMessage(DriverStore.displayName(this,choice)+(current.equals(choice)?" will be removed. The system driver will be selected.":" will be removed. Your selected driver is kept."))
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->work("Removing driver…",()->DriverStore.removeImported(this,choice),this::refresh)).show();
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
        else if(request==SAVE_OUT)work("Exporting saves…",()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Cannot create backup.");SaveArchive.exportState(new File(MobileConfig.base(this),"state"),pendingSaveScope,out);}},()->toast("Saves exported."));
        else if(request==SAVE_IN)work("Importing saves…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open backup.");SaveArchive.importInto(new File(MobileConfig.base(this),"state"),in);}},()->toast("Saves imported."));
        else if(request==XENIA_IN)work("Checking Xenia profile…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Cannot open Xenia ZIP.");try(XeniaSaveImport.Prepared prepared=XeniaSaveImport.prepare(new File(MobileConfig.base(this),"state"),in)){XeniaSaveImport.install(new File(MobileConfig.base(this),"state"),prepared);}}},()->toast("Xenia profile imported; original XUID selected for the next launch."));
        else if(request==PROFILE_PIC)work("Preparing gamer picture…",()->ProfilePage.importPicture(this,uri),()->toast("Gamer picture saved for the next launch."));
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
        refresh();rebuildTop();
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
