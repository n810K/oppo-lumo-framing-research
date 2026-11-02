import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
public class PatchGalleryEntry {
 public static void main(String[] args)throws Exception {
  Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
  List<ClassDef> cs=new ArrayList<>();int count=0;
  for(ClassDef c:dex.getClasses()){
   if(c.getType().equals("Lcom/oplus/camera/helper/GalleryHelper;")){
    List<Method> ms=new ArrayList<>();
    for(Method m:c.getMethods()){
     if(m.getName().equals("p") && m.getParameterTypes().size()==3 && m.getReturnType().equals("Z")) {
      ms.add(new ImmutableMethod(m.getDefiningClass(),"p$labOriginal",m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),m.getImplementation()));
      var ref=new ImmutableMethodReference("Lcom/n810/camera/lab/CameraMetadataTest;","openGallery",List.of("Ljava/lang/Object;","Ljava/lang/Object;","Ljava/lang/Object;","Z"),"Z");
      var impl=new ImmutableMethodImplementation(5,List.of(new com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c(Opcode.INVOKE_STATIC,4,1,2,3,4,0,ref),new com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x(Opcode.MOVE_RESULT,0),new com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x(Opcode.RETURN,0)),List.of(),List.of());
      m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);count++;
     }
     ms.add(m);
    }
    c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),ms);
   }cs.add(c);
  }
  if(count!=1)throw new IllegalStateException("Expected one Gallery entry, got "+count);
  DexFileFactory.writeDexFile(p+".patched",new ImmutableDexFile(dex.getOpcodes(),cs));Files.move(Path.of(p+".patched"),p,StandardCopyOption.REPLACE_EXISTING);
  System.out.println("Patched Gallery entry to public viewer with secure entry preserved");
 }
}
