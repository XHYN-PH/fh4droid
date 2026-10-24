package studio.deimos.pinyonshift;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.util.function.IntConsumer;

/** Android-only festival menus. No graphics runtime is loaded by these views. */
final class FestivalUi {
    static final int BG=0xff120c0d, PANEL=0xf21b1214, TEXT=0xfffff5ef,
            MUTED=0xffc5b8b3, RED=0xfff1393e, ORANGE=0xffff883b;
    static int dp(Activity a,float v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    static TextView text(Activity a,String value,int size,int color){
        TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));return t;
    }
    static LinearLayout column(Activity a){
        LinearLayout p=new LinearLayout(a);p.setOrientation(LinearLayout.VERTICAL);return p;
    }
    static GradientDrawable surface(Activity a,int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;
    }
    static Button button(Activity a,String title,boolean primary){
        Button b=new Button(a);b.setText(title);b.setAllCaps(false);b.setTextSize(16);
        b.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));b.setTextColor(primary?BG:TEXT);
        GradientDrawable base=primary?new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{RED,ORANGE}):surface(a,0xd5302225,12);
        base.setCornerRadius(dp(a,12));if(!primary)base.setStroke(dp(a,1),0xff624247);
        b.setBackgroundTintList(null);
        GradientDrawable focused=surface(a,primary?0xffffaf70:0xff50302a,12);focused.setStroke(dp(a,3),primary?TEXT:ORANGE);
        StateListDrawable states=new StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},focused);states.addState(new int[]{android.R.attr.state_selected},focused);states.addState(new int[]{},base);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55ffffff),states,surface(a,Color.WHITE,12)));
        b.setMinHeight(dp(a,48));b.setMinimumHeight(dp(a,48));b.setFocusable(true);b.setPadding(dp(a,20),dp(a,8),dp(a,20),dp(a,8));
        b.setStateListAnimator(null);b.setElevation(0);return b;
    }
    static Button action(Activity a,LinearLayout parent,String title,Runnable run){
        Button b=button(a,title,false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(a,9);
        parent.addView(b,p);b.setOnClickListener(v->run.run());return b;
    }
    static AlertDialog dialog(Activity a,String title,View body){
        LinearLayout panel=column(a);int pad=dp(a,20);panel.setPadding(pad,pad,pad,pad);panel.setBackground(surface(a,PANEL,18));
        LinearLayout head=new LinearLayout(a);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text(a,title,24,TEXT);label.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button back=button(a,"Back",false);head.addView(back);panel.addView(head);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);scroll.addView(body);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1);p.topMargin=dp(a,12);panel.addView(scroll,p);
        AlertDialog d=new AlertDialog.Builder(a).setView(panel).create();back.setOnClickListener(v->d.dismiss());
        d.setOnShowListener(v->{Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setDimAmount(.65f);w.setLayout(Math.min(dp(a,740),(int)(a.getResources().getDisplayMetrics().widthPixels*.94f)),
                (int)(a.getResources().getDisplayMetrics().heightPixels*.9f));}enter(panel);});
        return d;
    }
    static void choose(Activity a,String title,String[] labels,int selected,IntConsumer changed){
        LinearLayout p=column(a);AlertDialog d=dialog(a,title,p);
        for(int i=0;i<labels.length;i++){final int n=i;action(a,p,(i==selected?"●  ":"")+labels[i],()->{changed.accept(n);d.dismiss();});}d.show();
    }
    static void enter(View view){
        if(!ValueAnimator.areAnimatorsEnabled()||!view.getContext().getSharedPreferences("mobile",0).getBoolean("animations",true))return;
        view.setAlpha(0);view.setTranslationY(16*view.getResources().getDisplayMetrics().density);
        view.animate().alpha(1).translationY(0).setDuration(280).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    static void immersive(Activity a){
        a.getWindow().setStatusBarColor(BG);a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN
            |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    static final class Artwork extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Bitmap art,logo;
        Artwork(Activity a){super(a);art=asset(a,"menu-background.png");logo=asset(a,"menu-logo.png");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private Bitmap asset(Activity a,String name){try(InputStream in=a.getAssets().open(name)){return BitmapFactory.decodeStream(in);}catch(Exception e){return null;}}
        @Override protected void onDraw(Canvas c){
            float w=getWidth(),h=getHeight(),d=getResources().getDisplayMetrics().density;c.drawColor(BG);
            if(art!=null){float s=Math.max(w/art.getWidth(),h/art.getHeight()),bw=art.getWidth()*s,bh=art.getHeight()*s;
                c.drawBitmap(art,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),paint);}
            paint.setShader(new LinearGradient(0,0,w,0,new int[]{0xe6140c0e,0x77140c0e,0x17140c0e},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);
            paint.setShader(new LinearGradient(0,0,0,h,new int[]{0x44120c0d,0x00120c0d,0xf5120c0d},new float[]{0,.35f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,paint);
            paint.setShader(null);
            // The same upper-right placement as XHYN IV, preserving logo aspect.
            if(logo!=null){float lw=Math.min(180*d,w*.24f),lh=lw*logo.getHeight()/logo.getWidth(),right=w-24*d,top=18*d;
                c.drawBitmap(logo,null,new RectF(right-lw,top,right,top+lh),paint);}
            paint.setShader(new LinearGradient(0,h-3*d,w,h,new int[]{RED,ORANGE},null,Shader.TileMode.CLAMP));c.drawRect(0,h-3*d,w,h,paint);paint.setShader(null);
        }
    }
    private FestivalUi(){}
}
