import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.android.tools.smali.dexlib2.*;
public class CanonicalizeDex {
  public static void main(String[] args)throws Exception {
    ExecutorService pool=Executors.newFixedThreadPool(2);
    List<Future<?>> results=new ArrayList<>();
    try(var paths=Files.list(Path.of(args[0]))) {
      for(Path p:paths.filter(x->x.getFileName().toString().matches("classes[0-9]*\\.dex")).sorted().toList()) {
        results.add(pool.submit(()->{ try {
        var dex=DexFileFactory.loadDexFile(p.toFile(),Opcodes.forDexVersion(39));
        String prior=null;boolean sorted=true;
        for(var ref:dex.getStringReferences()) {
          String next=ref.getString();
          if(prior!=null && prior.compareTo(next)>=0){sorted=false;break;}
          prior=next;
        }
        if(sorted){System.out.println("Already valid "+p.getFileName());return;}
        String tmp=p+".canonical";
        DexFileFactory.writeDexFile(tmp,dex);
        var verified=DexFileFactory.loadDexFile(tmp,Opcodes.forDexVersion(39));
        String prev=null;
        for(var ref:verified.getStringReferences()) {
          String next=ref.getString();
          if(prev!=null && prev.compareTo(next)>=0)throw new IllegalStateException("Unsorted strings: "+p);
          prev=next;
        }
        Files.move(Path.of(tmp),p,StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Canonicalized and verified "+p.getFileName());
        } catch(Exception e){throw new RuntimeException(e);} }));
      }
    }
    pool.shutdown();
    for(var result:results)result.get();
  }
}
