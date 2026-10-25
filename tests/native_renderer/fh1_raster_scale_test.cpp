#include <rex/graphics/fh1_raster_scale.h>

#include <cassert>
#include <iostream>
using rex::graphics::Fh1RasterScale;
int main() {
  assert(Fh1RasterScale(50).Pixels(1280) == 640);
  assert(Fh1RasterScale(75).Pixels(720) == 540);
  assert(Fh1RasterScale(67).Pixels(1280) == 858);
  assert(Fh1RasterScale(24).units == 25);
  assert(Fh1RasterScale(101).units == 100);
  for (int percent = 25; percent <= 100; ++percent) {
    Fh1RasterScale raster(percent);
    assert(100 - raster.Code() == unsigned(percent));
    for (int n = -2048; n < 2048; ++n) {
      assert(raster.Origin(n) <= raster.End(n));
      assert(raster.End(n) - raster.Origin(n) <= 1);
      if (percent == 100) assert(raster.Origin(n) == n && raster.End(n) == n);
    }
    for (unsigned host = 0; host < raster.Pixels(1280); ++host) {
      const unsigned guest = (host * 100 + raster.units - 1) / raster.units;
      assert(guest * raster.units / 100 == host);
    }
  }
  for (unsigned scale : {1u, 2u, 3u, 4u})
    assert(Fh1RasterScale(100).Pixels(720, scale) == 720 * scale);
  std::cout
      << "PASS: fractional raster bounds, centres and 100% compatibility\n";
}
