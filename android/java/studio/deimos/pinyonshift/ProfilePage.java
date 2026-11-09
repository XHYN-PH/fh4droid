package studio.deimos.pinyonshift;

import android.app.*;
import android.graphics.*;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline profile UI reusing the runtime's identity, picture and achievements. */
final class ProfilePage {
    static void show(Activity a,Runnable choosePicture,Runnable saves){
        LinearLayout body=FestivalUi.column(a);Map<String,String> settings=MobileConfig.read(a);
        body.addView(FestivalUi.text(a,settings.getOrDefault("user_name","User")+" · Offline profile",18,FestivalUi.TEXT));
        body.addView(FestivalUi.text(a,"Next launch: "+LocalProfile.configuredProfile(settings)+"\n"+LocalProfile.backupStatus(settings),13,FestivalUi.MUTED));
        body.addView(FestivalUi.text(a,"Your local name, picture and achievements do not connect to Xbox LIVE. Online Rivals, storefront and multiplayer require services this app does not provide. Changing your name or picture keeps your existing save identity.",13,FestivalUi.MUTED));
        FestivalUi.action(a,body,"Edit gamertag · next launch",()->{
            EditText input=new EditText(a);input.setSingleLine(true);input.setText(settings.getOrDefault("user_name","User"));
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(15)});
            AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Local gamertag").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
            dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{String name=LocalProfile.gamertag(input.getText().toString());MobileConfig.put(a,"user_name",MobileConfig.quote(name));dialog.dismiss();Toast.makeText(a,"Gamertag saved for the next launch",Toast.LENGTH_SHORT).show();}
                catch(IllegalArgumentException|IOException error){input.setError(error.getMessage());}
            }));dialog.show();
        });
        if(choosePicture!=null)FestivalUi.action(a,body,"Choose gamer picture · next launch",choosePicture);
        FestivalUi.action(a,body,"Local achievements",()->AchievementsPage.show(a,null));
        if(saves!=null)FestivalUi.action(a,body,"Save backups",saves);
        FestivalUi.action(a,body,"Automatic save backups",()->FestivalUi.choose(a,"Automatic save backups · next launch",new String[]{"On","Off"},"false".equals(MobileConfig.get(a,"pinyon_shift_save_backups","true"))?1:0,index->{
            try{MobileConfig.put(a,"pinyon_shift_save_backups",Boolean.toString(index==0));Toast.makeText(a,"Backup preference saved for the next launch",Toast.LENGTH_SHORT).show();}
            catch(IOException error){SettingsHub.error(a,error);}
        }));
        FestivalUi.present(a,"Profile",body);
    }
    static void importPicture(Activity a,Uri uri)throws Exception{
        byte[] data;
        try(InputStream in=a.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IOException("Cannot open this image.");byte[] buffer=new byte[8192];
            for(int n;(n=in.read(buffer))!=-1;){FileOperation.check();if(out.size()+n>8*1024*1024)throw new IOException("Choose an image smaller than 8 MB.");out.write(buffer,0,n);}data=out.toByteArray();
        }
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>16384||bounds.outHeight>16384||(long)bounds.outWidth*bounds.outHeight>64000000)throw new IOException("Choose a valid image up to 64 megapixels.");
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>512)options.inSampleSize=options.inSampleSize<=1?2:options.inSampleSize*2;
        Bitmap source=BitmapFactory.decodeByteArray(data,0,data.length,options);if(source==null)throw new IOException("The image could not be decoded.");
        // Phone JPEGs often store rotation/mirroring in EXIF rather than pixels.
        // Apply it before the centre crop, with the already sampled image.
        Matrix orientation=new Matrix();int exif=1;
        try{exif=new android.media.ExifInterface(new ByteArrayInputStream(data)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
        switch(exif){
            case 2:orientation.setScale(-1,1);break;
            case 3:orientation.setRotate(180);break;
            case 4:orientation.setScale(1,-1);break;
            case 5:orientation.setRotate(90);orientation.postScale(-1,1);break;
            case 6:orientation.setRotate(90);break;
            case 7:orientation.setRotate(90);orientation.postScale(1,-1);break;
            case 8:orientation.setRotate(270);break;
            default:break;
        }
        if(!orientation.isIdentity()){Bitmap rotated=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),orientation,true);if(rotated!=source){source.recycle();source=rotated;}}
        Bitmap icon=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
        try{
            int side=Math.min(source.getWidth(),source.getHeight()),left=(source.getWidth()-side)/2,top=(source.getHeight()-side)/2;
            new Canvas(icon).drawBitmap(source,new Rect(left,top,left+side,top+side),new Rect(0,0,128,128),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            ByteArrayOutputStream png=new ByteArrayOutputStream();if(!icon.compress(Bitmap.CompressFormat.PNG,100,png))throw new IOException("Cannot encode this picture.");
            byte[] pixels=png.toByteArray();byte[] digest=MessageDigest.getInstance("SHA-256").digest(pixels);StringBuilder hash=new StringBuilder();for(byte b:digest)hash.append(String.format(Locale.ROOT,"%02x",b&255));
            File file=new File(MobileConfig.base(a),"state/config/gamerpic-"+hash+".png");Files.createDirectories(file.getParentFile().toPath());boolean existed=file.isFile();
            if(!existed){java.nio.file.Path stage=Files.createTempFile(file.getParentFile().toPath(),"gamerpic-",".part");try{Files.write(stage,pixels);Files.move(stage,file.toPath(),StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(stage);}}
            try{MobileConfig.put(a,"user_gamerpic",MobileConfig.quote(file.getAbsolutePath()));}catch(IOException error){if(!existed)Files.deleteIfExists(file.toPath());throw error;}
        }finally{source.recycle();icon.recycle();}
    }
    private ProfilePage(){}
}
