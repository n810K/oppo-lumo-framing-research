import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.immutable.*;

public class PatchRemoteDenied {
  public static void main(String[] args)throws Exception {
    Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
    List<ClassDef> classes=new ArrayList<>();int count=0;
    for(ClassDef c:dex.getClasses()) {
      if(c.getType().equals(args[1])) {
        List<Method> methods=new ArrayList<>();
        for(Method m:c.getMethods()) {
          var impl=m.getImplementation();
          if(impl!=null) {
            List<TryBlock<? extends ExceptionHandler>> blocks=new ArrayList<>();
            for(var block:impl.getTryBlocks()) {
              List<ExceptionHandler> handlers=new ArrayList<>();
              boolean already=block.getExceptionHandlers().stream().anyMatch(h->"Ljava/lang/SecurityException;".equals(h.getExceptionType()));
              for(var h:block.getExceptionHandlers()) {
                handlers.add(h);
                if(!already && "Landroid/os/RemoteException;".equals(h.getExceptionType())) {
                  handlers.add(new ImmutableExceptionHandler("Ljava/lang/SecurityException;",h.getHandlerCodeAddress()));count++;
                  System.out.println("Added denied-access handling to "+m.getName());
                }
              }
              blocks.add(new ImmutableTryBlock(block.getStartCodeAddress(),block.getCodeUnitCount(),handlers));
            }
            m=new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),new ImmutableMethodImplementation(impl.getRegisterCount(),impl.getInstructions(),blocks,impl.getDebugItems()));
          }
          methods.add(m);
        }
        c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
      }
      classes.add(c);
    }
    if(count==0)throw new IllegalStateException("No handlers changed");
    String temp=p+".denied";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
    System.out.println("Preserved existing bodies and extended "+count+" error handlers");
  }
}
