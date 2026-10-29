"""Offline candidate analysis only; does not change live behavior.
No runtime prediction, and rewards are used only to validate results, never as input constraints.
"""
import argparse, gzip, json, pathlib
from entity_research import report

parser=argparse.ArgumentParser(description="Offline floor-ID hypothesis audit; supply complete saved runs, not overlapping launch copies.")
parser.add_argument("logs",nargs="+",type=pathlib.Path)
parser.add_argument("--output",type=pathlib.Path)
args=parser.parse_args()
runs=[]
for path in args.logs:
 summary=report([path])
 if summary["gaps"]:raise ValueError(f"Incomplete research journal: {path}")
 floors={};events=[];scopes=set()
 with gzip.open(path,"rt",encoding="utf-8") as stream:
  for line in stream:
   if not line.strip():continue
   record=json.loads(line)
   if record.get("k")!="er":continue
   for event in record["events"]:
    data=event["data"];events.append(event)
    if event["action"]=="floor-components":
     scopes.add((record.get("c"),record.get("v"),record.get("world")))
     floors.setdefault(str(min(data["networkIds"])),{"ids":data["networkIds"],"xyz":[data["x"],data["y"],data["z"]],"first":event["time"]})
 if len(scopes)!=1:raise ValueError("Supply one complete saved run per file")
 rewards=[x for x in summary["floorRewards"] if x["candidateNetworkIds"]]
 # Ground truth is used only below to grade predictions, never to form candidates.
 actual=min(min(x["candidateNetworkIds"]) for x in rewards if x["category"]=="Item")
 runs.append({"file":path.name,"floors":floors,"events":events,"rewards":rewards,"base":actual})
nodes=[]
for line in (pathlib.Path(__file__).resolve().parents[1]/'src/main/resources/assets/safariutils/safari_areas.txt').read_text().splitlines():
 if not line or line.startswith('#'):continue
 a,*points=line.split()
 nodes.extend((int(a),*map(int,p.split(','))) for p in points)
def biome(xyz):
 x,y,z=xyz
 a,*_=min(nodes,key=lambda p:(p[1]-x)**2+((p[2]-y)*.3)**2+(p[3]-z)**2)
 return a
def category(delta,c,i):
 if delta<0:return 'INVALID'
 if delta>=3*(20+c+i):return 'Loot'
 if delta%3:return 'INVALID'
 n=delta//3
 if n<6:return ['Lime Gem','Purple Gem','Orange Gem'][n%3]
 if n<15:return ['Wriggleworm','Yogi Berry','Bag of Seeds'][(n-6)//3]
 if n<20:return 'Soothing Incense'
 if n<20+c:return 'Shining Coin'
 return 'Icebreaker'
def expectedBiome(cat):
 if cat.endswith('Gem'):return 2
 if cat in ['Wriggleworm','Yogi Berry','Bag of Seeds']:return 1
 if cat in ['Soothing Incense','Shining Coin']:return 4
 if cat=='Icebreaker':return 3
 return None
output=[]
for r in runs:
 floors={int(k):(v,biome(v['xyz'])) for k,v in r['floors'].items()}
 timeline=[]
 for k,(v,b) in floors.items():timeline.append((v['first'],'floor',k))
 for e in r['events']:
  d=e['data']
  if isinstance(d,dict) and 'Fluffling' in d.get('name','') and d.get('type')=='minecraft:armor_stand':timeline.append((e['time'],'fluff',d['networkId']))
 observed={};labels=set();candidates=[];firstBase=None;firstBoundary=None;onlineWrong=set();onlinePredictions=set()
 truthById={min(x["candidateNetworkIds"]):(x["message"][22:-15] if x["category"]=="Item" else "Loot") for x in r["rewards"]}
 for t,kind,k in sorted(timeline):
  if kind=='floor':observed[k]=floors[k]
  else:labels.add(k)
  if not observed:continue
  initial=[l for l in labels if l<min(observed)]
  # Self-identifying biome sequence, using observations only, not reward labels.
  signature=[2]*6+[1]*9+[4]*5
  bases={b for b in observed if b==min(observed) and all(b+3*n in observed and observed[b+3*n][1]==area for n,area in enumerate(signature))}
  # Intentionally do not use Fluffling identities or reward evidence.
  candidates=[]
  for b in bases:
   for coins in range(3,7):
    for ice in [4,5]:
     if all((cat:=category(fid-b,coins,ice))!='INVALID' and (expectedBiome(cat) is None or expectedBiome(cat)==area) for fid,(v,area) in observed.items()):candidates.append((b,coins,ice))
  if len({c[0] for c in candidates})==1 and firstBase is None:firstBase={'secondsAfterFirstFloor':round((t-min(v['first'] for v in r['floors'].values()))/1000,2),'base':candidates[0][0],'visibleFloors':len(observed),'coinCandidates':sorted({c[1] for c in candidates}),'iceCandidates':sorted({c[2] for c in candidates})}
  if len({(c[0],c[1]) for c in candidates})==1 and firstBoundary is None:firstBoundary=round((t-min(v['first'] for v in r['floors'].values()))/1000,3)
  for fid in observed:
   possible={category(fid-b,c,i) for b,c,i in candidates}
   if len(possible)==1:
    prediction=next(iter(possible));onlinePredictions.add(fid)
    if fid in truthById and prediction!=truthById[fid]:onlineWrong.add((fid,prediction,truthById[fid]))
 consensus={};ambiguous=[];wrong=[]
 for fid in floors:
  cats={category(fid-b,c,i) for b,c,i in candidates}
  if len(cats)==1:consensus[fid]=cats.pop()
  else:ambiguous.append({'idOffset':fid-r['base'],'possibilities':sorted(cats)})
 for reward in r['rewards']:
  fid=min(reward['candidateNetworkIds']);truth=reward['message'][22:-15] if reward['category']=='Item' else 'Loot'
  if fid in consensus and consensus[fid]!=truth:wrong.append((fid,consensus[fid],truth))
 row={'file':r['file'],'firstUniqueBase':firstBase,'actualBase':r['base'],'finalCandidates':candidates,'consensusDrops':len(consensus),'observedDrops':len(floors),'ambiguous':ambiguous,'wrong':wrong,'firstCoinBoundarySeconds':firstBoundary,'onlineWrong':sorted(onlineWrong),'onlinePredictedDrops':len(onlinePredictions)}
 output.append(row);print(json.dumps(row))
if args.output:args.output.write_text(json.dumps(output,indent=2),encoding='utf-8')
