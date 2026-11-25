#!/usr/bin/env python3
"""LIVE checks (need real internet; NOT simulation). Writes field_report.json.
  field_check.py logos <urls.txt>        # each URL must return image/* with >0 bytes, not cache
  field_check.py sources                 # each logo data source used by the app (GitHub trees for FootballLogos, ESPN standings) reachable?
  field_check.py streams <list.m3u>      # parses full list, counts groups/items, probes N sampled streams
Never prints URLs with credentials; redacts user/pass query values."""
import sys, json, re, urllib.request, random, time, collections
def redact(u): return re.sub(r'(username|password|user|pass|token)=[^&]+', r'\1=***', u)
def get(u, n=1, t=15):
    r = urllib.request.Request(u, headers={'User-Agent':'AboUobidahTV-FieldCheck'})
    with urllib.request.urlopen(r, timeout=t) as f: return f.status, f.headers.get('Content-Type',''), f.read(n)
rep = {'mode': sys.argv[1], 'when': time.strftime('%F %T')}
if sys.argv[1]=='logos':
    res=[]
    for u in open(sys.argv[2]).read().split():
        try: s,c,b = get(u, 1<<20); ok = s==200 and c.startswith('image/') and len(b)>200
        except Exception as e: ok,s,c=False,str(e)[:60],''
        res.append({'url':redact(u),'ok':ok,'status':s,'type':c})
    rep['results']=res; rep['pass']=sum(r['ok'] for r in res); rep['total']=len(res)
elif sys.argv[1]=='sources':
    # مصادر البيانات الموجودة في المشروع (FootballLogos.REPOS + ESPN). كل مصدر يُسجَّل منفصلًا: ok أو سبب الفشل.
    # فشل الاتصال هنا يعني فشل هذا الاختبار من هذا الجهاز، ولا يثبت أن المصدر نفسه متوقف.
    srcs=['https://api.github.com/repos/Leo4815162342/football-logos/git/trees/HEAD?recursive=1',
          'https://api.github.com/repos/JoseArroyave/football-logos/git/trees/HEAD?recursive=1',
          'https://api.github.com/repos/sportlogos/football.db.logos/git/trees/HEAD?recursive=1',
          'https://site.api.espn.com/apis/v2/sports/soccer/eng.1/standings']
    res=[]
    for u in srcs:
        try: s,c,b=get(u,4096); ok=(s==200)
        except Exception as e: ok,s,c=False,str(e)[:80],''
        res.append({'url':redact(u),'ok':ok,'status':s,'type':c})
    rep['results']=res; rep['pass']=sum(r['ok'] for r in res); rep['total']=len(res)
elif sys.argv[1]=='streams':
    txt=open(sys.argv[2],encoding='utf-8',errors='replace').read(); items=[];grp='';pend=False
    for l in txt.splitlines():
        if l.startswith('#EXTINF'): m=re.search(r'group-title="([^"]*)"',l); grp=m.group(1) if m else ''; pend=True
        elif l.strip() and not l.startswith('#') and pend: items.append((grp,l.strip())); pend=False
    rep['items']=len(items); rep['groups']=dict(collections.Counter(g for g,_ in items))
    sample=random.sample(items,min(int(sys.argv[3]) if len(sys.argv)>3 else 30,len(items))); res=[]
    for g,u in sample:
        try: s,c,b=get(u,512); ok = s==200 and (b'#EXTM3U' in b or len(b)>0)
        except Exception as e: ok,s=False,str(e)[:60]
        res.append({'group':g,'url':redact(u),'ok':ok,'status':s})
    rep['probe']=res; rep['probe_ok']=sum(r['ok'] for r in res)
json.dump(rep,open('field_report.json','w'),ensure_ascii=False,indent=1); print(json.dumps({k:v for k,v in rep.items() if k not in('results','probe')},ensure_ascii=False))
