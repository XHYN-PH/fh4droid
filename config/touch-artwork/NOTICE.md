# Control artwork sources

Camera, rewind, map, pause, directional arrows, parking-brake and stick-click
symbols are adapted from [Lucide](https://lucide.dev/), at revision
`7faf87628a1ad6175975bc595344337ce14a7db4`. The original 24 × 24 SVG grid is retained. Rendering changes
stroke weight and colour, without changing the proportions of the icon.
The full ISC and applicable Feather MIT notices are in [LICENSE.txt](LICENSE.txt).

Accelerator, brake, clutch and live-stick geometry are original project artwork.
The pedal faces use local SVG gradients, fine bevel edges, recessed grip strips
and mounting details.
The SVGs are the production source; concept pictures are not used in the build.

Regenerate with Pillow 11.3.0 and CairoSVG 2.8.2:

```sh
python tools/generate-touch-icons.py
python tools/generate-touch-artwork.py
```

`python tools/generate-touch-artwork.py --check` uses only the standard library.
