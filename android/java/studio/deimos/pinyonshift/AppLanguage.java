package studio.deimos.pinyonshift;
import android.app.Activity;
import android.content.Context;
import java.util.*;
/** UI language is independent of the emulated console language. */
final class AppLanguage {
    private static final Map<String,String> FILIPINO=new LinkedHashMap<>();
    static {
        String[][] rows={{"Play","Maglaro"},{"Settings","Mga setting"},{"More","Iba pa"},{"Graphics","Grapiko"},{"Controls","Mga kontrol"},{"Audio","Tunog"},{"Interface","Interface"},{"Gameplay","Paglalaro"},{"Advanced","Advanced"},{"Saves","Mga save"},{"Profile","Profile"},{"Back","Bumalik"},{"Cancel","Kanselahin"},{"Save","I-save"},{"Apply","Ilapat"},{"Resume","Ituloy"},{"Paused","Naka-pause"},{"Game files","Mga file ng laro"},{"Graphics drivers","Mga driver ng grapiko"},{"Achievements","Mga achievement"},{"Report a problem","Mag-ulat ng problema"},{"Quit to launcher","Bumalik sa launcher"},{"Edit touch layout","Ayusin ang touch layout"},{"Touch layout","Touch layout"},{"Properties","Mga katangian"},{"Canvas","Canvas"},{"Test","Subukan"},{"Stop test","Itigil ang pagsubok"},{"Edit","Baguhin"},{"Show tools","Ipakita ang mga tool"},{"Actions","Mga aksyon"},{"Export saves","I-export ang mga save"},{"Import saves","Mag-import ng mga save"},{"Restore previous saves","Ibalik ang nakaraang mga save"},{"My graphics profiles","Aking mga profile ng grapiko"},{"Graphics presets","Mga preset ng grapiko"},{"Show FPS","Ipakita ang FPS"},{"Automatic save backups","Awtomatikong backup ng save"},{"Phone tilt and vibration","Pagtagilid at pag-vibrate ng telepono"},{"Physical controller","Pisikal na controller"},{"Saved layouts and presets","Mga naka-save na layout at preset"},{"Touch buttons and steering","Mga touch button at pagliko"},{"Camera field of view","Lawak ng tanaw ng camera"},{"Texture size","Laki ng texture"},{"Texture filtering","Pag-filter ng texture"},{"Master volume","Pangkalahatang lakas ng tunog"},{"Mute audio","I-mute ang tunog"},{"Game radio and music","Radyo at musika ng laro"},{"Native game renderer","Native na renderer ng laro"},{"Dynamic rendering","Dynamic na rendering"},{"Camera G-force motion","Galaw ng camera sa G-force"},{"Camera lean","Pagtagilid ng camera"},{"Camera speed shake","Pag-uga ng camera sa bilis"},{"Camera head motion","Galaw ng ulo ng camera"},{"Local achievements","Mga lokal na achievement"},{"Save backups","Mga backup ng save"},{"Mark a visual issue","Markahan ang problema sa larawan"},{"Advanced settings","Mga advanced na setting"}};
        for(String[] row:rows)FILIPINO.put(row[0],row[1]);
    }
    static String translate(String language,String text){return "fil".equals(language)?FILIPINO.getOrDefault(text,text):text;}
    static String text(Context c,String text){return translate(c.getSharedPreferences("mobile",0).getString("app_language","en"),text);}
    static void show(Activity a){
        FestivalUi.choose(a,"App language · game language is separate",new String[]{"English","Filipino · core menus; detailed help stays English"},"fil".equals(a.getSharedPreferences("mobile",0).getString("app_language","en"))?1:0,index->{a.getSharedPreferences("mobile",0).edit().putString("app_language",index==1?"fil":"en").apply();UiPreferences.save(a);if(a instanceof PinyonShiftActivity)FestivalUi.explain(a,"App language","The new language applies when you reopen a menu. Your game keeps running.");else a.recreate();});
    }
    private AppLanguage(){}
}
