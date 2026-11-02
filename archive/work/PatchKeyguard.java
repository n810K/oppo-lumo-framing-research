import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;

public class PatchKeyguard {
  static Method copy(Method m,String name,MethodImplementation impl) {
    return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);
  }
  static Method wrapper(Method m) {
    var b=new MethodImplementationBuilder(4);
    b.addInstruction(new BuilderInstruction21c(Opcode.CONST_STRING,0,new ImmutableStringReference("android.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE")));
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,2,3,0,0,0,0,new ImmutableMethodReference("Landroid/content/Context;","checkSelfPermission",List.of("Ljava/lang/String;"),"I")));
    b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT,0));
    b.addInstruction(new BuilderInstruction21t(Opcode.IF_NEZ,0,b.getLabel("fallback")));
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC,1,3,0,0,0,0,new ImmutableMethodReference("Lqa/l3;","e$labOriginal",List.of("Landroid/content/Context;"),"Z")));
    b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT,0));
    b.addInstruction(new BuilderInstruction11x(Opcode.RETURN,0));
    b.addLabel("fallback");
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_STATIC,1,3,0,0,0,0,new ImmutableMethodReference("Lqa/l3;","a",List.of("Landroid/content/Context;"),"Landroid/app/KeyguardManager;")));
    b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,0));
    b.addInstruction(new BuilderInstruction21t(Opcode.IF_EQZ,0,b.getLabel("unknown")));
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,1,0,0,0,0,0,new ImmutableMethodReference("Landroid/app/KeyguardManager;","isKeyguardLocked",List.of(),"Z")));
    b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT,1));
    b.addInstruction(new BuilderInstruction35c(Opcode.INVOKE_VIRTUAL,1,0,0,0,0,0,new ImmutableMethodReference("Landroid/app/KeyguardManager;","isDeviceLocked",List.of(),"Z")));
    b.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT,2));
    b.addInstruction(new BuilderInstruction10t(Opcode.GOTO,b.getLabel("store")));
    b.addLabel("unknown");
    b.addInstruction(new BuilderInstruction11n(Opcode.CONST_4,1,1));
    b.addInstruction(new BuilderInstruction11n(Opcode.CONST_4,2,1));
    b.addLabel("store");
    b.addInstruction(new BuilderInstruction21c(Opcode.SPUT_BOOLEAN,1,new ImmutableFieldReference("Lqa/l3;","d","Z")));
    b.addInstruction(new BuilderInstruction21c(Opcode.SPUT_BOOLEAN,2,new ImmutableFieldReference("Lqa/l3;","e","Z")));
    b.addInstruction(new BuilderInstruction11x(Opcode.RETURN,1));
    return copy(m,"e",b.getMethodImplementation());
  }
  public static void main(String[] args)throws Exception {
    Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    List<ClassDef> classes=new ArrayList<>();int count=0;
    for(ClassDef c:dex.getClasses()) {
      if(c.getType().equals("Lqa/l3;")) {
        Set<String> fields=new HashSet<>();for(Field f:c.getStaticFields())fields.add(f.getName()+":"+f.getType());
        if(!fields.containsAll(List.of("d:Z","e:Z")))throw new IllegalStateException("Unexpected fields "+fields);
        List<Method> methods=new ArrayList<>();
        for(Method m:c.getMethods()) {
          if(m.getName().equals("e$labOriginal"))throw new IllegalStateException("Already patched");
          if(m.getName().equals("e")&&m.getParameterTypes().equals(List.of("Landroid/content/Context;"))&&m.getReturnType().equals("Z")) {
            methods.add(copy(m,"e$labOriginal",m.getImplementation()));methods.add(wrapper(m));count++;
          }else methods.add(m);
        }
        c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
      }
      classes.add(c);
    }
    if(count!=1)throw new IllegalStateException("Unexpected method count "+count);
    String temp=p+".keyguard";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Added real lock-state query fallback");
  }
}
