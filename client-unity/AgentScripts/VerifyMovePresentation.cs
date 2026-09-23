using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;
using System.Threading.Tasks;
using Ludo.Controllers;
using Ludo.Views;
using Newtonsoft.Json.Linq;
using TMPro;
using UnityEditor;
using UnityEngine;
using UnityEngine.SceneManagement;

public static class VerifyMovePresentation
{
    const BindingFlags Private = BindingFlags.Instance | BindingFlags.NonPublic;
    static int checks;
    static void Check(bool ok,string name) { if(!ok)throw new Exception(name);checks++; }
    static JObject State(string match,int step,long version)
    {
        var members=new JArray();
        for(int slot=0;slot<4;slot++)
        {
            var pieces=new JArray();
            for(int i=0;i<4;i++) {int s=slot==0&&i==0?step:-1;pieces.Add(new JObject{["pieceId"]="p"+slot+"-"+i,["ownerPlayerId"]="p"+slot,["color"]=BoardGeometry.Colors[slot],["state"]=s<0?"IN_YARD":s==53?"FINISHED":s>=48?"IN_FINISH_TRACK":"ON_TRACK",["stepCount"]=s});}
            members.Add(new JObject{["playerId"]="p"+slot,["color"]=BoardGeometry.Colors[slot],["matchStatus"]="ACTIVE",["pieces"]=pieces});
        }
        return new JObject{["roomId"]="test",["matchId"]=match,["stateVersion"]=version,["participants"]=members,["specialCells"]=new JArray(),["validPieceIds"]=new JArray()};
    }
    static JObject Move(int from,int landed,int to,string effect) => new JObject{["pieceId"]="p0-0",["fromStep"]=from,["landedStep"]=landed,["toStep"]=to,["triggeredEffect"]=effect};
    public static async Task<string> Main()
    {
        if(!EditorApplication.isPlaying)throw new Exception("Run in Play Mode.");
        checks=0; var scene=SceneManager.CreateScene("MovePresentationTest-"+Guid.NewGuid());
        GameObject root=null; float timeScale=Time.timeScale;
        try
        {
            root=UnityEngine.Object.Instantiate(AssetDatabase.LoadAssetAtPath<GameObject>("Assets/Prefabs/Game/GameScreen.prefab"));
            root.GetComponent<GameController>().enabled=false;
            SceneManager.MoveGameObjectToScene(root,scene);
            var board=root.GetComponentInChildren<BoardView>(true);
            var notice=root.GetComponentInChildren<BoardNotice>(true);
            var label=notice.GetComponentInChildren<TMP_Text>(true);
            var group=notice.GetComponent<CanvasGroup>();
            Time.timeScale=0; // Animations and auto-dismiss must not depend on gameplay timeScale.
            foreach(var c in new[]{(0,2,4,"SPEED"),(0,4,2,"SLOW"),(2,4,2,"SLOW"),(0,2,2,"SPEED"),(5,8,-1,"TRAP"),(3,6,6,"LUCKY"),(52,53,53,(string)null)})
            {
                string match=Guid.NewGuid().ToString();
                board.ResetPresentation();board.Bind(State(match,c.Item1,100),"p1",null,false,_=>{},false);
                var views=(Dictionary<string,PieceView>)typeof(BoardView).GetField("pieces",Private).GetValue(board);
                var horse=views["p0-0"];
                var next=State(match,c.Item3,101);next["lastMove"]=Move(c.Item1,c.Item2,c.Item3,c.Item4);
                board.Bind(next,"p1",null,false,_=>{},true);
                Check(horse.IsAnimating,"Server move animates for another player");
                var motion=typeof(PieceView).GetField("motion",Private).GetValue(horse);
                board.Bind(next,"p1",null,false,_=>{},false);
                Check(ReferenceEquals(motion,typeof(PieceView).GetField("motion",Private).GetValue(horse)),"Repeated UI refresh preserves animation");
                bool sawLanding=false;float start=Time.realtimeSinceStartup;
                while(horse.IsAnimating&&Time.realtimeSinceStartup-start<5)
                {
                    if(Vector2.Distance(((RectTransform)horse.transform).anchoredPosition,BoardGeometry.PathPosition(0,c.Item2,0))<1)sawLanding=true;
                    await Task.Delay(15);
                }
                Check(!horse.IsAnimating,"Animation completes");Check(sawLanding,"Pauses at authoritative intermediate landing");
                Check(Vector2.Distance(((RectTransform)horse.transform).anchoredPosition,BoardGeometry.Position(0,c.Item3,0))<.01f,"Exact authoritative destination");
                Check(notice.Visible,"Effect/finish notification appears");
                Check(!group.blocksRaycasts&&!group.interactable,"Notice never blocks input");
                if(c.Item4=="SLOW")Check(label.text.Contains("Lùi lại"),"Immediate retreat message");
                if(c.Item4=="SPEED"&&c.Item2==c.Item3)Check(label.text.Contains("bị chặn"),"Blocked effect explained");
                if(c.Item3==53)
                {
                    Check(horse.transform.Find("FinishBadge").gameObject.activeSelf,"Finished horse wears crown");
                    Check(horse.transform.localScale.x<1,"Finished horse fits podium");
                    Check(root.GetComponentsInChildren<TMP_Text>(true).Any(t=>t.text=="VỀ ĐÍCH  1/4"),"Podium count from snapshot");
                }
                notice.Clear();board.Bind(next,"p1",null,false,_=>{},true);
                Check(!horse.IsAnimating&&!notice.Visible,"Duplicate state cannot replay effects");
            }
            notice.Show("Tự tắt",Color.magenta);await Task.Delay(3000);
            Check(!notice.Visible&&group.alpha==0,"Notice fades and dismisses without confirmation at timeScale zero");
            var reconnect=State("reconnect",0,1);board.Bind(reconnect,"p1",null,false,_=>{},false);
            var moved=State("reconnect",4,2);moved["lastMove"]=Move(0,2,4,"SPEED");board.Bind(moved,"p1",null,false,_=>{},true);
            Check(board.IsAnimating,"Reconnect test begins in motion");
            board.ResetPresentation();board.Bind(moved,"p1",null,false,_=>{},false);
            Check(!board.IsAnimating&&!notice.Visible,"Reconnect snaps and suppresses historical notices");
            var skipped=State("reconnect",6,5);skipped["lastMove"]=Move(4,6,6,"LUCKY");board.Bind(skipped,"p1",null,false,_=>{},true);
            Check(!board.IsAnimating&&!notice.Visible,"Skipped versions do not invent a path");
            return checks+" movement/finish/notice Play Mode checks passed";
        }
        finally {Time.timeScale=timeScale;if(root!=null)UnityEngine.Object.Destroy(root);await Task.Yield();var unload=SceneManager.UnloadSceneAsync(scene);while(unload!=null&&!unload.isDone)await Task.Yield();}
    }
}
