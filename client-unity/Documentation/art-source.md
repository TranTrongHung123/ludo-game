# Login illustration

Output: `Assets/Art/Login/ludo-pieces.png`.
Created with the built-in `image_gen` tool, not the API/CLI fallback. The generated
PNG is copied into this project with its alpha channel preserved. It contains no UI text.

Final prompt:

> Use case: stylized-concept. Asset type: decorative hero illustration for a Vietnamese Ludo game login screen. Create a high-end playful 3D clay/plastic still life: four horse-head Ludo playing pieces on round tiered bases, one coral red, one periwinkle blue, one mint green, one warm golden yellow; two ivory dice with purple recessed pips. Elegant rounded stylized chess knight silhouettes, shiny soft toy material, three-quarter view, soft ambient occlusion, gentle studio lighting, subtle lavender shadows. Compact wide composition 3:2, pieces arranged at different heights with blue tallest behind, coral red in front left, green on right, small yellow behind and dice in foreground. Actual transparent alpha background, no floor rectangle, no background color, no text, no logo, no watermark, no UI. Objects fully contained with generous padding. The image will sit on a pale lavender background.

The rounded panels, button gradient, crown, arrow and eye icons were authored as
simple UI shapes and stored as PNG sprites. They can be replaced independently of
the prefab's interactive controls. All labels and fields are native TextMeshPro elements.
