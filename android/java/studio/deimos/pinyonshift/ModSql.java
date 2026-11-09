package studio.deimos.pinyonshift;

import java.io.IOException;
import java.util.*;

/** SQLite script boundaries, retaining quoted semicolons and SQL comments. */
final class ModSql {
    static List<String> statements(String sql)throws IOException{
        if(sql.startsWith("\uFEFF"))sql=sql.substring(1);
        List<String> out=new ArrayList<>();StringBuilder row=new StringBuilder();char quote=0;boolean line=false,block=false;
        for(int i=0;i<sql.length();i++){
            char c=sql.charAt(i),next=i+1<sql.length()?sql.charAt(i+1):0;
            if(line){if(c=='\n'){line=false;row.append(' ');}continue;}
            if(block){if(c=='*'&&next=='/'){block=false;i++;row.append(' ');}continue;}
            if(quote!=0){row.append(c);char end=quote=='['?']':quote;if(c==end){if(next==end&&quote!='['){row.append(next);i++;}else quote=0;}continue;}
            if(c=='-'&&next=='-'){line=true;i++;continue;}if(c=='/'&&next=='*'){block=true;i++;continue;}
            if(c=='\''||c=='"'||c=='`'||c=='['){quote=c;row.append(c);continue;}
            if(c==';'){add(out,row);row.setLength(0);}else row.append(c);
        }
        if(quote!=0||block)throw new IOException("Unfinished quote or comment in mod SQL.");add(out,row);return out;
    }
    private static void add(List<String> out,StringBuilder text)throws IOException{
        String sql=text.toString().trim();if(sql.isEmpty())return;
        String first=sql.split("\\s+",2)[0].toUpperCase(Locale.ROOT);
        if(!Arrays.asList("UPDATE","INSERT","DELETE","SELECT","CREATE","ALTER","DROP","REPLACE","WITH").contains(first))throw new IOException("Unsupported mod SQL statement: "+first);
        if(sql.matches("(?is)^CREATE\\s+(TEMP\\s+|TEMPORARY\\s+)?TRIGGER\\b.*"))throw new IOException("Trigger scripts need preparation with the PC mod tools.");
        out.add(sql);
    }
    static String substitute(String sql,Map<String,String> values)throws IOException{
        for(Map.Entry<String,String> item:values.entrySet()){
            if(!item.getKey().matches("[a-z][a-z0-9_]{0,31}")||!item.getValue().matches("[A-Za-z0-9_.+-]{1,32}"))throw new IOException("Invalid mod option.");
            sql=sql.replaceAll("\\{\\{\\s*"+java.util.regex.Pattern.quote(item.getKey())+"\\s*\\}\\}",java.util.regex.Matcher.quoteReplacement(item.getValue()));
        }
        if(sql.contains("{{"))throw new IOException("A mod option has no value.");return sql;
    }
    private ModSql(){}
}
