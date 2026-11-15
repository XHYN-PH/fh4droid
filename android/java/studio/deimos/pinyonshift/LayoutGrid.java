package studio.deimos.pinyonshift;
/** Safe-area-relative editor placement; grid snapping never pushes targets out of bounds. */
final class LayoutGrid {
  static float position(float value,float minimum,float maximum,float step,boolean snap){
    if(!Float.isFinite(value))value=minimum;
    if(snap&&Float.isFinite(step)&&step>0)value=Math.round(value/step)*step;
    return Math.max(minimum,Math.min(maximum,value));
  }
  private LayoutGrid(){}
}
