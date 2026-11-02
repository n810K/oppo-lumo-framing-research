import jadx.api.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class CameraAnalysis {
  public static void main(String[] args) throws Exception {
    JadxArgs cfg = new JadxArgs();
    cfg.setInputFile(new File(args[0]));
    cfg.setThreadsCount(2);
    cfg.setSkipResources(true);
    try (JadxDecompiler d = new JadxDecompiler(cfg)) {
      d.load();
      Path out = Path.of(args[1]); Files.createDirectories(out);
      for (ResourceFile r : d.getResources()) {
        if (r.getOriginalName().equals("AndroidManifest.xml")) {
          Files.writeString(out.resolve("AndroidManifest.xml"), r.loadContent().getText().getCodeStr());
        }
      }
      List<String> inventory = new ArrayList<>();
      for (JavaClass c : d.getClassesWithInners()) {
        String n = c.getFullName();
        inventory.add(n);
        boolean chosen = n.contains("aicomposition") || n.contains("autocomposition");
        for (int i=2; i<args.length; i++) if (n.equals(args[i])) chosen=true;
        if (chosen) {
          Path p = out.resolve(c.getTopParentClass().getFullName()+".java");
          if (!Files.exists(p)) {
            Files.writeString(p, c.getTopParentClass().getCode());
            System.out.println("Saved " + p.getFileName());
          }
        }
      }
      Files.write(out.resolve("classes.txt"), inventory);
    }
  }
}
