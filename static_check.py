#!/usr/bin/env python3
"""فحص ثابت سريع (ليس بديلًا عن المترجم): arity لـ Color.* وUi.* وأعضاء Prefs والكائنات المساعدة."""
import re,glob,os,sys
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)),'app/src/main/java/com/uobidah/launcher'))
def split_args(s):
    d=0;cur='';out=[];q=False
    for i,ch in enumerate(s):
        if ch=='"' and (i==0 or s[i-1]!='\\'): q=not q
        if not q:
            if ch in '([{': d+=1
            if ch in ')]}': d-=1
            if ch==',' and d==0: out.append(cur);cur='';continue
        cur+=ch
    out.append(cur);return [a for a in out if a.strip()]
def mp(t,i):
    d=1;j=i;q=False
    while d and j<len(t):
        c=t[j]
        if c=='"' and t[j-1]!='\\': q=not q
        if not q:
            if c=='(':d+=1
            elif c==')':d-=1
        j+=1
    return j
files=glob.glob('**/*.kt',recursive=True)
src={f:open(f,encoding='utf-8').read() for f in files}
bad=0
def line(t,p): return t[:p].count('\n')+1
for f,t in src.items():
    for fn,ok in (('Color.argb(',{4}),('Color.rgb(',{3}),('Ui.withAlpha(',{2}),('Color.parseColor(',{1})):
        for m in re.finditer(re.escape(fn),t):
            j=mp(t,m.end()); n=len(split_args(t[m.end():j-1]))
            if n not in ok: print('ARITY',f,line(t,m.start()),fn,n); bad+=1
ui=src['Ui.kt']; sigs={}
for m in re.finditer(r'\n\s*fun (\w+)\(',ui):
    j=mp(ui,m.end()); ps=split_args(ui[m.end():j-1])
    vararg=any('vararg' in p for p in ps)
    sigs.setdefault(m.group(1),[]).append((sum(1 for p in ps if '=' not in p and 'vararg' not in p),999 if vararg else len(ps)))
for f,t in src.items():
    for m in re.finditer(r'\bUi\.(\w+)\(',t):
        nm=m.group(1)
        if nm not in sigs: print('UNKNOWN Ui.',nm,f,line(t,m.start())); bad+=1; continue
        j=mp(t,m.end()); args=split_args(t[m.end():j-1])
        n=len(args)+(1 if t[j:j+3].lstrip(' ').startswith('{') else 0)
        if any(re.match(r'\s*\w+\s*=[^=]',a) for a in args): continue
        if not any(r<=n<=tot for r,tot in sigs[nm]): print('UIARITY',f,line(t,m.start()),nm,n); bad+=1
members=set(re.findall(r'\b(?:var|val|fun)\s+(\w+)',src['Prefs.kt']))
for f,t in src.items():
    for m in re.finditer(r'\bprefs\.(\w+)',t):
        if m.group(1) not in members and m.group(1)!='sp': print('PREFS',f,line(t,m.start()),m.group(1)); bad+=1
bodies={}
for f,t in src.items():
    for m in re.finditer(r'\n(?:private |internal )?object\s+(\w+)[^{]*\{',t):
        d=1;j=m.end()
        while d and j<len(t):
            d+= (t[j]=='{')-(t[j]=='}'); j+=1
        bodies[m.group(1)]=t[m.end():j]
for name,body in bodies.items():
    mem=set(re.findall(r'\b(?:fun|val|var|const val)\s+(?:<[^>]+>\s*)?(?:\w+\.)?(\w+)',body))|set(re.findall(r'\bclass\s+(\w+)',body))
    for f,t in src.items():
        for m in re.finditer(r'\b%s\.(\w+)'%name,t):
            if m.group(1) not in mem: print('MEMBER',name,m.group(1),f,line(t,m.start())); bad+=1
print('static_check: %d problems'%bad); sys.exit(1 if bad else 0)
