#include <rex/graphics/fh1_raster_scale.h>
#include <cassert>
#include <iostream>
using rex::graphics::Fh1RasterScale;
int main() {
  assert(Fh1RasterScale(50).Pixels(1280)==640);
  assert(Fh1RasterScale(75).Pixels(720)==540);
  assert(Fh1RasterScale(100).Pixels(1280)==1280);
  assert(Fh1RasterScale(66).Pixels(1280)==1280);
  for(int percent : {50,75,100}) {
    Fh1RasterScale raster(percent);
    for(int n=-2048;n<2048;++n) {
      assert(raster.Origin(n)<=raster.End(n));
      assert(raster.End(n)-raster.Origin(n)<=1);
      if(percent==100) {
        assert(raster.Origin(n)==n&&raster.End(n)==n);
      }
    }
    // Each host pixel centre must return to its own attachment texel after
    // host -> guest EDRAM -> source mapping (the resolve/transfer convention).
    for(unsigned host=0;host<raster.Pixels(1280);++host) {
      const unsigned guest=(host*4+2)/raster.units;
      assert(guest*raster.units/4==host);
    }
  }
  for(unsigned scale : {1u,2u,3u,4u})
    assert(Fh1RasterScale(100).Pixels(720,scale)==720*scale);
  std::cout<<"PASS: fractional raster bounds, centres and 100% compatibility\n";
}
