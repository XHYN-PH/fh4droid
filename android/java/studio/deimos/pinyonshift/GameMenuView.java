package studio.deimos.pinyonshift;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import java.util.function.Consumer;

/** Shared, text-only game-menu layout; all actions are supplied by the running activity. */
final class GameMenuView extends LinearLayout {
  GameMenuView(Activity a, Consumer<String> action) {
    super(a);setOrientation(VERTICAL);int pad=FestivalUi.dp(a,16);setPadding(pad,FestivalUi.dp(a,12),pad,FestivalUi.dp(a,12));setBackground(FestivalUi.surface(a,FestivalUi.PANEL,14));
    TextView title=FestivalUi.text(a,"Game menu",20,FestivalUi.TEXT);title.setTypeface(Typeface.DEFAULT_BOLD);addView(title,new LayoutParams(-1,FestivalUi.dp(a,32)));
    Button resume=button(a,"Resume",true,action);LayoutParams rp=new LayoutParams(-1,FestivalUi.dp(a,44));rp.topMargin=FestivalUi.dp(a,5);addView(resume,rp);
    for(String[] pair:new String[][]{{"Settings","Touch controls"},{"Controller","Diagnostics"},{"Achievements","Profile"},{"More","Quit to launcher"}}){LinearLayout row=new LinearLayout(a);LayoutParams r=new LayoutParams(-1,FestivalUi.dp(a,44));r.topMargin=FestivalUi.dp(a,6);addView(row,r);for(String label:pair){LayoutParams b=new LayoutParams(0,-1,1);if(row.getChildCount()>0)b.leftMargin=FestivalUi.dp(a,6);row.addView(button(a,label,false,action),b);}}
    setTag(FestivalUi.DEFAULT_FOCUS,resume);
  }
  private Button button(Activity a,String label,boolean primary,Consumer<String> action){Button b=FestivalUi.button(a,label,primary);b.setTag(label);b.setCompoundDrawables(null,null,null,null);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextSize(13);b.setOnClickListener(v->action.accept(label));return b;}
}
