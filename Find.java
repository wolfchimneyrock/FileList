package cli;
import static java.nio.file.FileVisitResult.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public class Find {
    public static FileList in(String path, String pattern, String prune) throws FileNotFoundException, SecurityException {
        int options = FileList.CANONICAL | FileList.NOORDER;
        boolean usePattern = false;
        boolean usePruning = false;
        boolean isUnreadable = false;
        Queue<Path> dirQ = new LinkedList<Path>();
        Path dir = Paths.get(path);
        FileList fl = FileList.empty(options);
        if (!Files.isDirectory(dir)) {
            if (Files.isReadable(dir)) {
                try { fl.add(dir.toFile()); }
                catch (Exception e) {} //System.out.println(e.getMessage()); }
            } else System.out.println("Find: Error: cannot read " + dir + ": permission denied");
            return fl; 
        }
        FileSystem fs = FileSystems.getDefault();
        PathMatcher patternMatch = null;
        PathMatcher pruningMatch = null;
        if (!pattern.equals("")) {
            usePattern = true;
            patternMatch = fs.getPathMatcher("glob:**/" + pattern); 
        }
        if (!prune.equals("")) {
            usePruning = true;
            pruningMatch = fs.getPathMatcher("glob:**/" + prune);
        }
        dirQ.add(dir);
        while ((dir = dirQ.poll()) != null) {
            isUnreadable = false;
            if (usePruning && pruningMatch.matches(dir)) continue;
            if (!Files.isReadable(dir)) {
                System.out.println("Find: Error: cannot read " + dir + ": permission denied");
                //continue;
                isUnreadable = true;
            }
            if (!usePattern) try {
                if (!isUnreadable) fl.add(dir.toFile());
            } catch (Exception e) { } //System.out.println(e.getMessage()); }
            try {
                for (Path p : Files.newDirectoryStream(dir)) {
                    if (Files.isDirectory(p)) { 
                        if (usePattern && Files.isReadable(p) && patternMatch.matches(p)) fl.add(p.toFile());
                        dirQ.add(p);
                    } else {
                        if (usePattern && !patternMatch.matches(p))
                            {} // do nothing
                        else if (!isUnreadable) fl.add(p.toFile());
                    }
                }
            } catch (Exception e) { } // System.out.println(e.getMessage()); }
            
        }
        return fl;
    } // in()

    private enum Token { NOTHING, VALUE, OPT_NAME, OPT_PRUNE, OPT_UNKNOWN }

    public static void main(String ... args) {
        String errorMsg = "";
        Queue<String> paths = new LinkedList<String>();
        String pattern = "";
        String prune   = "";
        String workingDir = System.getProperty("user.dir");
        Token last = Token.NOTHING;
        for (String arg : args) {
            if (arg.charAt(0) == '-') {
                switch (last) {
                    case OPT_NAME:
                        errorMsg = errorMsg + "Find: Error: missing argument to -name\n";
                        break;
                    case OPT_PRUNE:
                        errorMsg = errorMsg + "Find: Error: missing argument to -prune\n";
                        break;
                }
                switch (arg) {
                    case "-name":
                        last = Token.OPT_NAME;  
                        break;
                    case "-prune":
                        last = Token.OPT_PRUNE;
                        break;
                    default:
                        last = Token.OPT_UNKNOWN;
                        errorMsg = errorMsg + "Find: Unknown option: '" + arg + "'\n";
                }
            } else {
                switch(last) {
                    case OPT_NAME:
                        pattern = arg;
                        last = Token.VALUE;
                        break;
                    case OPT_PRUNE:
                        prune = arg;
                        last = Token.VALUE;
                        break;
                    case OPT_UNKNOWN:
                        errorMsg = errorMsg + "Find: Unexpected token '" + arg + "' skipped\n";
                    default:
                        if (arg.charAt(0) == '/')
                            paths.add(arg);
                        else paths.add(workingDir + File.separator + arg);
                } 
            }
        }
        switch (last) {
            case OPT_NAME:
                errorMsg = errorMsg + "Find: Error: missing argument to -name\n";
                break;
            case OPT_PRUNE:
                errorMsg = errorMsg + "Find: Error: missing argument to -prune\n";
                break;
        }
        
        if (errorMsg.equals("")) { 
            if (paths.isEmpty()) paths.add(workingDir);
            for (String path : paths) {
                try {
                    FileList fl = Find.in(path, pattern, prune);
                    for (String output : fl.format()) System.out.println(output);
                } catch (IOException e) { System.out.println("Find: Error: " + e.toString() + ": " + path); }
            }
        } else System.out.println(errorMsg);
    }
}
