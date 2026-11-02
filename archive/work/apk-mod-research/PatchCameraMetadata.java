import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;

class PatchCameraMetadata {
  public static void main(String[] args)throws Exception {
    Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    List<ClassDef> classes=new ArrayList<>();int count=0;
    for(ClassDef c:dex.getClasses()) {
      if(c.getType().equals("Lcom/n810/camera/lab/Compatibility;")) {
        List<Method> methods=new ArrayList<>();
        for(Method m:c.getMethods()) {
          if(m.getName().equals("beforePreview")&&m.getParameterTypes().isEmpty()) {
            var impl=new MutableMethodImplementation(m.getImplementation());
            impl.addInstruction(1,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,0,0,new ImmutableMethodReference("Lcom/n810/camera/lab/CameraMetadataTest;","apply",List.of(),"V")));
            m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);count++;
          }
          if(m.getName().equals("initialize")&&m.getParameterTypes().equals(List.of("Landroid/content/Context;"))) {
            var impl=new MutableMethodImplementation(m.getImplementation());
            int arg=impl.getRegisterCount()-1;
            impl.addInstruction(0,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,arg,1,new ImmutableMethodReference("Lcom/n810/camera/lab/CameraMetadataTest;","attach",List.of("Landroid/content/Context;"),"V")));
            m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);count++;
          }
          if(m.getName().equals("configOverride")&&m.getParameterTypes().equals(List.of("Ljava/lang/String;"))){
            var impl=new MutableMethodImplementation(2);
            impl.addInstruction(new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,1,1,new ImmutableMethodReference("Lcom/n810/camera/lab/CameraMetadataTest;","configOverride",List.of("Ljava/lang/String;"),"Ljava/lang/String;")));
            impl.addInstruction(new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT,0));impl.addInstruction(new BuilderInstruction11x(Opcode.RETURN_OBJECT,0));
            m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);count++;
          }
          methods.add(m);
        }
        c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
      }
      classes.add(c);
    }
    if(count!=3)throw new IllegalStateException("Expected three test hooks, got "+count);
    String temp=p+".startup";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Added test-copy startup hook");
  }
}
