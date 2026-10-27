package studio.deimos.pinyonshift;

import java.util.Map;

/** Local identity helpers. A display-name edit must never change save ownership. */
final class LocalProfile {
    static String gamertag(String raw){
        if(raw!=null)for(int i=0;i<raw.length();i++)if(raw.charAt(i)<32||raw.charAt(i)==127)
            throw new IllegalArgumentException("A gamertag cannot contain control characters.");
        String name=raw==null?"":raw.trim();
        if(!name.matches("[A-Za-z0-9][A-Za-z0-9 ]{0,14}"))
            throw new IllegalArgumentException("Use 1–15 letters, numbers and spaces, starting with a letter or number.");
        return name;
    }
    static String configuredProfile(Map<String,String> settings){
        if("true".equals(settings.get("pinyon_shift_cheats")))return "Modded profile";
        String mods=settings.getOrDefault("enabled_mods","").trim();
        return mods.isEmpty()||mods.equals("[]")?"Normal profile":"Profile determined by enabled mods";
    }
    static String backupStatus(Map<String,String> settings){
        return "false".equals(settings.get("pinyon_shift_save_backups"))?"Automatic save backups are off":"Automatic save backups are on";
    }
    private LocalProfile(){}
}
