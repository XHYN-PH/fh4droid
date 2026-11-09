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
      body.addView(search);
      Spinner filter = new Spinner(a);
      filter.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_spinner_dropdown_item,
          new String[] {"All achievements", "Unlocked", "Locked"}));
      body.addView(filter);
      LinearLayout rows = FestivalUi.column(a);
      body.addView(rows);
      Map<Integer, Bitmap> icons = new HashMap<>();
      Runnable refresh = () -> {
        rows.removeAllViews();
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        int mode = filter.getSelectedItemPosition();
        for (int i = 0; i < entries.length(); i++) {
          JSONObject e = entries.optJSONObject(i);
          if (e == null)
            continue;
          boolean done = e.optBoolean("unlocked");
          String name = e.optString("name", "Achievement");
          if ((mode == 1 && !done) || (mode == 2 && done)
              || !name.toLowerCase(Locale.ROOT).contains(query))
            continue;
          LinearLayout row = new LinearLayout(a);
          row.setGravity(android.view.Gravity.CENTER_VERTICAL);
          row.setPadding(0, FestivalUi.dp(a, 6), 0, FestivalUi.dp(a, 6));
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
          rows.addView(row);
        }
      };
      search.addTextChangedListener(new TextWatcher() {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {
          refresh.run();
        }
        public void afterTextChanged(Editable e) {}
      });
      filter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
        public void onItemSelected(
            android.widget.AdapterView<?> parent, android.view.View v, int p, long id) {
          refresh.run();
        }
        public void onNothingSelected(android.widget.AdapterView<?> p) {}
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
