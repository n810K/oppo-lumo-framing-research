import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;

public class PatchDiagnosticLog {
  public static void main(String[] args)throws Exception {
    Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    List<ClassDef> classes=new ArrayList<>();int count=0;
    for(ClassDef c:dex.getClasses()) {
      if(c.getType().equals("Ljb/a;")) {
        List<Method> methods=new ArrayList<>();
        for(Method m:c.getMethods()) {
          if(m.getName().equals("x")&&m.getParameterTypes().equals(List.of("Z"))&&m.getReturnType().equals("V")) {
            var impl=new MutableMethodImplementation(m.getImplementation());
            impl.addInstruction(0,new BuilderInstruction21s(Opcode.CONST_16,impl.getRegisterCount()-1,0));
            m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);count++;
          }
          methods.add(m);
        }
        c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
      }
      classes.add(c);
    }
    if(count!=1)throw new IllegalStateException("Expected one logger method");
    String temp=p+".logs";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Enabled readable diagnostic output in test process only");
  }
}
