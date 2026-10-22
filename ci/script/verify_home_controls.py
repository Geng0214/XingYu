"""Check home controls against the real map bounds in a captured Android UI hierarchy."""
import argparse, json, pathlib, re, xml.etree.ElementTree as ET
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("hierarchy",type=pathlib.Path)
parser.add_argument("--report",required=True,type=pathlib.Path)
args=parser.parse_args()
nodes=list(ET.parse(args.hierarchy).iter("node"))
maps=[n for n in nodes if n.get("content-desc","").startswith("共享家园地图。")]
assert len(maps)==1,"Capture the playable home map first"
def bounds(n):return tuple(map(int,re.findall(r"\d+",n.get("bounds"))))
x1,y1,x2,y2=bounds(maps[0])
controls=[];outside=[]
for n in nodes:
    if n.get("clickable")!="true":continue
    text=n.get("text","");label=n.get("content-desc","")
    a,b,c,d=bounds(n)
    # Application navigation is separate from game interactions and remains available.
    nav=text in {"聊天","家园","日程","设置"} or any(child.get("text") in {"聊天","家园","日程","设置"} for child in n.iter("node") if child is not n)
    if nav or label=="返回":continue
    entry={"label":label or text,"bounds":[a,b,c,d],"enabled":n.get("enabled")=="true"}
    controls.append(entry)
    if not(x1<=a<=c<=x2 and y1<=b<=d<=y2):outside.append(entry)
report={"mapBounds":[x1,y1,x2,y2],"controls":controls,"outside":outside,"status":"PASS" if not outside else "FAIL"}
args.report.parent.mkdir(parents=True,exist_ok=True)
args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding="utf-8")
assert not outside,outside
print(f"PASS: {len(controls)} game controls inside map")
