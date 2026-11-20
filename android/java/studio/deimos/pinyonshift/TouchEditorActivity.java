package studio.deimos.pinyonshift;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Full-window canvas with a dockable inspector. Only Save commits the draft. */
public final class TouchEditorActivity extends Activity {
    private enum PreviewContext { DRIVE, MENU, PHOTO }
    private final TouchLayout layout=new TouchLayout();
    private final Map<Integer,Integer> fingers=new HashMap<>();
    private FrameLayout root;
    private LinearLayout header,headerRow;
    private ScrollView properties;
    private Editor editor;
    private TextView status;
    private Button propertyButton,testButton,previewButton,reveal;
    private int active,page;
    private boolean propertyMode,testMode,toolsHidden,picker,toolsBottom;
    private boolean inspectorLeft;
    private PreviewContext preview=PreviewContext.DRIVE;
    private boolean showGrid=true,snapGrid=true;private int gridDp=24;
    private Rect safe=new Rect();
    private Bitmap artwork;
    private LayoutHistory history;private String savedLayout;private boolean changingHistory,gesture;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);UiPreferences.load(this);showGrid=getPreferences(0).getBoolean("grid",true);snapGrid=getPreferences(0).getBoolean("snap",true);gridDp=getPreferences(0).getInt("grid_dp",24);if(gridDp!=16&&gridDp!=24&&gridDp!=32)gridDp=24;layout.load(this);savedLayout=layout.serialize();history=new LayoutHistory(savedLayout);artwork=TouchArtwork.bitmap(this);
        if(saved!=null){layout.parse(saved.getString("draft",""));active=Math.max(0,Math.min(TouchLayout.LABELS.length-1,saved.getInt("active")));page=Math.max(0,Math.min(3,saved.getInt("page")));savedLayout=saved.getString("baseline",savedLayout);history.record(layout.serialize());propertyMode=saved.getBoolean("properties");toolsHidden=saved.getBoolean("tools_hidden");toolsBottom=saved.getBoolean("tools_bottom");inspectorLeft=saved.getBoolean("inspector_left");preview=PreviewContext.values()[Math.max(0,Math.min(2,saved.getInt("preview")))];}
        root=new FrameLayout(this);root.addView(new FestivalUi.Artwork(this),new FrameLayout.LayoutParams(-1,-1));
        editor=new Editor();root.addView(editor,new FrameLayout.LayoutParams(-1,-1));
        properties=new ScrollView(this);
        properties.setBackground(FestivalUi.surface(this,FestivalUi.PANEL,16));
        root.addView(properties,new FrameLayout.LayoutParams(-2,-1,Gravity.RIGHT));
        header=FestivalUi.column(this);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(FestivalUi.dp(this,5),FestivalUi.dp(this,4),FestivalUi.dp(this,5),FestivalUi.dp(this,4));
        header.setBackground(FestivalUi.surface(this,0xf514181f,16));
        header.setElevation(FestivalUi.dp(this,6));
        headerRow=new LinearLayout(this);
        header.addView(headerRow);
        propertyButton=headerAction("Properties",()->{propertyMode=!propertyMode;testMode=false;fingers.clear();showMode();});
        headerAction("Add button",this::addButton);
        testButton=headerAction("Test",()->{testMode=!testMode;propertyMode=false;fingers.clear();showMode();});
        if(getResources().getConfiguration().screenWidthDp<740
                ||getResources().getConfiguration().fontScale>1.25f){
            headerRow=new LinearLayout(this);
            header.addView(headerRow);
        }
        previewButton=headerAction("Preview",this::choosePreview);
        headerAction("Edit",this::editTools);
        headerAction("Cancel",this::close);
        headerAction("Save",()->{
            try{layout.save(this);savedLayout=layout.serialize();close();}
            catch(Exception error){SettingsHub.error(this,error);}
        });
        root.addView(header,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        reveal=FestivalUi.button(this,"Show tools",false);root.addView(reveal,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));reveal.setOnClickListener(v->{toolsHidden=false;showMode();});
        status=FestivalUi.text(this,"",13,FestivalUi.TEXT);status.setTextSize(13);status.setPadding(8,3,8,3);status.setBackground(FestivalUi.surface(this,0xaa14181f,6));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(status,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        root.setOnApplyWindowInsetsListener((v,insets)->{Rect i=PlatformCompat.safeInsets(insets);safe=new Rect(i.left,i.top,i.right,i.bottom);fit();editor.invalidate();return insets;});
        header.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot)fit();});
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)fit();});
        setContentView(root);FestivalUi.immersive(this);showMode();
    }
    private void addButton(){
        List<Integer> controls=new ArrayList<>();
        List<String> names=new ArrayList<>();
        for(int i=0;i<TouchLayout.LABELS.length;i++){
            if(editor.shown(i,PreviewContext.DRIVE))continue;
            controls.add(i);
            String note=!layout.enabled(i)?i==10?" · switches to stick":" · switches to arrows":"";
            names.add(TouchArtwork.CAPTIONS[i]+note);
        }
        if(controls.isEmpty()){
            FestivalUi.explain(this,"Add button","All controls for this layout are visible.");
            return;
        }
        FestivalUi.choose(this,"Add button",names.toArray(new String[0]),-1,choice->{
            active=controls.get(choice);
            layout.hidden[active]=false;
            if(active==10){layout.steering="stick";if(layout.leftMode==2)layout.leftMode=0;}
            if(active==11||active==12)layout.steering="arrows";
            if(active==13&&layout.rightMode==2)layout.rightMode=0;
            preview=PreviewContext.DRIVE;
            propertyMode=false;testMode=false;fingers.clear();showMode();
        });
    }
    private void choosePreview(){
        String[] modes={"Driving","Game menus","Photo mode"};
        FestivalUi.choose(this,"Preview context",modes,preview.ordinal(),next->{
            preview=PreviewContext.values()[next];
            fingers.clear();
            showMode();
        });
    }
    private String previewLabel() {
        return preview==PreviewContext.MENU?"Game menus":preview==PreviewContext.PHOTO?"Photo mode":"Driving";
    }
    private void editTools(){
        history.record(layout.serialize());
        String[] labels={"Undo last edit","Redo last edit","Check touch areas","Hide tools","Gridlines: "+(showGrid?"On":"Off"),"Snapping: "+(snapGrid?"On":"Off"),"Grid spacing: "+gridDp+" dp",toolsBottom?"Move tools to top":"Move tools to bottom"};
        FestivalUi.choose(this,"Layout tools",labels,-1,index->{
            if(index==7){toolsBottom=!toolsBottom;fit();return;}
            if(index>=4){if(index==4)showGrid=!showGrid;else if(index==5)snapGrid=!snapGrid;else gridDp=gridDp==16?24:gridDp==24?32:16;getPreferences(0).edit().putBoolean("grid",showGrid).putBoolean("snap",snapGrid).putInt("grid_dp",gridDp).apply();editor.invalidate();return;}
            if(index==3){toolsHidden=true;propertyMode=false;showMode();return;}
            if(index==2){java.util.List<String> warnings=LayoutWarnings.check(layout,editor.getWidth(),editor.getHeight(),safe.left,safe.top,safe.right,safe.bottom,FestivalUi.dp(this,48));
                FestivalUi.explain(this,"Touch areas",warnings.isEmpty()?"No overlapping or small targets found. Test simultaneous presses before saving.":String.join("\n",warnings)+"\n\nWarnings do not move your controls.");return;}
            if(index==0&&!history.canUndo()||index==1&&!history.canRedo()){Toast.makeText(this,index==0?"Nothing to undo":"Nothing to redo",Toast.LENGTH_SHORT).show();return;}
            changingHistory=true;try{layout.reset();layout.parse(index==0?history.undo():history.redo());fingers.clear();buildProperties();changed();}finally{changingHistory=false;}
        });
    }
    private Button headerAction(String label,Runnable action){
        Button b=FestivalUi.button(this,label,label.equals("Save"));
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(FestivalUi.dp(this,10),0,FestivalUi.dp(this,10),0);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        if(headerRow.getChildCount()>0)params.leftMargin=FestivalUi.dp(this,4);
        headerRow.addView(b,params);
        b.setOnClickListener(v->action.run());
        return b;
    }
    private void fit(){
        int gap=FestivalUi.dp(this,8);
        FrameLayout.LayoutParams h=(FrameLayout.LayoutParams)header.getLayoutParams();
        h.gravity=(toolsBottom?Gravity.BOTTOM:Gravity.TOP)|Gravity.CENTER_HORIZONTAL;
        h.setMargins(safe.left+gap,safe.top+gap,safe.right+gap,safe.bottom+gap);
        header.setLayoutParams(h);
        int dock=header.getHeight()+gap*2;
        FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)properties.getLayoutParams();
        int width=root.getWidth()>0?root.getWidth():getResources().getDisplayMetrics().widthPixels;
        int usable=Math.max(1,width-safe.left-safe.right-gap*2);
        p.width=Math.min(FestivalUi.dp(this,350),Math.round(usable*.49f));
        p.gravity=(inspectorLeft?Gravity.LEFT:Gravity.RIGHT)|Gravity.TOP;
        p.setMargins(safe.left+gap,safe.top+(toolsBottom?gap:dock),safe.right+gap,safe.bottom+(toolsBottom?dock:gap));
        properties.setLayoutParams(p);
        FrameLayout.LayoutParams r=(FrameLayout.LayoutParams)reveal.getLayoutParams();
        r.topMargin=safe.top+gap;reveal.setLayoutParams(r);
        FrameLayout.LayoutParams s=(FrameLayout.LayoutParams)status.getLayoutParams();
        s.gravity=(toolsBottom?Gravity.TOP:Gravity.BOTTOM)|Gravity.CENTER_HORIZONTAL;
        s.topMargin=safe.top+gap;s.bottomMargin=safe.bottom+gap;status.setLayoutParams(s);
    }
    private void showMode(){
        header.setVisibility(toolsHidden?View.GONE:View.VISIBLE);reveal.setVisibility(toolsHidden?View.VISIBLE:View.GONE);properties.setVisibility(propertyMode?View.VISIBLE:View.GONE);
        propertyButton.setText(propertyMode?"Canvas":"Properties");testButton.setText(testMode?"Stop test":"Test");
        previewButton.setText(preview==PreviewContext.DRIVE?"Preview":previewLabel());
        status.setVisibility(toolsHidden&&!testMode||propertyMode?View.GONE:View.VISIBLE);
        if(propertyMode)buildProperties();changed();
    }
    private String title(){return TouchLayout.displayLabel(active,layout.drivingLabels);}
    private void changed(){
        if(history!=null&&!changingHistory&&!gesture)history.record(layout.serialize());
        if(status!=null){if(testMode){Set<String> names=new LinkedHashSet<>();for(int i:fingers.values())names.add(TouchArtwork.CAPTIONS[i]);status.setText(names.isEmpty()?"Test controls · touches stay in the editor":String.join(" + ",names));}
            else status.setText(String.format(Locale.ROOT,"%s · %s · X %.0f%% · Y %.0f%% · %.1f%% size",previewLabel(),title(),layout.values[active][0]*100,layout.values[active][1]*100,layout.values[active][2]*100));}
        if(editor!=null)editor.invalidate();
    }
    private void chooseLayout(TouchLayout next){new AlertDialog.Builder(this).setTitle("Apply layout to this draft?").setMessage("This replaces positions and visibility in the draft. Save applies it; Cancel keeps your current layout.").setNegativeButton("Keep draft",null).setPositiveButton("Apply",(d,w)->{layout.reset();layout.parse(next.serialize());active=0;buildProperties();changed();}).show();}
    private void buildProperties(){
        properties.removeAllViews();LinearLayout body=FestivalUi.column(this);int pad=FestivalUi.dp(this,10);body.setPadding(pad,pad,pad,pad);properties.addView(body);
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(FestivalUi.text(this,"Properties",14,FestivalUi.TEXT),new LinearLayout.LayoutParams(0,-2,1));
        Button dock=FestivalUi.button(this,inspectorLeft?"›":"‹",false);
        dock.setContentDescription(inspectorLeft?"Move inspector right":"Move inspector left");
        dock.setOnClickListener(v->{inspectorLeft=!inspectorLeft;fit();buildProperties();});
        top.addView(dock,new LinearLayout.LayoutParams(FestivalUi.dp(this,42),-2));
        body.addView(top);
        String[] pages={"Control","Layout","Driving","Style"};
        for(int i=0;i<pages.length;i+=2){
            LinearLayout tabs=new LinearLayout(this);
            body.addView(tabs);
            for (int j=i;j<i+2;j++) {
                int next=j;
                Button button=FestivalUi.button(this,pages[j],false);
                button.setTextSize(12);
                button.setTextColor(page==j?FestivalUi.ORANGE:FestivalUi.MUTED);
                button.setSelected(page==j);
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);
                cell.setMargins(0,FestivalUi.dp(this,4),j==i?FestivalUi.dp(this,4):0,0);
                tabs.addView(button,cell);
                button.setOnClickListener(v->selectProperties(next));
            }
        }
        ControllerPrompts.tabs(properties,direction->selectProperties(Math.floorMod(page+direction,pages.length)));
        if(page==0){
            FestivalUi.action(this,body,title()+" · select control",()->{picker=!picker;buildProperties();});
            if(picker){for(int i=0;i<TouchLayout.LABELS.length;i++){final int n=i;FestivalUi.action(this,body,TouchArtwork.CAPTIONS[i]+(layout.hidden[i]?" · hidden":""),()->{active=n;picker=false;buildProperties();changed();});}return;}
            FestivalUi.slider(this,body,"Control opacity","Use the global appearance at 0%. High contrast still applies to the background.",0,100,1,
                    Math.round(layout.controlOpacity[active]*100),n->n==0?"Global":n+"%",
                    n->{layout.controlOpacity[active]=n/100f;changed();});
            if(TouchLayout.mappable(active))FestivalUi.action(this,body,"Action: "+TouchLayout.LABELS[layout.mapping[active]]+" · "+TouchArtwork.CAPTIONS[layout.mapping[active]],()->{
                java.util.List<Integer> ids=new java.util.ArrayList<>();java.util.List<String> names=new java.util.ArrayList<>();for(int i=0;i<TouchLayout.LABELS.length;i++)if(TouchLayout.mappable(i)){ids.add(i);names.add(TouchLayout.LABELS[i]+" · "+TouchArtwork.CAPTIONS[i]);}
                FestivalUi.choose(this,"Controller action (driving only)",names.toArray(new String[0]),ids.indexOf(layout.mapping[active]),index->{layout.mapping[active]=ids.get(index);changed();buildProperties();});
            });
            FestivalUi.slider(this,body,"Size","Pinch on the full-screen canvas to resize.",35,240,5,Math.round(layout.values[active][2]*1000),n->String.format(Locale.ROOT,"%.1f%%",n/10f),n->{layout.values[active][2]=n/1000f;changed();});
            FestivalUi.slider(this,body,"Horizontal position","",0,100,1,Math.round(layout.values[active][0]*100),n->n+"%",n->{layout.values[active][0]=n/100f;changed();});
            FestivalUi.slider(this,body,"Vertical position","",0,100,1,Math.round(layout.values[active][1]*100),n->n+"%",n->{layout.values[active][1]=n/100f;changed();});
            FestivalUi.toggle(this,body,"Show this control","Hidden controls stay in the selector.",!layout.hidden[active],on->{layout.hidden[active]=!on;changed();});
            FestivalUi.slider(this,body,"Fade timer","Held buttons remain visible.",-1,120,1,Math.round(layout.timeout[active]),n->n<0?"Global timer":n==0?"Always visible":n+" seconds",n->{layout.timeout[active]=n;changed();});
        }else if(page==1){
            TouchLayout modern=new TouchLayout();modern.modernDefaults(this);FestivalUi.action(this,body,"Modern racing preset",()->chooseLayout(modern));
            FestivalUi.action(this,body,"Full controller preset",()->chooseLayout(LayoutProfiles.controllerTemplate()));
            FestivalUi.action(this,body,"Left-handed racing preset",()->{TouchLayout left=new TouchLayout();left.modernDefaults(this);for(float[] value:left.values)value[0]=1-value[0];chooseLayout(left);});
            String[] modes={"Floating","Fixed","Invisible"};
            FestivalUi.action(this,body,"Left stick: "+modes[layout.leftMode],()->FestivalUi.choose(this,"Left stick",modes,layout.leftMode,n->{layout.leftMode=n;buildProperties();changed();}));
            FestivalUi.action(this,body,"Look stick: "+modes[layout.rightMode],()->FestivalUi.choose(this,"Look stick",new String[]{"Floating", "Fixed", "Invisible · right half"},layout.rightMode,n->{layout.rightMode=n;buildProperties();changed();}));
        }else if(page==2){
            FestivalUi.action(this,body,"Steering: "+(layout.steering.equals("arrows")?"Arrows":"Stick"),()->FestivalUi.choose(this,"Steering",new String[]{"Arrows","Stick"},layout.steering.equals("arrows")?0:1,n->{layout.steering=n==0?"arrows":"stick";buildProperties();changed();}));
            FestivalUi.action(this,body,"Arrow response: "+layout.steeringResponseMs+" ms",()->FestivalUi.choose(this,"Arrow response",new String[]{"Immediate","Gentle · 120 ms","Smooth · 240 ms"},Arrays.asList(0,120,240).indexOf(layout.steeringResponseMs),n->{layout.steeringResponseMs=new int[]{0,120,240}[n];buildProperties();changed();}));
            FestivalUi.slider(this,body,"Arrow return to centre","Separate from turning into a corner. Matching keeps the previous behaviour.",-1,500,1,layout.steeringReturnMs,n->n<0?"Match turn-in":n+" ms",n->{layout.steeringReturnMs=n;changed();});
            FestivalUi.slider(this,body,"Maximum arrow steering","Limit full-lock steering when using arrows.",25,100,5,Math.round(layout.steeringLock*100),n->n+"%",n->{layout.steeringLock=n/100f;changed();});
            FestivalUi.toggle(this,body,"Sliding pedals","Slide to vary pressure.",layout.analogPedals,on->{layout.analogPedals=on;changed();});
            FestivalUi.toggle(this,body,"Hide with controller","",layout.autoHide,on->{layout.autoHide=on;changed();});
            FestivalUi.toggle(this,body,"Touch vibration","Follows Android haptic settings.",layout.haptics,on->{layout.haptics=on;changed();});
        }else{
            FestivalUi.toggle(this,body,"Driving action names","Turn off for controller button codes.",layout.drivingLabels,on->{layout.drivingLabels=on;changed();});
            FestivalUi.toggle(this,body,"Button codes","Small controller codes above the action names.",layout.buttonHints,on->{layout.buttonHints=on;changed();});
            FestivalUi.slider(this,body,"Button background","",20,100,1,Math.round(layout.opacity*100),n->n+"%",n->{layout.opacity=n/100f;changed();});
            FestivalUi.slider(this,body,"Text label opacity","",30,100,1,Math.round(layout.iconOpacity*100),n->n+"%",n->{layout.iconOpacity=n/100f;changed();});
            FestivalUi.toggle(this,body,"High contrast","Raises backing opacity to at least 85%.",layout.highContrast,on->{layout.highContrast=on;changed();});
            FestivalUi.slider(this,body,"Global fade timer","",0,120,1,Math.round(layout.idleSeconds),n->n==0?"Always visible":n+" seconds",n->{layout.idleSeconds=n;changed();});
        }
    }
    private void selectProperties(int next){
        page=next;
        picker=false;
        buildProperties();
        properties.scrollTo(0,0);
    }
    private void close(){if(!layout.serialize().equals(savedLayout)){new AlertDialog.Builder(this).setTitle("Discard layout changes?").setMessage("Your saved layout is kept. Choose Save to apply this draft.").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->finishEditor()).show();return;}finishEditor();}
    private void finishEditor(){boolean game=getIntent().getBooleanExtra("return_to_game",false)&&GameFiles.running(this);finish();if(game)startActivity(new Intent(this,PinyonShiftActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));}
    @Override public void onBackPressed(){if(propertyMode){propertyMode=false;showMode();}else close();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        return ControllerPrompts.handle(this,getWindow(),event.getKeyCode(),event,this::onBackPressed)
            || super.dispatchKeyEvent(event);
    }
    @Override protected void onPause(){fingers.clear();changed();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle s){super.onSaveInstanceState(s);s.putString("draft",layout.serialize());s.putString("baseline",savedLayout);s.putInt("active",active);s.putInt("page",page);s.putBoolean("properties",propertyMode);s.putBoolean("tools_hidden",toolsHidden);s.putBoolean("tools_bottom",toolsBottom);s.putBoolean("inspector_left",inspectorLeft);s.putInt("preview",preview.ordinal());}
    private final class Editor extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ScaleGestureDetector pinch;
        private boolean dragging;
        private float dx,dy;
        Editor(){super(TouchEditorActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);pinch=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScaleBegin(ScaleGestureDetector e){if(testMode)return false;history.record(layout.serialize());gesture=true;return true;}@Override public void onScaleEnd(ScaleGestureDetector e){gesture=false;changed();}@Override public boolean onScale(ScaleGestureDetector e){if(testMode)return false;layout.values[active][2]=Math.max(.035f,Math.min(.24f,layout.values[active][2]*e.getScaleFactor()));changed();return true;}});}
        float width(){return Math.max(1,getWidth()-safe.left-safe.right);}float height(){return Math.max(1,getHeight()-safe.top-safe.bottom);}
        int icon(int i){return preview==PreviewContext.DRIVE?layout.mapping[i]:i;}
        float radius(int i){return Math.min(layout.values[i][2]*getHeight(),Math.min(width()*.49f/Math.max(1,TouchIcons.BOUNDS[icon(i)][0]),height()*.49f/Math.max(1,TouchIcons.BOUNDS[icon(i)][1])));}
        float x(int i){float r=radius(i)*Math.max(1,TouchIcons.BOUNDS[icon(i)][0]);return safe.left+Math.max(r,Math.min(width()-r,layout.values[i][0]*width()));}
        float y(int i){float r=radius(i)*Math.max(1,TouchIcons.BOUNDS[icon(i)][1]);return safe.top+Math.max(r,Math.min(height()-r,layout.values[i][1]*height()));}
        private boolean shown(int i,PreviewContext context){
            if(context==PreviewContext.DRIVE)
                return !layout.hidden[i]&&layout.enabled(i)&&!(i==10&&layout.leftMode==2)&&!(i==13&&layout.rightMode==2);
            if(i==11||i==12)return false;
            if(i==0||i==1)return context==PreviewContext.PHOTO&&!layout.hidden[i];
            return i<18||!layout.hidden[i];
        }
        private int hit(float x,float y){int found=-1;float best=Float.MAX_VALUE;for(int i=0;i<layout.values.length;i++){if(!shown(i,preview)&&!(i==active&&!testMode&&preview==PreviewContext.DRIVE))continue;float ax=Math.abs(x-x(i)),ay=Math.abs(y-y(i)),distance=(float)Math.hypot(ax,ay),r=radius(i);int icon=icon(i);boolean inside=TouchIcons.RECT[icon]?ax<=TouchIcons.BOUNDS[icon][0]*r*1.15f&&ay<=TouchIcons.BOUNDS[icon][1]*r*1.15f:distance<=r*1.15f;if(inside&&distance<best){best=distance;found=i;}}if(found<0&&testMode&&preview==PreviewContext.DRIVE&&y>=safe.top&&y<=getHeight()-safe.bottom&&x>=safe.left&&x<=getWidth()-safe.right){
            int stick=x>=safe.left+width()*.5f?13:x<safe.left+width()*.45f&&y>=safe.top+height()*.55f?10:-1;
            if(stick>=0&&!layout.hidden[stick]&&layout.enabled(stick)&&(stick==10?layout.leftMode:layout.rightMode)==2)found=stick;
        }return found;}
        @Override protected void onDraw(Canvas canvas){if(showGrid&&!testMode){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1);paint.setColor(0x35ffffff);float step=FestivalUi.dp(TouchEditorActivity.this,gridDp);for(float x=safe.left;x<=getWidth()-safe.right;x+=step)canvas.drawLine(x,safe.top,x,getHeight()-safe.bottom,paint);for(float y=safe.top;y<=getHeight()-safe.bottom;y+=step)canvas.drawLine(safe.left,y,getWidth()-safe.right,y,paint);paint.setColor(0x70ff326e);canvas.drawLine(safe.left+width()/2,safe.top,safe.left+width()/2,getHeight()-safe.bottom,paint);canvas.drawLine(safe.left,safe.top+height()/2,getWidth()-safe.right,safe.top+height()/2,paint);}
          if(dragging&&snapGrid&&!testMode){
              paint.setColor(0xadff326e);
              paint.setStrokeWidth(2);
              canvas.drawLine(x(active),safe.top,x(active),getHeight()-safe.bottom,paint);
              canvas.drawLine(safe.left,y(active),getWidth()-safe.right,y(active),paint);
          }
          for(int i=0;i<layout.values.length;i++){boolean visible=shown(i,preview);if(!visible&&!(i==active&&!testMode&&preview==PreviewContext.DRIVE))continue;boolean stick=i==10||i==13;float opacity=layout.backgroundOpacity(i);boolean driving=preview==PreviewContext.DRIVE&&layout.drivingLabels;
            TouchIconPainter.draw(canvas,paint,artwork,icon(i),x(i),y(i),radius(i),opacity,layout.labelOpacity(i),fingers.containsValue(i)&&!stick,!visible,driving,layout.buttonHints,!testMode&&i==active);
            if(stick)TouchIconPainter.draw(canvas,paint,artwork,icon(i),x(i),y(i),radius(i)*.55f,opacity,layout.labelOpacity(i),fingers.containsValue(i),!visible,driving,layout.buttonHints,false,true);}}
        @Override public boolean onTouchEvent(MotionEvent event){
            if(testMode){int action=event.getActionMasked(),index=event.getActionIndex();if(action==MotionEvent.ACTION_CANCEL)fingers.clear();else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)fingers.remove(event.getPointerId(index));else for(int p=0;p<event.getPointerCount();p++){int id=event.getPointerId(p);Integer held=fingers.get(id);boolean stickHeld=held!=null&&(held==10||held==13);
                int found=stickHeld?held:hit(event.getX(p),event.getY(p));
                if((found==10||found==13)&&!stickHeld&&fingers.containsValue(found))found=-1;
                if(found>=0)fingers.put(id,found);else fingers.remove(id);}changed();return true;}
            pinch.onTouchEvent(event);if(event.getPointerCount()>1){dragging=false;return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){int found=hit(event.getX(),event.getY());dragging=found>=0;if(dragging){history.record(layout.serialize());gesture=true;active=found;dx=event.getX()-x(active);dy=event.getY()-y(active);if(propertyMode)buildProperties();changed();}return true;}
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging&&!pinch.isInProgress()){float rx=radius(active)*Math.max(1,TouchIcons.BOUNDS[icon(active)][0]),ry=radius(active)*Math.max(1,TouchIcons.BOUNDS[icon(active)][1]);layout.values[active][0]=LayoutGrid.position(event.getX()-dx-safe.left,rx,width()-rx,FestivalUi.dp(TouchEditorActivity.this,gridDp),snapGrid)/width();layout.values[active][1]=LayoutGrid.position(event.getY()-dy-safe.top,ry,height()-ry,FestivalUi.dp(TouchEditorActivity.this,gridDp),snapGrid)/height();changed();}
            else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;gesture=false;if(propertyMode)buildProperties();changed();}
            return true;
        }
    }
}
