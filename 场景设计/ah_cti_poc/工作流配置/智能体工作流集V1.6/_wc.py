import io
t=open("wf_common.py",encoding="utf-8").read()
print("LEN",len(t))
i=t.find("def workflow")
j=t.find("\ndef ",i+10)
print(t[i:i+1500])
