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
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);selected=FestivalUi.text(this,"Drag a button · pinch to resize",16,FestivalUi.TEXT);
        top.addView(selected,new LinearLayout.LayoutParams(0,-2,1));Button save=FestivalUi.button(this,"Save",true),back=FestivalUi.button(this,"Cancel",false);top.addView(back);top.addView(save);root.addView(top);
        editor=new Editor();root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
        HorizontalScrollView strip=new HorizontalScrollView(this);LinearLayout tools=new LinearLayout(this);
        tool(tools,"Smaller",()->editor.resize(.9f));tool(tools,"Larger",()->editor.resize(1.1f));
        tool(tools,"Hide / show",()->{layout.hidden[editor.active]=!layout.hidden[editor.active];editor.invalidate();});
        tool(tools,"Opacity",()->{layout.opacity=layout.opacity>=.95f?.25f:Math.min(1,layout.opacity+.15f);editor.invalidate();});
        tool(tools,"Reset",()->new AlertDialog.Builder(this).setTitle("Reset layout?").setNegativeButton("Cancel",null).setPositiveButton("Reset",(d,w)->{layout.reset();editor.invalidate();}).show());
        Switch auto=new Switch(this);auto.setText("Hide with controller");auto.setTextColor(FestivalUi.MUTED);auto.setChecked(layout.autoHide);auto.setOnCheckedChangeListener((b,on)->layout.autoHide=on);tools.addView(auto);
        strip.addView(tools);root.addView(strip);setContentView(root);FestivalUi.immersive(this);
        save.setOnClickListener(v->{try{layout.save(this);finish();}catch(Exception e){Toast.makeText(this,e.getMessage(),1).show();}});back.setOnClickListener(v->finish());
    }
    private void tool(LinearLayout parent,String title,Runnable run){Button b=FestivalUi.button(this,title,false);parent.addView(b);b.setOnClickListener(v->run.run());}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("draft",layout.serialize());}
    private final class Editor extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int active=0;float dx,dy;boolean dragging;final ScaleGestureDetector pinch;
        Editor(){super(TouchEditorActivity.this);setContentDescription("Touch controller layout editor");pinch=new ScaleGestureDetector(getContext(),new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){resize(detector.getScaleFactor());return true;}});}
        float previewWidth(){float aspect=getRootView().getWidth()/(float)Math.max(1,getRootView().getHeight());return Math.min(getWidth(),getHeight()*aspect);}
        float previewHeight(){float aspect=getRootView().getWidth()/(float)Math.max(1,getRootView().getHeight());return previewWidth()/Math.max(.1f,aspect);}
        float left(){return (getWidth()-previewWidth())/2;}
        float top(){return (getHeight()-previewHeight())/2;}
        float radius(int i){return layout.values[i][2]*previewHeight();}
        float x(int i){float r=radius(i);return left()+Math.max(r,Math.min(previewWidth()-r,layout.values[i][0]*previewWidth()));}
        float y(int i){float r=radius(i);return top()+Math.max(r,Math.min(previewHeight()-r,layout.values[i][1]*previewHeight()));}
        void resize(float factor){layout.values[active][2]=Math.max(.035f,Math.min(.24f,layout.values[active][2]*factor));invalidate();}
        @Override protected void onDraw(Canvas c){
            c.drawColor(FestivalUi.BG);paint.setColor(0xff211619);c.drawRect(left(),top(),left()+previewWidth(),top()+previewHeight(),paint);
            paint.setColor(0xff3d292c);paint.setStrokeWidth(1);
            for(int i=1;i<10;i++)c.drawLine(left()+previewWidth()*i/10f,top(),left()+previewWidth()*i/10f,top()+previewHeight(),paint);
            for(int i=1;i<6;i++)c.drawLine(left(),top()+previewHeight()*i/6f,left()+previewWidth(),top()+previewHeight()*i/6f,paint);
            for(int i=0;i<layout.values.length;i++){paint.setStyle(Paint.Style.FILL);paint.setColor(i==active?FestivalUi.ORANGE:FestivalUi.RED);paint.setAlpha(layout.hidden[i]?40:(int)(layout.opacity*255));
                c.drawCircle(x(i),y(i),radius(i),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(i==active?4:2);paint.setColor(FestivalUi.TEXT);paint.setAlpha(layout.hidden[i]?80:230);c.drawCircle(x(i),y(i),radius(i),paint);
                paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.min(radius(i)*.6f,FestivalUi.dp(TouchEditorActivity.this,18)));paint.setAlpha(255);
                c.drawText(TouchLayout.LABELS[i],x(i),y(i)-(paint.ascent()+paint.descent())/2,paint);}
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            pinch.onTouchEvent(e);if(e.getPointerCount()>1){dragging=false;return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float best=Float.MAX_VALUE;
                for(int i=0;i<layout.values.length;i++){float d=(float)Math.hypot(e.getX()-x(i),e.getY()-y(i));if(d<=radius(i)*1.2f&&d<best){active=i;best=d;}}
                dragging=best<Float.MAX_VALUE;if(!dragging)return true;
                dx=e.getX()-x(active);dy=e.getY()-y(active);selected.setText(TouchLayout.LABELS[active]+" · drag or pinch");invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&dragging&&!pinch.isInProgress()){float r=radius(active);
                layout.values[active][0]=Math.max(r,Math.min(previewWidth()-r,e.getX()-dx-left()))/Math.max(1,previewWidth());
                layout.values[active][1]=Math.max(r,Math.min(previewHeight()-r,e.getY()-dy-top()))/Math.max(1,previewHeight());invalidate();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)dragging=false;
            return true;
        }
    }
}
