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
    private FrameLayout rightPane;private LinearLayout homeRow,footerBar;private float builtScale;
    /** A page may carry the code that builds it, so it can refresh after files, saves or drivers change. */
    private static final class PanelPage{
        final String title;final View body;final java.util.function.Supplier<View> builder;
        PanelPage(String title,View body){this(title,body,null);}
        PanelPage(String title,View body,java.util.function.Supplier<View> builder){this.title=title;this.body=body;this.builder=builder;}
    }
    private static final int ISO=310,SAVE_OUT=311,SAVE_IN=312,DRIVER_ZIP=313,PROBE=314,PROFILE_PIC=315,XENIA_IN=316;
    private TextView title,summary,note,versionFooter;private Button play;private boolean busy;
    private String pendingDriver,pendingSaveScope=SaveArchive.BOTH;private boolean playAfterProbe;private AlertDialog progress;private FileOperation operation;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);UiPreferences.load(this);if(state!=null){pendingDriver=state.getString("driver");playAfterProbe=state.getBoolean("play_after_probe");pendingSaveScope=state.getString("save_scope",SaveArchive.BOTH);}
        build();FestivalUi.immersive(this);
        new Thread(()->{try{DriverStore.installBundled(this);}catch(IOException ignored){}},"BundledDrivers").start();
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("driver",pendingDriver);state.putBoolean("play_after_probe",playAfterProbe);state.putString("save_scope",pendingSaveScope);}
    @Override protected void onResume(){super.onResume();UiPreferences.load(this);if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();refresh();rebuildTop();recovery();}
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);build();refresh();FestivalUi.immersive(this);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus){UiPreferences.load(this);if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();FestivalUi.immersive(this);refresh();if(rightPane!=null)FestivalUi.refreshTree(rightPane);}}
    private void build(){
        builtScale=FestivalUi.scale(this);
        FrameLayout canvas=new FrameLayout(this);canvas.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
        boolean wide=FestivalUi.wideLayout(this);int pad=FestivalUi.dp(this,18);
        LinearLayout row=new LinearLayout(this);homeRow=row;row.setPadding(pad,pad,pad,FestivalUi.dp(this,64));canvas.addView(row,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout menu=FestivalUi.column(this);menu.setGravity(Gravity.BOTTOM);menu.setPadding(0,FestivalUi.dp(this,12),wide?pad:0,0);scroll.addView(menu);row.addView(scroll,new LinearLayout.LayoutParams(0,-1,wide?.43f:1));
        title=FestivalUi.text(this,"Ready to drive",13,FestivalUi.MUTED);menu.addView(title);
        ImageView logo=new ImageView(this);logo.setContentDescription("FH4Droid logo");logo.setScaleType(ImageView.ScaleType.FIT_START);logo.setAdjustViewBounds(true);
        try(InputStream in=getAssets().open("menu-logo.png")){logo.setImageBitmap(android.graphics.BitmapFactory.decodeStream(in));}catch(IOException error){logo.setContentDescription("FH4Droid");}
        logo.setBackgroundColor(android.graphics.Color.TRANSPARENT);menu.addView(logo,new LinearLayout.LayoutParams(-1,FestivalUi.dp(this,getResources().getConfiguration().screenHeightDp<380?70:96)));
        summary=FestivalUi.text(this,"",13,FestivalUi.MUTED);summary.setPadding(0,FestivalUi.dp(this,4),0,FestivalUi.dp(this,6));menu.addView(summary);
        note=FestivalUi.text(this,"",12,0xffffa6c4);note.setPadding(0,0,0,FestivalUi.dp(this,8));menu.addView(note);
        play=FestivalUi.button(this,"Play",true);play.setOnClickListener(v->play(false));menu.addView(play,new LinearLayout.LayoutParams(-1,-2));
        Button settings=FestivalUi.button(this,"Settings",false);settings.setOnClickListener(v->{if(GameFiles.running(this)){toast("Open settings with Android Back while playing.");return;}openRoot();MobileSettings.show(this,null,this::controls,this::drivers);});
        Button controls=FestivalUi.button(this,"Controls",false);controls.setOnClickListener(v->controls());
        LinearLayout pair=new LinearLayout(this);menu.addView(pair,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,-2,1),second=new LinearLayout.LayoutParams(0,-2,1);
        first.topMargin=second.topMargin=FestivalUi.dp(this,8);second.leftMargin=FestivalUi.dp(this,8);pair.addView(settings,first);pair.addView(controls,second);
        LinearLayout shortcuts=new LinearLayout(this);menu.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
        View[] quick={
            FestivalUi.shortcut(this,"folder","Game files",()->{openRoot();files();}),
            FestivalUi.shortcut(this,"chip","Drivers",()->{openRoot();drivers();}),
            FestivalUi.shortcut(this,"save","Saves",()->{openRoot();saves();}),
            FestivalUi.shortcut(this,"layers","More",()->{openRoot();more();})};
        for(int i=0;i<quick.length;i++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.topMargin=FestivalUi.dp(this,8);if(i>0)lp.leftMargin=FestivalUi.dp(this,8);shortcuts.addView(quick[i],lp);}
        rightPane=new FrameLayout(this);rightPane.setVisibility(View.GONE);canvas.addView(rightPane,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout footer=new LinearLayout(this);footerBar=footer;footer.setGravity(Gravity.CENTER_VERTICAL);footer.setPadding(pad,0,pad,0);
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
        final android.graphics.Rect[] safeInsets={new android.graphics.Rect()};
        Runnable fitFooter=()->{
            android.graphics.Rect safe=safeInsets[0];int bottom=Math.max(FestivalUi.dp(this,64),footer.getHeight()+FestivalUi.dp(this,16))+safe.bottom;
            row.setPadding(pad+safe.left,pad+safe.top,pad+safe.right,bottom);
            footer.setPadding(pad+safe.left,0,pad+safe.right,0);footerParams.bottomMargin=FestivalUi.dp(this,8)+safe.bottom;footer.setLayoutParams(footerParams);
            FrameLayout.LayoutParams pane=(FrameLayout.LayoutParams)rightPane.getLayoutParams();pane.setMargins(pad+safe.left,pad+safe.top,pad+safe.right,pad+safe.bottom);rightPane.setLayoutParams(pane);
        };
        footer.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(l!=ol||t!=ot||r!=or||b!=ob)fitFooter.run();});
        canvas.setOnApplyWindowInsetsListener((v,insets)->{safeInsets[0]=PlatformCompat.safeInsets(insets);fitFooter.run();return insets;});
        setContentView(canvas);renderPanel();FestivalUi.enter(menu);
    }
    private void openRoot(){panels.clear();renderPanel();}
    @Override public void showPanel(String title,View body){if(rightPane!=null)FestivalUi.rememberTree(rightPane);panels.push(new PanelPage(title,body));renderPanel();}
    @Override public void replacePanel(String title,View body){if(!panels.isEmpty())panels.pop();showPanel(title,body);}
    @Override public void backPanel(){if(!panels.isEmpty())panels.pop();if(Math.abs(builtScale-FestivalUi.scale(this))>.001f)build();else renderPanel();}
    private void renderPanel(){
        if(rightPane==null)return;FestivalUi.rememberTree(rightPane);rightPane.removeAllViews();
        boolean open=!panels.isEmpty();
        // A page covers the whole screen, so the home menu and footer step aside while one is open.
        rightPane.setVisibility(open?View.VISIBLE:View.GONE);
        if(homeRow!=null)homeRow.setVisibility(open?View.GONE:View.VISIBLE);
        if(footerBar!=null)footerBar.setVisibility(open?View.GONE:View.VISIBLE);
        if(open){PanelPage page=panels.peek();View panel=FestivalUi.panel(this,page.title,page.body,this::backPanel);rightPane.addView(panel,new FrameLayout.LayoutParams(-1,-1));FestivalUi.slideIn(panel);}
    }
    private void openPage(String title,java.util.function.Supplier<View> builder){if(rightPane!=null)FestivalUi.rememberTree(rightPane);panels.push(new PanelPage(title,builder.get(),builder));renderPanel();}
    /** Rebuilds the open page, if it knows how, so it shows the latest files, saves or driver. */
    private void rebuildTop(){
        if(rightPane==null||panels.isEmpty()||panels.peek().builder==null)return;
        PanelPage top=panels.pop();FestivalUi.rememberTree(rightPane);panels.push(new PanelPage(top.title,top.builder.get(),top.builder));renderPanel();
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(ControllerPrompts.handle(this,getWindow(),event.getKeyCode(),event,this::onBackPressed))return true;return super.dispatchKeyEvent(event);}
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
    private void more(){openPage("More",()->{
        LinearLayout body=FestivalUi.column(this);
        FestivalUi.listRow(this,body,"user","Offline profile","View and manage your local profile.",()->ProfilePage.show(this,()->{if(canManage())pick(PROFILE_PIC,"image/*",false);},this::saves));
        FestivalUi.listRow(this,body,"trophy","Local achievements","View your unlocked achievements.",()->AchievementsPage.show(this,null));
        FestivalUi.listRow(this,body,"file","Diagnostics / Reports","Create a shareable report with logs and performance info.",()->{toast("Preparing report…");ProblemReports.share(this);});
        FestivalUi.listRow(this,body,"wrench","Cheats · separate profile","Trainer options that use their own modded profile.",()->TrainerPage.show(this,null));
        FestivalUi.listRow(this,body,"info","About","App version, credits and licenses.",this::about);
        return body;
    });}
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
            .setPositiveButton("Recovery options",(d,w)->{String[] choices=GraphicsProfilePage.lastGood(this).isFile()?new String[]{"Use system driver","Restore last working graphics"}:new String[]{"Use system driver"};FestivalUi.choose(this,"Recovery",choices,-1,index->{try{if(index==1)GraphicsProfilePage.restoreForRecovery(this);else MobileConfig.put(this,"android_gpu_driver","\"\"");toast("Recovery settings saved. Tap Play to try them.");}catch(IOException e){error(e.getMessage());}});}).show();
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
    private void endWork(){busy=false;operation=null;if(!isDestroyed())getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(progress!=null){progress.dismiss();progress=null;}if(!isDestroyed()&&!isFinishing())rebuildTop();}
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
