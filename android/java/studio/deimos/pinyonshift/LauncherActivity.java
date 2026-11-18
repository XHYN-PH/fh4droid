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
    private static final int ISO=310,SAVE_OUT=311,SAVE_IN=312,DRIVER_ZIP=313,PROBE=314,PROFILE_PIC=315,XENIA_IN=316,BACKUP_OUT=317;
    private boolean busy;
    private InstallationStatus installationStatus;
    private String pendingBackupPath;
    private Button cancelInstallation;
    private String installationResult;
    private String pendingDriver,pendingSaveScope=SaveArchive.BOTH;private boolean playAfterProbe;private AlertDialog progress;private FileOperation operation;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);UiPreferences.load(this);
        if (state != null) pendingBackupPath = state.getString("backup_path");
        if(state!=null){pendingDriver=state.getString("driver");playAfterProbe=state.getBoolean("play_after_probe");pendingSaveScope=state.getString("save_scope",SaveArchive.BOTH);restoredPage=state.getString("launcher_page","home");restoredFocus=state.getString("launcher_focus","play");restoredHomeFocus=state.getString("launcher_home_focus","play");}
        build();FestivalUi.immersive(this);
        new Thread(()->{try{DriverStore.installBundled(this);}catch(IOException ignored){}},"BundledDrivers").start();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("backup_path",pendingBackupPath);state.putString("driver",pendingDriver);state.putBoolean("play_after_probe",playAfterProbe);state.putString("save_scope",pendingSaveScope);if(launcher!=null){state.putString("launcher_page",launcher.page());state.putString("launcher_focus",launcher.navigationFocus());state.putString("launcher_home_focus",launcher.homeFocus());}}
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
            case "verify":files();verifyInstallation();break;
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
        if(busy||launcher==null||panels.isEmpty()||panels.peek().builder==null)return;
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
        showRecovery("The last game stopped unexpectedly", "A report can help identify the cause.", true);
    }
    private void showRecovery(String title, String message, boolean launch) {
        LinearLayout options = FestivalUi.column(this);
        options.addView(FestivalUi.text(this, message, 14, FestivalUi.MUTED));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(options)
            .setNegativeButton("Back", null).create();
        FestivalUi.action(this, options, launch ? "Retry with system driver" : "Test system driver", () -> {
            dialog.dismiss();
            if (!launch || GameFiles.ready(this)) {
                work("Checking the game executables…", () -> {
                    if (launch) { ContentFiles.recoverForLaunch(this); GameFiles.verify(this); }
                }, () -> probe("", launch));
            } else files();
        });
        if (GraphicsProfilePage.lastGood(this).isFile()) {
            FestivalUi.action(this, options, "Restore last working graphics", () -> {
                dialog.dismiss();
                try {
                    GraphicsProfilePage.restoreForRecovery(this);
                    toast("Graphics restored for the next launch.");
                    refresh();
                } catch (IOException failure) { error(failure.getMessage()); }
            });
        }
        FestivalUi.action(this, options, "Export report", () -> { dialog.dismiss(); ProblemReports.share(this); });
        dialog.show();
    }
    private boolean canManage(){if(busy)return false;if(GameFiles.running(this)){toast("Quit the game before changing files, saves or drivers.");return false;}return true;}
    private void work(String message, Task task, Runnable success) {
        work(message, task, success, null);
    }
    private void work(String message, Task task, Runnable success, InstallationStatus inline) {
        if (busy) return;
        busy = true;
        refresh();
        InstallationStatus status = inline != null ? inline : new InstallationStatus(this, "Operation progress", message);
        status.update(message, 0, 0);
        FileOperation current = new FileOperation((name, done, total) -> runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) status.update(name, done, total);
        }));
        operation = current;
        Button cancel;
        if (inline != null) {
            cancel = cancelInstallation;
            cancel.setVisibility(View.VISIBLE);
        } else {
            progress = new AlertDialog.Builder(this).setTitle("Please wait").setView(status)
                .setCancelable(false).setNegativeButton("Cancel", null).create();
            progress.show();
            cancel = progress.getButton(AlertDialog.BUTTON_NEGATIVE);
        }
        cancel.setEnabled(true);
        cancel.setOnClickListener(view -> {
            current.cancel();
            view.setEnabled(false);
            status.update("Stopping after the current read. Completed files are kept.", 0, 0);
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(() -> {
            try (FileOperation scope = current.attach()) {
                task.run();
                runOnUiThread(() -> {
                    if (inline != null) installationResult = "Installation verified.";
                    endWork();
                    if (!isFinishing() && !isDestroyed()) success.run();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    boolean cancelled = current.isCancelled() || error instanceof InterruptedIOException;
                    String detail = cancelled ? FileOperation.cancelMessage()
                        : error.getMessage() == null ? "The operation could not finish." : error.getMessage();
                    if (inline != null) installationResult = detail;
                    endWork();
                    if (inline != null || isFinishing() || isDestroyed()) return;
                    if (cancelled) toast(detail); else error(detail);
                });
            }
        }, "FH4DroidFiles").start();
    }
    private void endWork() {
        busy = false;
        operation = null;
        if (cancelInstallation != null) cancelInstallation.setVisibility(View.GONE);
        if (!isDestroyed()) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (progress != null) { progress.dismiss(); progress = null; }
        if (!isDestroyed() && !isFinishing()) { refresh(); rebuildTop(); }
    }
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
    private View filesPage() {
        LinearLayout body = FestivalUi.column(this);
        boolean ready = GameFiles.ready(this);
        boolean linked = GameFiles.linked(this);
        boolean partial = new File(MobileConfig.base(this), "game/.copy-incomplete").exists();
        String detail = ready ? linked ? "Linked extracted folder ready." : "Game files ready to play."
            : linked ? "Linked folder unavailable. Choose it again or copy it into app storage."
            : partial ? "Import incomplete. Choose the same source again to resume."
            : "No game files installed yet.";
        installationStatus = new InstallationStatus(this, installationResult == null ? detail : installationResult);
        body.addView(installationStatus);
        cancelInstallation = FestivalUi.action(this, body, "Cancel", () -> {});
        cancelInstallation.setVisibility(View.GONE);
        FestivalUi.listRow(this, body, "file", "Choose game ISO", "Play from the disc image.",
            () -> { if (canManage()) pick(ISO, "*/*", false); });
        FestivalUi.listRow(this, body, "folder", "Copy extracted folder", "Allow about 8 GB in app storage.",
            () -> chooseExtracted(false));
        FestivalUi.listRow(this, body, "folder", "Use extracted folder in place", "Use an accessible local folder without a duplicate copy.",
            () -> chooseExtracted(true));
        if (ready) {
            FestivalUi.action(this, body, "Check installation", this::verifyInstallation);
            FestivalUi.action(this, body, "Check collision sound files", () -> {
                if (canManage()) work("Checking collision and glass audio…", () -> GameFiles.verifyCollisionAudio(this),
                    () -> { installationResult = "Collision and glass audio files verified."; rebuildTop(); }, installationStatus);
            });
        }
        return body;
    }
    private void chooseExtracted(boolean link) {
        if (!canManage()) return;
        installationResult = null;
        startActivity(new Intent(this, SetupActivity.class).putExtra("setup", true)
            .putExtra("choose_folder", true).putExtra("link_folder", link));
    }
    private void verifyInstallation() {
        if (canManage() && GameFiles.ready(this)) {
            work("Checking supported game files…", () -> GameFiles.verifyComplete(this), () -> {}, installationStatus);
        }
    }
    private void controls(){startActivity(new Intent(this,TouchEditorActivity.class));}
    private View storagePage(){
        LinearLayout body=FestivalUi.column(this);
        long free=MobileConfig.base(this).getUsableSpace();
        FestivalUi.card(this,body,"Game storage",free>0?android.text.format.Formatter.formatFileSize(this,free)+" available on this storage volume.":"Storage availability is reported in Android settings.");
        TextView usage = FestivalUi.text(this, "Measuring installed data…", 14, FestivalUi.TEXT);
        body.addView(usage);
        FestivalUi.action(this, body, "Manage graphics cache", () -> GraphicsCache.show(this));
        new Thread(() -> {
            try {
                File base = MobileConfig.base(this);
                File state = new File(base, "state");
                long game = GraphicsCache.size(new File(base, "game/base"));
                File title = new File(state, "user/" + DlcStore.TITLE);
                long dlc = GraphicsCache.size(new File(title, "00000002"))
                    + GraphicsCache.size(new File(title, "Disabled/00000002"))
                    + GraphicsCache.size(new File(title, "Headers/00000002"));
                long saves = Math.max(0, GraphicsCache.size(new File(state, SaveArchive.NORMAL))
                    + GraphicsCache.size(new File(state, SaveArchive.MODDED)) - dlc);
                long graphics = 0;
                File cache = GraphicsCache.root(this);
                File[] directories = cache.listFiles(file -> GraphicsCache.graphicsDirectory(cache, file));
                if (directories != null) for (File directory : directories) graphics += GraphicsCache.size(directory);
                String text = storageLine("Local game files", game) + "\n" + storageLine("DLC", dlc)
                    + "\n" + storageLine("Mods", GraphicsCache.size(new File(state, "mods")))
                    + "\n" + storageLine("Graphics caches", graphics) + "\n" + storageLine("Saves", saves)
                    + "\n" + storageLine("Backups", GraphicsCache.size(new File(state, "backups")));
                runOnUiThread(() -> { if (!isDestroyed()) usage.setText(text); });
            } catch (IOException | SecurityException error) {
                runOnUiThread(() -> { if (!isDestroyed()) usage.setText("Could not measure installed data."); });
            }
        }, "StorageUsage").start();
        FestivalUi.listRow(this,body,"folder","Game files","Choose or check your installation.",this::files);
        FestivalUi.action(this,body,"Open Android storage settings",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS));}catch(ActivityNotFoundException error){toast("Storage settings are not available on this device.");}});
        return body;
    }
    private String storageLine(String label, long bytes) {
        return label + " · " + android.text.format.Formatter.formatFileSize(this, bytes);
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
        FestivalUi.listRow(this, p, "save", "Manage backups", "Export, restore, or delete an individual backup.",
            () -> openPage("Save backups", this::backupPage));
        FestivalUi.listRow(this, p, "trash", "Reset selected save type", "Move the selected live saves to a local backup.", this::confirmResetSaves);
        TextView help=FestivalUi.text(this,"The save type applies to exports. Imports and restores keep normal and modded profiles separate and retain a rollback copy. Quit the game before changing saves.",13,FestivalUi.MUTED);help.setPadding(FestivalUi.dp(this,4),FestivalUi.dp(this,10),FestivalUi.dp(this,4),0);p.addView(help);
        TextView next=FestivalUi.text(this,"Next launch: "+LocalProfile.configuredProfile(MobileConfig.read(this))+"\n"+LocalProfile.backupStatus(MobileConfig.read(this)),13,FestivalUi.ORANGE);next.setPadding(FestivalUi.dp(this,4),FestivalUi.dp(this,6),FestivalUi.dp(this,4),0);p.addView(next);
        return p;
    }
    private File saveState() { return new File(MobileConfig.base(this), "state"); }
    private View backupPage() {
        LinearLayout body = FestivalUi.column(this);
        List<SaveArchive.Backup> backups = SaveArchive.backups(saveState());
        if (backups.isEmpty()) body.addView(FestivalUi.text(this, "No save backups yet.", 14, FestivalUi.MUTED));
        java.text.DateFormat dates = java.text.DateFormat.getDateTimeInstance();
        for (SaveArchive.Backup backup : backups) {
            String date = dates.format(new java.util.Date(backup.folder.lastModified()));
            FestivalUi.listRow(this, body, "save", backup.label, date, () -> showBackup(backup, date));
        }
        return body;
    }
    private void showBackup(SaveArchive.Backup backup, String date) {
        if (!canManage()) return;
        LinearLayout actions = FestivalUi.column(this);
        TextView description = FestivalUi.text(this, date, 14, FestivalUi.MUTED);
        actions.addView(description);
        new Thread(() -> {
            try {
                long bytes = GraphicsCache.size(backup.folder);
                runOnUiThread(() -> { if (!isDestroyed()) description.setText(date + " · " + android.text.format.Formatter.formatFileSize(this, bytes)); });
            } catch (IOException ignored) {}
        }, "SaveBackupSize").start();
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(backup.label).setView(actions)
            .setNegativeButton("Back", null).create();
        FestivalUi.action(this, actions, "Export backup", () -> {
            dialog.dismiss();
            pendingBackupPath = backup.folder.getPath();
            pick(BACKUP_OUT, "application/zip", true);
        });
        FestivalUi.action(this, actions, "Restore backup", () -> {
            dialog.dismiss(); new AlertDialog.Builder(this).setTitle("Restore this backup?")
                .setMessage("Current saves will be backed up first. Installed DLC stays in place.")
                .setNegativeButton("Cancel", null).setPositiveButton("Restore", (ignored, which) -> {
                    if (canManage()) work("Restoring backup…", () -> ContentFiles.locked(this, () -> {
                        SaveArchive.restoreBackup(saveState(), backup); return null;
                    }), () -> toast("Backup restored."));
                }).show();
        });
        FestivalUi.action(this, actions, "Delete backup", () -> {
            dialog.dismiss(); new AlertDialog.Builder(this).setTitle("Delete this backup?")
                .setMessage("Your current saves stay in place.").setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (ignored, which) -> {
                    if (canManage()) work("Removing backup…", () -> ContentFiles.locked(this, () -> {
                        SaveArchive.deleteBackup(saveState(), backup); return null;
                    }), () -> toast("Backup removed."));
                }).show();
        });
        dialog.show();
    }
    private void confirmResetSaves() {
        if (!canManage()) return;
        String scope = pendingSaveScope;
        String label = SaveArchive.BOTH.equals(scope) ? "normal and modded saves"
            : SaveArchive.NORMAL.equals(scope) ? "normal saves" : "modded saves";
        new AlertDialog.Builder(this).setTitle("Reset " + label + "?")
            .setMessage("The selected saves will be moved to a backup. Installed DLC stays in place.")
            .setNegativeButton("Cancel", null).setPositiveButton("Back up and reset", (dialog, which) -> {
                if (canManage()) work("Backing up saves…", () -> ContentFiles.locked(this, () -> {
                    SaveArchive.resetProfiles(saveState(), scope); return null;
                }), () -> toast("Saves moved to a backup."));
            }).show();
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
        String backupPath = pendingBackupPath;
        if (request == BACKUP_OUT) pendingBackupPath = null;
        if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(!canManage())return;
        if(request==ISO){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException e){error("This file provider cannot keep access. Choose an ISO in local storage.");return;}
            work("Inspecting your disc and verifying the game…",()->installIso(uri),()->toast("Disc image ready."),installationStatus);}
        else if(request==SAVE_OUT)work("Exporting saves…",()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("Cannot create backup.");SaveArchive.exportState(new File(MobileConfig.base(this),"state"),pendingSaveScope,out);}},()->toast("Saves exported."));
        else if (request == BACKUP_OUT) {
            work("Exporting backup…", () -> {
                File state = saveState();
                SaveArchive.Backup backup = SaveArchive.backups(state).stream()
                    .filter(item -> item.folder.getPath().equals(backupPath)).findFirst()
                    .orElseThrow(() -> new IOException("This backup is no longer available."));
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Cannot create backup export.");
                    SaveArchive.exportBackup(state, backup, out);
                }
            }, () -> toast("Backup exported."));
        }
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
            showRecovery("Graphics driver could not start", e.getMessage(), launch);
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
            if (!getSharedPreferences("mobile", 0).edit().remove(GameFiles.LINKED_FOLDER)
                    .putString("iso_uri", uri.toString()).commit()) {
                throw new IOException("Could not save the selected ISO.");
            }
            new File(base,"game/.copy-incomplete").delete();
        }finally{SaveArchive.delete(stage);}
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void error(String message){new AlertDialog.Builder(this).setTitle("FH4Droid").setMessage(message==null?"The operation could not finish.":message).setPositiveButton("Back",null).show();}
    @Override protected void onDestroy(){if(operation!=null)operation.cancel();if(progress!=null)progress.dismiss();super.onDestroy();}
}
