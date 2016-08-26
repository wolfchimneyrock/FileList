package cli;

import java.util.Collections;
import java.util.List;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.lang.SecurityException;
import java.lang.RuntimeException;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.util.Date;
import java.time.LocalDate;
import java.nio.file.attribute.PosixFileAttributes;
import java.text.SimpleDateFormat;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.PosixFilePermission;
import java.io.File;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.Formatter;

public class FileList {

    public static FileList of(String pattern, int ... options) throws IOException, FileNotFoundException, SecurityException {
        File dir = new File(pattern);
        if (!dir.exists()) throw new FileNotFoundException("cannot access " + pattern + ": No such file or directory");
        if (!dir.canRead()) throw new SecurityException("cannot access " + pattern + ": Permission denied");
        File[] contents = dir.listFiles();
        FileList fl = FileList.empty(options);
        fl.path = pattern;
        for ( File f : contents ) { 
            try {
                fl.add(f);
            } catch (Exception e) { System.out.println(e.getMessage()); }
        }
        
        return fl;
    }

    public static FileList empty(int ... options) {
        int combinedOptions = 0;
        for (int o : options) combinedOptions = combinedOptions | o;
        FileList fl = new FileList(combinedOptions);
        return fl;
    }

    public List<File> files() {
        ArrayList<File> fl = new ArrayList<File>(files.size());
        fl.addAll(files);
        return fl;
    }

    public boolean contains(File f) {
        return files.contains(f);
    }

