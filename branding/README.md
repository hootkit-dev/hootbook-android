# Hootbook Android brand assets

The six SVGs are copied verbatim from
[hootkit-dev/hootbook-brand](https://github.com/hootkit-dev/hootbook-brand/tree/main/icon).
The default icon is the white twin-owl design. The alternatives are cream twin
owls, and white/cream versions of hoot and book.

Run `python3 scripts/generate_brand_icons.py` with Pillow and `rsvg-convert`
installed to regenerate Android vectors, adaptive icons, monochrome masks,
legacy launcher bitmaps, and the square 512px store artwork.

The original paths and colors are preserved. Adaptive foregrounds use a 108dp
viewport with the artwork inside the central 66dp safe area; previews use the
same artwork at the launcher's 72dp crop. Background layers have no baked-in
corner radius. Themed icons use transparent alpha masks at 432px (108dp at
xxxhdpi): cream eyes, beaks, and glasses become cutouts while navy shapes stay
opaque. The launcher supplies both theme colors, including in dark mode.

Existing launcher aliases stay stable for upgrades. Saved settings and backups
map ORIGINAL → white twins, ANARCHY → cream twins, ZENITH → white hoot,
COMIC → cream hoot, and MONOCHROME → white book. Cream book adds a sixth alias.
All six choices support the launcher's themed icon setting.
