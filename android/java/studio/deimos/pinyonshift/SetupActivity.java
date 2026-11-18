package studio.deimos.pinyonshift;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Local extracted-folder import. Source files are checked before replacement. */
public final class SetupActivity extends Activity {
    private static final String TAG="FH4DroidSetup";
    private static final int REQUEST_FOLDER=0x5055, REQUEST_LINK=0x5056;
    private static final String INCOMPLETE="game/.copy-incomplete";
    static final String ARGUMENTS_FILE="state/config/android-arguments.txt";
    static final String CONFIG_FILE="state/config/pinyon_shift.toml";
    static final String PENDING_MODS_FILE="state/config/pending-enabled-mods.txt";
    private File base;
    private InstallationStatus status;
    private Button folderButton, linkButton, cancelCopyButton;
    private FileOperation operation;
    private volatile boolean busy;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);base=MobileConfig.base(this);buildLayout();refresh();
        if(state==null&&getIntent()!=null&&getIntent().getBooleanExtra("choose_folder",false))chooseFolder(getIntent().getBooleanExtra("link_folder",false));
    }
    private void buildLayout(){
        FrameLayout root=new FrameLayout(this);root.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout body=FestivalUi.column(this);
        status = new InstallationStatus(this, GameFiles.ready(this) ? "Game files ready." : "Choose the folder containing default.xex.");
        body.addView(status);
        body.addView(FestivalUi.text(this,"Use in place keeps the source files where they are when Android provides direct access. Copy needs about 8 GB of free space.",14,FestivalUi.MUTED));
        folderButton=FestivalUi.action(this,body,"Copy extracted folder",()->chooseFolder(false));
        linkButton=FestivalUi.action(this,body,"Use extracted folder in place",()->chooseFolder(true));
        cancelCopyButton=FestivalUi.action(this,body,"Cancel",()->{if(operation!=null)operation.cancel();cancelCopyButton.setEnabled(false);status.update("Stopping after the current read. Completed copies are kept.",0,0);});
        View panel=FestivalUi.panel(this,"Import game folder",body,this::finish);root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Rect safe=PlatformCompat.safeInsets(insets);int pad=FestivalUi.dp(this,12);root.setPadding(pad+safe.left,pad+safe.top,pad+safe.right,pad+safe.bottom);return insets;});
        setContentView(root);FestivalUi.immersive(this);
    }
    private static String size(long bytes){return String.format(Locale.ROOT,"%.1f MB",bytes/1048576.0);}
    private void refresh(){
        boolean running=GameFiles.running(this);
        if (running) status.finish("Quit the game before changing your installation.", false);
        folderButton.setEnabled(!busy&&!running);
        linkButton.setEnabled(!busy&&!running);
        cancelCopyButton.setVisibility(busy?View.VISIBLE:View.GONE);
    }
    @Override protected void onResume(){super.onResume();refresh();FestivalUi.immersive(this);}
    @Override protected void onDestroy(){if(operation!=null)operation.cancel();super.onDestroy();}
    private void chooseFolder(boolean useInPlace){
        if(busy||GameFiles.running(this)){refresh();return;}
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try{startActivityForResult(picker,useInPlace?REQUEST_LINK:REQUEST_FOLDER);}
        catch(android.content.ActivityNotFoundException error){status.finish("This device has no folder picker.",false);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if((request!=REQUEST_FOLDER&&request!=REQUEST_LINK)||result!=RESULT_OK||data==null||data.getData()==null)return;
        if(busy||GameFiles.running(this)){refresh();return;}
        boolean link = request == REQUEST_LINK;
        Uri tree=data.getData();busy=true;cancelCopyButton.setEnabled(true);status.update("Inspecting the selected folder…",0,0);
        operation=new FileOperation((name,done,total)->runOnUiThread(()->{if(busy&&!isFinishing()&&!isDestroyed())status.update(name,done,total);}));
        FileOperation current=operation;refresh();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(()->{
            try(FileOperation scope=current.attach()){
                if (link) linkFolder(tree); else importFolder(tree);
                complete(link ? "Folder verified and linked. Return to the launcher to play." : "Import complete. Return to the launcher to play.", true);
            } catch(IOException|RuntimeException error) {
                if(!current.isCancelled())Log.e(TAG,"Folder setup stopped",error);
                complete(current.isCancelled()?FileOperation.cancelMessage():error.getMessage(),false);
            }
        },"FolderImport").start();
    }
    private void linkFolder(Uri tree) throws IOException {
        File folder = GameFiles.directFolder(this, tree);
        GameFiles.verifyFolder(folder, GameFiles.catalog(this).values());
        FileOperation.check();
        try {
            getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException error) {
            throw new IOException("Android could not retain access to this folder. Choose Copy extracted folder.", error);
        }
        if (!getSharedPreferences("mobile", 0).edit().remove("iso_uri")
                .putString(GameFiles.LINKED_FOLDER, tree.toString()).commit()) {
            throw new IOException("Could not save the linked folder.");
        }
    }
    private void complete(String message,boolean success){runOnUiThread(()->{busy=false;operation=null;if(isFinishing()||isDestroyed())return;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.finish(message==null?"Could not complete folder setup.":message,success);refresh();});}

    // Retain compatibility with a pending mod list left by an older installation.
    static void applyEnabledMods(File config,String mods)throws IOException{
        if(!config.isFile())throw new FileNotFoundException("Game settings have not been created yet");
        MobileConfig.putFile(config,Collections.singletonMap("enabled_mods",MobileConfig.quote(mods)));
    }
    private File destination(String path)throws IOException{
        if(!path.startsWith("game/base/")||path.contains("\\"))throw new IOException("Unexpected game path: "+path);
        for(String part:path.split("/"))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Unexpected game path: "+path);
        File target=new File(base,path);
        if(!target.getCanonicalPath().startsWith(base.getCanonicalPath()+File.separator))throw new IOException("Game path escapes the app folder");
        return target;
    }
    private long lastReport;

    private void report(long done, long total, int number, int count, long started) {
        long now = System.currentTimeMillis();
        if (now - lastReport < 250) return;
        lastReport = now;
        double seconds = Math.max(1, (now - started) / 1000.0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(isFinishing()||isDestroyed())return;
                status.update("Importing · file " + number + " / " + count + " · " + size((long) (done / seconds)) + "/s", done, total);
            }
        });
    }

    interface Progress {
        void written(long bytes);
    }

    private static void copy(InputStream in, OutputStream out, Progress progress) throws IOException {
        byte[] buffer = new byte[1 << 17];
        long written = 0;
        for (int read; (read = in.read(buffer)) > 0; ) {
            FileOperation.check();
            out.write(buffer, 0, read);
            written += read;
            if (progress != null) progress.written(written);
        }
    }

    private static final class Entry {
        final Uri uri;
        final String path;
        final long size;

        Entry(Uri uri, String path, long size) {
            this.uri = uri;
            this.path = path;
            this.size = size;
        }
    }

    private void importFolder(Uri tree) throws IOException {
        String root = DocumentsContract.getTreeDocumentId(tree);
        List<Entry> listed = new ArrayList<>();
        list(tree, root, "", listed);
        Map<String, Entry> incoming = new LinkedHashMap<>();
        for (Entry entry : listed) {
            String name = entry.path.toLowerCase(Locale.ROOT);
            if (incoming.put(name, entry) != null) throw new IOException("duplicate game path: " + entry.path);
        }
        Map<String, GameFiles.Expected> catalog = GameFiles.catalog(this);
        List<GameFiles.Expected> needed = new ArrayList<>();
        long remaining = 0;
        for (Map.Entry<String, GameFiles.Expected> row : catalog.entrySet()) {
            GameFiles.Expected expected = row.getValue();
            Entry source = incoming.get(row.getKey());
            if (source == null || source.size != expected.size) throw new IOException("missing or unexpected " + expected.path + "; choose the complete extracted base-game folder");
            File target = destination("game/base/" + expected.path);
            if (!target.isFile() || target.length() != expected.size || !DiscImage.hash(target).equals(expected.hash)) {
                needed.add(expected);
                remaining += expected.size;
            }
        }
        // Reject a wrong executable before marking an existing install incomplete.
        for (Map.Entry<String, String> executable : GameFiles.EXECUTABLES.entrySet()) {
            Entry source = incoming.get(executable.getKey().toLowerCase(Locale.ROOT));
            try (InputStream in = getContentResolver().openInputStream(source.uri)) {
                if (in == null || !GameFiles.hash(in).equals(executable.getValue())) throw new IOException("unsupported " + executable.getKey() + "; this build uses base-game files");
            }
        }
        if (remaining > Math.max(0, base.getUsableSpace() - (256L << 20))) {
            throw new IOException("not enough free space: " + size(remaining) + " still needs copying");
        }
        FileOperation.check();
        File incomplete = new File(base, INCOMPLETE);
        incomplete.getParentFile().mkdirs();
        new FileOutputStream(incomplete).close();
        long done = 0;
        final long started = System.currentTimeMillis(), total = remaining;
        for (int i = 0; i < needed.size(); i++) {
            FileOperation.check();
            GameFiles.Expected expected = needed.get(i);
            Entry source = incoming.get(expected.path.toLowerCase(Locale.ROOT));
            File target = destination("game/base/" + expected.path), part = new File(target.getPath() + ".part");
            target.getParentFile().mkdirs();
            final long before = done; final int number = i + 1;
            try (InputStream in = getContentResolver().openInputStream(source.uri); OutputStream out = new FileOutputStream(part)) {
                if (in == null) throw new IOException("cannot read " + expected.path);
                copy(in, out, new Progress() {
                    @Override public void written(long bytes) { report(before + bytes, total, number, needed.size(), started); }
                });
            }
            if (part.length() != expected.size || !DiscImage.hash(part).equals(expected.hash)) {
                part.delete(); throw new IOException("damaged or incomplete " + expected.path + "; check the original game files");
            }
            java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            done += expected.size;
        }
        FileOperation.check();
        if(!getSharedPreferences("mobile", 0).edit().remove("iso_uri").remove(GameFiles.LINKED_FOLDER).commit())throw new IOException("cannot save folder mode; choose the same folder to finish");
        if (!incomplete.delete() && incomplete.exists()) throw new IOException("cannot finalize game import");
    }

    private void list(Uri tree,String document,String prefix,List<Entry> out)throws IOException{
        list(tree,document,prefix,out,0,new HashSet<>());
    }
    private void list(Uri tree,String document,String prefix,List<Entry> out,int depth,Set<String> visited)throws IOException{
        FileOperation.check();
        if(depth>32||out.size()>12000||!visited.add(document))throw new IOException("The selected folder is too deeply nested or contains a folder loop");
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,document);
        try(Cursor cursor=getContentResolver().query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE},null,null,null)){
            if(cursor==null)throw new IOException("Cannot list the selected folder");
            while(cursor.moveToNext()){
                FileOperation.check();String id=cursor.getString(0),name=cursor.getString(1);
                if(id==null||name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.contains("/")||name.contains("\\"))throw new IOException("Invalid file name in the selected folder");
                if(DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2)))list(tree,id,prefix+name+"/",out,depth+1,visited);
                else{if(out.size()>=12000)throw new IOException("Too many files in the selected folder");out.add(new Entry(DocumentsContract.buildDocumentUriUsingTree(tree,id),prefix+name,cursor.getLong(3)));}
            }
        }
    }
}
