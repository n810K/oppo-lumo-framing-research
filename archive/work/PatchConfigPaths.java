import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
public class PatchConfigPaths {
 static Method replace(Method m,String name,MethodImplementation impl) {return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);}
 public static void main(String[] args)throws Exception {
  int config=0,preview=0;
  try(var paths=Files.list(Path.of(args[0]))) {
   for(Path p:paths.filter(x->x.toString().matches(".*classes[0-9]*\\.dex")).toList()) {
    var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    var classes=new ArrayList<ClassDef>();boolean changed=false;
    for(ClassDef c:dex.getClasses()) {
     if(c.getType().equals("Lcom/oplus/camera/configure/CameraConfig;")||c.getType().equals("Lkb/y0;")) {
      var methods=new ArrayList<Method>();
      for(Method m:c.getMethods()) {
       if(c.getType().equals("Lcom/oplus/camera/configure/CameraConfig;")&&m.getName().equals("w")&&m.getParameterTypes().equals(List.of("Ljava/lang/String;","Z"))) {
        methods.add(replace(m,"w$labOriginal",m.getImplementation()));
        var code=List.of(
         new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,2,0,0,0,0,new ImmutableMethodReference("Lcom/n810/camera/lab/Compatibility;","configOverride",List.of("Ljava/lang/String;"),"Ljava/lang/String;")),
         new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),
         new ImmutableInstruction21t(Opcode.IF_EQZ,0,3),
         new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0),
         new ImmutableInstruction35c(Opcode.INVOKE_STATIC,2,2,3,0,0,0,new ImmutableMethodReference(c.getType(),"w$labOriginal",List.of("Ljava/lang/String;","Z"),"Ljava/lang/String;")),
         new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),
         new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0));
        m=replace(m,"w",new ImmutableMethodImplementation(4,code,List.of(),List.of()));config++;changed=true;
       } else if(c.getType().equals("Lkb/y0;")&&m.getName().equals("O")&&m.getReturnType().equals("V")) {
        var impl=new MutableMethodImplementation(m.getImplementation());
        impl.addInstruction(0,new BuilderInstruction35c(Opcode.INVOKE_STATIC,0,0,0,0,0,0,new ImmutableMethodReference("Lcom/n810/camera/lab/Compatibility;","beforePreview",List.of(),"V")));
        m=replace(m,m.getName(),impl);preview++;changed=true;
       }
       methods.add(m);
      }
      c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
     }
     classes.add(c);
    }
    if(changed){String temp=p+".config";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);}
   }
  }
  if(config!=1||preview!=1)throw new IllegalStateException("Wrong patch counts "+config+" "+preview);
  System.out.println("Patched configuration consistency and preview preparation");
 }
}
