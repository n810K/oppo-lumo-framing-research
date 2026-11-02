import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.android.tools.smali.dexlib2.iface.value.*;
import com.android.tools.smali.dexlib2.immutable.*;
import com.android.tools.smali.dexlib2.immutable.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import com.android.tools.smali.dexlib2.immutable.value.*;
import com.android.tools.smali.dexlib2.rewriter.*;

public class PatchClone {
  static Map<String,String> ids=new LinkedHashMap<>();
  static long replacements=0; static int patched=0;
  static String rename(String s) {
    if(ids.containsKey(s)){replacements++;return ids.get(s);}
    for(var e:ids.entrySet()) {
      String prefix="content://"+e.getKey();
      if(s.equals(prefix)||s.startsWith(prefix+"/")){replacements++;return "content://"+e.getValue()+s.substring(prefix.length());}
    }
    return s;
  }
  static Method method(Method m,String name,MethodImplementation impl) {
    return new ImmutableMethod(m.getDefiningClass(),name,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),impl);
  }
  static Method forced(Method m) {
    var equals=new ImmutableMethodReference("Ljava/lang/String;","equals",List.of("Ljava/lang/Object;"),"Z");
    var fallback=new ImmutableMethodReference(m.getDefiningClass(),"d$labOriginal",List.of("Ljava/lang/String;","Z","Z"),"Z");
    List<Instruction> code=List.of(
      new ImmutableInstruction21c(Opcode.CONST_STRING,0,new ImmutableStringReference("com.oplus.feature.aicomposition.realscene.support")),
      new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,2,0,2,0,0,0,equals),
      new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
      new ImmutableInstruction21t(Opcode.IF_NEZ,0,15),
      new ImmutableInstruction21c(Opcode.CONST_STRING,0,new ImmutableStringReference("com.oplus.feature.aicomposition.inspiration.support")),
      new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,2,0,2,0,0,0,equals),
      new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
      new ImmutableInstruction21t(Opcode.IF_NEZ,0,7),
      new ImmutableInstruction35c(Opcode.INVOKE_STATIC,3,2,3,4,0,0,fallback),
      new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
      new ImmutableInstruction11x(Opcode.RETURN,0),
      new ImmutableInstruction11n(Opcode.CONST_4,0,1),
      new ImmutableInstruction11x(Opcode.RETURN,0));
    return method(m,m.getName(),new ImmutableMethodImplementation(5,code,List.of(),List.of()));
  }
  public static void main(String[] args)throws Exception {
    for(String l:Files.readAllLines(Path.of(args[1]))) {String[] p=l.split("\t");ids.put(p[0],p[1]);}
    DexRewriter rw=new DexRewriter(new RewriterModule(){
      public Rewriter<Instruction> getInstructionRewriter(Rewriters r){
        return new InstructionRewriter(r){public Instruction rewrite(Instruction i){
          if(i instanceof ReferenceInstruction ref && ref.getReference() instanceof StringReference s){
            String n=rename(s.getString());
            if(!n.equals(s.getString())){
              int reg=((OneRegisterInstruction)i).getRegisterA();
              return i.getOpcode()==Opcode.CONST_STRING_JUMBO ? new ImmutableInstruction31c(i.getOpcode(),reg,new ImmutableStringReference(n)) : new ImmutableInstruction21c(i.getOpcode(),reg,new ImmutableStringReference(n));
            }
          }
          return super.rewrite(i);
        }};
      }
      public Rewriter<EncodedValue> getEncodedValueRewriter(Rewriters r){
        return new EncodedValueRewriter(r){public EncodedValue rewrite(EncodedValue v){
          if(v instanceof StringEncodedValue s)return new ImmutableStringEncodedValue(rename(s.getValue()));
          return super.rewrite(v);
        }};
      }
    });
    try(var files=Files.list(Path.of(args[0]))){
      for(Path p:files.filter(x->x.getFileName().toString().matches("classes[0-9]*\\.dex")).sorted().toList()){
        DexFile dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
        List<ClassDef> classes=new ArrayList<>();
        for(ClassDef c:dex.getClasses()){
          if(c.getType().equals("Lcom/oplus/camera/configure/CameraConfig;")){
            List<Method> methods=new ArrayList<>();
            for(Method m:c.getMethods()){
              if(m.getName().equals("d") && m.getParameterTypes().equals(List.of("Ljava/lang/String;","Z","Z")) && m.getReturnType().equals("Z")){
                methods.add(method(m,"d$labOriginal",m.getImplementation()));methods.add(forced(m));patched++;
              }else methods.add(m);
            }
            c=new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);
          }
          classes.add(c);
        }
        DexFile out=rw.getDexFileRewriter().rewrite(new ImmutableDexFile(dex.getOpcodes(),classes));
        String temp=p.toString()+".new";DexFileFactory.writeDexFile(temp,out);Files.move(Path.of(temp),p,StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Processed "+p.getFileName());
      }
    }
    if(patched!=1)throw new IllegalStateException("Expected one patched method, found "+patched);
    System.out.println("Gate methods patched: "+patched+"; identity string rewrites: "+replacements);
  }
}
