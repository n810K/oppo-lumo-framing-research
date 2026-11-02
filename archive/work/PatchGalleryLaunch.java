import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.builder.*;
import com.android.tools.smali.dexlib2.builder.instruction.*;
public class PatchGalleryLaunch {
 static Method method(Method m,String name,MethodImplementation impl){return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);}
 public static void main(String[] args)throws Exception {
  int gallery=0,init=0;
  try(var paths=Files.list(Path.of(args[0]))) {
   for(Path p:paths.filter(x->x.toString().matches(".*classes[0-9]*\\.dex")).toList()) {
    var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));var classes=new ArrayList<ClassDef>();boolean changed=false;
    for(ClassDef c:dex.getClasses()) {
     if(c.getType().equals("Lcom/oplus/camera/helper/GalleryHelper;")||c.getType().equals("Lcom/oplus/camera/configure/CameraConfig;")) {
      var methods=new ArrayList<Method>();
      for(Method m:c.getMethods()) {
       if(c.getType().equals("Lcom/oplus/camera/helper/GalleryHelper;")&&m.getName().equals("q")&&m.getParameterTypes().equals(List.of("Landroid/content/Intent;","Z","Landroid/net/Uri;"))) {
        methods.add(method(m,"q$labOriginal",m.getImplementation()));
        var call=new ImmutableMethodReference("Lcom/n810/camera/lab/Compatibility;","startGallery",List.of("Ljava/lang/Object;","Landroid/content/Intent;","Z","Landroid/net/Uri;"),"V");
        m=method(m,"q",new ImmutableMethodImplementation(4,List.of(new ImmutableInstruction35c(Opcode.INVOKE_STATIC,4,0,1,2,3,0,call),new ImmutableInstruction10x(Opcode.RETURN_VOID)),List.of(),List.of()));gallery++;changed=true;
       } else if(c.getType().equals("Lcom/oplus/camera/configure/CameraConfig;")&&m.getName().equals("B")) {
        var impl=new MutableMethodImplementation(m.getImplementation());
        for(int i=0;i<impl.getInstructions().size();i++){
         var instruction=impl.getInstructions().get(i);
         if(instruction instanceof ReferenceInstruction ref&&ref.getReference() instanceof MethodReference called&&called.getName().equals("initConfigData")) {
          impl.addInstruction(i+1,new BuilderInstruction35c(Opcode.INVOKE_STATIC,0,0,0,0,0,0,new ImmutableMethodReference("Lcom/n810/camera/lab/Compatibility;","beforePreview",List.of(),"V")));init++;i++;
         }
        }
        m=method(m,m.getName(),impl);changed=true;
       }
       methods.add(m);
      }
      c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
     }
     classes.add(c);
    }
    if(changed){String temp=p+".gallerylaunch";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);}
   }
  }
  if(gallery!=1||init!=1)throw new IllegalStateException("Unexpected patch counts "+gallery+" "+init);
  System.out.println("Added public Gallery fallback and configuration-ready hook");
 }
}
