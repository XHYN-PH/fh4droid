package studio.deimos.pinyonshift;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import java.util.function.Consumer;

/** Shared, text-only game-menu layout; all actions are supplied by the running activity. */
final class GameMenuView extends LinearLayout {
  GameMenuView(Activity a, Consumer<String> action) {
    super(a);
    setOrientation(VERTICAL);
    int pad = FestivalUi.dp(a,14);
    setPadding(pad,pad,pad,pad);
    setBackground(FestivalUi.surface(a,FestivalUi.PANEL,16));
    ScrollView scroll = new ScrollView(a);
    scroll.setFocusable(false);
    addView(scroll,new LayoutParams(-1,-2));
    LinearLayout content = FestivalUi.column(a);
    scroll.addView(content);
    FestivalUi.attachPageScroll(this,scroll);
    TextView title = FestivalUi.text(a,"Game menu",20,FestivalUi.TEXT);
    title.setTypeface(Typeface.DEFAULT_BOLD);
    content.addView(title);
    Button resume = button(a,"Resume",true,action);
    LayoutParams primary = new LayoutParams(-1,-2);
    primary.topMargin = FestivalUi.dp(a,8);
    content.addView(resume,primary);
    for (String[] pair : new String[][]{{"Settings","Touch controls"},{"Controller","More"}}) {
      LinearLayout row = new LinearLayout(a);
      LayoutParams rowSize = new LayoutParams(-1,-2);
      rowSize.topMargin = FestivalUi.dp(a,6);
      content.addView(row,rowSize);
      for (String label : pair) {
        LayoutParams size = new LayoutParams(0,-2,1);
        if (row.getChildCount() > 0) size.leftMargin = FestivalUi.dp(a,6);
        row.addView(button(a,label,false,action),size);
      }
    }
    Button quit = button(a,"Quit to launcher",false,action);
    quit.setTextColor(FestivalUi.MUTED);
    LayoutParams exit = new LayoutParams(-1,-2);
    exit.topMargin = FestivalUi.dp(a,12);
    content.addView(quit,exit);
    setTag(FestivalUi.DEFAULT_FOCUS,resume);
    post(() -> FestivalUi.restorePage(this));
  }
  private Button button(Activity a,String label,boolean primary,Consumer<String> action) {
    Button button = FestivalUi.button(a,label,primary);
    button.setTag(label);
    button.setCompoundDrawables(null,null,null,null);
    button.setTextSize(primary ? 16 : 13);
    button.setOnClickListener(v -> action.accept(label));
    return button;
  }
}