    public void add(File f) throws FileNotFoundException, IOException, SecurityException {
        if (!f.exists()) throw new FileNotFoundException("cannot access " + f.getCanonicalPath() + ": No such file or directory");
        //if (!f.canRead()) throw new SecurityException("cannot access " + f.getName() + ": Permission denied");

            // skip hidden files if ALL isn't set
        if ( ((activeOptions & ALL) != ALL) && (f.getName().charAt(0) == '.') ) return;

        if ( ((activeOptions & RECURSE) == RECURSE) && (f.isDirectory())) {
            // recursively add subdirectory
            // all we do here is append the found subdirectory to a string list
            // to be processed elsewhere
            subDirs.add(f.getCanonicalPath());
        }
            // conditions passed.  add file.  if printing extended, calculate widths of text fields.
        int x;
        if ( ((activeOptions & EXTENDED) == EXTENDED)) {
            PosixFileAttributes attrs = Files.readAttributes(f.toPath(), PosixFileAttributes.class);
            x = attrs.owner().toString().length();
            if (x > ownerLength) ownerLength = x; 
            x = attrs.group().toString().length();
            if (x > groupLength) groupLength = x;
            x = (int)(Math.log10(attrs.size())+1);
            if (x > sizeLength) sizeLength = x;
        } 
        files.add(f);
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    public List<String> format() throws IOException {
          // first, generate format string based on calculated widths of text fields calculated during file add 
        formatString = String.format("%%s. %%-%ds %%-%ds %%%dd %%s %%s", ownerLength, groupLength, sizeLength); 
        List<String> output = new ArrayList<String>(files.size());
        try {
            if ((activeOptions & EXTENDED) == EXTENDED)
                 for (File f : files) output.add(formatExtended(f));
            else for (File f : files) output.add(formatPlain(f));
        }      catch (Exception e)  { System.out.println(e.getMessage()); }
        return output;   
    }

    public static void main(String ... args) {
        char c;
        int options = 0;
        int numItems = 0;
        int index = 0;
        String workingDir = System.getProperty("user.dir");
        List<File> items = new ArrayList<File>();
        for (String arg : args) {
            // an argument would definitely have at least one character
            switch(arg.charAt(0)) {
                case '-':    // process option
                    for (int n = 1; n < arg.length(); n++) {
                        c = arg.charAt(n);
                        if (OPTIONKEY.containsKey(c)) options = options | OPTIONKEY.get(c);
                        else { // option not found - print valid options and quit
                            System.out.println("Invalid option -- '" + c + "'");
                            FileList.displayOptions();
                            System.exit(1);
                        } 
                    }
                    break;
                case '/':    // add item absolutely
                    items.add(new File(arg));
                    break;
                default:     // add item relative to current path
                    items.add(new File(workingDir + File.separator + arg));
                    break;
            }
        }

          // if asked for help, print the message and quit
        if ((options & HELPME) == HELPME) { FileList.displayHelp(); System.exit(0); }

        FileList localItems;

          // if no parameters are passed, default to adding the current working directory.
        if (items.isEmpty()) { 
            try { localItems = FileList.of(workingDir, options); } 
            catch (Exception e) { 
                System.out.println(e.getMessage());
                localItems = FileList.empty(options);
            }
        } else localItems = FileList.empty(options);

          // subdirItems will contain contents of parameters that are directories, 
          // and recursively added subfolders
        List<FileList> subdirItems = new ArrayList<FileList>();

          // now loop through passed or globbed file parameters
        for (File item : items) {
            try {
                if (item.isDirectory()) {
                    numItems++;
                    subdirItems.add(FileList.of(item.getCanonicalPath(), options));
                } else localItems.add(item);  // if its not a directory is a globbed file - add to localItems
            } catch (Exception e) { System.out.println(e.getMessage()); }
        }
         
          // if recursing, add subdirs from the localItems first
        for (String subDir : localItems.subDirectories()) {
            try { 
                numItems++;
                subdirItems.add(FileList.of(subDir, options));
            } catch (Exception e) { System.out.println(e.getMessage()); }
        }
        
          // first print any items in the current working dir i.e. result of globbing or no parameters at all
        if (!localItems.isEmpty()) {
            try {
                index++;
                List<String> names = localItems.format();
                if ((localItems.activeOptions & REVERSE) == REVERSE) Collections.reverse(names);
                for (String name : names) System.out.println(name);
            } catch (Exception e) { System.out.println(e.getMessage()); }
        }

          // print the contents of any explicitly passed folders
          // use old-fashioned for loop because we might be adding to the list
          // if we are recursing subdirs
        for (int i = 0; i < subdirItems.size(); i++) {
            FileList fl = subdirItems.get(i);

              // add subdirectories if we're recursing
            for (String subDir : fl.subDirectories()) {
                try {
                    numItems++;
                    subdirItems.add(FileList.of(subDir, options));
                } catch (Exception e) { System.out.println(e.getMessage()); }
            }

              // print the path, and then the list of files.
            try {
                List<String> names = fl.format();
                if (index > 0) System.out.println();
                index++;
                if (numItems > 0) System.out.println(fl.path + ":");
                if ((fl.activeOptions & REVERSE) == REVERSE) Collections.reverse(names);
                for (String name : names) System.out.println(name);
            } catch (Exception e) { System.out.println(e.getMessage()); }
        }
    }

    
    // constants that represent options, to be bitmasked together to form one option parameter
    public static final int ALL      = 1;     // show hidden files except . and ..
    public static final int EXTENDED = 2;     // show extended listing data
    public static final int CANONICAL= 4;     // display path in canonical (absolute) format
    public static final int BYTIME   = 8;     // primary sort = date modified, secondary sort filename
    public static final int BYSIZE   = 16;    // primary sort = file size, secondary sort filename
    public static final int REVERSE  = 32;    // reverse order of primary sort
    public static final int RECURSE  = 64;    // follow subdirectories
    public static final int USECOLOR = 128;   // use ANSI escape sequences to color code file types
    public static final int HELPME   = 256;   // show help message
    public static final int NOORDER  = 512;   // use linked list instead of tree 

    private static final String ANSI_RESET     = "\033[0;0m";
    private static final String ANSI_DIRECTORY = "\033[34m";    // directory = blue
    private static final String ANSI_EXECUTE   = "\033[1m";     // execute = BOLD
    private static final String ANSI_READONLY  = "\033[2m";     // read-only = DIM
    private static final String ANSI_NOACCESS  = "\033[9m";     // no access = strike-thru
    private static final String ANSI_HIDDEN    = "\033[7m";     // hidden = inverted
 
    private static final Map<Character, Integer> OPTIONKEY = 
        Collections.unmodifiableMap(new HashMap<Character, Integer>() {{
            put('A', ALL);
            put('l', EXTENDED);
            put('c', CANONICAL);
            put('t', BYTIME);
            put('S', BYSIZE);
            put('r', REVERSE);
            put('R', RECURSE);
            put('C', USECOLOR);
            put('h', HELPME);
        }});  
    
    private static final Map<Character, String> OPTIONDESC = 
        Collections.unmodifiableMap(new HashMap<Character, String>() {{
            put('A', "do not ignore entries starting with .");
            put('l', "use a long listing format");
            put('c', "use canonical file path");
            put('t', "sort by modification time, newest first");
            put('S', "sort by file size, largest first");
            put('R', "recurse subdirectories");
            put('r', "reverse order while sorting");
            put('C', "colorize the output");
            put('h', "show help message");
        }}); 

    private int activeOptions;
    private int ownerLength;
    private int groupLength;
    private int sizeLength;
    private String formatString; 
    private String path;
    private Collection<File> files;
    private long thisYear;
    private List<String> subDirs;

    private static void displayHelp() {
        System.out.println("Usage: FileList [OPTION]... [FILE]...");
        System.out.println("List information about the FILEs (the current directory by default).");
        System.out.println("Sort entries alphabetically if none of -tSr is specified.");
        System.out.println();
        FileList.displayOptions();
    }

    private static void displayOptions() {
        System.out.println("FileList options:");
        for (Map.Entry<Character, String> entry : OPTIONDESC.entrySet()) 
            System.out.println("   -" + entry.getKey() + ":   " + entry.getValue());
        System.out.println(); 
    }
      // private constructor
    private FileList(int options) throws RuntimeException {
        this.activeOptions = options;
        this.ownerLength = 0;
        this.groupLength = 0;
        this.sizeLength  = 0;
        Comparator<File> compareBy;
          // depending on the sort order specified, we use a different comparator 
          // to build the tree 
        switch ( options & (BYTIME | BYSIZE) ) {
            case BYTIME:
                compareBy = new FileList.compareByTime();
                break;
            case BYSIZE:
                compareBy = new FileList.compareBySize();
                break;
            case BYTIME | BYSIZE:
                throw new RuntimeException("Can only sort by date or size, not both");
            default:
                compareBy = new FileList.compareByName();
                break;
        }
        if ((options & NOORDER) == NOORDER) files = new LinkedList<File>();  
        else files = new TreeSet<File>(compareBy);

          // get the epoch time of the start of the current year, so that the extended
          // format output can use MMM-DD HH:MM for files modified in the current year, 
          // or MMM-DD YYYY for files modified in a previous year like the real ls
        LocalDate d = LocalDate.ofYearDay(LocalDate.now().getYear(),1);
        ZoneId zone = ZoneId.systemDefault();    
        thisYear = 1000L * d.atStartOfDay(zone).toEpochSecond();
        subDirs = new ArrayList<String>();
    }
    
    private Iterable<String> subDirectories() {
        return subDirs;
    }

    private String formatExtended(File f) throws IOException {
       String output = "";
       PosixFileAttributes attrs = Files.readAttributes(f.toPath(), PosixFileAttributes.class); 

       Set<PosixFilePermission> permissions = attrs.permissions();
       SimpleDateFormat formatter;
       if (f.lastModified() > thisYear)
            formatter = new SimpleDateFormat("MMM dd HH:mm");
       else formatter = new SimpleDateFormat("MMM dd  YYYY");
       output = String.format(formatString,
           (f.isDirectory() ? "d" : "-") +
           PosixFilePermissions.toString(permissions),
           attrs.owner().getName(),
           attrs.group().getName(),
           attrs.size(),
           formatter.format(f.lastModified()),
           formatPlain(f)
           );
       return output; 
    }

    private String formatPlain(File f) throws IOException {
        String output = "";
        output = ((activeOptions & CANONICAL) == CANONICAL) ? f.getCanonicalPath() : f.getName();
        if (f.isDirectory()) output = output + File.separator;
        if ((activeOptions & USECOLOR) == USECOLOR) {
              // use ansi control codes, this should work in anything from vt100 thru windows console
            if (f.isHidden()) output = ANSI_HIDDEN + output;
            if (!f.canRead()) output = ANSI_NOACCESS + output;
            if (f.isDirectory()) output = ANSI_DIRECTORY + output;
            if (f.canExecute()) output = ANSI_EXECUTE + output;
            if (!f.canWrite()) output = ANSI_READONLY + output;
            output = output + ANSI_RESET;
        } 
        return output;
    }

    private class compareBySize implements Comparator<File> {
        public int compare(File a, File b) {
            long sizea = a.length();
            long sizeb = b.length();
            if (sizea == sizeb)
                return a.getName().compareTo(b.getName());
            return (int)(sizeb - sizea);  // this is backwards, because size sort is biggest first
        }
    }

    private class compareByTime implements Comparator<File> {
        public int compare(File a, File b) {
            long datea = a.lastModified();
            long dateb = b.lastModified();
            if (datea == dateb)
                return a.getName().compareTo(b.getName());
            return (int)(datea - dateb);
        }
    }

    private class compareByName implements Comparator<File> {
        public int compare(File a, File b) {
            return a.getName().compareTo(b.getName());
        }
    }
    
}

