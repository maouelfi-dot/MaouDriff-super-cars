import sys, numpy as np
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.collections import PolyCollection
def load(p):
    v=[];f=[];mats=[];cur=None
    for l in open(p):
        if l.startswith("v "): v.append([float(x) for x in l.split()[1:4]])
        elif l.startswith("usemtl"): cur=l.split()[1]
        elif l.startswith("f "): f.append([int(t.split("/")[0])-1 for t in l.split()[1:4]]); mats.append(cur)
    return np.array(v),np.array(f),mats
v,f,m=load(sys.argv[1])
fig,ax=plt.subplots(1,3,figsize=(18,5))
col=[]
for n in m:
    col.append("#cc3377" if n=="paint" else "#88aacc" if n=="glass" else "#ffee66" if n.startswith("light") else "#222222" if n.startswith(("rubber","plastic","piano")) else "#bbbbbb")
for a,(i,j,t) in zip(ax,[(2,1,"samping (z=depan ->, y=atas)"),(0,2,"atas (x, z)"),(0,1,"depan (x,y)")]):
    tri=v[f][:,:,[i,j]]
    depth=v[f][:,:,3-i-j].mean(1)
    o=np.argsort(depth)
    a.add_collection(PolyCollection(tri[o],facecolors=np.array(col)[o],edgecolors="none"))
    a.set_xlim(v[:,i].min()-.2,v[:,i].max()+.2); a.set_ylim(v[:,j].min()-.2,v[:,j].max()+.2); a.set_aspect("equal"); a.set_title(t)
plt.savefig(sys.argv[2],dpi=60,bbox_inches="tight")
