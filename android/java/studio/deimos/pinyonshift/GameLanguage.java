package studio.deimos.pinyonshift;
import android.app.*;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.*;
/** Known native language/country pairs, offered only when their archive exists. */
final class GameLanguage {
    private static final String[][] LANGUAGES={{"English (US)","EN","1","103"},{"English (UK)","GB","1","35"},{"French","FR","4","34"},{"German","DE","3","24"},{"Italian","IT","6","50"},{"Spanish (Spain)","ES","5","31"},{"Spanish (Mexico)","MX","5","71"},{"Portuguese (Brazil)","BR","9","13"},{"Dutch","NL","16","74"},{"Danish","DA","1","25"},{"Norwegian","NB","15","75"},{"Swedish","SV","13","90"},{"Finnish","FI","1","32"},{"Polish","PL","11","82"},{"Czech","CZ","1","23"},{"Hungarian","HU","1","42"},{"Russian","RU","12","88"},{"Japanese","JP","2","53"},{"Korean","KO","7","56"},{"Chinese (Traditional)","CHT","8","101"}};
    static void show(Activity a){
        new Thread(()->{List<String[]> available=new ArrayList<>();String error=null;
            try{
                Set<String> archives=new HashSet<>();String iso=a.getSharedPreferences("mobile",0).getString("iso_uri","");
                if(!iso.isEmpty())try(ParcelFileDescriptor fd=a.getContentResolver().openFileDescriptor(android.net.Uri.parse(iso),"r")){if(fd==null)throw new IOException("Game image unavailable.");try(FileInputStream input=new FileInputStream(fd.getFileDescriptor())){DiscImage disc=new DiscImage(input.getChannel());for(String name:disc.files.keySet())if(name.startsWith("media/stringtables/"))archives.add(name.toLowerCase(Locale.ROOT));}}
                else {File folder=new File(MobileConfig.base(a),"game/base/media");File[] media=folder.listFiles(f->f.isDirectory()&&f.getName().equalsIgnoreCase("stringtables"));if(media!=null)for(File table:media){File[] files=table.listFiles(File::isFile);if(files!=null)for(File f:files)archives.add("media/stringtables/"+f.getName().toLowerCase(Locale.ROOT));}}
                for(String[] language:LANGUAGES)if(archives.contains("media/stringtables/"+language[1].toLowerCase(Locale.ROOT)+".zip"))available.add(language);
            }catch(Exception e){error="Could not inspect your game language archives. "+e.getMessage();}
            final String failure=error;a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;if(failure!=null){FestivalUi.explain(a,"Game language",failure);return;}if(available.isEmpty()){FestivalUi.explain(a,"Game language","No language archives found in your owned game files. Import the complete game or select its disc image first.");return;}
                String[] names=new String[available.size()];int selected=-1;for(int i=0;i<names.length;i++){String[] row=available.get(i);names[i]=row[0];if(row[2].equals(MobileConfig.get(a,"user_language","1"))&&row[3].equals(MobileConfig.get(a,"user_country","103")))selected=i;}
                FestivalUi.choose(a,"Game language · next launch",names,selected,index->{String[] row=available.get(index);Map<String,String> values=new LinkedHashMap<>();values.put("user_language",row[2]);values.put("user_country",row[3]);SettingsHub.apply(a,null,values,true);});
            });
        },"GameLanguages").start();
    }
    private GameLanguage(){}
}
