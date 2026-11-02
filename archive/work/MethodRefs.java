import java.io.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
public class MethodRefs {
 public static void main(String[] args)throws Exception {
  var dex=DexFileFactory.loadDexFile(new File(args[0]),Opcodes.forDexVersion(39));
  for(ClassDef c:dex.getClasses()) for(Method m:c.getMethods()) {
   if(m.getImplementation()==null)continue;
   for(Instruction i:m.getImplementation().getInstructions()) if(i instanceof ReferenceInstruction ri&&ri.getReference() instanceof MethodReference mr&&mr.getName().equals(args[1])) {
    System.out.println(c.getType()+" "+m.getName()+" -> "+mr);break;
   }
  }
 }
}
