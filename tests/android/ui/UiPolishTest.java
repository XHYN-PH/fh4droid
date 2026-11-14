package studio.deimos.pinyonshift;

import static org.junit.Assert.*;
import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=33,qualifiers="w960dp-h432dp-land-400dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class UiPolishTest {
  private Activity activity(){Activity a=Robolectric.buildActivity(Activity.class).setup().visible().get();a.getSharedPreferences("mobile",0).edit().clear().putBoolean("animations",false).commit();return a;}
  private LinearLayout settings(Activity a,String section)throws Exception{Method m=SettingsHub.class.getDeclaredMethod("buildPage",Activity.class,String.class,MobileSettings.Runtime.class,Runnable.class);m.setAccessible(true);return (LinearLayout)m.invoke(null,a,section,null,(Runnable)()->{});}
  private void arrange(View v,int w,int h){ShadowLooper.idleMainLooper();v.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));v.layout(0,0,w,h);}
  private void render(View v,int w,int h,String name)throws Exception{arrange(v,w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));new File("renders-integrated").mkdirs();try(OutputStream out=new FileOutputStream("renders-integrated/"+name+".png")){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
  private RacingLauncherView shell(Activity a)throws Exception{File source=new File(System.getProperty("launcher.source.root"));Bitmap bg=BitmapFactory.decodeFile(new File(source,"android/art/menu-background.png").toString());BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=4;Bitmap logo=BitmapFactory.decodeFile(new File(source,"android/art/menu-logo.png").toString(),opts);RacingLauncherView v=new RacingLauncherView(a,RacingLauncherView.Design.GRID,new RacingLauncherView.Model(),bg,logo,x->{});a.setContentView(v);return v;}
  @Test public void actualSettingsFocusAdjustmentAndPersistence()throws Exception{
    Activity a=activity();MobileConfig.put(a,"fh1_render_scale_percent","100");RacingLauncherView root=shell(a);LinearLayout settings=settings(a,"Graphics");root.showContent("Settings",settings,false);arrange(root,2400,1080);
    View resolution=settings.findViewWithTag("fh1_render_scale_percent");assertNotNull(resolution);assertTrue(resolution.requestFocus());View panel=settings.findViewWithTag("details-panel"),scroll=settings.findViewWithTag("settings-scroll");Rect d=new Rect(),s=new Rect();assertTrue(panel.getGlobalVisibleRect(d));assertTrue(scroll.getGlobalVisibleRect(s));assertTrue(d.left>=s.right);
    render(root,2400,1080,"test5-settings-graphics");resolution.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));resolution.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT));assertEquals("99",MobileConfig.get(a,"fh1_render_scale_percent",""));
    View frame=settings.findViewWithTag("pinyon_shift_fh1_render_fps_limit");assertTrue(frame.requestFocus());assertTrue(text(panel).contains("Higher targets"));render(root,2400,1080,"test5-settings-frame");
    LinearLayout reopened=settings(a,"Graphics");assertNotNull(reopened.findViewWithTag("fh1_render_scale_percent"));assertTrue(text(reopened).contains("99%"));
    for(String section:SettingsHub.PAGES){LinearLayout next=settings(a,section);root.showContent("Settings",next,false);render(root,2400,1080,"test5-settings-"+section.toLowerCase(Locale.ROOT));}
  }
  @Test @Config(qualifiers="w640dp-h360dp-land-xxhdpi") public void compactDetailsRemainOnRight()throws Exception{Activity a=activity();RacingLauncherView root=shell(a);LinearLayout settings=settings(a,"Graphics");root.showContent("Settings",settings,false);render(root,1920,1080,"test5-settings-compact");Rect d=new Rect(),s=new Rect();assertTrue(settings.findViewWithTag("details-panel").getGlobalVisibleRect(d));assertTrue(settings.findViewWithTag("settings-scroll").getGlobalVisibleRect(s));assertTrue(d.left>=s.right);}
  @Test public void gameMenuSingleTapAndTextOnlyActions()throws Exception{Activity a=activity();List<String> actions=new ArrayList<>();GameMenuView menu=new GameMenuView(a,actions::add);a.setContentView(menu);render(menu,1250,850,"test5-game-menu");Button settings=menu.findViewWithTag("Settings");assertFalse(settings.hasFocus());long t=android.os.SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(t,t+30,action,settings.getWidth()/2f,settings.getHeight()/2f,0);settings.dispatchTouchEvent(e);e.recycle();}ShadowLooper.idleMainLooper();assertEquals(Collections.singletonList("Settings"),actions);for(String title:new String[]{"Resume","Settings","Touch controls","Controller","Diagnostics","Achievements","Profile","More","Quit to launcher"}){Button b=menu.findViewWithTag(title);assertNotNull(b);for(android.graphics.drawable.Drawable icon:b.getCompoundDrawables())assertNull(icon);Rect r=new Rect();assertTrue(b.getGlobalVisibleRect(r));assertEquals(b.getHeight(),r.height());}}
  @Test public void uiPreferencesSurviveProcessHandoff(){Activity a=activity();a.getSharedPreferences("mobile",0).edit().putString("app_language","fil").putBoolean("tilt_invert",true).commit();UiPreferences.save(a);a.getSharedPreferences("mobile",0).edit().clear().commit();UiPreferences.load(a);assertEquals("fil",a.getSharedPreferences("mobile",0).getString("app_language",""));assertTrue(a.getSharedPreferences("mobile",0).getBoolean("tilt_invert",false));}
  @Test public void adreno730AutomaticUsesSystem()throws Exception{Method m=PinyonShiftActivity.class.getDeclaredMethod("recommendedDriver",String.class);m.setAccessible(true);assertNull(m.invoke(null,"730"));assertNull(m.invoke(null,(Object)null));assertEquals("turnip-gen8-v37",m.invoke(null,"740"));}
  @Test public void profileAndAchievementsUseCompactPages()throws Exception{
    Activity a=activity();org.json.JSONArray achievements=new org.json.JSONArray();for(int i=0;i<13;i++)achievements.put(new org.json.JSONObject().put("id",i).put("name",String.format("Achievement %02d",i)).put("score",10).put("unlocked",i%2==0));AchievementsPage.show(a,new org.json.JSONObject().put("achievements",achievements).toString());android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);View root=dialog.getWindow().getDecorView();arrange(root,2400,1080);assertTrue(text(root).contains("Achievement 00"));assertFalse(text(root).contains("Achievement 06"));TextView next=find(root,"Next");assertNotNull(next);next.performClick();assertTrue(text(root).contains("Achievement 06"));assertFalse(text(root).contains("Achievement 00"));find(root,"Unlocked").performClick();assertTrue(text(root).contains("Achievement 00"));assertFalse(text(root).contains("Achievement 01"));dialog.dismiss();
    ProfilePage.show(a,()->{},()->{});dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertNotNull(dialog);assertTrue(text(dialog.getWindow().getDecorView()).contains("Edit gamertag"));dialog.dismiss();
  }
  private TextView find(View v,String caption){if(v instanceof TextView&&((TextView)v).getText().toString().equals(caption))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView result=find(((ViewGroup)v).getChildAt(i),caption);if(result!=null)return result;}return null;}
  private String text(View v){if(v instanceof TextView)return ((TextView)v).getText().toString();StringBuilder out=new StringBuilder();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.append(text(((ViewGroup)v).getChildAt(i))).append(' ');return out.toString();}
}
