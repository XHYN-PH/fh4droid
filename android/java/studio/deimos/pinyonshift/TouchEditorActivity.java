package studio.deimos.pinyonshift;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Full-window canvas and full-window properties; only Save commits the draft. */
public final class TouchEditorActivity extends Activity {
    private final TouchLayout layout=new TouchLayout();
    private final Map<Integer,Integer> fingers=new HashMap<>();
    private FrameLayout root;
    private LinearLayout header;
    private ScrollView properties;
    private Editor editor;
    private TextView status;
    private Button propertyButton,testButton,reveal;
    private int active,page;
    private boolean propertyMode,testMode,toolsHidden,picker;
    private Rect safe=new Rect();
    private Bitmap artwork;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);UiPreferences.load(this);layout.load(this);artwork=TouchArtwork.bitmap(this);
        if(saved!=null){layout.parse(saved.getString("draft",""));active=Math.max(0,Math.min(TouchLayout.LABELS.length-1,saved.getInt("active")));page=Math.max(0,Math.min(3,saved.getInt("page")));propertyMode=saved.getBoolean("properties");toolsHidden=saved.getBoolean("tools_hidden");}
        root=new FrameLayout(this);root.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
        editor=new Editor();root.addView(editor,new FrameLayout.LayoutParams(-1,-1));
        properties=new ScrollView(this);properties.setBackgroundColor(FestivalUi.PANEL);root.addView(properties,new FrameLayout.LayoutParams(-1,-1));
        header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackgroundColor(0xf514181f);
        TextView title=FestivalUi.text(this,"Touch layout",18,FestivalUi.TEXT);title.setPadding(FestivalUi.dp(this,12),0,FestivalUi.dp(this,8),0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        propertyButton=headerAction("Properties",()->{propertyMode=!propertyMode;testMode=false;fingers.clear();showMode();});
        testButton=headerAction("Test",()->{testMode=!testMode;propertyMode=false;fingers.clear();showMode();});
        headerAction("Hide tools",()->{toolsHidden=true;propertyMode=false;showMode();});
        headerAction("Cancel",this::close);
        Button save=FestivalUi.button(this,"Save",true);header.addView(save);save.setOnClickListener(v->{try{layout.save(this);close();}catch(Exception error){SettingsHub.error(this,error);}});
        root.addView(header,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        reveal=FestivalUi.button(this,"Show tools",false);root.addView(reveal,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));reveal.setOnClickListener(v->{toolsHidden=false;showMode();});
        status=FestivalUi.text(this,"",13,FestivalUi.TEXT);status.setTextSize(13);status.setPadding(8,3,8,3);status.setBackground(FestivalUi.surface(this,0xaa14181f,6));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        root.setOnApplyWindowInsetsListener((v,insets)->{Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());safe=new Rect(i.left,i.top,i.right,i.bottom);fit();editor.invalidate();return insets;});
        header.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot)fit();});
        setContentView(root);FestivalUi.immersive(this);showMode();
    }
    private Button headerAction(String label,Runnable action){Button b=FestivalUi.button(this,label,false);b.setTextSize(13);b.setPadding(FestivalUi.dp(this,8),0,FestivalUi.dp(this,8),0);header.addView(b);b.setOnClickListener(v->action.run());return b;}
    private void fit(){
        header.setPadding(safe.left,safe.top,safe.right,0);
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)properties.getLayoutParams();p.setMargins(safe.left,Math.max(header.getHeight(),safe.top+FestivalUi.dp(this,48)),safe.right,safe.bottom);properties.setLayoutParams(p);
        FrameLayout.LayoutParams r=(FrameLayout.LayoutParams)reveal.getLayoutParams();r.topMargin=safe.top;reveal.setLayoutParams(r);
        FrameLayout.LayoutParams s=(FrameLayout.LayoutParams)status.getLayoutParams();s.bottomMargin=safe.bottom+FestivalUi.dp(this,4);status.setLayoutParams(s);
    }
    private void showMode(){
        header.setVisibility(toolsHidden?View.GONE:View.VISIBLE);reveal.setVisibility(toolsHidden?View.VISIBLE:View.GONE);properties.setVisibility(propertyMode?View.VISIBLE:View.GONE);
        propertyButton.setText(propertyMode?"Canvas":"Properties");testButton.setText(testMode?"Stop test":"Test");
        if(propertyMode)buildProperties();changed();
    }
    private String title(){return TouchLayout.displayLabel(active,layout.drivingLabels);}
    private void changed(){
        if(status!=null){if(testMode){Set<String> names=new LinkedHashSet<>();for(int i:fingers.values())names.add(TouchArtwork.CAPTIONS[i]);status.setText(names.isEmpty()?"Test controls · touches stay in the editor":String.join(" + ",names));}
            else status.setText(String.format(Locale.ROOT,"%s · X %.0f%% · Y %.0f%% · %.1f%% size",title(),layout.values[active][0]*100,layout.values[active][1]*100,layout.values[active][2]*100));}
        if(editor!=null)editor.invalidate();
    }
    private void chooseLayout(TouchLayout next){new AlertDialog.Builder(this).setTitle("Apply layout to this draft?").setMessage("This replaces positions and visibility in the draft. Save applies it; Cancel keeps your current layout.").setNegativeButton("Keep draft",null).setPositiveButton("Apply",(d,w)->{layout.reset();layout.parse(next.serialize());active=0;buildProperties();changed();}).show();}
    private void buildProperties(){
        properties.removeAllViews();LinearLayout body=FestivalUi.column(this);int pad=FestivalUi.dp(this,16);body.setPadding(pad,pad,pad,pad);properties.addView(body);
        LinearLayout tabs=new LinearLayout(this);body.addView(tabs);String[] pages={"Control","Layout","Driving","Appearance"};
        for(int i=0;i<pages.length;i++){final int n=i;Button b=FestivalUi.button(this,pages[i],false);b.setTextColor(page==i?FestivalUi.ORANGE:FestivalUi.MUTED);tabs.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{page=n;picker=false;buildProperties();});}
        if(page==0){
            FestivalUi.action(this,body,title()+" · select control",()->{picker=!picker;buildProperties();});
            if(picker){for(int i=0;i<TouchLayout.LABELS.length;i++){final int n=i;FestivalUi.action(this,body,TouchArtwork.CAPTIONS[i]+(layout.hidden[i]?" · hidden":""),()->{active=n;picker=false;buildProperties();changed();});}return;}
            FestivalUi.slider(this,body,"Size","Pinch on the full-screen canvas to resize.",35,240,5,Math.round(layout.values[active][2]*1000),n->String.format(Locale.ROOT,"%.1f%%",n/10f),n->{layout.values[active][2]=n/1000f;changed();});
            FestivalUi.slider(this,body,"Horizontal position","",0,100,1,Math.round(layout.values[active][0]*100),n->n+"%",n->{layout.values[active][0]=n/100f;changed();});
            FestivalUi.slider(this,body,"Vertical position","",0,100,1,Math.round(layout.values[active][1]*100),n->n+"%",n->{layout.values[active][1]=n/100f;changed();});
            FestivalUi.toggle(this,body,"Show this control","Hidden controls stay in the selector.",!layout.hidden[active],on->{layout.hidden[active]=!on;changed();});
            FestivalUi.slider(this,body,"Fade timer","Held buttons remain visible.",-1,120,1,Math.round(layout.timeout[active]),n->n<0?"Global timer":n==0?"Always visible":n+" seconds",n->layout.timeout[active]=n);
        }else if(page==1){
            TouchLayout modern=new TouchLayout();modern.modernDefaults(this);FestivalUi.action(this,body,"Modern racing preset",()->chooseLayout(modern));
            FestivalUi.action(this,body,"Full controller preset",()->chooseLayout(LayoutProfiles.controllerTemplate()));
            FestivalUi.action(this,body,"Left-handed racing preset",()->{TouchLayout left=new TouchLayout();left.modernDefaults(this);for(float[] value:left.values)value[0]=1-value[0];chooseLayout(left);});
            String[] modes={"Floating","Fixed","Invisible"};
            FestivalUi.action(this,body,"Left stick: "+modes[layout.leftMode],()->FestivalUi.choose(this,"Left stick",modes,layout.leftMode,n->{layout.leftMode=n;buildProperties();changed();}));
            FestivalUi.action(this,body,"Look stick: "+modes[layout.rightMode],()->FestivalUi.choose(this,"Look stick",modes,layout.rightMode,n->{layout.rightMode=n;buildProperties();changed();}));
        }else if(page==2){
            FestivalUi.action(this,body,"Steering: "+(layout.steering.equals("arrows")?"Arrows":"Stick"),()->FestivalUi.choose(this,"Steering",new String[]{"Arrows","Stick"},layout.steering.equals("arrows")?0:1,n->{layout.steering=n==0?"arrows":"stick";buildProperties();changed();}));
            FestivalUi.action(this,body,"Arrow response: "+layout.steeringResponseMs+" ms",()->FestivalUi.choose(this,"Arrow response",new String[]{"Immediate","Gentle · 120 ms","Smooth · 240 ms"},Arrays.asList(0,120,240).indexOf(layout.steeringResponseMs),n->{layout.steeringResponseMs=new int[]{0,120,240}[n];buildProperties();}));
            FestivalUi.toggle(this,body,"Sliding pedals","Slide to vary pressure.",layout.analogPedals,on->layout.analogPedals=on);
            FestivalUi.toggle(this,body,"Hide with controller","",layout.autoHide,on->layout.autoHide=on);
            FestivalUi.toggle(this,body,"Touch vibration","Follows Android haptic settings.",layout.haptics,on->layout.haptics=on);
        }else{
            FestivalUi.toggle(this,body,"Modern driving icons","Turn off for controller letters.",layout.drivingLabels,on->{layout.drivingLabels=on;changed();});
            FestivalUi.toggle(this,body,"Button names","Button codes above each icon and action names below.",layout.buttonHints,on->{layout.buttonHints=on;changed();});
            FestivalUi.slider(this,body,"Button background","",20,100,1,Math.round(layout.opacity*100),n->n+"%",n->{layout.opacity=n/100f;changed();});
            FestivalUi.slider(this,body,"Icon and label opacity","",30,100,1,Math.round(layout.iconOpacity*100),n->n+"%",n->{layout.iconOpacity=n/100f;changed();});
            FestivalUi.toggle(this,body,"High contrast","Raises backing opacity to at least 85%.",layout.highContrast,on->{layout.highContrast=on;changed();});
            FestivalUi.slider(this,body,"Global fade timer","",0,120,1,Math.round(layout.idleSeconds),n->n==0?"Always visible":n+" seconds",n->layout.idleSeconds=n);
        }
    }
    private void close(){boolean game=getIntent().getBooleanExtra("return_to_game",false)&&GameFiles.running(this);finish();if(game)startActivity(new Intent(this,PinyonShiftActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    @Override public void onBackPressed(){if(propertyMode){propertyMode=false;showMode();}else close();}
    @Override protected void onPause(){fingers.clear();changed();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("draft",layout.serialize());s.putInt("active",active);s.putInt("page",page);s.putBoolean("properties",propertyMode);s.putBoolean("tools_hidden",toolsHidden);}
    private final class Editor extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector pinch;
        private boolean dragging;
        private float dx,dy;
        Editor(){super(TouchEditorActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);pinch=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector e){if(testMode)return false;layout.values[active][2]=Math.max(.035f,Math.min(.24f,layout.values[active][2]*e.getScaleFactor()));changed();return true;}});}
        float width(){return Math.max(1,getWidth()-safe.left-safe.right);}float height(){return Math.max(1,getHeight()-safe.top-safe.bottom);}
        float radius(int i){return Math.min(layout.values[i][2]*getHeight(),Math.min(width()*.49f/Math.max(1,TouchIcons.BOUNDS[i][0]),height()*.49f/Math.max(1,TouchIcons.BOUNDS[i][1])));}
        float x(int i){float r=radius(i)*Math.max(1,TouchIcons.BOUNDS[i][0]);return safe.left+Math.max(r,Math.min(width()-r,layout.values[i][0]*width()));}
        float y(int i){float r=radius(i)*Math.max(1,TouchIcons.BOUNDS[i][1]);return safe.top+Math.max(r,Math.min(height()-r,layout.values[i][1]*height()));}
        private boolean shown(int i){return !layout.hidden[i]&&layout.enabled(i)&&!(TouchLayout.LABELS[i].equals("LS")&&layout.leftMode==2)&&!(TouchLayout.LABELS[i].equals("RS")&&layout.rightMode==2);}
        private int hit(float x,float y){int found=-1;float best=Float.MAX_VALUE;for(int i=0;i<layout.values.length;i++){if(!shown(i)&&!(i==active&&!testMode))continue;float ax=Math.abs(x-x(i)),ay=Math.abs(y-y(i)),distance=(float)Math.hypot(ax,ay),r=radius(i);boolean inside=TouchIcons.RECT[i]?ax<=TouchIcons.BOUNDS[i][0]*r*1.15f&&ay<=TouchIcons.BOUNDS[i][1]*r*1.15f:distance<=r*1.15f;if(inside&&distance<best){best=distance;found=i;}}if(found<0&&testMode&&y>=safe.top+height()*.55f&&y<=getHeight()-safe.bottom&&x>=safe.left&&x<=getWidth()-safe.right){
            int stick=x<safe.left+width()*.45f?10:x>safe.left+width()*.55f?13:-1;
            if(stick>=0&&!layout.hidden[stick]&&layout.enabled(stick)&&(stick==10?layout.leftMode:layout.rightMode)==2)found=stick;
        }return found;}
        @Override protected void onDraw(Canvas canvas){for(int i=0;i<layout.values.length;i++){if(!shown(i)&&!(i==active&&!testMode))continue;boolean stick=TouchLayout.LABELS[i].equals("LS")||TouchLayout.LABELS[i].equals("RS");float opacity=layout.highContrast?Math.max(.85f,layout.opacity):layout.opacity;
            TouchIconPainter.draw(canvas,paint,artwork,i,x(i),y(i),radius(i),opacity,layout.iconOpacity,fingers.containsValue(i)&&!stick,!shown(i),layout.drivingLabels,layout.buttonHints,!testMode&&i==active);
            if(stick)TouchIconPainter.draw(canvas,paint,artwork,i,x(i),y(i),radius(i)*.55f,opacity,layout.iconOpacity,fingers.containsValue(i),!shown(i),layout.drivingLabels,layout.buttonHints,false,true);}}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(testMode){int action=event.getActionMasked(),index=event.getActionIndex();if(action==MotionEvent.ACTION_CANCEL)fingers.clear();else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)fingers.remove(event.getPointerId(index));else for(int p=0;p<event.getPointerCount();p++){int id=event.getPointerId(p);Integer held=fingers.get(id);boolean stickHeld=held!=null&&(held==10||held==13);
                int found=stickHeld?held:hit(event.getX(p),event.getY(p));
                if((found==10||found==13)&&!stickHeld&&fingers.containsValue(found))found=-1;
                if(found>=0)fingers.put(id,found);else fingers.remove(id);}changed();return true;}
            pinch.onTouchEvent(event);if(event.getPointerCount()>1){dragging=false;return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){int found=hit(event.getX(),event.getY());dragging=found>=0;if(dragging){active=found;dx=event.getX()-x(active);dy=event.getY()-y(active);changed();}return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging&&!pinch.isInProgress()){float rx=radius(active)*Math.max(1,TouchIcons.BOUNDS[active][0]),ry=radius(active)*Math.max(1,TouchIcons.BOUNDS[active][1]);layout.values[active][0]=Math.max(rx,Math.min(width()-rx,event.getX()-dx-safe.left))/width();layout.values[active][1]=Math.max(ry,Math.min(height()-ry,event.getY()-dy-safe.top))/height();changed();}
            else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)dragging=false;
            return true;
        }
    }
}
