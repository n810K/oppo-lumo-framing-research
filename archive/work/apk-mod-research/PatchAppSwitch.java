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
public class PatchAppSwitch {
 public static void main(String[] args)throws Exception {
  Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
  List<ClassDef> cs=new ArrayList<>();int count=0;
  for(ClassDef c:dex.getClasses()){
   if(c.getType().equals("Lcom/oplus/camera/util/h0;")){
    List<Method> ms=new ArrayList<>();
    for(Method m:c.getMethods()){
     if(m.getImplementation()!=null){
      var impl=new MutableMethodImplementation(m.getImplementation());boolean changed=false;
      for(int i=0;i<impl.getInstructions().size();i++){
       var ins=impl.getInstructions().get(i);
       if(ins instanceof ReferenceInstruction ri && ri.getReference() instanceof MethodReference mr && mr.getDefiningClass().equals("Lcom/oplus/app/OplusAppSwitchManager;") && mr.getName().equals("registerAppSwitchObserver")){
        var ref=new ImmutableMethodReference("Lcom/n810/camera/lab/CameraMetadataTest;","registerAppSwitchObserver",List.of("Ljava/lang/Object;","Landroid/content/Context;","Ljava/lang/Object;","Ljava/lang/Object;"),"Z");
        if(ins instanceof RegisterRangeInstruction rr) impl.replaceInstruction(i,new BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE,rr.getStartRegister(),rr.getRegisterCount(),ref));
        else if(ins instanceof FiveRegisterInstruction fr)impl.replaceInstruction(i,new BuilderInstruction35c(Opcode.INVOKE_STATIC,fr.getRegisterCount(),fr.getRegisterC(),fr.getRegisterD(),fr.getRegisterE(),fr.getRegisterF(),fr.getRegisterG(),ref));
        else throw new IllegalStateException("Unexpected opcode");
        count++;changed=true;
       }
      }
      if(changed)m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);
     }
     ms.add(m);
    }
    c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),ms);
   }cs.add(c);
  }
  if(count!=1)throw new IllegalStateException("Expected exactly one app-switch registration, got "+count);
  DexFileFactory.writeDexFile(p+".patched",new ImmutableDexFile(dex.getOpcodes(),cs));Files.move(Path.of(p+".patched"),p,StandardCopyOption.REPLACE_EXISTING);
  System.out.println("Patched exact app-switch registration with truthful denial handling");
 }
}
