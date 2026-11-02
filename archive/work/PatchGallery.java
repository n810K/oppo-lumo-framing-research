import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;

public class PatchGallery {
  public static void main(String[] args) throws Exception {
    Path p=Path.of(args[0]);
    DexFile dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    List<ClassDef> classes=new ArrayList<>(); int count=0;
    for(ClassDef c:dex.getClasses()) {
      if(c.getType().equals("Lzs/d;")) {
        List<Method> methods=new ArrayList<>();
        for(Method m:c.getMethods()) {
          List<Instruction> code=null; int registers=0;
          if(m.getName().equals("b") && m.getParameterTypes().isEmpty() && m.getReturnType().equals("V")) {
            code=List.of(new ImmutableInstruction10x(Opcode.RETURN_VOID)); registers=1;
          } else if(m.getName().equals("d") && m.getParameterTypes().equals(List.of("Lzs/d$b;")) && m.getReturnType().equals("V")) {
            code=List.of(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,1,1,0,0,0,0,
                new ImmutableMethodReference("Lzs/d$b;","b",List.of(),"V")),new ImmutableInstruction10x(Opcode.RETURN_VOID)); registers=2;
          }
          if(code!=null) {
            m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),new ImmutableMethodImplementation(registers,code,List.of(),List.of()));
            count++;
          }
          methods.add(m);
        }
        c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
      }
      classes.add(c);
    }
    if(count!=2)throw new IllegalStateException("Expected two methods, found "+count);
    String temp=p+".gallery";
    DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));
    Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Patched Gallery connect and unavailable callback: "+count+" methods");
  }
}
