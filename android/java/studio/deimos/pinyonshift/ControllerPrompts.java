package studio.deimos.pinyonshift;
import android.app.Activity;
import android.view.*;
final class ControllerPrompts {
    private static final int TAB_ACTION = 0x7f0a7f08;
    static void tabs(View page, java.util.function.IntConsumer change) {
        page.setTag(TAB_ACTION, change);
    }
    private static java.util.function.IntConsumer findTabs(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        Object action = view.getTag(TAB_ACTION);
        if (action instanceof java.util.function.IntConsumer) return (java.util.function.IntConsumer) action;
        if (view instanceof ViewGroup) {
            ViewGroup children = (ViewGroup) view;
            for (int i = children.getChildCount() - 1; i >= 0; i--) {
                java.util.function.IntConsumer found = findTabs(children.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    static String hints(){
        for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&!d.isVirtual()&&(d.getSources()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD)return d.getVendorId()==0x054c?"D-pad: move · Cross: select · Circle: back":"D-pad: move · A: select · B: back";}
        return "Keyboard: arrows / Tab to move · Enter to select · Esc to go back";
    }
    static boolean handle(Activity a,Window window,int key,KeyEvent event,Runnable back){
        if (window != null && (key == KeyEvent.KEYCODE_BUTTON_L1 || key == KeyEvent.KEYCODE_BUTTON_R1)) {
            java.util.function.IntConsumer change = findTabs(window.getDecorView());
            if (change == null) return false;
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                change.accept(key == KeyEvent.KEYCODE_BUTTON_L1 ? -1 : 1);
            }
            return true;
        }
        if(key!=KeyEvent.KEYCODE_BUTTON_A&&key!=KeyEvent.KEYCODE_BUTTON_B&&key!=KeyEvent.KEYCODE_ESCAPE)return false;
        if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){
            if(key!=KeyEvent.KEYCODE_BUTTON_A)back.run();else {View focus=window==null?null:window.getCurrentFocus();if(focus!=null&&focus.isEnabled())focus.performClick();}
        }return true;
    }
    private ControllerPrompts(){}
}
