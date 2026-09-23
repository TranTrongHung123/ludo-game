using System;
using System.IO;
using System.Linq;
using Ludo.Views;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

public static class PreviewMovePresentation
{
    public static JObject Snapshot()
    {
        var members = new JArray(); var specials = new JArray();
        for (int slot=0;slot<4;slot++)
        {
            var pieces=new JArray();
            for(int i=0;i<4;i++) pieces.Add(new JObject { ["pieceId"]="p"+slot+"-"+i,["ownerPlayerId"]="p"+slot,["color"]=BoardGeometry.Colors[slot],["state"]=i<=slot?"FINISHED":"IN_YARD",["stepCount"]=i<=slot?53:-1 });
            members.Add(new JObject { ["playerId"]="p"+slot,["displayName"]=BoardGeometry.Names[slot],["slotIndex"]=slot,["color"]=BoardGeometry.Colors[slot],["presenceState"]="PLAYING",["matchStatus"]="ACTIVE",["pieces"]=pieces });
            for(int i=0;i<4;i++) specials.Add(new JObject { ["globalIndex"]=slot*12+2+i*2,["type"]=new[]{"SPEED","SLOW","LUCKY","TRAP"}[i] });
        }
        return new JObject { ["roomId"]="preview",["matchId"]="preview",["stateVersion"]=1,["participants"]=members,["specialCells"]=specials,["validPieceIds"]=new JArray() };
    }
    public static string Main()
    {
        if(EditorApplication.isPlaying) throw new Exception("Run in Edit Mode.");
        var scene=EditorSceneManager.NewPreviewScene();
        RenderTexture rt=null; Texture2D texture=null; Camera camera=null;
        var previous=RenderTexture.active;
        try
        {
            var root=(GameObject)PrefabUtility.InstantiatePrefab(AssetDatabase.LoadAssetAtPath<GameObject>("Assets/Prefabs/Game/GameScreen.prefab"),scene);
            var board=root.GetComponentInChildren<BoardView>(true); var snapshot=Snapshot();
            board.Bind(snapshot,"p0",null,false,_=>{},false);
            var players=root.GetComponentsInChildren<PlayerCardView>(true);
            for(int i=0;i<4;i++)players[i].Bind(snapshot["participants"][i],i,"p0","p0");
            var notice=root.GetComponentInChildren<BoardNotice>(true);notice.Show("Đỏ • Ngựa 1 đã về đích!",BoardGeometry.Tints[0]);
            notice.GetComponent<CanvasGroup>().alpha=1;
            var cameraGo=new GameObject("PreviewCamera",typeof(Camera));UnityEngine.SceneManagement.SceneManager.MoveGameObjectToScene(cameraGo,scene);
            camera=cameraGo.GetComponent<Camera>(); camera.clearFlags=CameraClearFlags.SolidColor; camera.backgroundColor=new Color32(241,236,249,255);
            camera.scene=scene; camera.transform.position=new Vector3(0,0,-10);
            rt=new RenderTexture(1920,1080,24);camera.targetTexture=rt;
            var canvas=root.GetComponent<Canvas>();canvas.renderMode=RenderMode.ScreenSpaceCamera;canvas.worldCamera=camera;canvas.planeDistance=10;
            Canvas.ForceUpdateCanvases(); camera.Render();RenderTexture.active=rt;
            texture=new Texture2D(1920,1080,TextureFormat.RGB24,false);texture.ReadPixels(new Rect(0,0,1920,1080),0,0);texture.Apply();
            const string path="Documentation/move-presentation-preview.png";File.WriteAllBytes(path,texture.EncodeToPNG());return path;
        }
        finally {RenderTexture.active=previous;if(camera!=null)camera.targetTexture=null;if(rt!=null){rt.Release();UnityEngine.Object.DestroyImmediate(rt);}if(texture!=null)UnityEngine.Object.DestroyImmediate(texture);EditorSceneManager.ClosePreviewScene(scene);}
    }
}
