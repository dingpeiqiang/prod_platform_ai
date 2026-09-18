import glob, re, io

d = r"D:\工作\sitech\项目\研发\git_workspace\AI\prod_platform_ai\场景设计\ah_cti_poc\工作流配置\智能体工作流集V1.6"
for f in glob.glob(d + r"\gen_workflows*.py"):
    txt = io.open(f, encoding="utf-8").read()
    urls = sorted(set(re.findall(r"/api/v1/appstore[^\"'\s]*", txt)))
    print("=" * 20, f.split("\\")[-1])
    for u in urls:
        print("  ", u)
