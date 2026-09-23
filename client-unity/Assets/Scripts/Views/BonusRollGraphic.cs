using UnityEngine;

namespace Ludo.Views
{
    // A die and plus sign, independent of font glyph coverage.
    [RequireComponent(typeof(CanvasRenderer))]
    public sealed class BonusRollGraphic : UnityEngine.UI.MaskableGraphic
    {
        protected override void OnPopulateMesh(UnityEngine.UI.VertexHelper vh)
        {
            vh.Clear();
            var rect = GetPixelAdjustedRect();
            void Box(float x, float y, float w, float h)
            {
                int start = vh.currentVertCount;
                var origin = new Vector2(rect.x + x * rect.width, rect.y + y * rect.height);
                vh.AddVert(origin, color, Vector2.zero);
                vh.AddVert(origin + new Vector2(0, h * rect.height), color, Vector2.zero);
                vh.AddVert(origin + new Vector2(w * rect.width, h * rect.height), color, Vector2.zero);
                vh.AddVert(origin + new Vector2(w * rect.width, 0), color, Vector2.zero);
                vh.AddTriangle(start, start + 1, start + 2);
                vh.AddTriangle(start, start + 2, start + 3);
            }
            Box(.03f,.16f,.60f,.07f); Box(.03f,.77f,.60f,.07f);
            Box(.03f,.16f,.07f,.68f); Box(.56f,.16f,.07f,.68f);
            foreach (var p in new[] { new Vector2(.17f,.31f), new Vector2(.41f,.31f), new Vector2(.29f,.46f), new Vector2(.17f,.61f), new Vector2(.41f,.61f) })
                Box(p.x,p.y,.08f,.08f);
            Box(.71f,.46f,.27f,.08f); Box(.805f,.35f,.08f,.30f);
        }
    }
}
