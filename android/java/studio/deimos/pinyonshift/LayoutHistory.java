package studio.deimos.pinyonshift;

import java.util.*;

/** Bounded draft history; callers commit once at the end of a drag or pinch. */
final class LayoutHistory {
    private final ArrayList<String> states=new ArrayList<>();
    private int cursor;
    LayoutHistory(String initial){states.add(initial);}
    void record(String state){
        if(state.equals(states.get(cursor)))return;
        while(states.size()>cursor+1)states.remove(states.size()-1);
        states.add(state);cursor++;
        if(states.size()>65){states.remove(0);cursor--;}
    }
    boolean canUndo(){return cursor>0;}
    boolean canRedo(){return cursor+1<states.size();}
    String undo(){if(canUndo())cursor--;return states.get(cursor);}
    String redo(){if(canRedo())cursor++;return states.get(cursor);}
}
