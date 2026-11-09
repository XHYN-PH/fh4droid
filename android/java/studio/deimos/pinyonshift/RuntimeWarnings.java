package studio.deimos.pinyonshift;
import java.util.*;
import java.util.regex.*;
/** Do not assign unscoped shared-log warnings to the current game. */
final class RuntimeWarnings {
    static String summarize(String log,String session,boolean truncated){
        if(session==null||session.isEmpty())return "No current session; warning counts unavailable.";
        Pattern boundary=Pattern.compile("\\\"session\\\":\\\"([^\\\"]+)\\\".*\\\"event\\\":\\\"(?:logging.ready|process.start)\\\"");
        boolean active=false,found=false;int warnings=0,errors=0,xma=0,recovery=0,draw=0;
        for(String line:log.split("\\n")){
            Matcher m=boundary.matcher(line);if(m.find()){active=session.equals(m.group(1));if(active)found=true;}
            if(!active)continue;
            if(line.contains("[warning]")||line.contains("[warn]"))warnings++;
            if(line.contains("[error]")||line.contains("[critical]"))errors++;
            if(line.toLowerCase(Locale.ROOT).contains("xma")&&(line.contains("[warning]")||line.contains("[warn]")))xma++;
            if(line.toLowerCase(Locale.ROOT).contains("physical page protection")&&line.toLowerCase(Locale.ROOT).contains("recover"))recovery++;
            if(line.contains("PM4_DRAW_INDX_2")&&(line.contains("[error]")||line.contains("failed")))draw++;
        }
        if(!found)return "The start marker for "+session+" is outside the retained log. Current-only warning counts are unavailable. Shared logs may contain other sessions.";
        return "Session "+session+" retained runtime lines: "+warnings+" warnings, "+errors+" errors; "+xma+" XMA warning lines, "+recovery+" page-recovery lines, "+draw+" failed indexed-draw lines. Counts are log lines, not distinct incidents or crashes."+(truncated?" Earlier shared-log history was truncated.":"");
    }
    private RuntimeWarnings(){}
}
