using System;
using System.IO;
using UnityEngine;
using UnityEditor;

public static class CaptureResultUi
{
    // Offscreen Camera.Render does not include overlay UI. Temporarily attach the
    // canvas to the capture camera, then restore the actual runtime configuration.
    public static string Main(int width = 1920, int height = 1080, string suffix = "")
    {
        var canvas=UnityEngine.Object.FindFirstObjectByType<Canvas>();var camera=Camera.main;
        var mode=canvas.renderMode;var priorCamera=canvas.worldCamera;float distance=canvas.planeDistance;
        int mask=camera.cullingMask;var previous=camera.targetTexture;var active=RenderTexture.active;
        var rt=new RenderTexture(width,height,24);Texture2D texture=null;
        try
        {
            camera.targetTexture=rt;camera.cullingMask=-1;canvas.renderMode=RenderMode.ScreenSpaceCamera;canvas.worldCamera=camera;canvas.planeDistance=10;
            Canvas.ForceUpdateCanvases();camera.Render();RenderTexture.active=rt;
            texture=new Texture2D(width,height,TextureFormat.RGB24,false);texture.ReadPixels(new Rect(0,0,width,height),0,0);texture.Apply();
            string path="Documentation/result-"+width+"x"+height+suffix+".png";File.WriteAllBytes(path,texture.EncodeToPNG());return path;
        }
        finally
        {
            canvas.renderMode=mode;canvas.worldCamera=priorCamera;canvas.planeDistance=distance;camera.cullingMask=mask;camera.targetTexture=previous;RenderTexture.active=active;
            rt.Release();UnityEngine.Object.DestroyImmediate(rt);if(texture!=null)UnityEngine.Object.DestroyImmediate(texture);Canvas.ForceUpdateCanvases();
        }
    }
}
