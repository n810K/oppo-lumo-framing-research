import urllib.request,struct,json,pathlib
w=pathlib.Path('work/compatible-engine');u='https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct/resolve/main/model.safetensors'
def get(a,b):
 r=urllib.request.urlopen(urllib.request.Request(u,headers={'Range':f'bytes={a}-{b}'}),timeout=30)
 if r.status!=206:raise RuntimeError('Range unavailable')
 data=r.read(b-a+1);assert len(data)==b-a+1;return data
b=get(0,7);n=struct.unpack('<Q',b)[0];assert n<1000000
h=json.loads(get(8,8+n-1));(w/'qwen-safetensors-header.json').write_text(json.dumps(h,indent=2));e=h['model.embed_tokens.weight'];print(e,flush=True)
start=8+n+e['data_offsets'][0];count=896*4;base=get(start,start+count*2-1)
local=pathlib.Path('work/firmware-donor/extracted/odm/ai_composition/embedding_fp16.bin').open('rb').read(count*2)
assert e['dtype']=='BF16'
bv=[struct.unpack('<f',struct.pack('<I',v<<16))[0] for v in struct.unpack('<'+str(count)+'H',base)]
lv=struct.unpack('<'+str(count)+'e',local)
r={'candidate':'Qwen/Qwen2.5-0.5B-Instruct','tensor':e,'compared_first_rows':4,'elements':count,'exact_value_matches':sum(a==b for a,b in zip(bv,lv)),'mean_absolute_difference':sum(abs(a-b) for a,b in zip(bv,lv))/count}
(w/'embedding-comparison.json').write_text(json.dumps(r,indent=2));print(json.dumps(r,indent=2))
