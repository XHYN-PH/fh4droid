package studio.deimos.pinyonshift;
import android.app.Activity;
import android.view.*;
import java.util.function.IntConsumer;
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
    static boolean isGamepad(InputDevice device) {
        if (device == null || device.isVirtual()) return false;
        int sources = device.getSources();
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
    static boolean horizontalKey(KeyEvent event, IntConsumer change) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && !event.isCanceled())
            change.accept(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
        return true;
    }
    /** Repeat while the stick is held, without taking vertical menu navigation. */
    static void horizontalStick(View view, IntConsumer change) {
        view.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            int latched;
            Integer sourceDevice;
            final Runnable repeat = new Runnable() {
                @Override public void run() {
                    if (latched == 0 || !view.isFocused() || !view.hasWindowFocus()
                        || !view.isAttachedToWindow() || !view.isEnabled()
                        || sourceDevice!=null && InputDevice.getDevice(sourceDevice)==null) {
                        stop();
                        return;
                    }
                    change.accept(latched);
                    view.postDelayed(this,140);
                }
            };
            private void stop() {
                latched=0;
                sourceDevice=null;
                view.removeCallbacks(repeat);
            }
            @Override public boolean onGenericMotion(View v, MotionEvent event) {
                if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false;
                if (!v.isFocused() || !v.isEnabled() || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    stop();
                    return false;
                }
                if (event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
                float x = event.getAxisValue(MotionEvent.AXIS_X);
                float y = event.getAxisValue(MotionEvent.AXIS_Y);
                if (Math.abs(y) > Math.abs(x)) {
                    stop();
                    return false;
                }
                if (Math.abs(x) <= .30f) {
                    boolean consumed = latched != 0;
                    stop();
                    return consumed;
                }
                if (Math.abs(x) < .60f) return latched != 0;
                int direction = x < 0 ? -1 : 1;
                if (direction != latched) {
                    stop();
                    latched = direction;
                    sourceDevice=event.getDevice()==null?null:event.getDeviceId();
                    change.accept(direction);
                    view.postDelayed(repeat,320);
                }
                return true;
            }
        });
    }
    static String hints(){
        for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(isGamepad(d))return d.getVendorId()==0x054c?"D-pad: move · Cross: select · Circle: back":"D-pad: move · A: select · B: back";}
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
