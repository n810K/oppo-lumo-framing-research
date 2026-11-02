import socket, struct, time, collections
s=socket.create_connection(('127.0.0.1',8700)); s.settimeout(3)
s.sendall(b'JDWP-Handshake')
def read(n):
    b=b''
    while len(b)<n:
        x=s.recv(n-len(b))
        if not x: raise EOFError()
        b+=x
    return b
assert read(14)==b'JDWP-Handshake'
seq=0; events=collections.deque()
def packet():
    h=read(11); length,ident,flags=struct.unpack('>IIB',h[:9]); body=read(length-11)
    if not flags&128:
        if h[9:]==bytes([64,100]): events.append(body)
        else: print('VM command',list(h[9:]),flush=True)
    return ident,flags,h[9:],body
def cmd(cs,c,p=b''):
    global seq
    seq+=1; ident=seq; s.sendall(struct.pack('>IIBBB',11+len(p),ident,0,cs,c)+p)
    while True:
        i,f,e,b=packet()
        if f&128 and i==ident:
            if e!=b'\0\0': raise RuntimeError((cs,c,int.from_bytes(e,'big')))
            return b
def u32(n): return struct.pack('>I',n)
def u64(n): return struct.pack('>Q',n)
def string(b,off=0):
    n=int.from_bytes(b[off:off+4],'big');return b[off+4:off+4+n].decode(errors='replace'),off+4+n
print('version',cmd(1,1).hex(),flush=True)
sizes=struct.unpack('>IIIII',cmd(1,7));print('sizes',sizes,flush=True)
assert sizes==(8,8,8,8,8),sizes
cache={}
def typename(cid):
    if cid not in cache: cache[cid]=string(cmd(2,1,u64(cid)))[0]
    return cache[cid]
methods={}
field_cache={}
def fields(obj):
    cid=int.from_bytes(cmd(9,1,u64(obj))[1:9],'big')
    if cid not in field_cache:
        b=cmd(2,4,u64(cid));n=int.from_bytes(b[:4],'big');p=4;items=[]
        for _ in range(n):
            fid=int.from_bytes(b[p:p+8],'big');p+=8
            name,p=string(b,p);sig,p=string(b,p);flags=int.from_bytes(b[p:p+4],'big');p+=4
            if not flags&8:items.append((fid,name,sig))
        field_cache[cid]=items
    items=field_cache[cid]
    b=cmd(9,2,u64(obj)+u32(len(items))+b''.join(u64(x[0]) for x in items));p=4;result={}
    for fid,name,sig in items:
        tag=chr(b[p]);p+=1;n={'B':1,'Z':1,'C':2,'S':2,'I':4,'F':4,'J':8,'D':8}.get(tag,8)
        value=int.from_bytes(b[p:p+n],'big');p+=n
        if tag=='s' and value:value=string(cmd(10,1,u64(value)))[0]
        result[name]=(sig,value)
    return result
def dump_surfaces(obj):
    f=fields(obj)
    val=f.get('mSessionSurfaceWrapperList')
    if not val or not val[1]:return
    ll=fields(val[1]);node=ll.get('first',('',0))[1];seen=0
    while node and seen<24:
        nf=fields(node);item=nf['item'][1]
        sf=fields(item)
        for k,(sig,v) in list(sf.items()):
            if sig=='Landroid/util/Size;' and v:sf[k]=fields(v)
        print('SURFACE',sf,flush=True)
        node=nf['next'][1];seen+=1
message_field=None
def exception_message(obj):
    global message_field
    if message_field is None:
        sig=b'Ljava/lang/Throwable;'
        found=cmd(1,2,u32(len(sig))+sig)
        cls=int.from_bytes(found[5:13],'big')
        b=cmd(2,4,u64(cls)); count=int.from_bytes(b[:4],'big');p=4
        for _ in range(count):
            fid=int.from_bytes(b[p:p+8],'big');p+=8
            name,p=string(b,p);_,p=string(b,p);p+=4
            if name=='detailMessage': message_field=fid
    b=cmd(9,2,u64(obj)+u32(1)+u64(message_field))
    sid=int.from_bytes(b[5:13],'big')
    return string(cmd(10,1,u64(sid)))[0] if sid else None
def methodname(cid,mid):
    if cid not in methods:
        b=cmd(2,5,u64(cid)); count=int.from_bytes(b[:4],'big');off=4;d={}
        for _ in range(count):
            m=int.from_bytes(b[off:off+8],'big');off+=8
            name,off=string(b,off);sig,off=string(b,off);off+=4;d[m]=name+sig
        methods[cid]=d
    return methods[cid].get(mid,str(mid))
# Catch exceptions only in the camera SDK, retaining normal Android enforcement.
pattern=b'com.oplus.ocs.camera.*'
request=bytes([4,2])+u32(1)+bytes([8])+u64(0)+bytes([1,1])
print('request',cmd(15,1,request).hex(),flush=True)
cmd(1,9)
end=time.time()+20
while time.time()<end:
    if not events:
        try: packet()
        except socket.timeout: continue
    if not events: continue
    b=events.popleft(); policy=b[0]; count=int.from_bytes(b[1:5],'big');off=5
    for _ in range(count):
        kind=b[off];off+=5
        if kind==90: off+=8;continue
        if kind!=4: print('event',kind,b.hex(),flush=True);break
        thread=int.from_bytes(b[off:off+8],'big');off+=8
        location=b[off:off+25];off+=25
        exc=int.from_bytes(b[off+1:off+9],'big');off+=9
        catchloc=b[off:off+25];off+=25
        cid=int.from_bytes(location[1:9],'big');mid=int.from_bytes(location[9:17],'big')
        exc_type=int.from_bytes(cmd(9,1,u64(exc))[1:9],'big')
        print('EXCEPTION',typename(exc_type),exception_message(exc),'at',typename(cid),methodname(cid,mid),flush=True)
        frames=cmd(11,6,u64(thread)+u32(0)+u32(0xffffffff));n=int.from_bytes(frames[:4],'big');p=4
        for j in range(min(n,10)):
            frameid=int.from_bytes(frames[p:p+8],'big');loc=frames[p+8:p+33];p+=33
            c=int.from_bytes(loc[1:9],'big');m=int.from_bytes(loc[9:17],'big')
            print(' ',typename(c),methodname(c,m),'index',int.from_bytes(loc[17:25],'big'),flush=True)
            if typename(c)=='Lcom/oplus/ocs/camera/producer/device/Camera2Impl;' and 'createNewSession' in methodname(c,m):
                obj=int.from_bytes(cmd(16,3,u64(thread)+u64(frameid))[1:9],'big')
                dump_surfaces(obj)
    if policy: cmd(1,9)
cmd(1,6)
