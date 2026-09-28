import subprocess
for f in ["_rebuild11_and_transform.py","_transform_v3.py","_s11.py","_s11b.py"]:
    out=subprocess.run(["git","show","7e1375db:场景设计/ah_cti_poc/工作流配置/智能体工作流集V1.6/"+f],capture_output=True).stdout
    t=out.decode("utf-8-sig")
    hits=[ (n,l[:120]) for n,l in enumerate(t.split("\n")) if ("position" in l or "layout" in l or "MAIN_Y" in l or "ROW" in l) ]
    print("=== ",f, "len",len(t), "hits",len(hits))
    for n,l in hits[:20]:
        print(n,l)
