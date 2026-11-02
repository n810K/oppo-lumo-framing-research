import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;

public class PatchFeatureHook {
  public static void main(String[] args)throws Exception {
    Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    List<ClassDef> classes=new ArrayList<>();int count=0;
    for(ClassDef c:dex.getClasses()) {
      if(c.getType().equals("Lcom/oplus/ocs/camera/config/FeatureConfigureAdapter;")) {
        List<Method> methods=new ArrayList<>();
        for(Method m:c.getMethods()) {
          if(m.getName().equals("getFeatureInfoList")&&m.getParameterTypes().equals(List.of("I"))) {
            var impl=new MutableMethodImplementation(m.getImplementation());
            impl.addInstruction(0,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,impl.getRegisterCount()-2,1,new ImmutableMethodReference("Lcom/n810/camera/lab/Compatibility;","beforeFeatures",List.of("Ljava/lang/Object;"),"V")));
            m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);count++;
          }
          methods.add(m);
        }
        c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
      }
      classes.add(c);
    }
    if(count!=1)throw new IllegalStateException("Expected one startup hook, got "+count);
    String temp=p+".startup";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Added test-copy startup hook");
  }
}
