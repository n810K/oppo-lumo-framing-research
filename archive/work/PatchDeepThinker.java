import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.rewriter.*;
public class PatchDeepThinker {
 static int count=0;
 public static void main(String[] args)throws Exception {
  Path p=Path.of(args[0]);var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
  var rw=new DexRewriter(new RewriterModule(){
   public Rewriter<Instruction> getInstructionRewriter(Rewriters r){return new InstructionRewriter(r){public Instruction rewrite(Instruction i){
    if(i instanceof ReferenceInstruction ref&&ref.getReference() instanceof MethodReference m&&m.getName().equals("bindService")&&m.getParameterTypes().equals(List.of("Landroid/content/Intent;","I","Ljava/util/concurrent/Executor;","Landroid/content/ServiceConnection;"))) {
     var call=new ImmutableMethodReference("Lcom/n810/camera/lab/Compatibility;","tryBindService",List.of("Landroid/content/Context;","Landroid/content/Intent;","I","Ljava/util/concurrent/Executor;","Landroid/content/ServiceConnection;"),"Z");count++;
     if(i instanceof FiveRegisterInstruction v)return new ImmutableInstruction35c(Opcode.INVOKE_STATIC,5,v.getRegisterC(),v.getRegisterD(),v.getRegisterE(),v.getRegisterF(),v.getRegisterG(),call);
     var v=(RegisterRangeInstruction)i;return new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE,v.getStartRegister(),5,call);
    }
    return super.rewrite(i);
   }};}
  });
  var classes=new ArrayList<ClassDef>();
  for(ClassDef c:dex.getClasses())classes.add(c.getType().equals("Lg4/a;")?rw.getClassDefRewriter().rewrite(c):c);
  String temp=p+".deepthink";DexFileFactory.writeDexFile(temp,new ImmutableDexFile(dex.getOpcodes(),classes));
  if(count!=1)throw new IllegalStateException("Expected one service bind, got "+count);
  Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
  System.out.println("Preserved DeepThinker bind with existing connection-failure path");
 }
}
