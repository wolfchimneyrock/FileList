# FileList
## Clone of unix commands ls and find in java 
This was a project for my class 'Application design and implementation' CISC3120.  The intent was to design an extensible modular framework that is able to be used for various file system related tasks.  

## Key features:
### FileList:
1.  Plain and Extended format output
2.  Colored output based on filetype
3.  Sort by name, size, date
4.  Recursive listing of subdirectories

### Find:
1.  globbing, regex pattern matching
2.  pruning based on pattern
3.  features inherited from FileList above

## Implementation details
1.  Sub-directory recursion uses a breadth-first traversal with an ArrayList, and filters based on explicit pattern and permission.
2.  Sorting is accomplished with custom Comparators to build a TreeSet.  This has the benefit of being extensible by defining new comparators.  The downside is that it does not support a secondary sort key.
3.  Colored output is accomplished with ANSI control codes, which are compatible with nearly any VT100 compatible terminal interface, including any modern Linux terminal, Windows command prompt, and MacOSX terminal.
4.  grabs PosixFilePermissions, and may not work as expected on windows.
