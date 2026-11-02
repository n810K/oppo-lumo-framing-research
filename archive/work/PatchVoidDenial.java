import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;

public class PatchVoidDenial {
  static Method copy(Method m,String name,MethodImplementation impl) {
    return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);
  }
  static Method wrapper(Method m) {
    boolean isStatic=AccessFlags.STATIC.isSet(m.getAccessFlags());
    int words=isStatic?0:1;for(var t:m.getParameterTypes())words+=(t.equals("J")||t.equals("D"))?2:1;
    var b=new MethodImplementationBuilder(3+words);
    b.addLabel("start");
    b.addInstruction(new BuilderInstruction3rc(isStatic?Opcode.INVOKE_STATIC_RANGE:AccessFlags.PRIVATE.isSet(m.getAccessFlags())?Opcode.INVOKE_DIRECT_RANGE:Opcode.INVOKE_VIRTUAL_RANGE,3,words,new ImmutableMethodReference(m.getDefiningClass(),m.getName()+"$labOriginal",m.getParameterTypes(),"V")));
    b.addLabel("end");
    b.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
    b.addLabel("handler");
    b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_EXCEPTION,0));
    b.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING,1,new ImmutableStringReference("CameraLabCompat")));
    b.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING,2,new ImmutableStringReference("Protected integration denied: "+m.getDefiningClass()+"."+m.getName())));
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC,3,1,2,0,0,0,new ImmutableMethodReference("Landroid/util/Log;","w",List.of("Ljava/lang/String;","Ljava/lang/String;","Ljava/lang/Throwable;"),"I")));
    b.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
    b.addCatch("Ljava/lang/SecurityException;",b.getLabel("start"),b.getLabel("end"),b.getLabel("handler"));
    return copy(m,m.getName(),b.getMethodImplementation());
  }
  public static void main(String[] args)throws Exception {
    Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    var names=new HashSet<>(Arrays.asList(args).subList(2,args.length));
    List<ClassDef> classes=new ArrayList<>();int count=0;
    for(ClassDef c:dex.getClasses()) {
      if(c.getType().equals(args[1])) {
        List<Method> methods=new ArrayList<>();
        for(Method m:c.getMethods()) {
          if(m.getName().endsWith("$labOriginal") && names.contains(m.getName().replace("$labOriginal","")))throw new IllegalStateException("Already patched");
          if(names.contains(m.getName())) {
            if(!m.getReturnType().equals("V")||m.getImplementation()==null)throw new IllegalStateException("Unsupported method "+m);
            methods.add(copy(m,m.getName()+"$labOriginal",m.getImplementation()));methods.add(wrapper(m));count++;
          }else methods.add(m);
        }
        c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
      }
      classes.add(c);
    }
    if(count!=names.size())throw new IllegalStateException("Expected "+names.size()+", found "+count);
    String temp=p+".voiddenial";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Preserved and guarded "+count+" methods");
  }
}
