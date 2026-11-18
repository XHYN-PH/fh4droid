package studio.deimos.pinyonshift;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Mod, Marketplace DLC and prepared-shader management while the game is closed. */
public final class ContentActivity extends Activity implements FestivalUi.PanelHost {
    private static final int MOD_ZIP=501,MOD_FOLDER=502,DLC_FILES=503,DLC_FOLDER=504,SHADERS=505,RALLY_FOLDER=506;
    private FrameLayout host;private int tab;private boolean busy;private FileOperation operation;private AlertDialog progress;
    private boolean loading;private int scanGeneration;
    private String selectedMod="",selectedDlc="";
    private List<JSONObject> modItems=Collections.emptyList(),dlcItems=Collections.emptyList();
    private JSONObject modOptions=new JSONObject();private List<String> enabledMods=Collections.emptyList();
    private String optionsProblem="";
    private static final class Snapshot{
        List<JSONObject> mods,dlc;List<String> enabled;JSONObject options;String problem="";
        Snapshot(Context context)throws Exception{mods=ModStore.list(context);dlc=DlcStore.list(context);enabled=ModStore.enabled(context);try{options=ModStore.options(context);}catch(Exception error){options=new JSONObject();problem=error.getMessage();}}
    }
    private final java.util.concurrent.ExecutorService scans=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"ContentScan"));
    private final ArrayDeque<Page> pages=new ArrayDeque<>();
    private static final class Page{final String title;final View body;Page(String title,View body){this.title=title;this.body=body;}}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);UiPreferences.load(this);if(state!=null){tab=state.getInt("tab");selectedMod=state.getString("mod","");selectedDlc=state.getString("dlc","");}
        FrameLayout canvas=new FrameLayout(this);FestivalUi.Artwork artwork=new FestivalUi.Artwork(this);artwork.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(14,14,android.graphics.Shader.TileMode.CLAMP));canvas.addView(artwork,new FrameLayout.LayoutParams(-1,-1));host=new FrameLayout(this);canvas.addView(host,new FrameLayout.LayoutParams(-1,-1));
        canvas.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Rect safe=PlatformCompat.safeInsets(insets);host.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});setContentView(canvas);refresh();FestivalUi.immersive(this);
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("tab",tab);state.putString("mod",selectedMod);state.putString("dlc",selectedDlc);}
    @Override protected void onDestroy(){scanGeneration++;scans.shutdownNow();if(operation!=null)operation.cancel();if(progress!=null)progress.dismiss();super.onDestroy();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)FestivalUi.immersive(this);}
    @Override public void showPanel(String title,View body){pages.addLast(new Page(title,body));ActionBreadcrumbs.record(this,"Content: "+title,"view");render();}
    @Override public void replacePanel(String title,View body){if(!pages.isEmpty())pages.removeLast();showPanel(title,body);}
    @Override public void backPanel(){if(busy){Toast.makeText(this,"Cancel the current operation first.",Toast.LENGTH_SHORT).show();return;}if(pages.size()>1){pages.removeLast();selectedMod="";selectedDlc="";render(true);}else finish();}
    @Override public void onBackPressed(){backPanel();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(ControllerPrompts.handle(this,getWindow(),event.getKeyCode(),event,this::onBackPressed))return true;return super.dispatchKeyEvent(event);}
    private void render(){render(false);}
    private void render(boolean reverse){Page page=pages.peekLast();if(page==null)return;FestivalUi.rememberTree(host);host.removeAllViews();View panel=FestivalUi.panel(this,page.title,page.body,this::backPanel);host.addView(panel,new FrameLayout.LayoutParams(-1,-1));MenuMotion.page(panel,reverse);}
    private void refresh(){
        if(isFinishing()||isDestroyed())return;loading=true;final int generation=++scanGeneration;
        if(pages.isEmpty()){LinearLayout wait=FestivalUi.column(this);wait.addView(FestivalUi.text(this,"Loading installed content…",16,FestivalUi.TEXT));showPanel("Mods and DLC",wait);}
        scans.execute(()->{try{
            final Snapshot snapshot=ContentFiles.locked(this,()->new Snapshot(this));
            runOnUiThread(()->{if(generation!=scanGeneration||isFinishing()||isDestroyed())return;loading=false;modItems=snapshot.mods;dlcItems=snapshot.dlc;modOptions=snapshot.options;enabledMods=snapshot.enabled;optionsProblem=snapshot.problem;rebuild();});
        }catch(Exception error){runOnUiThread(()->{if(generation!=scanGeneration||isFinishing()||isDestroyed())return;loading=false;pages.clear();LinearLayout retry=FestivalUi.column(this);retry.addView(FestivalUi.text(this,"Content could not be loaded: "+error.getMessage(),14,FestivalUi.TEXT));FestivalUi.action(this,retry,"Try again",this::refresh);showPanel("Mods and DLC",retry);});}});
    }
    private void selectTab(int index) {
        if (busy || loading) return;
        tab = Math.floorMod(index, 3);
        selectedMod = "";
        selectedDlc = "";
        rebuild();
    }
    private void rebuild(){
        FestivalUi.rememberTree(host);Page oldRoot=pages.peekFirst(),oldDetail=pages.size()>1?pages.peekLast():null;
        pages.clear();LinearLayout body=FestivalUi.column(this);LinearLayout tabs=new LinearLayout(this);body.addView(tabs);
        String[] names={"Mods","DLC","Shaders"};for(int i=0;i<names.length;i++){final int selected=i;Button button=FestivalUi.button(this,names[i],false);button.setSelected(tab==i);tabs.addView(button,new LinearLayout.LayoutParams(0,-2,1));button.setOnClickListener(v->selectTab(selected));}
        ControllerPrompts.tabs(body, direction -> selectTab(tab + direction));
        try{if(tab==0)mods(body);else if(tab==1)dlc(body);else shaders(body);}
        catch(Exception error){body.addView(FestivalUi.text(this,"Content could not be loaded: "+error.getMessage(),14,FestivalUi.MUTED));}
        if(oldRoot!=null)FestivalUi.transferPageState(oldRoot.body,body);showPanel("Mods and DLC",body);
        if(!selectedMod.isEmpty()){String id=selectedMod;if(find(modItems,"name",id)!=null)modDetails(id);else selectedMod="";}
        else if(!selectedDlc.isEmpty()){JSONObject item=find(dlcItems,"package_id",selectedDlc);if(item!=null)dlcDetails(item);}
        if(oldDetail!=null&&pages.size()>1){FestivalUi.transferPageState(oldDetail.body,pages.peekLast().body);render();}
    }
    private static JSONObject find(List<JSONObject> items,String key,String id){for(JSONObject item:items)if(id.equals(item.optString(key)))return item;return null;}
    private boolean available(){if(busy||loading)return false;if(GameFiles.running(this)){error("Quit the game before changing content.");return false;}return true;}
    private void mods(LinearLayout body)throws Exception{
        FestivalUi.listRow(this,body,"wrench","Cheats · separate profile","Trainer options keep standard progress separate.",()->TrainerPage.show(this,null));
        body.addView(FestivalUi.text(this,"Changes apply at the next launch. Earlier mods win conflicts. Database mods use a separate modded profile.",13,FestivalUi.MUTED));
        if(optionsProblem!=null&&!optionsProblem.isEmpty())body.addView(FestivalUi.text(this,"Saved mod options could not be read. Disable optional mods for recovery: "+optionsProblem,14,FestivalUi.ORANGE));
        FestivalUi.tileRow(this,body,FestivalUi.tile(this,"file","Import ZIP","One mod package",()->pick(MOD_ZIP,false,false)),FestivalUi.tile(this,"folder","Import folder","Choose a mod folder",()->pick(MOD_FOLDER,true,false)));
        builtin(body,"festival_pacing","Festival pacing");
        builtin(body,"hidden_cars","Hidden cars");
        if(!enabledMods.isEmpty())FestivalUi.action(this,body,"Disable all optional mods",this::disableMods);
        for(JSONObject item:modItems){
            String id=item.getString("name"),title=item.optString("title",id);boolean on=item.optBoolean("enabled");String problem=item.optString("problem","");
            FestivalUi.listRow(this,body,"layers",title,(on?"Enabled · priority "+(item.optInt("order")+1):"Disabled")+(problem.isEmpty()?"":" · "+problem),()->modDetails(id));
        }
        body.addView(FestivalUi.text(this,"Game-file overlays, database scripts and Android ARM64 plugins are supported. Mods that rebuild archive members need preparation with the PC mod tools before import.",12,FestivalUi.MUTED));
    }
    private void builtin(LinearLayout body,String id,String title){
        boolean installed=new File(ModStore.root(this),id).isDirectory();
        FestivalUi.action(this,body,installed?title+" · details":"Install "+title,()->{
            if(installed)modDetails(id);else perform("Installing "+title+"…",()->{ModStore.installBuiltin(this,id);return "Installed. Open its details to enable it.";});
        });
    }
    private void disableMods(){
        new AlertDialog.Builder(this).setTitle("Disable optional mods?").setMessage("Return to the normal profile next launch. Installed mods, their options and both save profiles are kept.").setNegativeButton("Cancel",null).setPositiveButton("Disable",(d,w)->perform("Disabling mods…",()->{ModStore.disableAll(this);return "Optional mods disabled for the next launch.";})).show();
    }
    private void modDetails(String id){
        selectedMod=id;selectedDlc="";
        try{JSONObject item=find(modItems,"name",id);if(item==null)throw new IOException("Mod is no longer installed.");if(item.has("problem"))throw new IOException(item.optString("problem"));LinearLayout body=FestivalUi.column(this);body.addView(FestivalUi.text(this,item.optString("description",""),14,FestivalUi.TEXT));boolean on=enabledMods.contains(id);
            FestivalUi.action(this,body,on?"Disable mod":"Enable mod",()->perform("Updating mods…",()->{ModStore.setEnabled(this,id,!on);return "Mod selection saved for the next launch.";})).setTag(FestivalUi.FOCUS_KEY,"mod-toggle:"+id);
            if(on){FestivalUi.action(this,body,"Move earlier",()->perform("Updating load order…",()->{ModStore.move(this,id,-1);return "Load order saved.";}));FestivalUi.action(this,body,"Move later",()->perform("Updating load order…",()->{ModStore.move(this,id,1);return "Load order saved.";}));}
            JSONObject stored=modOptions.optJSONObject(id);JSONArray options=item.optJSONArray("option");
            if(options!=null)for(int i=0;i<options.length();i++){
                JSONObject option=options.getJSONObject(i);String key=option.getString("key"),current=stored==null?option.getString("default"):stored.optString(key,option.getString("default"));JSONArray choices=option.getJSONArray("choices");String[] labels=new String[choices.length()],values=new String[choices.length()];int selected=-1;
                for(int j=0;j<choices.length();j++){Object choice=choices.get(j);values[j]=choice instanceof JSONObject?((JSONObject)choice).getString("value"):String.valueOf(choice);labels[j]=choice instanceof JSONObject?((JSONObject)choice).optString("label",values[j]):values[j];if(current.equals(values[j]))selected=j;}
                final int checked=selected;FestivalUi.listRow(this,body,"sliders",option.optString("title",key),current,()->FestivalUi.choose(this,option.optString("title",key),labels,checked,index->perform("Applying mod option…",()->{ModStore.setOption(this,id,key,values[index]);return "Option saved; the game database was rebuilt if needed.";}))).setTag(FestivalUi.FOCUS_KEY,"mod-option:"+id+":"+key);
            }
            FestivalUi.action(this,body,"Remove mod",()->new AlertDialog.Builder(this).setTitle("Remove "+item.optString("title",id)+"?").setMessage("Disable it first. Your saved game is kept.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->perform("Removing mod…",()->{ModStore.remove(this,id);return "Mod removed.";})).show());showPanel(item.optString("title",id),body);
        }catch(Exception error){
            LinearLayout recovery=FestivalUi.column(this);recovery.addView(FestivalUi.text(this,"This mod could not be read: "+error.getMessage(),14,FestivalUi.TEXT));
            if(enabledMods.contains(id))FestivalUi.action(this,recovery,"Disable all optional mods",this::disableMods);
            FestivalUi.action(this,recovery,"Remove disabled mod",()->new AlertDialog.Builder(this).setTitle("Remove "+id+"?").setMessage("Your saves are kept. Enabled mods must be disabled first.").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->perform("Removing mod…",()->{ModStore.remove(this,id);return "Mod removed.";})).show());
            showPanel("Mod recovery",recovery);
        }
    }
    private void dlc(LinearLayout body)throws Exception{
        body.addView(FestivalUi.text(this,"Import your original Marketplace packages, ZIPs containing packages, or a folder. Nested ZIPs are supported up to three levels. Imports start disabled.",13,FestivalUi.MUTED));
        FestivalUi.tileRow(this,body,FestivalUi.tile(this,"file","Import packages / ZIPs","Choose one or more files",()->pick(DLC_FILES,false,true)),FestivalUi.tile(this,"folder","Import folder","Find packages in a folder",()->pick(DLC_FOLDER,true,false)));
        FestivalUi.action(this,body,"UDLC download page",()->external("https://www.moddb.com/mods/forza-horizon-udlc-pack/downloads/udlcv12"));
        FestivalUi.action(this,body,"Traffic Car Unlocker page",()->external("https://www.moddb.com/mods/forza-horizon-udlc-pack/downloads/udlc-traffic-car-unlocker"));
        body.addView(FestivalUi.text(this,"UDLC and its traffic unlocker are optional community content. Cars and upgrades need device testing; some Fast & Furious cars are known to stall in the paint shop. Keep a save backup.",12,FestivalUi.MUTED));
        for(JSONObject item:dlcItems){
            String id=item.getString("package_id"),title=item.getString("display_name");boolean installed=item.optBoolean("installed"),enabled=item.optBoolean("enabled");
            FestivalUi.listRow(this,body,"folder",title,item.optBoolean("conflict")?"Conflicting installed copies":enabled?"Enabled":installed?"Installed · disabled":"Not installed",()->dlcDetails(item));
        }
    }
    private void dlcDetails(JSONObject item){
        String id=item.optString("package_id"),title=item.optString("display_name");boolean enabled=item.optBoolean("enabled");
        if(!item.optBoolean("installed")){FestivalUi.explain(this,title,"Import this package from your own files first.");return;}selectedDlc=id;selectedMod="";LinearLayout details=FestivalUi.column(this);
                FestivalUi.action(this,details,enabled?"Disable":"Enable",()->perform("Updating DLC…",()->{DlcStore.enabled(this,id,!enabled);return "DLC selection saved for the next launch.";})).setTag(FestivalUi.FOCUS_KEY,"dlc-toggle:"+id);
                FestivalUi.action(this,details,"Verify installed files",()->perform("Verifying DLC…",()->ContentFiles.locked(this,()->{DlcStore.verify(this,id);return "Installed DLC matches the verified catalog.";})));
                if(id.equals(DlcStore.RALLY)){
                    details.addView(FestivalUi.text(this,"Rally also needs the adapter folder prepared from your files by upstream 0.8.2 setup. The import checks its package, base inputs and generated-file hashes.",13,FestivalUi.MUTED));
                    FestivalUi.action(this,details,"Import prepared Rally folder",()->pick(RALLY_FOLDER,true,false));
                }showPanel(title,details);
    }
    private void shaders(LinearLayout body){
        File file=ShaderPreparation.installed(this);body.addView(FestivalUi.text(this,file.isFile()?"A prepared free-roam pipeline list is installed.":"No prepared free-roam pipeline list is installed.",15,FestivalUi.TEXT));
        body.addView(FestivalUi.text(this,"Prepare the pipeline list with the updated PC setup from your own game files, then import the .xpso file here. The renderer checks its shader hashes while loading it.",13,FestivalUi.MUTED));
        FestivalUi.action(this,body,"Import prepared pipeline list",()->pick(SHADERS,false,false));
        body.addView(FestivalUi.text(this,"Stored and imported pipelines are prepared before the game starts.",13,FestivalUi.MUTED));
    }
    private void pick(int request,boolean folder,boolean multiple){if(!available())return;Intent intent=new Intent(folder?Intent.ACTION_OPEN_DOCUMENT_TREE:Intent.ACTION_OPEN_DOCUMENT);if(!folder){intent.addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);}intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivityForResult(intent,request);}catch(ActivityNotFoundException error){error("No file picker is available.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||!available())return;List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());if(uris.isEmpty())return;
        perform("Importing content…",()->{File temporary=ContentFiles.temporary(this,"picker-");try{
            if(request==MOD_FOLDER||request==DLC_FOLDER||request==RALLY_FOLDER){ContentFiles.folder(this,uris.get(0),temporary);if(request==MOD_FOLDER){ModStore.importDirectory(this,temporary);return "Mod imported; it starts disabled.";}if(request==RALLY_FOLDER){RallyPreparation.importFolder(this,temporary);return "Prepared Rally files imported.";}return DlcStore.importFiles(this,Collections.singletonList(temporary));}
            List<File> files=new ArrayList<>();Map<File,String> displayNames=new HashMap<>();long remaining=ContentFiles.MAX_BYTES;if(uris.size()>ContentFiles.MAX_FILES)throw new IOException("Too many selected files.");for(Uri uri:uris){File file=new File(temporary,UUID.randomUUID()+".input");try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE},null,null,null)){if(cursor!=null&&cursor.moveToFirst()){String name=cursor.getString(0);if(name!=null)displayNames.put(file,name.replaceAll("[\\p{Cntrl}]"," "));if(!cursor.isNull(1))ContentFiles.requireSpace(temporary,Math.max(0,cursor.getLong(1)));}}
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("Selected file is unavailable.");remaining-=ContentFiles.copyToFile(in,file,remaining);}files.add(file);}
            if(request==MOD_ZIP){ModStore.importZip(this,files.get(0));return "Mod imported; it starts disabled.";}if(request==SHADERS){ShaderPreparation.install(this,files.get(0));return "Prepared pipeline list imported.";}if(request==DLC_FILES){return DlcStore.importFiles(this,files,displayNames);}throw new IOException("Unknown content request.");
        }finally{ContentFiles.delete(temporary);}});
    }
    private void perform(String title,ContentFiles.Work<String> work){
        if(!available())return;busy=true;operation=new FileOperation((name,done,total)->runOnUiThread(()->{if(progress!=null)progress.setMessage(name+(total>0?" · "+done/1048576+" / "+total/1048576+" MB":""));}));FileOperation current=operation;
        ActionBreadcrumbs.record(this,title,"starting");
        progress=new AlertDialog.Builder(this).setTitle(title).setMessage("Working…").setCancelable(false).setNegativeButton("Cancel",null).create();progress.show();progress.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{current.cancel();v.setEnabled(false);progress.setMessage("Stopping and recovering the current transaction…");});getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(()->{String message;boolean success=false;try(FileOperation scope=current.attach()){message=work.run();success=true;}catch(Exception error){message=current.isCancelled()?"Import cancelled. Completed DLC packages are kept.":error.getMessage();}
            final String result=message;final boolean ok=success;ActionBreadcrumbs.record(this,title,success?"complete":current.isCancelled()?"cancelled":"failed");runOnUiThread(()->{busy=false;operation=null;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(progress!=null){progress.dismiss();progress=null;}if(isFinishing()||isDestroyed())return;refresh();if(ok){if(result!=null&&result.contains("\n"))new AlertDialog.Builder(this).setTitle("Content import report").setMessage(result).setPositiveButton("OK",null).show();else Toast.makeText(this,result,Toast.LENGTH_LONG).show();}else error(result);});},"ContentManager").start();
    }
    private void external(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(ActivityNotFoundException error){error("No browser is available.");}}
    private void error(String message){if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("Content operation stopped").setMessage(message==null?"Content could not be changed.":message).setPositiveButton("OK",null).show();}
}
