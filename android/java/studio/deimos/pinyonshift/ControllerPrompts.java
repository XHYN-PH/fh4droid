package studio.deimos.pinyonshift;
import android.app.Activity;
import android.view.*;
final class ControllerPrompts {
    static String hints(){
        for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d!=null&&!d.isVirtual()&&(d.getSources()&InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD)return d.getVendorId()==0x054c?"D-pad: move · Cross: select · Circle: back":"D-pad: move · A: select · B: back";}
        return "Keyboard: arrows / Tab to move · Enter to select · Esc to go back";
    }
    static boolean handle(Activity a,Window window,int key,KeyEvent event,Runnable back){
        if(key!=KeyEvent.KEYCODE_BUTTON_A&&key!=KeyEvent.KEYCODE_BUTTON_B&&key!=KeyEvent.KEYCODE_ESCAPE)return false;
        if(event.getAction()==KeyEvent.ACTION_UP&&!event.isCanceled()){
            if(key!=KeyEvent.KEYCODE_BUTTON_A)back.run();else {View focus=window==null?null:window.getCurrentFocus();if(focus!=null&&focus.isEnabled())focus.performClick();}
        }return true;
    }
    private ControllerPrompts(){}
}
