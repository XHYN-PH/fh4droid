package studio.deimos.pinyonshift;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Full-screen draft; Save commits atomically and Cancel keeps the active file. */
public final class TouchEditorActivity extends Activity {
  private final TouchLayout layout=new TouchLayout();
  private int active,tab;
  private boolean collapsed,fine;
  private FrameLayout root;
  private LinearLayout tools;
  private Editor editor;
  private TextView status;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);UiPreferences.load(this);layout.load(this);
    if(state!=null){layout.parse(state.getString("draft",""));active=Math.max(0,Math.min(TouchLayout.LABELS.length-1,state.getInt("active",0)));tab=state.getInt("tab",0);collapsed=state.getBoolean("collapsed",false);fine=state.getBoolean("fine",false);}
    root=new FrameLayout(this);root.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
    editor=new Editor();root.addView(editor,new FrameLayout.LayoutParams(-1,-1));
    tools=FestivalUi.column(this);int pad=FestivalUi.dp(this,8);tools.setPadding(pad,pad,pad,pad);tools.setBackground(FestivalUi.surface(this,FestivalUi.PANEL,14));
    FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(FestivalUi.dp(this,260),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);params.topMargin=pad;root.addView(tools,params);
    tools.setElevation(FestivalUi.dp(this,8));setContentView(root);FestivalUi.immersive(this);rebuild();
    if(state!=null)tools.post(()->{tools.setX(state.getFloat("tool_x",tools.getX()));tools.setY(state.getFloat("tool_y",tools.getY()));clampTools();});
    root.setOnApplyWindowInsetsListener((v,insets)->{Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());editor.safe=new Rect(safe.left,safe.top,safe.right,safe.bottom);editor.invalidate();tools.post(this::clampTools);return insets;});
    root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->tools.post(this::clampTools));
  }
  private void clampTools(){
    if(root.getWidth()==0)return;
    int maxWidth=Math.max(1,root.getWidth()-editor.safe.left-editor.safe.right);
    ViewGroup.LayoutParams p=tools.getLayoutParams();int width=Math.min(FestivalUi.dp(this,260),maxWidth);
    if(p.width!=width){p.width=width;tools.setLayoutParams(p);}
    int height=collapsed?-2:Math.min(FestivalUi.dp(this,340),Math.max(1,root.getHeight()-editor.safe.top-editor.safe.bottom));
    if(p.height!=height){p.height=height;tools.setLayoutParams(p);}
    tools.setX(Math.max(editor.safe.left,Math.min(Math.max(editor.safe.left,root.getWidth()-editor.safe.right-tools.getWidth()),tools.getX())));
    tools.setY(Math.max(editor.safe.top,Math.min(Math.max(editor.safe.top,root.getHeight()-editor.safe.bottom-tools.getHeight()),tools.getY())));
  }
  private String title(){return TouchLayout.displayLabel(active,layout.drivingLabels);}
  private void describe(){if(status!=null)status.setText(String.format(Locale.ROOT,"%s · %s\nX %.0f%% · Y %.0f%% · size %.1f%%",title(),layout.hidden[active]?"hidden":"visible",layout.values[active][0]*100,layout.values[active][1]*100,layout.values[active][2]*100));}
  private void changed(){describe();editor.invalidate();}
  private void nudge(float x,float y){layout.values[active][0]=Math.max(0,Math.min(1,layout.values[active][0]+x));layout.values[active][1]=Math.max(0,Math.min(1,layout.values[active][1]+y));changed();}
  private void rebuild(){
    tools.removeAllViews();LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
    TextView handle=FestivalUi.text(this,"Drag tools",13,FestivalUi.MUTED);handle.setContentDescription("Drag to move the tools panel");head.addView(handle,new LinearLayout.LayoutParams(0,FestivalUi.dp(this,44),1));
    handle.setOnTouchListener(new View.OnTouchListener(){float dx,dy;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){dx=e.getRawX()-tools.getX();dy=e.getRawY()-tools.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){tools.setX(e.getRawX()-dx);tools.setY(e.getRawY()-dy);clampTools();return true;}return e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL;}});
    Button fold=FestivalUi.button(this,collapsed?"Expand":"Collapse",false);head.addView(fold);fold.setOnClickListener(v->{collapsed=!collapsed;rebuild();});tools.addView(head);
    LinearLayout actions=new LinearLayout(this);tools.addView(actions);
    Button cancel=FestivalUi.button(this,"Cancel",false),save=FestivalUi.button(this,"Save",true);actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
    cancel.setOnClickListener(v->close());save.setOnClickListener(v->{try{layout.save(this);close();}catch(Exception e){SettingsHub.error(this,e);}});
    if(collapsed){tools.post(this::clampTools);return;}
    HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);LinearLayout tabs=new LinearLayout(this);strip.addView(tabs);tools.addView(strip);
    String[] names={"Control","Layout","Driving","Appearance"};
    for(int i=0;i<names.length;i++){final int selected=i;Button button=FestivalUi.button(this,names[i],false);button.setTextColor(tab==i?FestivalUi.ORANGE:FestivalUi.MUTED);tabs.addView(button);button.setOnClickListener(v->{tab=selected;rebuild();});}
    ScrollView scroll=new ScrollView(this);LinearLayout body=FestivalUi.column(this);scroll.addView(body);tools.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    status=FestivalUi.text(this,"",13,FestivalUi.TEXT);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);describe();
    if(tab==0){
      FestivalUi.action(this,body,"Select control",()->{String[] names2=new String[TouchLayout.LABELS.length];for(int i=0;i<names2.length;i++)names2[i]=TouchLayout.displayLabel(i,layout.drivingLabels)+(layout.hidden[i]?" (hidden)":"");FestivalUi.choose(this,"Select control",names2,active,i->{active=i;rebuild();editor.invalidate();});});
      FestivalUi.slider(this,body,"Size","Pinch also resizes the selected control.",35,240,5,Math.round(layout.values[active][2]*1000),i->String.format(Locale.ROOT,"%.1f%%",i/10f),i->{layout.values[active][2]=i/1000f;changed();});
      FestivalUi.action(this,body,fine?"Hide exact position":"Exact position",()->{fine=!fine;rebuild();});
      if(fine){
        FestivalUi.slider(this,body,"Horizontal position","",0,100,1,Math.round(layout.values[active][0]*100),i->i+"%",i->{layout.values[active][0]=i/100f;changed();});
        FestivalUi.slider(this,body,"Vertical position","",0,100,1,Math.round(layout.values[active][1]*100),i->i+"%",i->{layout.values[active][1]=i/100f;changed();});
      }
      LinearLayout move=new LinearLayout(this);body.addView(move);
      String[] labels={"Left","Right","Up","Down"};float[][] steps={{-.01f,0},{.01f,0},{0,-.01f},{0,.01f}};
      for(int i=0;i<labels.length;i++){final int n=i;Button b=FestivalUi.button(this,labels[i],false);b.setContentDescription("Move selected control "+labels[i].toLowerCase(Locale.ROOT));move.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->nudge(steps[n][0],steps[n][1]));}
      FestivalUi.toggle(this,body,"Show this control","Hidden controls can still be selected in this editor.",!layout.hidden[active],on->{layout.hidden[active]=!on;changed();});
      FestivalUi.slider(this,body,"This control's fade timer","Held controls remain visible.",-1,120,1,Math.round(layout.timeout[active]),i->i<0?"Use global timer":i==0?"Always visible":i+" seconds",i->layout.timeout[active]=i);
    }else if(tab==1){
      String[] modes={"Floating","Fixed","Invisible"};
      FestivalUi.action(this,body,"Left stick: "+modes[layout.leftMode],()->FestivalUi.choose(this,"Left stick mode",modes,layout.leftMode,i->{layout.leftMode=i;rebuild();editor.invalidate();}));
      FestivalUi.action(this,body,"Look stick: "+modes[layout.rightMode],()->FestivalUi.choose(this,"Look stick mode",modes,layout.rightMode,i->{layout.rightMode=i;rebuild();editor.invalidate();}));
      body.addView(FestivalUi.text(this,"Invisible sticks use free space in the lower left/right. Buttons take priority. Save applies this draft; Cancel keeps your current layout.",12,FestivalUi.MUTED));
      FestivalUi.action(this,body,"Dock tools left",()->{tools.setX(editor.safe.left);tools.setY(editor.safe.top);clampTools();});
      FestivalUi.action(this,body,"Dock tools right",()->{tools.setX(root.getWidth()-editor.safe.right-tools.getWidth());tools.setY(editor.safe.top);clampTools();});
      FestivalUi.action(this,body,"Reset this draft",()->new AlertDialog.Builder(this).setTitle("Reset this draft?").setMessage("Restore default positions and touch options? Save will apply the reset.").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{layout.reset();active=0;rebuild();editor.invalidate();}).show());
    }else if(tab==2){
      FestivalUi.action(this,body,"Steering: "+(layout.steering.equals("arrows")?"Arrows":"Stick"),()->FestivalUi.choose(this,"Steering",new String[]{"Arrows","Stick"},layout.steering.equals("arrows")?0:1,i->{layout.steering=i==0?"arrows":"stick";rebuild();editor.invalidate();}));
      FestivalUi.action(this,body,"Response: "+layout.steeringResponseMs+" ms",()->FestivalUi.choose(this,"Steering response",new String[]{"Immediate","Gentle · 120 ms","Smooth · 240 ms"},Arrays.asList(0,120,240).indexOf(layout.steeringResponseMs),i->{layout.steeringResponseMs=new int[]{0,120,240}[i];rebuild();}));
      FestivalUi.toggle(this,body,"Sliding pedals","Slide up for more pressure and down for less.",layout.analogPedals,on->layout.analogPedals=on);
      FestivalUi.toggle(this,body,"Hide with controller","",layout.autoHide,on->layout.autoHide=on);
      FestivalUi.toggle(this,body,"Touch vibration","Follows the Android haptic preference.",layout.haptics,on->layout.haptics=on);
    }else{
      FestivalUi.toggle(this,body,"Driving action icons","Symbols assume default game bindings. Turn off for Xbox button labels.",layout.drivingLabels,on->{layout.drivingLabels=on;changed();});
      FestivalUi.toggle(this,body,"Xbox button hints","Keep game menu prompts recognizable.",layout.buttonHints,on->{layout.buttonHints=on;changed();});
      FestivalUi.slider(this,body,"Opacity","",20,100,1,Math.round(layout.opacity*100),i->i+"%",i->{layout.opacity=i/100f;changed();});
      FestivalUi.toggle(this,body,"High contrast","Raises icon opacity to at least 85%.",layout.highContrast,on->{layout.highContrast=on;changed();});
      FestivalUi.slider(this,body,"Global fade timer","Held controls remain visible.",0,120,1,Math.round(layout.idleSeconds),i->i==0?"Always visible":i+" seconds",i->layout.idleSeconds=i);
    }
    tools.post(this::clampTools);
  }
  private void close(){boolean game=getIntent().getBooleanExtra("return_to_game",false)&&GameFiles.running(this);finish();if(game)startActivity(new Intent(this,PinyonShiftActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
  @Override public void onBackPressed(){close();}
  @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("draft",layout.serialize());state.putInt("active",active);state.putInt("tab",tab);state.putBoolean("collapsed",collapsed);state.putBoolean("fine",fine);state.putFloat("tool_x",tools.getX());state.putFloat("tool_y",tools.getY());}
  private final class Editor extends View{
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final ScaleGestureDetector pinch;Rect safe=new Rect();boolean dragging;float dx,dy;
    Editor(){super(TouchEditorActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);pinch=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector e){layout.values[active][2]=Math.max(.035f,Math.min(.24f,layout.values[active][2]*e.getScaleFactor()));changed();return true;}});}
    float width(){return Math.max(1,getWidth()-safe.left-safe.right);}float height(){return Math.max(1,getHeight()-safe.top-safe.bottom);}
    float radius(int i){return Math.min(layout.values[i][2]*getHeight(),Math.min(width(),height())*.49f);}
    float x(int i){float r=radius(i);return safe.left+Math.max(r,Math.min(width()-r,layout.values[i][0]*width()));}
    float y(int i){float r=radius(i);return safe.top+Math.max(r,Math.min(height()-r,layout.values[i][1]*height()));}
    @Override protected void onDraw(Canvas canvas){
      paint.setColor(0x26ffffff);paint.setStrokeWidth(1);paint.setStyle(Paint.Style.STROKE);
      for(int i=1;i<10;i++)canvas.drawLine(safe.left+width()*i/10,safe.top,safe.left+width()*i/10,safe.top+height(),paint);
      for(int i=1;i<6;i++)canvas.drawLine(safe.left,safe.top+height()*i/6,safe.left+width(),safe.top+height()*i/6,paint);
      for(int i=0;i<layout.values.length;i++)TouchIconPainter.draw(canvas,paint,i,x(i),y(i),radius(i),layout.highContrast?Math.max(.85f,layout.opacity):layout.opacity,i==active,layout.hidden[i]||!layout.enabled(i),layout.drivingLabels,layout.buttonHints);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
      pinch.onTouchEvent(e);if(e.getPointerCount()>1){dragging=false;return true;}
      if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float best=Float.MAX_VALUE;int found=-1;
        for(int i=0;i<layout.values.length;i++){float ax=Math.abs(e.getX()-x(i)),ay=Math.abs(e.getY()-y(i)),distance=(float)Math.hypot(ax,ay),r=radius(i);boolean hit=TouchIcons.RECT[i]?ax<=TouchIcons.BOUNDS[i][0]*r*1.15f&&ay<=TouchIcons.BOUNDS[i][1]*r*1.15f:distance<=r*1.15f;if(hit&&distance<best){best=distance;found=i;}}
        dragging=found>=0;if(dragging){active=found;dx=e.getX()-x(active);dy=e.getY()-y(active);rebuild();changed();}return true;
      }
      if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging&&!pinch.isInProgress()){float r=radius(active);layout.values[active][0]=Math.max(r,Math.min(width()-r,e.getX()-dx-safe.left))/width();layout.values[active][1]=Math.max(r,Math.min(height()-r,e.getY()-dy-safe.top))/height();changed();}
      else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)dragging=false;
      return true;
    }
  }
}
