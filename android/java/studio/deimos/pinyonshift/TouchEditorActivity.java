package studio.deimos.pinyonshift;
import android.app.*;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

public final class TouchEditorActivity extends Activity {
    private final TouchLayout layout=new TouchLayout();private Editor editor;private TextView selected;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);layout.load(this);if(state!=null)layout.parse(state.getString("draft",""));
        LinearLayout root=FestivalUi.column(this);root.setBackgroundColor(FestivalUi.BG);int p=FestivalUi.dp(this,12);root.setPadding(p,p,p,p);
        root.setOnApplyWindowInsetsListener((v,insets)->{Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            root.setPadding(p+safe.left,p+safe.top,p+safe.right,p+safe.bottom);return insets;});
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        Button save=FestivalUi.button(this,"Save",true),back=FestivalUi.button(this,"Cancel",false);
        top.addView(back,new LinearLayout.LayoutParams(0,-2,1));top.addView(save,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        selected=FestivalUi.text(this,"",16,FestivalUi.TEXT);selected.setPadding(0,p,0,p);selected.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(selected);
        editor=new Editor();editor.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if(state!=null){int active=state.getInt("active",0);if(active>=0&&active<TouchLayout.LABELS.length&&layout.enabled(active))editor.active=active;}
        float aspect=getResources().getDisplayMetrics().widthPixels/(float)Math.max(1,getResources().getDisplayMetrics().heightPixels);
        int height=FestivalUi.dp(this,Math.max(160,Math.min(getResources().getConfiguration().screenWidthDp/aspect,getResources().getConfiguration().screenHeightDp*.55f)));
        root.addView(editor,new LinearLayout.LayoutParams(-1,height));
        GridLayout groups=new GridLayout(this);groups.setColumnCount(getResources().getConfiguration().screenWidthDp>=600&&getResources().getConfiguration().fontScale<1.3f?4:2);
        group(groups,"Select control",this::selectControl);group(groups,"Placement",this::placement);group(groups,"Driving",this::driving);group(groups,"Appearance",this::appearance);root.addView(groups,new LinearLayout.LayoutParams(-1,-2));
        FestivalUi.action(this,root,"Help",this::help);
        ScrollView page=new ScrollView(this);page.setFillViewport(true);page.addView(root);setContentView(page);FestivalUi.immersive(this);editor.describe();
        save.setOnClickListener(v->{try{layout.save(this);finish();}catch(Exception e){Toast.makeText(this,e.getMessage(),1).show();}});back.setOnClickListener(v->finish());
    }
    private void group(GridLayout parent,String title,Runnable run){
        Button b=FestivalUi.button(this,title,false);GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=-2;p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        int margin=FestivalUi.dp(this,3);p.setMargins(margin,margin,margin,margin);parent.addView(b,p);b.setOnClickListener(v->run.run());
    }
    private void selectControl(){
        java.util.List<String> names=new java.util.ArrayList<>();java.util.List<Integer> ids=new java.util.ArrayList<>();
        for(int i=0;i<TouchLayout.LABELS.length;i++)if(layout.enabled(i)){ids.add(i);names.add(TouchIcons.TITLES[i]+(layout.hidden[i]?" (hidden)":""));}
        FestivalUi.choose(this,"Select control",names.toArray(new String[0]),ids.indexOf(editor.active),n->{editor.active=ids.get(n);editor.describe();editor.invalidate();});
    }
    private void placement(){
        LinearLayout tools=FestivalUi.column(this);TextView status=FestivalUi.text(this,editor.description(),16,FestivalUi.TEXT);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);tools.addView(status);
        tool(tools,"Move left",()->{editor.nudge(-.01f,0);status.setText(editor.description());});tool(tools,"Move right",()->{editor.nudge(.01f,0);status.setText(editor.description());});
        tool(tools,"Move up",()->{editor.nudge(0,-.01f);status.setText(editor.description());});tool(tools,"Move down",()->{editor.nudge(0,.01f);status.setText(editor.description());});
        tool(tools,"Smaller",()->{editor.resize(.9f);status.setText(editor.description());});tool(tools,"Larger",()->{editor.resize(1.1f);status.setText(editor.description());});
        tool(tools,"Hide / show",()->{layout.hidden[editor.active]=!layout.hidden[editor.active];editor.describe();editor.invalidate();status.setText(editor.description());});
        AlertDialog dialog=FestivalUi.dialog(this,"Control placement",tools);
        tool(tools,"Reset all controls",()->new AlertDialog.Builder(this).setTitle("Reset layout?").setMessage("Restore all positions and touch options to their defaults?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{layout.reset();editor.active=0;editor.describe();editor.invalidate();dialog.dismiss();}).show());dialog.show();
    }
    private String response(){return layout.steeringResponseMs==0?"Immediate":layout.steeringResponseMs+" ms";}
    private String opacity(){return Math.round(layout.opacity*100)+"%"+(layout.highContrast?" · "+Math.round(Math.max(.85f,layout.opacity)*100)+"% with high contrast":"");}
    private void driving(){
        LinearLayout tools=FestivalUi.column(this);
        Button mode=tool(tools,"Steering: "+(layout.steering.equals("arrows")?"Arrows":"Stick"),()->{});
        mode.setOnClickListener(v->FestivalUi.choose(this,"Steering",new String[]{"Arrows","Stick"},layout.steering.equals("arrows")?0:1,n->{
            layout.steering=n==0?"arrows":"stick";if(!layout.enabled(editor.active))editor.active=java.util.Arrays.asList(TouchLayout.LABELS).indexOf(n==0?"LEFT":"LS");
            mode.setText("Steering: "+(n==0?"Arrows":"Stick"));editor.describe();editor.invalidate();}));
        Button response=tool(tools,"Steering response: "+response(),()->{});
        response.setOnClickListener(v->FestivalUi.choose(this,"Steering response",new String[]{"Immediate","Gentle · 120 ms","Smooth · 240 ms"},layout.steeringResponseMs==0?0:layout.steeringResponseMs==120?1:layout.steeringResponseMs==240?2:-1,n->{layout.steeringResponseMs=new int[]{0,120,240}[n];response.setText("Steering response: "+response());editor.describe();}));
        toggle(tools,"Sliding pedals",layout.analogPedals,on->layout.analogPedals=on);
        toggle(tools,"Hide with controller",layout.autoHide,on->layout.autoHide=on);
        toggle(tools,"Touch vibration",layout.haptics,on->layout.haptics=on);
        FestivalUi.dialog(this,"Driving controls",tools).show();
    }
    private void appearance(){
        LinearLayout tools=FestivalUi.column(this);Button opacity=tool(tools,"Opacity: "+opacity(),()->{});
        opacity.setOnClickListener(v->{int[] values={20,25,40,55,70,85,100};String[] labels=new String[values.length];int chosen=-1;
            for(int i=0;i<values.length;i++){labels[i]=values[i]+"%";if(Math.round(layout.opacity*100)==values[i])chosen=i;}
            FestivalUi.choose(this,"Opacity",labels,chosen,n->{layout.opacity=values[n]/100f;opacity.setText("Opacity: "+opacity());editor.describe();editor.invalidate();});});
        toggle(tools,"High contrast",layout.highContrast,on->{layout.highContrast=on;opacity.setText("Opacity: "+opacity());});FestivalUi.dialog(this,"Touch appearance",tools).show();
    }
    private void help(){new AlertDialog.Builder(this).setTitle("Touch controls").setMessage("Choose a control, then drag it or open Placement to move, resize or hide it. Hidden controls remain selectable. Pinch to resize the selected control.\n\nDriving contains steering and pedal options. Arrows steer left/right; holding both is neutral. Steering response controls how quickly steering reaches full lock. Sliding pedals start at half input: slide up for more, down for less.\n\nAppearance contains opacity and high contrast. Save applies your changes; Cancel keeps your previous layout.").setPositiveButton("Back",null).show();}
    private Button tool(LinearLayout parent,String title,Runnable run){Button b=FestivalUi.action(this,parent,title,run);b.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);return b;}
    private Switch toggle(LinearLayout parent,String title,boolean value,java.util.function.Consumer<Boolean> change){
        Switch control=new Switch(this);control.setText(title);control.setTextColor(FestivalUi.TEXT);control.setMinHeight(FestivalUi.dp(this,48));control.setChecked(value);
        control.setOnCheckedChangeListener((v,on)->{change.accept(on);editor.describe();editor.invalidate();});parent.addView(control);return control;
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("draft",layout.serialize());state.putInt("active",editor.active);}
    private final class Editor extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int active=0;float dx,dy;boolean dragging;final ScaleGestureDetector pinch;
        Editor(){super(TouchEditorActivity.this);setContentDescription("Touch controller layout editor");pinch=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){resize(detector.getScaleFactor());return true;}});}
        float previewWidth(){float aspect=getRootView().getWidth()/(float)Math.max(1,getRootView().getHeight());return Math.min(getWidth(),getHeight()*aspect);}
        float previewHeight(){float aspect=getRootView().getWidth()/(float)Math.max(1,getRootView().getHeight());return previewWidth()/Math.max(.1f,aspect);}
        float left(){return (getWidth()-previewWidth())/2;}
        float top(){return (getHeight()-previewHeight())/2;}
        float radius(int i){return layout.values[i][2]*previewHeight();}
        Insets safe(){WindowInsets insets=getRootWindowInsets();return insets==null?Insets.NONE:
            insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());}
        float insetScale(){return previewWidth()/Math.max(1,getRootView().getWidth());}
        float safeLeft(){return left()+safe().left*insetScale();}
        float safeTop(){return top()+safe().top*insetScale();}
        float safeWidth(){return Math.max(1,previewWidth()-(safe().left+safe().right)*insetScale());}
        float safeHeight(){return Math.max(1,previewHeight()-(safe().top+safe().bottom)*insetScale());}
        float clampRadius(int i){return Math.min(radius(i),Math.min(safeWidth(),safeHeight())*.49f);}
        float x(int i){float r=clampRadius(i);return safeLeft()+Math.max(r,Math.min(safeWidth()-r,layout.values[i][0]*safeWidth()));}
        float y(int i){float r=clampRadius(i);return safeTop()+Math.max(r,Math.min(safeHeight()-r,layout.values[i][1]*safeHeight()));}
        String description(){return String.format(java.util.Locale.ROOT,"%s · %s\nPosition %.0f%%, %.0f%% · size %.1f%%\nOpacity %s · response %s",TouchIcons.TITLES[active],layout.hidden[active]?"hidden":"visible",layout.values[active][0]*100,layout.values[active][1]*100,layout.values[active][2]*100,opacity(),response());}
        void describe(){String text=description();if(!text.contentEquals(selected.getText()))selected.setText(text);}
        void nudge(float dx,float dy){layout.values[active][0]=Math.max(0,Math.min(1,layout.values[active][0]+dx));layout.values[active][1]=Math.max(0,Math.min(1,layout.values[active][1]+dy));describe();invalidate();}
        void resize(float factor){layout.values[active][2]=Math.max(.035f,Math.min(.24f,layout.values[active][2]*factor));describe();invalidate();}
        @Override protected void onDraw(Canvas c){
            c.drawColor(FestivalUi.BG);paint.setColor(0xff211619);c.drawRect(left(),top(),left()+previewWidth(),top()+previewHeight(),paint);
            paint.setColor(0xff3d292c);paint.setStrokeWidth(1);
            for(int i=1;i<10;i++)c.drawLine(left()+previewWidth()*i/10f,top(),left()+previewWidth()*i/10f,top()+previewHeight(),paint);
            for(int i=1;i<6;i++)c.drawLine(left(),top()+previewHeight()*i/6f,left()+previewWidth(),top()+previewHeight()*i/6f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(0xff745152);paint.setStrokeWidth(2);
            c.drawRect(safeLeft(),safeTop(),safeLeft()+safeWidth(),safeTop()+safeHeight(),paint);
            for(int i=0;i<layout.values.length;i++)if(layout.enabled(i))
                TouchIconPainter.draw(c,paint,i,x(i),y(i),radius(i),layout.highContrast?Math.max(.85f,layout.opacity):layout.opacity,i==active,layout.hidden[i]);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            pinch.onTouchEvent(e);
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;}
            if(e.getPointerCount()>1){getParent().requestDisallowInterceptTouchEvent(true);dragging=false;return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float best=Float.MAX_VALUE;
                for(int i=0;i<layout.values.length;i++){if(!layout.enabled(i))continue;float d=(float)Math.hypot(e.getX()-x(i),e.getY()-y(i));
                    boolean hit=TouchIcons.RECT[i]?Math.abs(e.getX()-x(i))<=TouchIcons.BOUNDS[i][0]*radius(i)*1.15f&&Math.abs(e.getY()-y(i))<=TouchIcons.BOUNDS[i][1]*radius(i)*1.15f:d<=radius(i)*1.15f;
                    if(hit&&d<best){active=i;best=d;}}
                dragging=best<Float.MAX_VALUE;if(!dragging)return true;getParent().requestDisallowInterceptTouchEvent(true);
                dx=e.getX()-x(active);dy=e.getY()-y(active);describe();invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging&&!pinch.isInProgress()){float r=clampRadius(active);
                layout.values[active][0]=Math.max(r,Math.min(safeWidth()-r,e.getX()-dx-safeLeft()))/safeWidth();
                layout.values[active][1]=Math.max(r,Math.min(safeHeight()-r,e.getY()-dy-safeTop()))/safeHeight();describe();invalidate();return true;}
            return true;
        }
    }
}
