import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;

public class PatchCompatibility {
  static Method copy(Method m,String name,MethodImplementation impl) {
    return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);
  }
  static Method wrapper(Method m,Field selector) {
    var b=new MethodImplementationBuilder(4);
    var original=new ImmutableMethodReference(m.getDefiningClass(),"run$labOriginal",List.of(),"V");
    if(selector!=null) {
      b.addInstruction(new BuilderInstruction22c(Opcode.IGET,0,3,new ImmutableFieldReference(selector.getDefiningClass(),selector.getName(),"I")));
      b.addInstruction(new BuilderInstruction21s(Opcode.CONST_16,1,15));
      b.addInstruction(new BuilderInstruction22t(Opcode.IF_NE,0,1,b.getLabel("ordinary")));
    }
    b.addLabel("start");
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,1,3,0,0,0,0,original));
    b.addLabel("end");
    b.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
    b.addLabel("handler");
    b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_EXCEPTION,0));
    b.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING,1,new ImmutableStringReference("CameraLabCompat")));
    b.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING,2,new ImmutableStringReference(selector==null?"Athena memory optimization unavailable to test copy":"Gallery predecode denied; ordinary Gallery viewing remains available")));
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC,3,1,2,0,0,0,new ImmutableMethodReference("Landroid/util/Log;","w",List.of("Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/Throwable;"),"I")));
    b.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
    if(selector!=null) {
      b.addLabel("ordinary");
      b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,1,3,0,0,0,0,original));
      b.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
    }
    b.addCatch("Ljava/lang/SecurityException;",b.getLabel("start"),b.getLabel("end"),b.getLabel("handler"));
    return copy(m,"run",b.getMethodImplementation());
  }
  public static void main(String[] args)throws Exception {
    var stock=DexFileFactory.loadDexFile(Path.of(args[1]).toFile(),Opcodes.forDexVersion(39));
    ClassDef gallery=null;for(ClassDef c:stock.getClasses())if(c.getType().equals("Lzs/d;"))gallery=c;
    if(gallery==null)throw new IllegalStateException("Missing stock Gallery client");
    int patched=0,restored=0;
    try(var files=Files.list(Path.of(args[0]))) {
      for(Path p:files.filter(x->x.getFileName().toString().matches("classes[0-9]*\\.dex")).sorted().toList()) {
        var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
        List<ClassDef> classes=new ArrayList<>();boolean changed=false;
        for(ClassDef c:dex.getClasses()) {
          if(c.getType().equals("Lzs/d;")){c=gallery;restored++;changed=true;}
          if(c.getType().equals("Lul/d;")||c.getType().equals("Lcom/oplus/camera/util/Util$a;")) {
            Field selector=null;
            if(c.getType().equals("Lul/d;")) {
              for(Field f:c.getInstanceFields())if(f.getType().equals("I")) {if(selector!=null)throw new IllegalStateException("Ambiguous selector");selector=f;}
              if(selector==null)throw new IllegalStateException("No selector");
            }
            List<Method> methods=new ArrayList<>();
            for(Method m:c.getMethods()) {
              if(m.getName().equals("run$labOriginal"))throw new IllegalStateException("Already patched");
              if(m.getName().equals("run")&&m.getParameterTypes().isEmpty()) {
                methods.add(copy(m,"run$labOriginal",m.getImplementation()));methods.add(wrapper(m,selector));patched++;changed=true;
              }else methods.add(m);
            }
            c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
          }
          classes.add(c);
        }
        if(changed) {
          String temp=p+".compat";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
          System.out.println("Updated "+p);
        }
      }
    }
    if(patched!=2||restored!=1)throw new IllegalStateException("Unexpected counts: "+patched+" "+restored);
    System.out.println("Restored original Gallery integration; added two targeted SecurityException handlers");
  }
}
