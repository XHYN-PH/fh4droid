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
    private static final int REQUEST_FOLDER=0x5055;
    private static final String INCOMPLETE="game/.copy-incomplete";
    static final String ARGUMENTS_FILE="state/config/android-arguments.txt";
    static final String CONFIG_FILE="state/config/pinyon_shift.toml";
    static final String PENDING_MODS_FILE="state/config/pending-enabled-mods.txt";
    private File base;
    private TextView intro,progressText;
    private Button folderButton,cancelCopyButton;
    private ProgressBar progress;
    private FileOperation operation;
    private volatile boolean busy;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);base=MobileConfig.base(this);buildLayout();refresh();
        if(state==null&&getIntent()!=null&&getIntent().getBooleanExtra("choose_folder",false))chooseFolder();
    }
    private void buildLayout(){
        FrameLayout root=new FrameLayout(this);root.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout body=FestivalUi.column(this);
        intro=FestivalUi.text(this,"",15,FestivalUi.MUTED);body.addView(intro);
        body.addView(FestivalUi.text(this,"Choose the complete extracted base-game folder containing default.xex. Allow about 8 GB of space for the imported copy. You can also select an ISO from the launcher's Game files page.",14,FestivalUi.MUTED));
        folderButton=FestivalUi.action(this,body,"Choose extracted game folder",this::chooseFolder);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);progress.setVisibility(View.GONE);body.addView(progress);
        progressText=FestivalUi.text(this,"",14,FestivalUi.TEXT);body.addView(progressText);
        cancelCopyButton=FestivalUi.action(this,body,"Cancel import",()->{if(operation!=null)operation.cancel();cancelCopyButton.setEnabled(false);progressText.setText("Stopping after the current read. Completed files are kept.");});
        View panel=FestivalUi.panel(this,"Import game folder",body,this::finish);root.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int pad=FestivalUi.dp(this,12);root.setPadding(pad+safe.left,pad+safe.top,pad+safe.right,pad+safe.bottom);return insets;});
        setContentView(root);FestivalUi.immersive(this);
    }
    private static String size(long bytes){return String.format(Locale.ROOT,"%.1f MB",bytes/1048576.0);}
    private void refresh(){
        boolean running=GameFiles.running(this);
        intro.setText(running?"Quit the game before importing files.":GameFiles.ready(this)?"Your game is installed. Choose the same source folder to resume an import or repair files.":"Add your own supported Forza Horizon (2012) base-game files.");
        folderButton.setEnabled(!busy&&!running);cancelCopyButton.setVisibility(busy?View.VISIBLE:View.GONE);
    }
    @Override protected void onResume(){super.onResume();refresh();FestivalUi.immersive(this);}
    @Override protected void onDestroy(){if(operation!=null)operation.cancel();super.onDestroy();}
    private void chooseFolder(){
        if(busy||GameFiles.running(this)){refresh();return;}
        try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),REQUEST_FOLDER);}
        catch(android.content.ActivityNotFoundException error){progressText.setText("This device has no folder picker.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=REQUEST_FOLDER||result!=RESULT_OK||data==null||data.getData()==null)return;
        if(busy||GameFiles.running(this)){refresh();return;}
        Uri tree=data.getData();busy=true;cancelCopyButton.setEnabled(true);progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);progressText.setText("Inspecting the selected folder…");
        operation=new FileOperation((name,done,total)->runOnUiThread(()->{if(!busy||isFinishing()||isDestroyed())return;progressText.setText(name+" · "+size(done)+" / "+size(total));progress.setIndeterminate(total<=0);if(total>0)progress.setProgress((int)Math.min(1000,done*1000/total));}));
        FileOperation current=operation;refresh();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(()->{try(FileOperation scope=current.attach()){importFolder(tree);complete("Import complete. Return to the launcher to play.",true);}
            catch(IOException|RuntimeException error){if(!current.isCancelled())Log.e(TAG,"Folder import stopped",error);complete(current.isCancelled()?FileOperation.cancelMessage():"Import stopped: "+error.getMessage()+". Choose the same source folder to resume.",false);}},"FolderImport").start();
    }
    private void complete(String message,boolean success){runOnUiThread(()->{busy=false;operation=null;if(isFinishing()||isDestroyed())return;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);progress.setIndeterminate(false);if(success)progress.setProgress(1000);progressText.setText(message);refresh();});}

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
        final String line = size(done) + " of " + size(total) + " · file " + number + " of " + count
                + " · " + size((long) (done / seconds)) + "/s";
        final int value = total > 0 ? (int) (done * 1000 / total) : 0;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(isFinishing()||isDestroyed())return;
                progress.setIndeterminate(total<=0);
                progress.setProgress(value);
                progressText.setText(line);
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
        if(!getSharedPreferences("mobile", 0).edit().remove("iso_uri").commit())throw new IOException("cannot save folder mode; choose the same folder to finish");
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
