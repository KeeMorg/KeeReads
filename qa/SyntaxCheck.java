import com.sun.source.util.JavacTask;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

public final class SyntaxCheck {
    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var javaFiles = new ArrayList<java.io.File>();
            for (String a : args) javaFiles.add(new java.io.File(a));
            var units = manager.getJavaFileObjectsFromFiles(javaFiles);
            var task = (JavacTask)compiler.getTask(null, manager, diagnostics, List.of("-proc:none", "--release", "17"), null, units);
            int count = 0;
            for (var unit : task.parse()) { if(unit != null)count++; }
            boolean errors = false;
            for (var d : diagnostics.getDiagnostics()) {
                System.out.println(d.toString());
                errors |= d.getKind() == Diagnostic.Kind.ERROR;
            }
            if (errors || count != javaFiles.size()) System.exit(1);
            System.out.println("PASS: Parsed " + count + " Java source files using javac 17 syntax parser.");
        }
    }
}
