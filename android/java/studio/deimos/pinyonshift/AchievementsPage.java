package studio.deimos.pinyonshift;

import android.app.*;
import android.graphics.*;
import android.text.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.DateFormat;
import java.util.*;
import org.json.*;

final class AchievementsPage {
  static void show(Activity a, String current) {
    LinearLayout body = FestivalUi.column(a);
    try {
      File file = new File(MobileConfig.base(a), "state/logs/android-achievements.json");
      String data = current;
      if (data == null || data.isEmpty()) {
        if (file.isFile() && file.length() <= 2 * 1024 * 1024)
          data = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        else
          data = "";
      }
      if (data.isEmpty()) {
        body.addView(
            FestivalUi.text(a, "Play the game to load local achievements.", 13, FestivalUi.MUTED));
        FestivalUi.present(a, "Achievements", body);
        return;
      }
      JSONObject root = new JSONObject(data);
      JSONArray entries = root.optJSONArray("achievements");
      if (entries == null || entries.length() > 1024)
        throw new IOException("Achievement list is unavailable");
      int unlocked = 0, earned = 0, total = 0;
      for (int i = 0; i < entries.length(); i++) {
        JSONObject e = entries.getJSONObject(i);
        total += e.optInt("score");
        if (e.optBoolean("unlocked")) {
          unlocked++;
          earned += e.optInt("score");
        }
      }
      body.addView(FestivalUi.text(a,
          unlocked + " / " + entries.length() + " unlocked · " + earned + " / " + total + " G · "
              + (root.optBoolean("modded") ? "Modded profile" : "Normal profile"),
          13, FestivalUi.ORANGE));
      EditText search = new EditText(a);
      search.setHint("Find an achievement");
      search.setTextColor(FestivalUi.TEXT);
      search.setHintTextColor(FestivalUi.MUTED);
      search.setSingleLine(true);
      LinearLayout toolbar=new LinearLayout(a);toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);body.addView(toolbar);
      int[] mode={0},page={0};Runnable[] update={null};
      for(int i=0;i<3;i++){final int selected=i;Button filter=FestivalUi.button(a,new String[]{"All","Unlocked","Locked"}[i],false);filter.setTextSize(12);toolbar.addView(filter);filter.setOnClickListener(v->{mode[0]=selected;page[0]=0;update[0].run();});}
      search.setTextSize(12);toolbar.addView(search,new LinearLayout.LayoutParams(0,-2,1));
      LinearLayout paging=new LinearLayout(a);paging.setGravity(android.view.Gravity.RIGHT|android.view.Gravity.CENTER_VERTICAL);body.addView(paging);
      Button previous=FestivalUi.button(a,"Previous",false),next=FestivalUi.button(a,"Next",false);paging.addView(previous);TextView count=FestivalUi.text(a,"",12,FestivalUi.TEXT);paging.addView(count);paging.addView(next);
      previous.setOnClickListener(v->{page[0]=Math.max(0,page[0]-1);update[0].run();});next.setOnClickListener(v->{page[0]++;update[0].run();});
      LinearLayout rows=FestivalUi.column(a);body.addView(rows);
      Map<Integer, Bitmap> icons = new HashMap<>();
      Runnable refresh = () -> {
        rows.removeAllViews();
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        List<JSONObject> matched=new ArrayList<>();for(int i=0;i<entries.length();i++){JSONObject item=entries.optJSONObject(i);if(item==null)continue;boolean done=item.optBoolean("unlocked");if((mode[0]==1&&!done)||(mode[0]==2&&done)||!item.optString("name","").toLowerCase(Locale.ROOT).contains(query))continue;matched.add(item);}
        int pages=Math.max(1,(matched.size()+5)/6);page[0]=Math.min(page[0],pages-1);count.setText("  "+(page[0]+1)+" / "+pages+"  ");previous.setEnabled(page[0]>0);next.setEnabled(page[0]+1<pages);LinearLayout pair=null;
        for (int i = page[0]*6; i < Math.min(matched.size(),page[0]*6+6); i++) {
          JSONObject e = matched.get(i);
          if (e == null)
            continue;
          boolean done = e.optBoolean("unlocked");
          String name = e.optString("name", "Achievement");

          LinearLayout row = new LinearLayout(a);
          row.setGravity(android.view.Gravity.CENTER_VERTICAL);
          row.setPadding(FestivalUi.dp(a,10),FestivalUi.dp(a,8),FestivalUi.dp(a,10),FestivalUi.dp(a,8));row.setFocusable(true);row.setClickable(true);FestivalUi.glassBackground(a,row,false,10);
          int id = e.optInt("id", -1);
          File icon =
              new File(MobileConfig.base(a), "state/cache/achievement-icons/" + id + ".png");
          Bitmap bitmap = icons.get(id);
          if (bitmap == null && icons.size() < 128 && id >= 0 && icon.isFile()
              && !Files.isSymbolicLink(icon.toPath()) && icon.length() <= 1024 * 1024) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(icon.getPath(), bounds);
            if (bounds.outWidth > 0 && bounds.outWidth <= 1024 && bounds.outHeight > 0
                && bounds.outHeight <= 1024) {
              bounds.inJustDecodeBounds = false;
              bounds.inSampleSize = 1;
              while (Math.max(bounds.outWidth, bounds.outHeight) / bounds.inSampleSize > 128)
                bounds.inSampleSize *= 2;
              bitmap = BitmapFactory.decodeFile(icon.getPath(), bounds);
              if (bitmap != null)
                icons.put(id, bitmap);
            }
          }
          if (bitmap != null) {
            ImageView image = new ImageView(a);
            image.setImageBitmap(bitmap);
            if (!done) {
              ColorMatrix gray = new ColorMatrix();
              gray.setSaturation(0);
              image.setColorFilter(new ColorMatrixColorFilter(gray));
              image.setAlpha(.45f);
            }
            row.addView(
                image, new LinearLayout.LayoutParams(FestivalUi.dp(a, 48), FestivalUi.dp(a, 48)));
          } else
            row.addView(new OutlineIcon(a, "trophy", done ? FestivalUi.ORANGE : FestivalUi.MUTED),
                new LinearLayout.LayoutParams(FestivalUi.dp(a, 48), FestivalUi.dp(a, 48)));
          LinearLayout text = FestivalUi.column(a);
          row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
          text.addView(FestivalUi.text(a, name, 14, done ? FestivalUi.TEXT : FestivalUi.MUTED));
          long time = e.optLong("unlocked_ms");
          String date = time > 0 ? DateFormat.getDateInstance().format(new Date(time)) : "";
          text.addView(FestivalUi.text(a,
              e.optInt("score") + " G · "
                  + (done ? "Unlocked" + (date.isEmpty() ? "" : " · " + date) : "Locked"),
              12, FestivalUi.MUTED));
          row.setOnClickListener(v -> FestivalUi.explain(a, name, e.optString("description", "")));
          if(i%2==0){pair=new LinearLayout(a);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=FestivalUi.dp(a,6);rows.addView(pair,pp);}LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);if(i%2==1)rp.leftMargin=FestivalUi.dp(a,8);pair.addView(row,rp);
        }
      };
      update[0]=refresh;
      search.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          page[0]=0;refresh.run();
        }
        public void afterTextChanged(Editable e) {}
      });

      refresh.run();
    } catch (Exception e) {
      body.addView(FestivalUi.text(
          a, "Achievements could not be loaded: " + e.getMessage(), 13, FestivalUi.MUTED));
    }
    FestivalUi.present(a, "Achievements", body);
  }
  private AchievementsPage() {}
}
