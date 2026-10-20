package studio.deimos.pinyonshift;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Grants only a read-only view of the one explicitly exported report. */
public final class ReportProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{
        if(!"/fh4droid-report.zip".equals(uri.getPath()))throw new FileNotFoundException();
        return new File(getContext().getCacheDir(),"reports/fh4droid-report.zip");
    }
    public String getType(Uri uri){return "application/zip";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        try{File f=file(uri);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{f.getName(),f.length()});return c;}
        catch(FileNotFoundException e){return null;}
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
}
