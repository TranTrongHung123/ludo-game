// Edit Mode: run_script AgentScripts/VerifyIntegrationFixes.cs, entry VerifyIntegrationFixes.Main.
// Uses temporary preview scenes; does not save or switch the user's open scenes.
using System;
using System.Reflection;
using Ludo.Controllers;
using Ludo.Services;
using Newtonsoft.Json.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

public static class VerifyIntegrationFixes
{
    static int checks;
    const BindingFlags Private = BindingFlags.Instance | BindingFlags.NonPublic;
    static void Check(bool value, string name) { if (!value) throw new Exception(name); checks++; }
    static object Call(object target, string name, params object[] args) => target.GetType().GetMethod(name, Private).Invoke(target, args);
    static void Set(object target, string name, object value) => target.GetType().GetField(name, Private).SetValue(target, value);
    static JObject Room(string presence, string state = "PLAYING") => new JObject {
        ["roomId"]="room", ["state"]=state, ["players"]=new JArray(new JObject { ["playerId"]="1", ["presenceState"]=presence }) };
    static JObject Game(long version = 10, string match = "match") => new JObject {
        ["roomId"]="room", ["matchId"]=match, ["roomState"]="PLAYING", ["stateVersion"]=version,
        ["participants"]=new JArray(new JObject { ["playerId"]="1", ["presenceState"]="PLAYING" }) };
    static void Event(NetworkSession session, string type, JObject data) => Call(session, "ApplyLobbyEvent", new JObject { ["type"]=type, ["data"]=data });

    public static string Main()
    {
        if (EditorApplication.isPlaying) throw new Exception("Run this check outside Play Mode.");
        checks=0;
        var scene=EditorSceneManager.NewPreviewScene();
        try
        {
            var go=new GameObject("IntegrationRegression"); go.SetActive(false);
            UnityEngine.SceneManagement.SceneManager.MoveGameObjectToScene(go,scene);
            var session=go.AddComponent<NetworkSession>();
            Set(session,"<SessionId>k__BackingField","test-session");
            Set(session,"<Profile>k__BackingField",JObject.Parse("{\"playerId\":\"1\",\"username\":\"self\",\"totalScore\":0,\"firstPlaceCount\":0}"));
            Event(session,"ONLINE_PLAYERS_UPDATED",new JObject { ["players"]=new JArray(
                new JObject { ["playerId"]="other", ["totalScore"]=99 },
                new JObject { ["playerId"]="1", ["displayName"]="Tên mới", ["totalScore"]=4.5m, ["firstPlaceCount"]=2 }) });
            Check((decimal)session.Profile["totalScore"]==4.5m,"Authoritative profile score");
            Check((int)session.Profile["firstPlaceCount"]==2,"Authoritative wins");
            Check((string)session.Profile["username"]=="self","Profile-only fields retained");

            Call(session,"ApplyRoom",Room("PLAYING"));
            Check((bool)Call(session,"ApplyGameState",Game()),"Initial game accepted");
            Event(session,"ROOM_UPDATED",new JObject { ["room"]=Room("DISCONNECTED") });
            Check((string)session.GameState["participants"][0]["presenceState"]=="DISCONNECTED","Presence changes without gameplay version change");
            Check((long)session.GameState["stateVersion"]==10,"Presence does not invent game version");
            Check(!(bool)Call(session,"ApplyGameState",Game(9)),"Old gameplay rejected");
            Check(!(bool)Call(session,"ApplyGameState",Game(10)),"Duplicate gameplay rejected");
            Check((bool)Call(session,"ApplyGameState",Game(11)),"New gameplay accepted");
            Check((string)session.GameState["participants"][0]["presenceState"]=="DISCONNECTED","Game response cannot regress room presence");
            Event(session,"ROOM_UPDATED",new JObject { ["room"]=Room("PLAYING") });
            Check((string)session.GameState["participants"][0]["presenceState"]=="PLAYING","Reconnect presence restored");

            var finished=Game(12); finished["roomState"]="FINISHED";
            var result=new JObject { ["roomId"]="room", ["matchId"]="match", ["standings"]=new JArray() };
            Call(session,"RestoreSnapshot",new JObject { ["room"]=Room("PLAYING","FINISHED"), ["gameState"]=finished, ["gameOver"]=result });
            Check(session.GameOver!=null,"Missed GAME_OVER recovered from reconnect snapshot");
            Check((string)session.GameOver["matchId"]=="match","Recovered result match");
            Call(session,"RestoreSnapshot",new JObject { ["room"]=Room("PLAYING","FINISHED"), ["gameState"]=finished });
            Check(session.GameOver!=null,"Legacy snapshot retains known same-match result");
            Call(session,"RestoreSnapshot",new JObject { ["room"]=Room("PLAYING"), ["gameState"]=Game(0,"next"), ["gameOver"]=result });
            Check(session.GameOver==null,"Wrong-match reconnect result rejected");
            Event(session,"ROOM_UPDATED",new JObject { ["room"]=Room("IN_ROOM","WAITING") });
            Check(session.GameState==null && session.GameOver==null,"Rematch room clears finished game and result");
            Check(!(bool)Call(session,"ApplyGameState",Game(99,"next")),"Late retired-match response cannot resurrect game");
            Check((bool)Call(session,"ApplyGameState",Game(0,"new-match")),"New match accepts reset version");

            Check(!GameController.EscapeChatText("</b><size=80>Tên</size>").Contains("<size"),"Chat display name escaped");
            Check(GameController.EscapeChatText("Xin chào") == "Xin chào","Unicode text preserved");

            var prefab=AssetDatabase.LoadAssetAtPath<GameObject>("Assets/Prefabs/Result/ResultScreen.prefab");
            var controller=prefab.GetComponentInChildren<ResultController>(true);
            var serialized=new SerializedObject(controller);
            var replay=(UnityEngine.UI.Button)serialized.FindProperty("rematchButton").objectReferenceValue;
            var lobby=(UnityEngine.UI.Button)serialized.FindProperty("lobbyButton").objectReferenceValue;
            Check(replay!=null && serialized.FindProperty("rematchLabel").objectReferenceValue!=null,"Rematch serialized references");
            Check(replay!=lobby && replay.targetGraphic.raycastTarget,"Separate interactive rematch button");
            var a=(RectTransform)replay.transform; var b=(RectTransform)lobby.transform;
            Check(a.sizeDelta.x>0 && a.anchoredPosition.x+a.sizeDelta.x<=b.anchoredPosition.x,"Result buttons do not overlap");
            return checks+" integration regression checks passed.";
        }
        finally { EditorSceneManager.ClosePreviewScene(scene); }
    }
}
