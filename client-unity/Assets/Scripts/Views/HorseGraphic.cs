using System.Collections.Generic;
using UnityEngine;

namespace Ludo.Views
{
    // A scalable horse silhouette, independent of fonts and texture resolution.
    [RequireComponent(typeof(CanvasRenderer))]
    public sealed class HorseGraphic : UnityEngine.UI.MaskableGraphic
    {
        private static readonly Vector2[] Shape = {
            new Vector2(.16f,.08f), new Vector2(.85f,.08f), new Vector2(.81f,.20f),
            new Vector2(.72f,.28f), new Vector2(.77f,.45f), new Vector2(.74f,.66f),
            new Vector2(.64f,.82f), new Vector2(.52f,.89f), new Vector2(.43f,.97f),
            new Vector2(.39f,.82f), new Vector2(.30f,.77f), new Vector2(.13f,.57f),
            new Vector2(.17f,.46f), new Vector2(.30f,.45f), new Vector2(.43f,.56f),
            new Vector2(.46f,.46f), new Vector2(.39f,.32f), new Vector2(.23f,.21f)
        };
        private static float Cross(Vector2 a, Vector2 b, Vector2 c) => (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);
        protected override void OnPopulateMesh(UnityEngine.UI.VertexHelper mesh)
        {
            mesh.Clear(); var r = GetPixelAdjustedRect(); var indices = new List<int>();
            for (int i=0;i<Shape.Length;i++)
            { mesh.AddVert(new Vector3(r.x+Shape[i].x*r.width,r.y+Shape[i].y*r.height),color,Vector2.zero); indices.Add(i); }
            while (indices.Count > 2)
            {
                bool clipped = false;
                for (int i=0;i<indices.Count;i++)
                {
                    int a=indices[(i+indices.Count-1)%indices.Count], b=indices[i], c=indices[(i+1)%indices.Count];
                    if (Cross(Shape[a],Shape[b],Shape[c]) <= 0) continue;
                    bool occupied=false;
                    foreach (int p in indices)
                        if(p!=a && p!=b && p!=c && Cross(Shape[a],Shape[b],Shape[p])>=0 && Cross(Shape[b],Shape[c],Shape[p])>=0 && Cross(Shape[c],Shape[a],Shape[p])>=0) { occupied=true; break; }
                    if(occupied) continue;
                    mesh.AddTriangle(a,b,c); indices.RemoveAt(i); clipped=true; break;
                }
                if(!clipped) break;
            }
        }
    }
}
